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

    public static final ConfigKey<Long> KvmHAHealthCheckTimeout = new ConfigKey<>("Advanced", Long.class, "kvm.ha.health.check.timeout", "10",
            "The maximum length of time, in seconds, expected for an health check to complete.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAActivityCheckTimeout = new ConfigKey<>("Advanced", Long.class, "kvm.ha.activity.check.timeout", "60",
            "The maximum length of time, in seconds, expected for an activity check to complete.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAActivityCheckInterval = new ConfigKey<>("Advanced", Long.class, "kvm.ha.activity.check.interval", "5",
            "Minimum seconds between activity checks. HA polling and alternating health checks can extend the actual interval.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAActivityCheckMaxAttempts = new ConfigKey<>("Advanced", Long.class, "kvm.ha.activity.check.max.attempts", "7",
            "The reference sample count for the consecutive activity failure threshold: floor(count * failure ratio) + 1. Activity observation continues until health recovers or the threshold is met.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Double> KvmHAActivityCheckFailureThreshold = new ConfigKey<>("Advanced", Double.class, "kvm.ha.activity.check.failure.ratio", "0.5",
            "The ratio used to compute the consecutive activity failure threshold: floor(max.attempts * ratio) + 1. Unknown results do not count as confirmed failures.",
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
            "Use repeated fresh BMC OFF responses to detect a powered-off host before storage heartbeat expiry. Failed or unknown responses never confirm power off.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAPowerOffConfirmations = new ConfigKey<>("Advanced", Long.class, "kvm.ha.power.off.confirmations", "3",
            "Minimum consecutive fresh OFF responses required for early detection and fencing verification. Must be at least 3 and fit within the HA task timeout.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAPowerCheckInterval = new ConfigKey<>("Advanced", Long.class, "kvm.ha.power.check.interval", "1",
            "Minimum seconds between completed BMC OFF observations. Must be at least 1.", true, ConfigKey.Scope.Cluster);

    public static final ConfigKey<Long> KvmHAPowerCheckTimeout = new ConfigKey<>("Advanced", Long.class, "kvm.ha.power.check.timeout", "2",
            "Maximum seconds for each live BMC power-status query. The full observation sequence must fit within the HA task timeout.", true, ConfigKey.Scope.Cluster);

}
