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

package org.apache.cloudstack.ha.provider;

import org.apache.cloudstack.ha.HAConfig;
import org.apache.cloudstack.ha.HAResource;
import org.joda.time.DateTime;

import com.cloud.utils.component.Adapter;

public interface HAProvider<R extends HAResource> extends Adapter {

    enum PowerObservation { ON, OFF, UNKNOWN }

    enum HAProviderConfig {
        HealthCheckTimeout,
        ActivityCheckTimeout,
        RecoveryTimeout,
        FenceTimeout,
        ActivityCheckFailureThreshold,
        ActivityCheckSuccessThreshold,
        MaxRecoveryAttempts,
        MaxActivityCheckInterval,
        MaxDegradedWaitTimeout,
        RecoveryWaitTimeout
    };

    HAResource.ResourceType resourceType();

    HAResource.ResourceSubType resourceSubType();

    boolean isDisabled(R r);

    boolean isInMaintenanceMode(R r);

    boolean isEligible(R r);

    boolean isHealthy(R r) throws HACheckerException;

    /**
     * One fresh power observation. The caller accumulates OFF observations across
     * separate health tasks; an unanswered probe or cached state is UNKNOWN.
     */
    default PowerObservation checkPowerState(R r) throws HACheckerException {
        return PowerObservation.UNKNOWN;
    }

    default long getPowerOffConfirmations(R r) {
        return 3L;
    }

    default long getPowerOffMaxInterval(R r) {
        return 60L;
    }

    boolean hasActivity(R r, DateTime afterThis) throws HACheckerException;

    boolean recover(R r) throws HARecoveryException;

    boolean fence(R r) throws HAFenceException;

    /** Persist the recovery roster before reboot can erase the source host's VM associations. */
    default void prepareFenceSubResources(R r) {
    }

    void fenceSubResources(R r);

    void enableMaintenance(R r);

    void sendAlert(R r, HAConfig.HAState nextState);

    Object getConfigValue(HAProviderConfig name, R r);
}
