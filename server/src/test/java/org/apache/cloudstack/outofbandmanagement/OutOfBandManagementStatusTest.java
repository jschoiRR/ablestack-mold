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

package org.apache.cloudstack.outofbandmanagement;

import com.cloud.host.Host;
import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.response.OutOfBandManagementResponse;
import org.apache.cloudstack.outofbandmanagement.driver.OutOfBandManagementDriverCommand;
import org.apache.cloudstack.outofbandmanagement.driver.OutOfBandManagementDriverPowerCommand;
import org.apache.cloudstack.outofbandmanagement.driver.OutOfBandManagementDriverResponse;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OutOfBandManagementStatusTest {
    private OutOfBandManagementResponse status(OutOfBandManagement.PowerState cached, OutOfBandManagementDriverResponse observation) {
        OutOfBandManagementServiceImpl service = spy(new OutOfBandManagementServiceImpl());
        Host host = mock(Host.class);
        OutOfBandManagement config = mock(OutOfBandManagement.class);
        when(config.getPowerState()).thenReturn(cached);
        OutOfBandManagementDriver driver = mock(OutOfBandManagementDriver.class);
        when(driver.execute(any(OutOfBandManagementDriverCommand.class))).thenReturn(observation);
        doReturn(true).when(service).transitionPowerState(any(OutOfBandManagement.PowerState.Event.class), eq(config));
        OutOfBandManagementResponse result = service.executePowerOperation(host, OutOfBandManagement.PowerOperation.STATUS, 2L, config, driver);
        verify(driver, times(1)).execute(argThat(command -> command instanceof OutOfBandManagementDriverPowerCommand
                && ((OutOfBandManagementDriverPowerCommand) command).getPowerOperation() == OutOfBandManagement.PowerOperation.STATUS));
        return result;
    }

    private OutOfBandManagementDriverResponse observation(OutOfBandManagement.PowerState state) {
        OutOfBandManagementDriverResponse result = new OutOfBandManagementDriverResponse("observed", null, true);
        result.setPowerState(state);
        return result;
    }

    @Test public void cachedOffCannotOverrideLiveOn() {
        assertEquals(OutOfBandManagement.PowerState.On,
                status(OutOfBandManagement.PowerState.Off, observation(OutOfBandManagement.PowerState.On)).getPowerState());
    }
    @Test public void cachedOnCannotHideLiveOff() {
        OutOfBandManagementResponse result = status(OutOfBandManagement.PowerState.On, observation(OutOfBandManagement.PowerState.Off));
        assertEquals(OutOfBandManagement.PowerState.Off, result.getPowerState());
        assertTrue(result.getSuccess());
    }
    @Test public void missingStateNeverUsesCachedOff() {
        assertEquals(OutOfBandManagement.PowerState.Unknown,
                status(OutOfBandManagement.PowerState.Off, observation(null)).getPowerState());
    }
    @Test(expected = CloudRuntimeException.class) public void failedObservationCannotReturnCachedOff() {
        status(OutOfBandManagement.PowerState.Off, new OutOfBandManagementDriverResponse(null, "timeout", false));
    }
    @Test(expected = CloudRuntimeException.class) public void missingObservationCannotReturnCachedOff() {
        status(OutOfBandManagement.PowerState.Off, null);
    }
    @Test public void failedObservationDoesNotAcknowledgeRequestedOff() {
        OutOfBandManagementServiceImpl service = new OutOfBandManagementServiceImpl();
        Host host = mock(Host.class);
        OutOfBandManagement config = mock(OutOfBandManagement.class);
        when(config.getPowerState()).thenReturn(OutOfBandManagement.PowerState.Off);
        OutOfBandManagementDriver driver = mock(OutOfBandManagementDriver.class);
        OutOfBandManagementDriverResponse failed = new OutOfBandManagementDriverResponse(null, "unreachable", false);
        failed.setPowerState(OutOfBandManagement.PowerState.Off);
        when(driver.execute(any(OutOfBandManagementDriverCommand.class))).thenReturn(failed);
        try {
            service.executePowerOperation(host, OutOfBandManagement.PowerOperation.OFF, 10L, config, driver);
            fail("Failed status and action cannot acknowledge OFF");
        } catch (CloudRuntimeException expected) {
            verify(driver, times(2)).execute(any(OutOfBandManagementDriverCommand.class));
        }
    }

    @Test public void powerCommandUsesRemainingOperationBudget() {
        OutOfBandManagementServiceImpl service = new OutOfBandManagementServiceImpl();
        Host host = mock(Host.class);
        OutOfBandManagement config = mock(OutOfBandManagement.class);
        OutOfBandManagementDriver driver = mock(OutOfBandManagementDriver.class);
        when(driver.execute(any(OutOfBandManagementDriverCommand.class))).thenReturn(observation(OutOfBandManagement.PowerState.On),
                new OutOfBandManagementDriverResponse("accepted", null, true));
        assertTrue(service.executePowerOperation(host, OutOfBandManagement.PowerOperation.OFF, 10L, config, driver).getSuccess());
        org.mockito.ArgumentCaptor<OutOfBandManagementDriverCommand> commands = org.mockito.ArgumentCaptor.forClass(OutOfBandManagementDriverCommand.class);
        verify(driver, times(2)).execute(commands.capture());
        assertTrue(commands.getAllValues().get(1).getTimeout().getMillis() < commands.getAllValues().get(0).getTimeout().getMillis());
        assertTrue(commands.getAllValues().get(1).getTimeout().getMillis() > 0);
    }
}
