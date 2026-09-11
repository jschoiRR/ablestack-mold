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

import org.apache.cloudstack.ha.HAConfig;
import org.apache.cloudstack.ha.HAResource;
import org.apache.cloudstack.ha.HAResourceCounter;
import org.apache.cloudstack.ha.provider.HACheckerException;
import org.apache.cloudstack.ha.provider.HAProvider;

import java.util.concurrent.ExecutorService;

public class HealthCheckTask extends BaseHATask {

    private HAProvider.PowerObservation powerObservation = HAProvider.PowerObservation.UNKNOWN;
    private long requiredPowerOffConfirmations;
    private long powerOffMaxInterval;
    private boolean resultProcessed;

    public HealthCheckTask(final HAResource resource, final HAProvider<HAResource> haProvider, final HAConfig haConfig,
                           final HAProvider.HAProviderConfig haProviderConfig, final ExecutorService executor) {
        super(resource, haProvider, haConfig, haProviderConfig, executor);
    }

    public boolean performAction() throws HACheckerException {
        try {
            powerObservation = getHaProvider().checkPowerState(getResource());
            if (powerObservation == HAProvider.PowerObservation.OFF) {
                requiredPowerOffConfirmations = getHaProvider().getPowerOffConfirmations(getResource());
                powerOffMaxInterval = getHaProvider().getPowerOffMaxInterval(getResource());
            }
        } catch (HACheckerException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            powerObservation = HAProvider.PowerObservation.UNKNOWN;
            logger.debug("Power state is unknown for {}; continuing health checks", getResource(), e);
        }
        if (powerObservation == HAProvider.PowerObservation.OFF) {
            // Return this single observation promptly. Do not wait for agent
            // health or perform additional BMC queries in the same task.
            return false;
        }
        return getHaProvider().isHealthy(getResource());
    }

    public synchronized void processResult(boolean result, Throwable e) {
        if (resultProcessed || !isCurrentResult()) {
            return;
        }
        resultProcessed = true;
        final HAConfig haConfig = getHaConfig();
        final HAResourceCounter counter = getCounter();
        if (e == null && powerObservation == HAProvider.PowerObservation.OFF) {
            long confirmations = counter.recordPowerOffObservation(System.nanoTime(), powerOffMaxInterval, requiredPowerOffConfirmations);
            if (confirmations > 0) {
                logger.debug("Fresh BMC OFF observations across health tasks for {}: {}/{}",
                        getResource(), confirmations, requiredPowerOffConfirmations);
                if (confirmations >= requiredPowerOffConfirmations) {
                    if (!getHaManager().transitionHAState(HAConfig.Event.PowerOffConfirmed, haConfig)) {
                        counter.resetPowerOffCounter();
                    }
                    return;
                }
            } else {
                logger.warn("Invalid consecutive power observation configuration for {}", getResource());
            }
        } else {
            counter.resetPowerOffCounter();
        }
        if (result && e == null) {
            boolean alreadyAvailable = haConfig.getState() == HAConfig.HAState.Available;
            if (getHaManager().transitionHAState(HAConfig.Event.HealthCheckPassed, haConfig) || alreadyAvailable) {
                counter.resetForNewCycle();
            }
        } else {
            boolean alreadyInvestigating = haConfig.getState() == HAConfig.HAState.Suspect || haConfig.getState() == HAConfig.HAState.Degraded;
            if (getHaManager().transitionHAState(HAConfig.Event.HealthCheckFailed, haConfig) || alreadyInvestigating) {
                counter.markResourceSuspected();
            } else {
                counter.resetPowerOffCounter();
            }
        }
    }
}
