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

package org.apache.cloudstack.kvm.ha;

import java.lang.reflect.Field;
import java.util.Arrays;

import com.cloud.host.Host;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.impl.ConfigDepotImpl;
import org.apache.cloudstack.ha.provider.HAProvider.HAProviderConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KVMHAActivityConfigTest {
    private ConfigDepotImpl previousDepot;
    private Object previousValue;
    private Field valueField;
    private ConfigDepotImpl depot;
    private KVMHAProvider provider;
    private Host host;

    @Before
    public void setUp() throws Exception {
        Field depotField = ConfigKey.class.getDeclaredField("s_depot");
        depotField.setAccessible(true);
        previousDepot = (ConfigDepotImpl) depotField.get(null);
        valueField = ConfigKey.class.getDeclaredField("_value");
        valueField.setAccessible(true);
        previousValue = valueField.get(KVMHAConfig.KvmHAActivityCheckFailureThreshold);
        depot = mock(ConfigDepotImpl.class);
        ConfigKey.init(depot);
        provider = new KVMHAProvider();
        host = mock(Host.class);
        when(host.getClusterId()).thenReturn(42L);
    }

    @After
    public void restoreConfigurationDepot() throws Exception {
        ConfigKey.init(previousDepot);
        valueField.set(KVMHAConfig.KvmHAActivityCheckFailureThreshold, previousValue);
    }

    @Test
    public void providerRegistersSingleFailureSettingAndDefaultsToFour() {
        assertEquals(4L, provider.getConfigValue(HAProviderConfig.ActivityCheckFailureThreshold, host));
        assertTrue(Arrays.asList(provider.getConfigKeys()).contains(KVMHAConfig.KvmHAActivityCheckFailureThreshold));
        for (ConfigKey<?> key : provider.getConfigKeys()) {
            assertFalse("kvm.ha.activity.check.max.attempts".equals(key.key()));
            assertFalse("kvm.ha.activity.check.failure.ratio".equals(key.key()));
        }
    }

    @Test
    public void clusterFailureThresholdOverridesGlobalAndRefreshesDynamically() {
        String key = KVMHAConfig.KvmHAActivityCheckFailureThreshold.key();
        when(depot.getConfigStringValue(key, ConfigKey.Scope.Global, null)).thenReturn("6");
        when(depot.getConfigStringValue(key, ConfigKey.Scope.Cluster, 42L)).thenReturn("5");
        assertEquals(5L, provider.getConfigValue(HAProviderConfig.ActivityCheckFailureThreshold, host));

        when(depot.getConfigStringValue(key, ConfigKey.Scope.Cluster, 42L)).thenReturn("7");
        assertEquals(7L, provider.getConfigValue(HAProviderConfig.ActivityCheckFailureThreshold, host));

        when(depot.getConfigStringValue(key, ConfigKey.Scope.Cluster, 42L)).thenReturn(null);
        assertEquals(6L, provider.getConfigValue(HAProviderConfig.ActivityCheckFailureThreshold, host));
        when(depot.getConfigStringValue(key, ConfigKey.Scope.Global, null)).thenReturn("8");
        assertEquals(8L, provider.getConfigValue(HAProviderConfig.ActivityCheckFailureThreshold, host));
    }
}
