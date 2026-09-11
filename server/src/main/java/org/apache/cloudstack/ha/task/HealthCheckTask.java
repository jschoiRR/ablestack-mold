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

    private boolean powerOffConfirmed;

    public HealthCheckTask(final HAResource resource, final HAProvider<HAResource> haProvider, final HAConfig haConfig,
                           final HAProvider.HAProviderConfig haProviderConfig, final ExecutorService executor) {
        super(resource, haProvider, haConfig, haProviderConfig, executor);
    }

    public boolean performAction() throws HACheckerException {
        try {
            powerOffConfirmed = getHaProvider().isPowerOffConfirmed(getResource());
        } catch (HACheckerException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            powerOffConfirmed = false;
            logger.debug("Power state is unknown for {}; continuing health checks", getResource(), e);
        }
        if (powerOffConfirmed) {
            return false;
        }
        return getHaProvider().isHealthy(getResource());
    }

    public void processResult(boolean result, Throwable e) {
        if (!isCurrentResult()) {
            return;
        }
        final HAConfig haConfig = getHaConfig();
        final HAResourceCounter counter = getCounter();
        if (e == null && powerOffConfirmed) {
            getHaManager().transitionHAState(HAConfig.Event.PowerOffConfirmed, haConfig);
            return;
        }
        if (result && e == null) {
            boolean alreadyAvailable = haConfig.getState() == HAConfig.HAState.Available;
            if (getHaManager().transitionHAState(HAConfig.Event.HealthCheckPassed, haConfig) || alreadyAvailable) {
                counter.resetForNewCycle();
            }
        } else {
            boolean alreadySuspect = haConfig.getState() == HAConfig.HAState.Suspect;
            if (getHaManager().transitionHAState(HAConfig.Event.HealthCheckFailed, haConfig) || alreadySuspect) {
                counter.markResourceSuspected();
            }
        }
    }
}
