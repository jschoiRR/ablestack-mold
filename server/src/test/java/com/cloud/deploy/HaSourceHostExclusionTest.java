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
package com.cloud.deploy;

import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.resource.ResourceState;
import com.cloud.vm.VirtualMachineProfile;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HaSourceHostExclusionTest {
    @Test
    public void sourceHostRemainsExcludedEvenWhenUpAndEnabledAgain() {
        VirtualMachineProfile profile = mock(VirtualMachineProfile.class);
        when(profile.getParameter(VirtualMachineProfile.Param.HaSourceHostId)).thenReturn(7L);
        HostVO host = mock(HostVO.class);
        when(host.getId()).thenReturn(7L);
        when(host.getStatus()).thenReturn(Status.Up);
        when(host.getResourceState()).thenReturn(ResourceState.Enabled);
        DeploymentPlanner.ExcludeList avoids = new DeploymentPlanner.ExcludeList();
        avoids.addHost(9L);
        new DeploymentPlanningManagerImpl().avoidHaSourceHost(profile, avoids);
        assertTrue(avoids.shouldAvoid(host));
        assertTrue(avoids.getHostsToAvoid().contains(9L));
    }

    @Test
    public void ordinaryVmStartDoesNotExcludeItsLastHost() {
        VirtualMachineProfile profile = mock(VirtualMachineProfile.class);
        HostVO host = mock(HostVO.class);
        when(host.getId()).thenReturn(7L);
        DeploymentPlanner.ExcludeList avoids = new DeploymentPlanner.ExcludeList();
        new DeploymentPlanningManagerImpl().avoidHaSourceHost(profile, avoids);
        assertFalse(avoids.shouldAvoid(host));
    }
}
