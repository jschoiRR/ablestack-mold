/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cloudstack.kvm.ha;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.cloudstack.api.response.OutOfBandManagementResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.impl.ConfigDepotImpl;
import org.apache.cloudstack.ha.HAConfig;
import org.apache.cloudstack.ha.HAManager;
import org.apache.cloudstack.ha.HAResource;
import org.apache.cloudstack.ha.dao.HAConfigDao;
import org.apache.cloudstack.ha.provider.HACheckerException;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagement.PowerOperation;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagement.PowerState;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagementService;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.PcsFenceHostCommand;
import com.cloud.exception.StorageUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.resource.ResourceState;

@RunWith(MockitoJUnitRunner.class)
public class KVMHostHATest {

    @Mock
    private Host host;
    @Mock
    private KVMHostActivityChecker kvmHostActivityChecker;
    private KVMHAProvider kvmHAProvider;
    private ConfigDepotImpl originalDepot;

    @Before
    public void setup() {
        originalDepot = (ConfigDepotImpl) ReflectionTestUtils.getField(ConfigKey.class, "s_depot");
        kvmHAProvider = new KVMHAProvider();
        kvmHAProvider.hostActivityChecker = kvmHostActivityChecker;
    }

    @After
    public void restoreConfiguration() {
        ConfigKey.init(originalDepot);
    }

    @Test
    public void testHostActivityForHealthyHost() throws HACheckerException, StorageUnavailableException {
        lenient().when(host.getHypervisorType()).thenReturn(HypervisorType.KVM);
        when(kvmHostActivityChecker.isHealthy(host)).thenReturn(true);
        assertTrue(kvmHAProvider.isHealthy(host));
    }

    @Test
    public void testHostActivityForUnHealthyHost() throws HACheckerException, StorageUnavailableException {
        lenient().when(host.getHypervisorType()).thenReturn(HypervisorType.KVM);
        when(kvmHostActivityChecker.isHealthy(host)).thenReturn(false);
        assertFalse(kvmHAProvider.isHealthy(host));
    }

    @Test
    public void testHostActivityForActiveHost() throws HACheckerException, StorageUnavailableException {
        lenient().when(host.getHypervisorType()).thenReturn(HypervisorType.KVM);
        DateTime dt = new DateTime();
        when(kvmHostActivityChecker.isActive(host, dt)).thenReturn(true);
        assertTrue(kvmHAProvider.hasActivity(host, dt));
    }

    @Test
    public void testHostActivityForDownHost() throws HACheckerException, StorageUnavailableException {
        lenient().when(host.getHypervisorType()).thenReturn(HypervisorType.KVM);
        DateTime dt = new DateTime();
        when(kvmHostActivityChecker.isActive(host, dt)).thenReturn(false);
        assertFalse(kvmHAProvider.hasActivity(host, dt));
    }

    @Test
    public void testUnavailableIpmiAllowsFreshPacemakerPowerOff() throws Exception {
        setUpFencing(true);
        when(kvmHAProvider.outOfBandManagementService.executePowerOperation(eq(host), eq(PowerOperation.OFF), anyLong()))
                .thenThrow(new IllegalStateException("BMC unavailable"));
        when(kvmHostActivityChecker.sendProbe(eq(2L), any(PcsFenceHostCommand.class), anyLong())).thenAnswer(call -> {
            PcsFenceHostCommand command = call.getArgument(1);
            return new Answer(command, "mold-ha".equals(command.getClusterName())
                    && "node-a".equals(command.getTargetNode()) && "node-b".equals(command.getExecutorNode()), null);
        });

        assertTrue(kvmHAProvider.fence(host));
        verify(kvmHAProvider.outOfBandManagementService, never()).executePowerOperation(any(), eq(PowerOperation.ON), anyLong());
    }

    @Test
    public void testUnavailableIpmiAndUnconfirmedPacemakerFenceKeepHostQuarantined() throws Exception {
        setUpFencing(true);
        when(kvmHostActivityChecker.sendProbe(eq(2L), any(PcsFenceHostCommand.class), anyLong()))
                .thenReturn(new Answer(null, false, "quorum unavailable"));

        assertFalse(kvmHAProvider.fence(host));
    }

