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

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import com.cloud.utils.concurrency.NamedThreadFactory;

import org.apache.cloudstack.ha.HAConfig;
import org.apache.cloudstack.ha.HAResource;
import org.apache.cloudstack.ha.HAManager;
import org.apache.cloudstack.ha.HAResourceCounter;
import org.apache.cloudstack.ha.provider.HACheckerException;
import org.apache.cloudstack.ha.provider.HAFenceException;
import org.apache.cloudstack.ha.provider.HAProvider;
import org.apache.cloudstack.ha.provider.HARecoveryException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.joda.time.DateTime;

public abstract class BaseHATask implements Callable<Boolean> {
    protected Logger logger = LogManager.getLogger(getClass());
    private static final ExecutorService innerExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("HA-InnerTask"));

    private final HAResource resource;
    private final HAProvider<HAResource> haProvider;
    private HAConfig haConfig;
    @Inject
    private HAManager haManager;
    private HAResourceCounter counter;
    private HAResourceCounter.TaskToken taskToken;
    private final AtomicBoolean workerFinished = new AtomicBoolean(false);
    private final AtomicBoolean resultFinished = new AtomicBoolean(false);
    private final Object workerLock = new Object();
    private volatile boolean abandoned;
    private Thread workerThread;
    private final ExecutorService executor;
    private Long timeout;
    private DateTime created;

    public BaseHATask(final HAResource resource, final HAProvider<HAResource> haProvider, final HAConfig haConfig, final HAProvider.HAProviderConfig haProviderConfig,
            final ExecutorService executor) {
        this.resource = resource;
        this.haProvider = haProvider;
        this.haConfig = haConfig;
        this.executor = executor;
        this.timeout = (Long)haProvider.getConfigValue(haProviderConfig, resource);
        this.created = new DateTime();
    }

    public HAProvider<HAResource> getHaProvider() {
        return haProvider;
    }

    public HAConfig getHaConfig() {
        return haConfig;
    }

    public void initialize(HAResourceCounter resourceCounter, HAResourceCounter.TaskToken token) {
        counter = resourceCounter;
        taskToken = token;
    }

    protected HAManager getHaManager() {
        return haManager;
    }

    protected HAResourceCounter getCounter() {
        return counter;
    }

    protected boolean isCurrentTask() {
        return !abandoned && isCurrentResult();
    }

    protected boolean isCurrentResult() {
        if (counter == null || !counter.isCurrentTask(taskToken)) {
            return false;
        }
        HAConfig current = haManager.getCurrentHAConfig(haConfig, counter, taskToken);
        if (current == null) {
            return false;
        }
        haConfig = current;
        return true;
    }

    private void releaseCompletedTask() {
        if (workerFinished.get() && resultFinished.get()) {
            counter.finishTask(taskToken);
        }
    }

    public HAResource getResource() {
        return resource;
    }

    public String getTaskType() {
        return this.getClass().getSimpleName();
    }

    public boolean performAction() throws HACheckerException, HAFenceException, HARecoveryException {
        return true;
    }

    public abstract void processResult(boolean result, Throwable e);

    @Override
    public Boolean call() {
        if (counter == null) {
            throw new IllegalStateException("HA task must be reserved before submission");
        }
        try {
            if (new DateTime().minusHours(1).isAfter(getCreated()) || !isCurrentTask()) {
                counter.finishTask(taskToken);
                return false;
            }
        } catch (RuntimeException e) {
            counter.finishTask(taskToken);
            throw e;
        }
        boolean result = false;
        Throwable throwable = null;
        try {
            final Future<Boolean> future = innerExecutor.submit(() -> {
                synchronized (workerLock) {
                    workerThread = Thread.currentThread();
                }
                try {
                    if (abandoned || !isCurrentTask()) {
                        throw new HACheckerException("HA task is no longer current", null);
                    }
                    return performAction();
                } finally {
                    synchronized (workerLock) {
                        workerThread = null;
                    }
                    workerFinished.set(true);
                    releaseCompletedTask();
                }
            });
            if (timeout == null) {
                result = future.get();
            } else {
                result = future.get(timeout, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            throwable = e;
            abandoned = true;
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.warn("Exception occurred while running " + getTaskType() + " on a resource: " + e.getMessage(), e.getCause());
            throwable = e.getCause();
        } catch (TimeoutException e) {
            logger.trace("{} operation timed out for resource: {}", getTaskType(), resource);
            throwable = e;
            abandoned = true;
        } catch (RuntimeException e) {
            // Submission failure means there is no worker that can release the reservation.
            workerFinished.set(true);
            throwable = e;
        }
        try {
            synchronized (workerLock) {
                if (abandoned && workerThread != null) {
                    workerThread.interrupt();
                }
            }
            synchronized (counter) {
                if (isCurrentResult()) {
                    processResult(result, throwable);
                }
            }
            return result;
        } finally {
            resultFinished.set(true);
            releaseCompletedTask();
        }
    }

    public DateTime getCreated() {
        return created;
    }
}
