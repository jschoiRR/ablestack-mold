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
package org.apache.cloudstack.api.command.user.vm;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.cloud.exception.InvalidParameterValueException;
import org.junit.Assert;
import org.junit.Test;

public class UpdateVMGroupParametersTest {
    private UpdateVMCmd command(String name, Long id, Boolean clear) throws Exception {
        UpdateVMCmd cmd = new UpdateVMCmd();
        set(cmd, "group", name);
        set(cmd, "groupId", id);
        set(cmd, "clearGroup", clear);
        return cmd;
    }

    private void set(UpdateVMCmd cmd, String name, Object value) throws Exception {
        Field field = UpdateVMCmd.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(cmd, value);
    }

    private void rejected(String name, Long id, Boolean clear) throws Exception {
        Assert.assertThrows(InvalidParameterValueException.class, () -> command(name, id, clear).validateGroupUpdateParameters());
    }

    @Test public void omittedGroupPreservesMembership() throws Exception {
        UpdateVMCmd cmd = command(null, null, null);
        cmd.validateGroupUpdateParameters();
        Assert.assertFalse(cmd.hasGroupUpdate());
    }
    @Test public void falseClearIsNotAnOperation() throws Exception {
        Assert.assertFalse(command(null, null, false).hasGroupUpdate());
    }
    @Test public void legacyNameRemainsSupported() throws Exception {
        UpdateVMCmd cmd = command(" 기존 그룹 ", null, false);
        cmd.validateGroupUpdateParameters();
        Assert.assertEquals(" 기존 그룹 ", cmd.getGroup());
    }
    @Test public void existingIdAndClearAreIndependentSelectors() throws Exception {
        command(null, 12L, false).validateGroupUpdateParameters();
        command(null, null, true).validateGroupUpdateParameters();
    }
    @Test public void blankNameDoesNotMeanClear() throws Exception {
        rejected("", null, null);
        rejected("   ", null, null);
    }
    @Test public void rejectsAllConflictingSelectors() throws Exception {
        rejected("name", 12L, false);
        rejected("name", null, true);
        rejected(null, 12L, true);
        rejected("name", 12L, true);
        rejected("", null, true);
    }
    @Test public void groupOnlyRequestAcceptsAuthenticationAndProjectContext() throws Exception {
        UpdateVMCmd cmd = command(null, 12L, false);
        cmd.setFullUrlParams(Map.of("id", "vm", "groupid", "group", "sessionkey", "session", "projectid", "project"));
        Assert.assertTrue(cmd.isGroupOnlyUpdate());
    }
    @Test public void metadataOrFutureArgumentsPreventGroupOnlyShortcut() throws Exception {
        for (String key : new String[] {"displayname", "userdata", "haenable", "customid", "newproperty"}) {
            UpdateVMCmd cmd = command(null, null, true);
            Map<String, String> params = new HashMap<>(Map.of("id", "vm", "cleargroup", "true"));
            params.put(key, "");
            cmd.setFullUrlParams(params);
            Assert.assertFalse(cmd.isGroupOnlyUpdate());
        }
    }
    @Test public void programmaticCallsWithoutParameterMetadataUseFullPath() throws Exception {
        Assert.assertFalse(command("name", null, false).isGroupOnlyUpdate());
    }
}