    @Test
    public void testConfirmedIpmiPowerOffDoesNotRequirePowerOn() throws Exception {
        setUpFencing(false);
        OutOfBandManagementResponse off = new OutOfBandManagementResponse();
        off.setSuccess(true);
        off.setPowerState(PowerState.Off);
        when(kvmHAProvider.outOfBandManagementService.executePowerOperation(eq(host), eq(PowerOperation.OFF), anyLong())).thenReturn(off);
        when(kvmHAProvider.outOfBandManagementService.executePowerOperation(eq(host), eq(PowerOperation.STATUS), anyLong())).thenReturn(off);

        assertTrue(kvmHAProvider.fence(host));
        verify(kvmHAProvider.outOfBandManagementService, never()).executePowerOperation(any(), eq(PowerOperation.ON), anyLong());
    }

    private void setUpFencing(boolean configurePcs) {
        final String targetUuid = "e0da975a-59bf-45f8-a8fb-4cbe1d3c63b1";
        final String peerUuid = "3cd37a20-1a9e-4c07-ae77-f70b266b7260";
        final ConfigDepotImpl depot = mock(ConfigDepotImpl.class);
        ConfigKey.init(depot);
        when(host.getId()).thenReturn(1L);
        when(host.getUuid()).thenReturn(targetUuid);
        when(host.getClusterId()).thenReturn(99L);
        kvmHAProvider.kvmHostDao = mock(HostDao.class);
        kvmHAProvider.kvmHAConfigDao = mock(HAConfigDao.class);
        kvmHAProvider.kvmHAManager = mock(HAManager.class);
        kvmHAProvider.outOfBandManagementService = mock(OutOfBandManagementService.class);
        final HostVO target = mock(HostVO.class);
        when(target.getUuid()).thenReturn(targetUuid);
        when(target.getResourceState()).thenReturn(ResourceState.Maintenance);
        when(kvmHAProvider.kvmHostDao.findById(1L)).thenReturn(target);
        when(kvmHAProvider.kvmHAManager.isHAEligible(target)).thenReturn(true);
        final HAConfig config = mock(HAConfig.class);
        when(config.getState()).thenReturn(HAConfig.HAState.Fencing);
        when(config.isEnabled()).thenReturn(true);
        when(config.getManagementServerId()).thenReturn(ManagementServerNode.getManagementServerId());
        when(kvmHAProvider.kvmHAConfigDao.findHAResource(1L, HAResource.ResourceType.Host)).thenReturn(config);
        when(kvmHAProvider.outOfBandManagementService.isOutOfBandManagementEnabled(host)).thenReturn(true);
        if (configurePcs) {
            when(depot.getConfigStringValue(KVMHAConfig.KvmHAPcsFenceCluster.key(), ConfigKey.Scope.Cluster, 99L)).thenReturn("mold-ha");
            when(depot.getConfigStringValue(KVMHAConfig.KvmHAPcsFenceNodeMap.key(), ConfigKey.Scope.Cluster, 99L))
                    .thenReturn(targetUuid + "=node-a," + peerUuid + "=node-b");
            final HostVO peer = mock(HostVO.class);
            when(peer.getUuid()).thenReturn(peerUuid);
            when(peer.getClusterId()).thenReturn(99L);
            when(peer.getState()).thenReturn(Status.Up);
            when(kvmHAProvider.kvmHostDao.findById(2L)).thenReturn(peer);
            when(kvmHostActivityChecker.getNeighbors(host)).thenReturn(new long[]{2L});
        } else {
            when(depot.getConfigStringValue(KVMHAConfig.KvmHAFencePowerOffConfirmations.key(), ConfigKey.Scope.Cluster, 99L)).thenReturn("3");
            when(depot.getConfigStringValue(KVMHAConfig.KvmHAPowerCheckInterval.key(), ConfigKey.Scope.Cluster, 99L)).thenReturn("1");
        }
    }
}
