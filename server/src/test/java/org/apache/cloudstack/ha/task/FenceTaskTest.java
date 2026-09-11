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
package org.apache.cloudstack.ha.task;

import org.apache.cloudstack.ha.HAConfig;
import org.apache.cloudstack.ha.HAConfigVO;
import org.apache.cloudstack.ha.HAManager;
import org.apache.cloudstack.ha.HAResource;
import org.apache.cloudstack.ha.HAResourceCounter;
import org.apache.cloudstack.ha.provider.HAProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FenceTaskTest {
    private HAProvider<HAResource> provider;
    private HAResource resource;
    private HAManager manager;
    private HAConfigVO config;
    private boolean current;
    private FenceTask task;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        provider = mock(HAProvider.class);
        resource = mock(HAResource.class);
        manager = mock(HAManager.class);
        config = new HAConfigVO();
        config.setResourceId(7L);
        config.setResourceType(HAResource.ResourceType.Host);
        config.setHastate(HAConfig.HAState.Fencing);
        current = true;
        final HAResourceCounter counter = new HAResourceCounter();
        task = new FenceTask(resource, provider, config, HAProvider.HAProviderConfig.FenceTimeout, null) {
            @Override protected HAManager getHaManager() { return manager; }
            @Override protected HAResourceCounter getCounter() { return counter; }
            @Override protected boolean isCurrentTask() { return current; }
        };
    }

    @Test
    public void maintenancePrecedesPhysicalFence() throws Exception {
        when(provider.fence(resource)).thenReturn(true);
        assertTrue(task.performAction());
        InOrder order = inOrder(provider);
        order.verify(provider).enableMaintenance(resource);
        order.verify(provider).prepareFenceSubResources(resource);
        order.verify(provider).fence(resource);
    }

    @Test(expected = IllegalStateException.class)
    public void failedMaintenanceCannotPowerCycle() throws Exception {
        doThrow(new IllegalStateException("write failed")).when(provider).enableMaintenance(resource);
        try {
            task.performAction();
        } finally {
            verify(provider, never()).fence(any());
        }
    }

    @Test
    public void failedStateCheckpointCannotQueueVms() {
        task.processResult(true, null);
        verify(provider, never()).fenceSubResources(any());
        verify(manager, never()).disableHA(anyLong(), any());
    }

    @Test
    public void checkpointsFenceBeforeQueueAndDisablesOnlyAfterQueue() {
        doAnswer(invocation -> { config.setHastate(HAConfig.HAState.Fenced); return true; })
                .when(manager).transitionHAState(HAConfig.Event.Fenced, config);
        task.processResult(true, null);
        InOrder order = inOrder(manager, provider);
        order.verify(manager).transitionHAState(HAConfig.Event.Fenced, config);
        order.verify(provider).enableMaintenance(resource);
        order.verify(provider).fenceSubResources(resource);
        order.verify(manager).disableHA(7L, HAResource.ResourceType.Host);
    }

    @Test
    public void fencedCheckpointResumesWithoutAnotherPowerCycle() throws Exception {
        config.setHastate(HAConfig.HAState.Fenced);
        assertTrue(task.performAction());
        task.processResult(true, null);
        verify(provider, never()).fence(any());
        verify(provider, never()).prepareFenceSubResources(any());
        verify(provider).fenceSubResources(resource);
        verify(manager).disableHA(7L, HAResource.ResourceType.Host);
    }

    @Test(expected = IllegalStateException.class)
    public void queueFailureLeavesHaEnabledForFinalizerRetry() {
        config.setHastate(HAConfig.HAState.Fenced);
        doThrow(new IllegalStateException("queue unavailable")).when(provider).fenceSubResources(resource);
        try {
            task.processResult(true, null);
        } finally {
            verify(manager, never()).disableHA(anyLong(), any());
        }
    }

    @Test
    public void staleOrFailedCompletionCannotQueueVms() {
        current = false;
        task.processResult(true, null);
        current = true;
        task.processResult(true, new IllegalStateException("failure"));
        task.processResult(false, null);
        verify(provider, never()).fenceSubResources(any());
        verify(manager, never()).transitionHAState(any(), any());
    }
}
