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

package org.apache.cloudstack.api.command.test;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import org.junit.Test;
import org.apache.cloudstack.api.ResponseGenerator;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.command.user.autoscale.CreateAutoScaleVmGroupCmd;
import org.apache.cloudstack.api.response.AutoScaleVmGroupResponse;
import org.springframework.test.util.ReflectionTestUtils;
import com.cloud.network.as.AutoScaleService;
import com.cloud.network.as.AutoScaleVmGroup;
import com.cloud.utils.db.EntityManager;

public class CreateAutoScaleVmGroupCmdTest {
    @Test public void replayOfConfiguredGroupDoesNotReconfigureOrDeleteIt() throws Exception {
        for (AutoScaleVmGroup.State state : new AutoScaleVmGroup.State[] {AutoScaleVmGroup.State.ENABLED, AutoScaleVmGroup.State.SCALING, AutoScaleVmGroup.State.DISABLED}) {
            CreateAutoScaleVmGroupCmd cmd = new CreateAutoScaleVmGroupCmd();
            EntityManager entities = mock(EntityManager.class); AutoScaleService service = mock(AutoScaleService.class);
            ResponseGenerator responses = mock(ResponseGenerator.class); AutoScaleVmGroup group = mock(AutoScaleVmGroup.class);
            ReflectionTestUtils.setField(cmd, "injectedJobId", "fixture-id"); ReflectionTestUtils.setField(cmd, "_entityMgr", entities);
            ReflectionTestUtils.setField(cmd, "_autoScaleService", service); ReflectionTestUtils.setField(cmd, "_responseGenerator", responses);
            cmd.setEntityId(5L); when(group.getState()).thenReturn(state); when(entities.findById(AutoScaleVmGroup.class, 5L)).thenReturn(group);
            when(responses.createAutoScaleVmGroupResponse(group)).thenReturn(new AutoScaleVmGroupResponse());
            cmd.execute(); verify(service, never()).configureAutoScaleVmGroup(cmd); verify(service, never()).deleteAutoScaleVmGroup(5L, true);
        }
    }
    @Test public void unknownExistingGroupStateDoesNotEnterDestructiveFailureCleanup() {
        CreateAutoScaleVmGroupCmd cmd = new CreateAutoScaleVmGroupCmd();
        EntityManager entities = mock(EntityManager.class); AutoScaleService service = mock(AutoScaleService.class); AutoScaleVmGroup group = mock(AutoScaleVmGroup.class);
        ReflectionTestUtils.setField(cmd, "injectedJobId", "fixture-id"); ReflectionTestUtils.setField(cmd, "_entityMgr", entities); ReflectionTestUtils.setField(cmd, "_autoScaleService", service);
        cmd.setEntityId(5L); when(group.getState()).thenReturn(AutoScaleVmGroup.State.REVOKE); when(entities.findById(AutoScaleVmGroup.class, 5L)).thenReturn(group);
        assertThrows(ServerApiException.class, cmd::execute); verify(service, never()).deleteAutoScaleVmGroup(5L, true);
    }
}
