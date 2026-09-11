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

package org.apache.cloudstack.ha.task;

import java.util.concurrent.ExecutorService;


import org.apache.cloudstack.api.ApiCommandResourceType;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.ha.HAConfig;
import org.apache.cloudstack.ha.HAResource;
import org.apache.cloudstack.ha.HAResourceCounter;
import org.apache.cloudstack.ha.provider.HACheckerException;
import org.apache.cloudstack.ha.provider.HAProvider;
import org.apache.cloudstack.ha.provider.HAProvider.HAProviderConfig;
import org.joda.time.DateTime;

import com.cloud.domain.Domain;
import com.cloud.event.ActionEventUtils;
import com.cloud.event.EventTypes;

public class ActivityCheckTask extends BaseHATask {


    private long disconnectTime;
    private long maxActivityChecks;
    private double activityCheckFailureRatio;

    public ActivityCheckTask(final HAResource resource, final HAProvider<HAResource> haProvider, final HAConfig haConfig, final HAProvider.HAProviderConfig haProviderConfig,
            final ExecutorService executor, final long disconnectTime) {
        super(resource, haProvider, haConfig, haProviderConfig, executor);
        this.disconnectTime = disconnectTime;
        this.maxActivityChecks = (Long)haProvider.getConfigValue(HAProviderConfig.MaxActivityChecks, resource);
        this.activityCheckFailureRatio = (Double)haProvider.getConfigValue(HAProviderConfig.ActivityCheckFailureRatio, resource);
    }

    public boolean performAction() throws HACheckerException {
        return getHaProvider().hasActivity(getResource(), new DateTime(disconnectTime));
    }

    public synchronized void processResult(boolean result, Throwable t) {
        if (!isCurrentResult()) {
            return;
        }
        final HAConfig haConfig = getHaConfig();
        final HAResourceCounter counter = getCounter();

        if (t != null) {
            // An unavailable witness is not proof of inactivity and breaks consecutiveness.
            counter.breakActivityFailureSequence();
            logger.warn("Activity check is unknown for {}: {}", getResource(), t.toString());
            getHaManager().transitionHAState(HAConfig.Event.TooFewActivityCheckSamples, haConfig);
            return;
        }

        if (maxActivityChecks < 1 || !Double.isFinite(activityCheckFailureRatio)
                || activityCheckFailureRatio < 0 || activityCheckFailureRatio >= 1) {
            counter.breakActivityFailureSequence();
            logger.warn("Invalid activity check threshold for {}", getResource());
            getHaManager().transitionHAState(HAConfig.Event.TooFewActivityCheckSamples, haConfig);
            return;
        }

        counter.incrActivityCounter(!result);

        long requiredFailures = (long) Math.floor(maxActivityChecks * activityCheckFailureRatio) + 1;

        int ratioPercent = (int) (activityCheckFailureRatio * 100);
        String message = String.format("[VM Activity Check] Observations: %d | Configured Attempts: %d | Consecutive Failures: %d (Threshold: %d, Failure Ratio: %d%%)",
                            counter.getActivityCheckCounter(),
                            maxActivityChecks,
                            counter.getConsecutiveActivityCheckFailureCounter(),
                            requiredFailures,
                            ratioPercent);
        ActionEventUtils.onActionEvent(CallContext.current().getCallingUserId(), CallContext.current().getCallingAccountId(),
                                        Domain.ROOT_DOMAIN, EventTypes.EVENT_HA_STATE_TRANSITION, message, haConfig.getResourceId(), ApiCommandResourceType.Host.toString());

        if (counter.getConsecutiveActivityCheckFailureCounter() >= requiredFailures) {
            if (getHaManager().transitionHAState(HAConfig.Event.ActivityCheckFailureOverThresholdRatio, haConfig)) {
                counter.resetActivityCounter();
            }
            return;
        }

        getHaManager().transitionHAState(HAConfig.Event.TooFewActivityCheckSamples, haConfig);
    }
}
