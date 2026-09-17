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
package com.cloud.vm;

import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.Assert;
import org.apache.cloudstack.api.command.user.vm.DeployVMCmd;
import org.apache.cloudstack.api.command.user.vm.BaseDeployVMCmd;
import org.apache.cloudstack.api.command.user.vm.DeployVMVolumeCmd;

public class TpmDeployApiTest {
    @Test public void unspecifiedTpmDoesNotOverwriteInheritance() {
        Assert.assertFalse(new DeployVMCmd().getDetails().containsKey("tpmversion"));
        Assert.assertFalse(new DeployVMVolumeCmd().getDetails().containsKey("tpmversion"));
    }
    @Test public void explicitVersionAndNoneArePreserved() throws Exception {
        Field field = BaseDeployVMCmd.class.getDeclaredField("tpmversion");
        field.setAccessible(true);
        DeployVMCmd cmd = new DeployVMCmd();
        field.set(cmd, "2.0");
        Assert.assertEquals("2.0", cmd.getDetails().get("tpmversion"));
        field.set(cmd, "NONE");
        Assert.assertEquals("NONE", cmd.getDetails().get("tpmversion"));
    }
}
