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
package org.apache.cloudstack.ha.provider.host;

import com.cloud.agent.AgentManager;
import com.cloud.ha.HighAvailabilityManager;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceState;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HAAbstractHostProviderTest {
    private HAAbstractHostProvider provider;
    private HostVO stale;
    private HostVO current;

    @Before
    public void setUp() {
        provider = mock(HAAbstractHostProvider.class, CALLS_REAL_METHODS);
        provider.hostDao = mock(HostDao.class);
        provider.hostDetailsDao = mock(HostDetailsDao.class);
        provider.vmInstanceDao = mock(VMInstanceDao.class);
        provider.agentManager = mock(AgentManager.class);
        provider.resourceManager = mock(ResourceManager.class);
        provider.oldHighAvailabilityManager = mock(HighAvailabilityManager.class);
        stale = mock(HostVO.class);
        current = mock(HostVO.class);
        when(stale.getId()).thenReturn(7L);
        when(current.getId()).thenReturn(7L);
        when(provider.hostDao.findById(7L)).thenReturn(current);
    }

    @Test
    public void persistsMaintenanceBeforeBlockingConnectedAgent() throws Exception {
        when(current.getResourceState()).thenReturn(ResourceState.Enabled, ResourceState.Maintenance);
        when(provider.resourceManager.resourceStateTransitTo(eq(current), eq(ResourceState.Event.InternalEnterMaintenance), anyLong())).thenReturn(true);

        provider.enableMaintenance(stale);

        InOrder order = inOrder(provider.resourceManager, provider.agentManager);
        order.verify(provider.resourceManager).resourceStateTransitTo(eq(current), eq(ResourceState.Event.InternalEnterMaintenance), anyLong());
        order.verify(provider.agentManager).pullAgentToMaintenance(7L);
    }

    @Test
    public void maintenanceReentryUsesFreshState() throws Exception {
        when(current.getResourceState()).thenReturn(ResourceState.Maintenance);
        provider.enableMaintenance(stale);
        verify(provider.resourceManager, never()).resourceStateTransitTo(eq(current), eq(ResourceState.Event.InternalEnterMaintenance), anyLong());
        verify(provider.agentManager).pullAgentToMaintenance(7L);
    }

    @Test(expected = CloudRuntimeException.class)
    public void failedMaintenanceWriteDoesNotProceed() throws Exception {
        when(current.getResourceState()).thenReturn(ResourceState.Enabled);
        try {
            provider.enableMaintenance(stale);
        } finally {
            verify(provider.agentManager, never()).pullAgentToMaintenance(anyLong());
        }
    }

    @Test
    public void alreadyDownHostStillRegistersDurableRecovery() {
        when(current.getResourceState()).thenReturn(ResourceState.Maintenance);
        when(current.getState()).thenReturn(Status.Down);
        when(provider.hostDetailsDao.findDetails(7L)).thenReturn(Map.of(HAAbstractHostProvider.FENCE_ROSTER, "1",
                HAAbstractHostProvider.FENCE_VM_PREFIX + "11", "vm-11"));
        provider.fenceSubResources(stale);
        verify(provider.agentManager, never()).disconnectWithoutInvestigation(anyLong(), eq(Status.Event.HostDown));
        verify(provider.oldHighAvailabilityManager).scheduleRestartForFencedVms(current, Map.of(11L, "vm-11"));
        verify(provider.vmInstanceDao, never()).listByHostId(anyLong());
    }

    @Test(expected = CloudRuntimeException.class)
    public void cannotRegisterFencedRecoveryAfterMaintenanceWasCancelled() {
        when(current.getResourceState()).thenReturn(ResourceState.Enabled);
        provider.fenceSubResources(stale);
    }

    @Test
    public void persistsRosterBeforePowerAndKeepsItWhenRebootClearsHostMembership() {
        when(current.getResourceState()).thenReturn(ResourceState.Maintenance);
        Map<String, String> details = new HashMap<>();
        when(provider.hostDetailsDao.findDetails(7L)).thenAnswer(invocation -> new HashMap<>(details));
        doAnswer(invocation -> { details.putAll(invocation.getArgument(1)); return null; })
                .when(provider.hostDetailsDao).persist(eq(7L), org.mockito.ArgumentMatchers.anyMap());
        VMInstanceVO vm = mock(VMInstanceVO.class);
        when(vm.getId()).thenReturn(11L);
        when(vm.getUuid()).thenReturn("vm-11");
        when(provider.vmInstanceDao.listByHostId(7L)).thenReturn(List.of(vm), List.of());
        provider.prepareFenceSubResources(stale);
        provider.prepareFenceSubResources(stale);
        provider.fenceSubResources(stale);
        verify(provider.oldHighAvailabilityManager).scheduleRestartForFencedVms(current, Map.of(11L, "vm-11"));
    }

    @Test(expected = CloudRuntimeException.class)
    public void rosterWriteMustBeReadBackBeforeFencingCanProceed() {
        when(current.getResourceState()).thenReturn(ResourceState.Maintenance);
        provider.prepareFenceSubResources(stale);
    }

    @Test(expected = CloudRuntimeException.class)
    public void corruptedRosterCannotAuthorizeRecovery() {
        when(current.getResourceState()).thenReturn(ResourceState.Maintenance);
        when(provider.hostDetailsDao.findDetails(7L)).thenReturn(Map.of(HAAbstractHostProvider.FENCE_ROSTER, "1",
                HAAbstractHostProvider.FENCE_VM_PREFIX + "invalid", "vm-11"));
        provider.fenceSubResources(stale);
    }

    @Test(expected = CloudRuntimeException.class)
    public void failedQueueKeepsPersistedRosterForRetry() {
        when(current.getResourceState()).thenReturn(ResourceState.Maintenance);
        when(provider.hostDetailsDao.findDetails(7L)).thenReturn(Map.of(HAAbstractHostProvider.FENCE_ROSTER, "1",
                HAAbstractHostProvider.FENCE_VM_PREFIX + "11", "vm-11"));
        doThrow(new CloudRuntimeException("queue write failed")).when(provider.oldHighAvailabilityManager)
                .scheduleRestartForFencedVms(current, Map.of(11L, "vm-11"));
        try {
            provider.fenceSubResources(stale);
        } finally {
            verify(provider.hostDetailsDao, never()).remove(anyLong());
        }
    }
}
