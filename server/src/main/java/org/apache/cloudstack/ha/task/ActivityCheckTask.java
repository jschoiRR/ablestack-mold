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
    private long activityCheckFailureThreshold;
    private long activityCheckSuccessThreshold;

    public ActivityCheckTask(final HAResource resource, final HAProvider<HAResource> haProvider, final HAConfig haConfig, final HAProvider.HAProviderConfig haProviderConfig,
            final ExecutorService executor, final long disconnectTime) {
        super(resource, haProvider, haConfig, haProviderConfig, executor);
        this.disconnectTime = disconnectTime;
        this.activityCheckFailureThreshold = (Long)haProvider.getConfigValue(HAProviderConfig.ActivityCheckFailureThreshold, resource);
        this.activityCheckSuccessThreshold = (Long)haProvider.getConfigValue(HAProviderConfig.ActivityCheckSuccessThreshold, resource);
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
        // Only a validated Activity result consumes the fallback request. Merely
        // reserving a task, or rejecting its submission, must not consume it.
        counter.completeActivityRecheck();

        if (t != null) {
            // An unavailable witness proves neither activity nor inactivity.
            counter.breakActivitySequences();
            logger.warn("Activity check is unknown for {}: {}", getResource(), t.toString());
            continueObservation(haConfig);
            return;
        }

        final boolean validFailureThreshold = activityCheckFailureThreshold > 0;
        if ((result && activityCheckSuccessThreshold < 1) || (!result && !validFailureThreshold)) {
            counter.breakActivitySequences();
            logger.warn("Invalid activity {} threshold for {}", result ? "success" : "failure", getResource());
            continueObservation(haConfig);
            return;
        }

        counter.incrActivityCounter(!result);

        final String message = String.format("[VM Activity Check] Observations: %d | Consecutive ALIVE: %d (Threshold: %d) | Consecutive DEAD: %d (Threshold: %s)",
                counter.getActivityCheckCounter(), counter.getConsecutiveActivityCheckSuccessCounter(), activityCheckSuccessThreshold,
                counter.getConsecutiveActivityCheckFailureCounter(), validFailureThreshold ? Long.toString(activityCheckFailureThreshold) : "invalid");
        // Continuous degraded observation must not create one database event per probe forever.
        logger.debug(message);

        if (!result && counter.getConsecutiveActivityCheckFailureCounter() >= activityCheckFailureThreshold) {
            if (getHaManager().transitionHAState(HAConfig.Event.ActivityCheckFailureOverThresholdRatio, haConfig)) {
                recordDecision(haConfig, message);
                counter.resetActivityCounter();
            }
            return;
        }

        if (result && haConfig.getState() == HAConfig.HAState.Checking
                && counter.getConsecutiveActivityCheckSuccessCounter() >= activityCheckSuccessThreshold) {
            if (getHaManager().transitionHAState(HAConfig.Event.ActivityCheckSuccessThresholdReached, haConfig)) {
                counter.markResourceDegraded();
                recordDecision(haConfig, message);
            }
            return;
        }

        continueObservation(haConfig);
    }

    private void continueObservation(HAConfig haConfig) {
        if (haConfig.getState() == HAConfig.HAState.Checking) {
            getHaManager().transitionHAState(HAConfig.Event.TooFewActivityCheckSamples, haConfig);
        }
        // Keep the last confirmed Degraded state while observing ALIVE, UNKNOWN,
        // or fewer than the required consecutive DEAD results.
    }

    private void recordDecision(HAConfig haConfig, String message) {
        ActionEventUtils.onActionEvent(CallContext.current().getCallingUserId(), CallContext.current().getCallingAccountId(),
                Domain.ROOT_DOMAIN, EventTypes.EVENT_HA_STATE_TRANSITION, message, haConfig.getResourceId(), ApiCommandResourceType.Host.toString());
    }
}
