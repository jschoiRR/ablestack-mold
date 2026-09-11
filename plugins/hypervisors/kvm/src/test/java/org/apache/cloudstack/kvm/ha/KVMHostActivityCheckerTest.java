// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.kvm.ha;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.CheckVMActivityOnStoragePoolAnswer;
import com.cloud.agent.api.CheckVMActivityOnStoragePoolAnswer.ActivityState;
import com.cloud.host.Host;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StoragePool;
import com.cloud.storage.Volume;
import org.apache.cloudstack.ha.provider.HACheckerException;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(MockitoJUnitRunner.class)
public class KVMHostActivityCheckerTest {
    @Mock private StorageManager storageManager;
    @Mock private com.cloud.agent.AgentManager agentMgr;
    @Spy @InjectMocks private KVMHostActivityChecker checker = new KVMHostActivityChecker();

    private boolean check(Answer answer) throws Exception {
        Host host = mock(Host.class);
        StoragePool pool = mock(StoragePool.class);
        when(host.getClusterId()).thenReturn(1L);
        HashMap<StoragePool, List<Volume>> volumes = new HashMap<>();
        volumes.put(pool, Collections.emptyList());
        doReturn(new long[] {2L}).when(checker).getNeighbors(host);
        when(storageManager.getUpHostsInPool(pool.getId())).thenReturn(Collections.singletonList(2L));
        doReturn(answer).when(checker).sendProbe(eq(2L), any(Command.class), anyLong());
        return checker.verifyActivityOfStorageOnHost(volumes, pool, host, new DateTime(), true);
    }

    @Test public void knownAliveIsAlive() throws Exception {
        assertTrue(check(new CheckVMActivityOnStoragePoolAnswer(null, ActivityState.ALIVE, "alive")));
    }
    @Test public void knownDeadIsInactive() throws Exception {
        assertFalse(check(new CheckVMActivityOnStoragePoolAnswer(null, ActivityState.DEAD, "dead")));
    }
    @Test(expected = HACheckerException.class) public void explicitUnknownIsNotASample() throws Exception {
        check(new CheckVMActivityOnStoragePoolAnswer(null, ActivityState.UNKNOWN, "timeout"));
    }
    @Test(expected = HACheckerException.class) public void legacyFalseIsAmbiguous() throws Exception {
        check(new Answer(null, false, "could be alive or a failed probe"));
    }
    @Test(expected = HACheckerException.class) public void legacyTrueIsNotExplicitEvidence() throws Exception {
        check(new Answer(null));
    }
    @Test(expected = HACheckerException.class) public void missingResponseIsUnknown() throws Exception {
        check(null);
    }
    @Test public void nullStorageGuardIsPreserved() {
        assertFalse(checker.isStoragePoolHeartbeatEnabled(null));
    }
    @Test public void unknownHeartbeatCannotPassHealthCheck() {
        assertEquals(com.cloud.host.Status.Unknown, checker.healthStatus(new com.cloud.agent.api.CheckOnHostAnswer(null, (Boolean) null, "timeout")));
        assertEquals(com.cloud.host.Status.Unknown, checker.healthStatus(new Answer(null, false, "legacy error")));
        assertEquals(com.cloud.host.Status.Up, checker.healthStatus(new com.cloud.agent.api.CheckOnHostAnswer(null, Boolean.TRUE, "alive")));
    }
    @Test public void healthReadUsesAsyncReplyWithFiniteTimeout() throws Exception {
        com.cloud.agent.api.CheckOnHostCommand command = new com.cloud.agent.api.CheckOnHostCommand(mock(Host.class));
        Answer response = new com.cloud.agent.api.CheckOnHostAnswer(command, Boolean.TRUE, "alive");
        doAnswer(invocation -> {
            com.cloud.agent.Listener listener = invocation.getArgument(2);
            assertTrue(listener.getTimeout() > 0);
            listener.processAnswers(1L, 1L, new Answer[] {response});
            return 1L;
        }).when(agentMgr).send(eq(1L), any(com.cloud.agent.manager.Commands.class), any(com.cloud.agent.Listener.class));
        assertSame(response, checker.sendHealthCheck(1L, command, System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2)));
    }
    @Test(expected = java.util.concurrent.TimeoutException.class)
    public void expiredHealthBudgetDoesNotContactAgent() throws Exception {
        try {
            checker.sendHealthCheck(1L, new com.cloud.agent.api.CheckOnHostCommand(mock(Host.class)), System.nanoTime() - 1L);
        } finally {
            verifyNoInteractions(agentMgr);
        }
    }
    private boolean mixedActivity(ActivityState first, ActivityState second) throws Exception {
        Host host = mock(Host.class);
        when(host.getHypervisorType()).thenReturn(com.cloud.hypervisor.Hypervisor.HypervisorType.KVM);
        StoragePool firstPool = mock(StoragePool.class);
        StoragePool secondPool = mock(StoragePool.class);
        HashMap<StoragePool, List<Volume>> pools = new java.util.LinkedHashMap<>();
        pools.put(firstPool, Collections.emptyList());
        pools.put(secondPool, Collections.emptyList());
        doReturn(pools).when(checker).getVolumeUuidOnHost(host);
        doReturn(true).when(checker).isStoragePoolHeartbeatEnabled(any(StoragePool.class));
        doAnswer(invocation -> {
            ActivityState state = invocation.getArgument(1) == firstPool ? first : second;
            if (state == ActivityState.UNKNOWN) { throw new HACheckerException("unknown", null); }
            return state == ActivityState.ALIVE;
        }).when(checker).verifyActivityOfStorageOnHost(eq(pools), any(StoragePool.class), eq(host), any(DateTime.class), eq(true), anyLong());
        return checker.isActive(host, new DateTime());
    }
    @Test public void anyPoolActivityPreventsInactiveVerdict() throws Exception { assertTrue(mixedActivity(ActivityState.DEAD, ActivityState.ALIVE)); }
    @Test public void activityOutweighsUnavailableEarlierPool() throws Exception { assertTrue(mixedActivity(ActivityState.UNKNOWN, ActivityState.ALIVE)); }
    @Test(expected = HACheckerException.class) public void unavailablePoolPreventsAllDeadVerdict() throws Exception { mixedActivity(ActivityState.DEAD, ActivityState.UNKNOWN); }
    @Test public void allPoolsMustExplicitlyBeInactive() throws Exception { assertFalse(mixedActivity(ActivityState.DEAD, ActivityState.DEAD)); }
}
