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

import org.apache.cloudstack.framework.config.ConfigKey;

public class KVMHAConfig {

    public static final ConfigKey<Long> KvmHAHealthCheckTimeout = new ConfigKey<>("Advanced", Long.class, "kvm.ha.health.check.timeout", "20",
            "The maximum length of time, in seconds, expected for an health check to complete.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAActivityCheckTimeout = new ConfigKey<>("Advanced", Long.class, "kvm.ha.activity.check.timeout", "60",
            "The maximum length of time, in seconds, expected for an activity check to complete.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAActivityCheckInterval = new ConfigKey<>("Advanced", Long.class, "kvm.ha.activity.check.interval", "5",
            "Minimum seconds between activity checks. HA polling and alternating health checks can extend the actual interval.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAActivityCheckFailureThreshold = new ConfigKey<>("Advanced", Long.class, "kvm.ha.activity.check.failure.threshold", "4",
            "Consecutive DEAD activity observations required to enter recovery. Must be positive. ALIVE or UNKNOWN resets the failure sequence; observation has no total attempt limit.",
            true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHADegradedMaxPeriod = new ConfigKey<>("Advanced", Long.class, "kvm.ha.degraded.max.period", "60",
            "Legacy degraded wait setting retained for compatibility. Continuous HA observation now resumes activity checks at the regular activity interval without this pause.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAActivityCheckSuccessThreshold = new ConfigKey<>("Advanced", Long.class, "kvm.ha.activity.check.success.threshold", "3",
            "Consecutive ALIVE activity observations required to enter Degraded while host health remains abnormal. Must be positive. Activity checks continue in Degraded; only a healthy host check restores Available.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHARecoverTimeout = new ConfigKey<>("Advanced", Long.class, "kvm.ha.recover.timeout", "60",
            "The maximum length of time, in seconds, expected for a recovery operation to complete.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHARecoverWaitPeriod = new ConfigKey<>("Advanced", Long.class, "kvm.ha.recover.wait.period", "600",
            "The maximum length of time, in seconds, to wait for a resource to recover.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHARecoverAttemptThreshold = new ConfigKey<>("Advanced", Long.class, "kvm.ha.recover.failure.threshold", "1",
            "The maximum recovery attempts to be made for a resource, after which the resource is fenced. The recovery counter resets when a health check passes for a resource.",
            true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAFenceTimeout = new ConfigKey<>("Advanced", Long.class, "kvm.ha.fence.timeout", "60",
            "The maximum length of time, in seconds, expected for a fence operation to complete.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Boolean> KvmHAPowerOffCheckEnabled = new ConfigKey<>("Advanced", Boolean.class, "kvm.ha.power.off.check.enabled", "true",
            "Query BMC power once per health task and count consecutive fresh OFF responses across HA polls. Failed or unknown responses reset the sequence.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAPowerOffConfirmations = new ConfigKey<>("Advanced", Long.class, "kvm.ha.power.off.confirmations", "3",
            "Consecutive fresh OFF observations across separate health tasks required for early detection. Must be at least 3. Independent of fencing verification.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAPowerOffMaxInterval = new ConfigKey<>("Advanced", Long.class, "kvm.ha.power.off.max.interval", "60",
            "Maximum seconds between consecutive OFF observations for early detection. A longer gap starts a new sequence; this is not a wait. Must be between 1 and 3600.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAFencePowerOffConfirmations = new ConfigKey<>("Advanced", Long.class, "kvm.ha.fence.power.off.confirmations", "5",
            "Consecutive fresh OFF responses required after the fencing OFF command before ON. Must be at least 3 and fit within the fence timeout.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAPowerCheckInterval = new ConfigKey<>("Advanced", Long.class, "kvm.ha.power.check.interval", "3",
            "Seconds to wait between BMC observations during fencing verification only. Early detection queries once per health task without this wait. Must be at least 1.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAPowerCheckTimeout = new ConfigKey<>("Advanced", Long.class, "kvm.ha.power.check.timeout", "1",
            "Maximum seconds for each live BMC power-status query. One query must fit within the health timeout; repeated fencing queries must fit within the fence timeout.", true, ConfigKey.Scope.Cluster);

}
