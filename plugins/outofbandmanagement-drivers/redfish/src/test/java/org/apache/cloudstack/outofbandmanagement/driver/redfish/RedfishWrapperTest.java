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

package org.apache.cloudstack.outofbandmanagement.driver.redfish;

import org.apache.cloudstack.outofbandmanagement.OutOfBandManagement.PowerState;
import org.apache.cloudstack.utils.redfish.RedfishClient.RedfishPowerState;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class RedfishWrapperTest {
    @Test public void onlyCompletedShutdownIsOff() {
        RedfishWrapper wrapper = new RedfishWrapper();
        assertEquals(PowerState.Off, wrapper.parseRedfishPowerStateToOutOfBand(RedfishPowerState.Off));
        assertEquals(PowerState.Unknown, wrapper.parseRedfishPowerStateToOutOfBand(RedfishPowerState.PoweringOff));
        assertEquals(PowerState.On, wrapper.parseRedfishPowerStateToOutOfBand(RedfishPowerState.On));
        assertEquals(PowerState.On, wrapper.parseRedfishPowerStateToOutOfBand(RedfishPowerState.PoweringOn));
    }
    @Test public void offFencesWhileSoftRequestsGracefulShutdown() {
        RedfishWrapper wrapper = new RedfishWrapper();
        assertEquals(org.apache.cloudstack.utils.redfish.RedfishClient.RedfishResetCmd.ForceOff,
                wrapper.parsePowerCommand(org.apache.cloudstack.outofbandmanagement.OutOfBandManagement.PowerOperation.OFF));
        assertEquals(org.apache.cloudstack.utils.redfish.RedfishClient.RedfishResetCmd.GracefulShutdown,
                wrapper.parsePowerCommand(org.apache.cloudstack.outofbandmanagement.OutOfBandManagement.PowerOperation.SOFT));
    }
}
