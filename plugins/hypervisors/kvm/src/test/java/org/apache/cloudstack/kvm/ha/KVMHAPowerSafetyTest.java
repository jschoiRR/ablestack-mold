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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.resource.ResourceState;
import org.apache.cloudstack.api.response.OutOfBandManagementResponse;
import org.apache.cloudstack.ha.HAConfig;
import org.apache.cloudstack.ha.HAManager;
import org.apache.cloudstack.ha.HAResource;
import org.apache.cloudstack.ha.dao.HAConfigDao;
import org.apache.cloudstack.ha.provider.HAFenceException;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagement.PowerOperation;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagement.PowerState;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagementService;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class KVMHAPowerSafetyTest {
    @Mock private HostVO host;
    @Mock private HostDao hostDao;
    @Mock private HAConfigDao haConfigDao;
    @Mock private HAConfig haConfig;
    @Mock private HAManager haManager;
    @Mock private OutOfBandManagementService powerService;
    private KVMHAProvider provider;
    private AtomicLong now;

    @Before
    public void setup() throws Exception {
        provider = spy(new KVMHAProvider());
        provider.outOfBandManagementService = powerService;
        provider.kvmHostDao = hostDao;
        provider.kvmHAConfigDao = haConfigDao;
        provider.kvmHAManager = haManager;
        now = new AtomicLong();
        doAnswer(call -> now.get()).when(provider).nanoTime();
        doAnswer(call -> { now.addAndGet(TimeUnit.SECONDS.toNanos(call.getArgument(0, Long.class))); return null; })
                .when(provider).waitForPowerObservation(anyLong());
        doReturn(true).when(provider).isPowerOffCheckEnabled(host);
        doReturn(3L).when(provider).getPowerOffConfirmations(host);
        doReturn(1L).when(provider).getPowerCheckInterval(host);
        doReturn(2L).when(provider).getPowerCheckTimeout(host);
        doReturn(10L).when(provider).getHealthCheckTimeout(host);
        doReturn(60L).when(provider).getFenceTimeout(host);
        when(host.getId()).thenReturn(42L);
        when(host.getResourceState()).thenReturn(ResourceState.Maintenance);
        when(hostDao.findById(42L)).thenReturn(host);
        when(haConfigDao.findHAResource(42L, HAResource.ResourceType.Host)).thenReturn(haConfig);
        when(haConfig.isEnabled()).thenReturn(true);
        when(haConfig.getState()).thenReturn(HAConfig.HAState.Fencing);
        when(haConfig.getManagementServerId()).thenReturn(ManagementServerNode.getManagementServerId());
        when(haManager.isHAEligible(host)).thenReturn(true);
        when(powerService.isOutOfBandManagementEnabled(host)).thenReturn(true);
        when(powerService.executePowerOperation(eq(host), eq(PowerOperation.OFF), anyLong())).thenReturn(response(PowerState.On));
        when(powerService.executePowerOperation(eq(host), eq(PowerOperation.ON), anyLong())).thenReturn(response(PowerState.Off));
    }

    private OutOfBandManagementResponse response(PowerState state) {
        OutOfBandManagementResponse response = new OutOfBandManagementResponse();
        response.setSuccess(true);
        response.setPowerState(state);
        return response;
    }

    @Test
    public void requiresThreeSeparatedFreshOffResponses() throws Exception {
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L)).thenReturn(response(PowerState.Off));
        assertTrue(provider.isPowerOffConfirmed(host));
        verify(powerService, times(3)).executePowerOperation(host, PowerOperation.STATUS, 2L);
        assertEquals(TimeUnit.SECONDS.toNanos(2), now.get());
        verify(powerService, never()).executePowerOperation(eq(host), eq(PowerOperation.OFF), anyLong());
        verify(powerService, never()).executePowerOperation(eq(host), eq(PowerOperation.ON), anyLong());
    }

    @Test
    public void onResponseBreaksEarlyConfirmation() throws Exception {
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L))
                .thenReturn(response(PowerState.Off), response(PowerState.On), response(PowerState.Off));
        assertFalse(provider.isPowerOffConfirmed(host));
        verify(powerService, times(2)).executePowerOperation(host, PowerOperation.STATUS, 2L);
    }

    @Test
    public void unknownDoesNotConfirmPowerOff() throws Exception {
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L))
                .thenReturn(response(PowerState.Off), response(PowerState.Unknown));
        assertFalse(provider.isPowerOffConfirmed(host));
    }

    @Test
    public void failedResponseCannotCarryOffEvidence() throws Exception {
        OutOfBandManagementResponse failed = response(PowerState.Off);
        failed.setSuccess(false);
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L)).thenReturn(failed);
        assertFalse(provider.isPowerOffConfirmed(host));
    }

    @Test
    public void nullOrExceptionNeverConfirmsOff() throws Exception {
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L)).thenReturn(null);
        assertFalse(provider.isPowerOffConfirmed(host));
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L)).thenThrow(new IllegalStateException("BMC timeout"));
        assertFalse(provider.isPowerOffConfirmed(host));
    }

    @Test
    public void tooFewConfirmationsDisableFastProof() throws Exception {
        doReturn(2L).when(provider).getPowerOffConfirmations(host);
        assertFalse(provider.isPowerOffConfirmed(host));
        verify(powerService, never()).executePowerOperation(eq(host), eq(PowerOperation.STATUS), anyLong());
    }

    @Test
    public void observationBudgetMustFitHealthTimeout() throws Exception {
        doReturn(5L).when(provider).getHealthCheckTimeout(host);
        assertFalse(provider.isPowerOffConfirmed(host));
        verify(powerService, never()).executePowerOperation(eq(host), eq(PowerOperation.STATUS), anyLong());
    }

    @Test
    public void lateOffResponseIsNotFreshProof() throws Exception {
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L)).thenAnswer(call -> {
            now.addAndGet(TimeUnit.SECONDS.toNanos(11));
            return response(PowerState.Off);
        });
        assertFalse(provider.isPowerOffConfirmed(host));
    }

    @Test
    public void rebootRequiresOffVerificationBeforeOn() throws Exception {
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L)).thenReturn(response(PowerState.Off));
        assertTrue(provider.fence(host));
        InOrder ordered = inOrder(powerService);
        ordered.verify(powerService).executePowerOperation(eq(host), eq(PowerOperation.OFF), anyLong());
        ordered.verify(powerService, times(3)).executePowerOperation(host, PowerOperation.STATUS, 2L);
        ordered.verify(powerService).executePowerOperation(eq(host), eq(PowerOperation.ON), anyLong());
        verify(powerService, never()).executePowerOperation(eq(host), eq(PowerOperation.CYCLE), anyLong());
    }

    @Test
    public void transientOffDoesNotCompleteFencing() throws Exception {
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L))
                .thenReturn(response(PowerState.Off), response(PowerState.Off), response(PowerState.Unknown));
        assertFalse(provider.fence(host));
        verify(powerService, never()).executePowerOperation(eq(host), eq(PowerOperation.ON), anyLong());
    }

    @Test
    public void fenceWaitsForThreeOffAfterInterveningOn() throws Exception {
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L))
                .thenReturn(response(PowerState.Off), response(PowerState.On), response(PowerState.Off), response(PowerState.Off), response(PowerState.Off));
        assertTrue(provider.fence(host));
        verify(powerService, times(5)).executePowerOperation(host, PowerOperation.STATUS, 2L);
    }

    @Test
    public void maintenanceRequiredBeforeAnyPowerMutation() throws Exception {
        when(host.getResourceState()).thenReturn(ResourceState.Enabled);
        assertFenceFails();
        verify(powerService, never()).executePowerOperation(any(), eq(PowerOperation.OFF), anyLong());
    }

    @Test
    public void disabledHaBlocksPowerMutation() throws Exception {
        when(haConfig.isEnabled()).thenReturn(false);
        assertFenceFails();
        verify(powerService, never()).executePowerOperation(any(), eq(PowerOperation.OFF), anyLong());
    }

    @Test
    public void unownedFencingCannotChangePower() throws Exception {
        when(haConfig.getManagementServerId()).thenReturn(null);
        assertFenceFails();
        verify(powerService, never()).executePowerOperation(any(), eq(PowerOperation.OFF), anyLong());
    }

    @Test
    public void ownershipLossBetweenOffAndOnStopsReboot() throws Exception {
        when(haConfig.getManagementServerId()).thenReturn(ManagementServerNode.getManagementServerId(),
                ManagementServerNode.getManagementServerId(), null);
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L)).thenReturn(response(PowerState.Off));
        assertFenceFails();
        verify(powerService, never()).executePowerOperation(any(), eq(PowerOperation.ON), anyLong());
    }

    @Test
    public void maintenanceIsRecheckedBeforeRebootOn() throws Exception {
        when(host.getResourceState()).thenReturn(ResourceState.Maintenance, ResourceState.Enabled);
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L)).thenReturn(response(PowerState.Off));
        assertFenceFails();
        verify(powerService, never()).executePowerOperation(any(), eq(PowerOperation.ON), anyLong());
    }

    @Test
    public void parentHaDisableBetweenOffAndOnStopsReboot() throws Exception {
        when(haManager.isHAEligible(host)).thenReturn(true, false);
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L)).thenReturn(response(PowerState.Off));
        assertFenceFails();
        verify(powerService, never()).executePowerOperation(any(), eq(PowerOperation.ON), anyLong());
    }

    @Test
    public void onFailureDoesNotCompleteFencing() throws Exception {
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L)).thenReturn(response(PowerState.Off));
        OutOfBandManagementResponse failed = response(PowerState.Unknown);
        failed.setSuccess(false);
        when(powerService.executePowerOperation(eq(host), eq(PowerOperation.ON), anyLong())).thenReturn(failed);
        assertFenceFails();
    }

    @Test
    public void insufficientFenceBudgetDoesNotPowerOffHost() throws Exception {
        doReturn(20L).when(provider).getFenceTimeout(host);
        assertFenceFails();
        verify(powerService, never()).executePowerOperation(any(), eq(PowerOperation.OFF), anyLong());
    }

    @Test
    public void interruptionStopsRebootSequence() throws Exception {
        when(powerService.executePowerOperation(host, PowerOperation.STATUS, 2L)).thenReturn(response(PowerState.Off));
        doThrow(new InterruptedException("cancelled")).when(provider).waitForPowerObservation(anyLong());
        try {
            assertFenceFails();
            assertTrue(Thread.currentThread().isInterrupted());
            verify(powerService, never()).executePowerOperation(any(), eq(PowerOperation.ON), anyLong());
        } finally {
            Thread.interrupted();
        }
    }

    private void assertFenceFails() throws Exception {
        try {
            provider.fence(host);
            fail("Unsafe or incomplete fencing must not succeed");
        } catch (HAFenceException expected) {
            // Maintenance is deliberately retained for a safe retry.
        }
    }
}
