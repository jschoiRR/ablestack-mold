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
package com.cloud.hypervisor.kvm.resource;

import java.util.Map;
import org.junit.Test;
import org.junit.Assert;
import com.cloud.vm.KvmTpmConfig;

public class TpmCapabilitiesTest {
    private String xml(String backend, String versions) {
        return "<domainCapabilities><devices><tpm supported='yes'><enum name='model'><value>tpm-tis</value><value>tpm-crb</value></enum>"
            + "<enum name='backendModel'><value>" + backend + "</value></enum><enum name='backendVersion'>" + versions
            + "</enum></tpm></devices></domainCapabilities>";
    }
    @Test public void detectsOnlyAdvertisedEmulatorVersion() throws Exception {
        Map<String, String> capability = TpmCapabilities.parse(xml("emulator", "<value>2.0</value>"));
        Assert.assertTrue(KvmTpmConfig.resolve(Map.of("tpmversion", "2.0"), true).supportedBy(capability));
        Assert.assertFalse(KvmTpmConfig.resolve(Map.of("tpmversion", "1.2"), true).supportedBy(capability));
    }
    @Test public void rejectsPassthroughOnlyAndMissingCapability() throws Exception {
        Assert.assertEquals("false", TpmCapabilities.parse(xml("passthrough", "<value>2.0</value>")).get("host.tpm.enable"));
        Assert.assertEquals("false", TpmCapabilities.parse("<domainCapabilities><devices><tpm supported='no'/></devices></domainCapabilities>").get("host.tpm.enable"));
    }
    @Test public void rejectsExternalEntities() {
        Assert.assertThrows(Exception.class, () -> TpmCapabilities.parse("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><domainCapabilities>&e;</domainCapabilities>"));
    }
}
