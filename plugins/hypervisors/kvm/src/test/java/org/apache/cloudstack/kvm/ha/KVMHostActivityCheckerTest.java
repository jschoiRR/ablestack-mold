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

import org.junit.Before;
import com.cloud.agent.api.CheckOnHostAnswer;
import com.cloud.agent.api.CheckOnHostCommand;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.resource.ResourceManager;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.Storage.StoragePoolType;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.storage.datastore.db.PrimaryDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.StoragePoolVO;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagement.PowerState;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagementVO;
import org.apache.cloudstack.outofbandmanagement.dao.OutOfBandManagementDao;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.lenient;
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

    @Mock private VMInstanceDao vmInstanceDao;
    @Mock private VolumeDao volumeDao;
    @Mock private PrimaryDataStoreDao storagePoolDao;
    @Mock private ResourceManager resourceManager;
    @Mock private OutOfBandManagementDao outOfBandManagementDao;
    private HostVO host;
    private HostVO neighbor;

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
    @Before
    public void setUp() {
        host = mock(HostVO.class);
        neighbor = mock(HostVO.class);
        lenient().when(host.getId()).thenReturn(1L);
        lenient().when(host.getClusterId()).thenReturn(7L);
        lenient().when(host.getHypervisorType()).thenReturn(HypervisorType.KVM);
        lenient().when(neighbor.getId()).thenReturn(2L);
        lenient().when(neighbor.getHypervisorType()).thenReturn(HypervisorType.KVM);
        lenient().when(resourceManager.listHostsInClusterByStatus(7L, Status.Up)).thenReturn(List.of(host, neighbor));
    }

    private CheckOnHostAnswer observation(Boolean alive) {
        return new CheckOnHostAnswer(new CheckOnHostCommand(host), alive, "fixture observation");
    }

    @Test
    public void onlySuccessfulDeterminedAnswersEstablishLifeOrDeath() {
        assertEquals(Status.Disconnected, checker.getDeterminedHostStatus(null));
        assertEquals(Status.Disconnected, checker.getDeterminedHostStatus(new Answer(null, true, "wrong reply")));
        assertEquals(Status.Disconnected, checker.getDeterminedHostStatus(new CheckOnHostAnswer(new CheckOnHostCommand(host), "transport error")));
        assertEquals(Status.Disconnected, checker.getDeterminedHostStatus(observation(null)));
        assertEquals(Status.Up, checker.getDeterminedHostStatus(observation(true)));
        assertEquals(Status.Down, checker.getDeterminedHostStatus(observation(false)));
    }

    @Test
    public void missingOrUnknownOobmCannotOverrideAliveNeighbor() throws Exception {
        doReturn(null).when(checker).sendHealthCheck(eq(1L), any(CheckOnHostCommand.class), anyLong());
        doReturn(observation(true)).when(checker).sendHealthCheck(eq(2L), any(CheckOnHostCommand.class), anyLong());
        assertEquals(Status.Disconnected, checker.getHostAgentStatus(host));
        OutOfBandManagementVO oobm = mock(OutOfBandManagementVO.class);
        when(outOfBandManagementDao.findByHost(1L)).thenReturn(oobm);
        when(oobm.getPowerState()).thenReturn(PowerState.Unknown);
        assertEquals(Status.Disconnected, checker.getHostAgentStatus(host));
        when(oobm.getPowerState()).thenReturn(PowerState.Off);
        assertEquals(Status.Down, checker.getHostAgentStatus(host));
    }

    @Test
    public void unknownNeighborsDoNotProveHostDeath() throws Exception {
        doReturn(observation(null)).when(checker).sendHealthCheck(eq(1L), any(CheckOnHostCommand.class), anyLong());
        doReturn(new Answer(null, false, "agent unavailable")).when(checker).sendHealthCheck(eq(2L), any(CheckOnHostCommand.class), anyLong());
        assertEquals(Status.Disconnected, checker.getHostAgentStatus(host));
        doReturn(observation(false)).when(checker).sendHealthCheck(eq(2L), any(CheckOnHostCommand.class), anyLong());
        assertEquals(Status.Down, checker.getHostAgentStatus(host));
        doReturn(observation(true)).when(checker).sendHealthCheck(eq(1L), any(CheckOnHostCommand.class), anyLong());
        assertEquals(Status.Up, checker.getHostAgentStatus(host));
    }

    @Test
    public void rbdVolumesAreForwardedOnlyForOptedInPool() {
        VMInstanceVO vm = mock(VMInstanceVO.class);
        when(vm.getId()).thenReturn(11L);
        when(vmInstanceDao.listByHostId(1L)).thenReturn(List.of(vm));
        VolumeVO volume = mock(VolumeVO.class);
        when(volume.getPoolId()).thenReturn(21L);
        when(volume.getPath()).thenReturn("rbd-volume-a");
        when(volumeDao.findByInstance(11L)).thenReturn(List.of(volume));
        StoragePoolVO pool = mock(StoragePoolVO.class);
        when(pool.getPoolType()).thenReturn(StoragePoolType.RBD);
        when(storagePoolDao.findById(21L)).thenReturn(pool);
        KVMHostActivityChecker spy = checker;
        doReturn(false).when(spy).isStoragePoolHeartbeatEnabled(pool);
        assertNull(spy.createHostCheckCommand(host, false).getVolumeList());
        doReturn(true).when(spy).isStoragePoolHeartbeatEnabled(pool);
        CheckOnHostCommand command = spy.createHostCheckCommand(host, true);
        assertEquals("rbd-volume-a", command.getVolumeList());
        assertTrue(command.shouldReportIfHeartBeatFailedForOneStoragePool());
        assertEquals(20, command.getWait());
    }
}
