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

package org.apache.cloudstack.ha;

import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class HAResourceCounter {
    public enum Operation { HEALTH, ACTIVITY, RECOVERY, FENCE }

    public static final class TaskToken {
        private final long generation;
        private final Operation operation;

        private TaskToken(long generation, Operation operation) {
            this.generation = generation;
            this.operation = operation;
        }

        public Operation getOperation() {
            return operation;
        }
    }

    private long generation;
    private TaskToken activeTask;
    private Operation lastProbeOperation;
    private long consecutivePowerOffCounter;
    private long lastPowerOffObservationNanos;
    private long powerOffRequiredConfirmations;
    private boolean powerOffActivityRecheckRequired;
    private String powerObservationProvider;
    private AtomicLong activityCheckCounter = new AtomicLong(0);
    private AtomicLong activityCheckFailureCounter = new AtomicLong(0);
    private AtomicLong consecutiveActivityCheckFailureCounter = new AtomicLong(0);
    private AtomicLong consecutiveActivityCheckSuccessCounter = new AtomicLong(0);
    private AtomicLong recoveryOperationCounter = new AtomicLong(0);

    private Long firstHealthCheckFailureTimestamp;
    private Long lastActivityCheckTimestamp;
    private Long degradedTimestamp;
    private Long recoverTimestamp;
    private Future<Boolean> recoveryFuture;
    private Future<Boolean> fenceFuture;

    public long getActivityCheckCounter() {
        return activityCheckCounter.get();
    }

    public long getActivityCheckFailureCounter() {
        return activityCheckFailureCounter.get();
    }

    public long getConsecutiveActivityCheckFailureCounter() {
        return consecutiveActivityCheckFailureCounter.get();
    }

    public long getConsecutiveActivityCheckSuccessCounter() {
        return consecutiveActivityCheckSuccessCounter.get();
    }

    public long getRecoveryCounter() {
        return recoveryOperationCounter.get();
    }

    public synchronized long getConsecutivePowerOffCounter() {
        return consecutivePowerOffCounter;
    }

    public synchronized boolean hasPendingPowerOffObservation(long nowNanos, long maxIntervalSeconds, long requiredConfirmations) {
        if (!isPowerObservationConfigurationValid(maxIntervalSeconds, requiredConfirmations)) {
            resetPowerOffCounter();
            return false;
        }
        expirePowerOffObservation(nowNanos, maxIntervalSeconds, requiredConfirmations);
        return consecutivePowerOffCounter > 0 && !powerOffActivityRecheckRequired;
    }

    public synchronized long recordPowerOffObservation(long nowNanos, long maxIntervalSeconds, long requiredConfirmations) {
        if (!isPowerObservationConfigurationValid(maxIntervalSeconds, requiredConfirmations)) {
            resetPowerOffCounter();
            return 0;
        }
        expirePowerOffObservation(nowNanos, maxIntervalSeconds, requiredConfirmations);
        powerOffRequiredConfirmations = requiredConfirmations;
        lastPowerOffObservationNanos = nowNanos;
        if (consecutivePowerOffCounter < requiredConfirmations) {
            consecutivePowerOffCounter++;
        }
        return consecutivePowerOffCounter;
    }

    private boolean isPowerObservationConfigurationValid(long maxIntervalSeconds, long requiredConfirmations) {
        return maxIntervalSeconds >= 1 && maxIntervalSeconds <= 3600 && requiredConfirmations >= 3;
    }

    private void expirePowerOffObservation(long nowNanos, long maxIntervalSeconds, long requiredConfirmations) {
        long elapsed = nowNanos - lastPowerOffObservationNanos;
        if (consecutivePowerOffCounter > 0 && (powerOffRequiredConfirmations != requiredConfirmations
                || elapsed < 0 || elapsed > TimeUnit.SECONDS.toNanos(maxIntervalSeconds))) {
            resetPowerOffCounter();
            // A delayed Health result can start a fresh OFF sequence after the
            // poll has already selected Health. Keep Activity eligible even when
            // that new OFF result is still fresh at the next poll.
            powerOffActivityRecheckRequired = true;
        }
    }

    public synchronized void completeActivityRecheck() {
        powerOffActivityRecheckRequired = false;
    }

    public synchronized void resetPowerOffCounter() {
        consecutivePowerOffCounter = 0;
        lastPowerOffObservationNanos = 0;
        powerOffRequiredConfirmations = 0;
        powerOffActivityRecheckRequired = false;
    }

    public synchronized void synchronizePowerObservationProvider(String provider) {
        if (!Objects.equals(powerObservationProvider, provider)) {
            resetPowerOffCounter();
            powerObservationProvider = provider;
        }
    }

    public synchronized void incrActivityCounter(final boolean isFailure) {
        activityCheckCounter.incrementAndGet();
        if (isFailure) {
            activityCheckFailureCounter.incrementAndGet();
            consecutiveActivityCheckFailureCounter.incrementAndGet();
            consecutiveActivityCheckSuccessCounter.set(0);
        } else {
            consecutiveActivityCheckFailureCounter.set(0);
            consecutiveActivityCheckSuccessCounter.incrementAndGet();
        }
    }

    public synchronized void incrRecoveryCounter() {
        recoveryOperationCounter.incrementAndGet();
    }

    public synchronized void resetActivityCounter() {
        activityCheckCounter.set(0);
        activityCheckFailureCounter.set(0);
        consecutiveActivityCheckFailureCounter.set(0);
        consecutiveActivityCheckSuccessCounter.set(0);
    }

    public synchronized void breakActivityFailureSequence() {
        consecutiveActivityCheckFailureCounter.set(0);
    }

    public synchronized void breakActivitySequences() {
        consecutiveActivityCheckFailureCounter.set(0);
        consecutiveActivityCheckSuccessCounter.set(0);
    }

    public synchronized TaskToken tryStartTask(Operation operation) {
        if (activeTask != null) {
            return null;
        }
        activeTask = new TaskToken(generation, operation);
        return activeTask;
    }

    public synchronized boolean isCurrentTask(TaskToken token) {
        return token != null && activeTask == token && token.generation == generation;
    }

    public synchronized boolean hasActiveTask() {
        return activeTask != null;
    }

    public synchronized void finishTask(TaskToken token) {
        if (activeTask == token) {
            if (token.operation == Operation.HEALTH || token.operation == Operation.ACTIVITY) {
                lastProbeOperation = token.operation;
            }
            activeTask = null;
        }
    }

    public synchronized boolean needsHealthCheck() {
        return lastProbeOperation == Operation.ACTIVITY;
    }

    public synchronized void resetForNewCycle() {
        generation++;
        resetPowerOffCounter();
        resetActivityCounter();
        resetRecoveryCounter();
        firstHealthCheckFailureTimestamp = null;
        lastActivityCheckTimestamp = null;
        degradedTimestamp = null;
        lastProbeOperation = null;
        // Keep the reservation until the real worker exits, including after a timeout.
    }

    public synchronized void resetRecoveryCounter() {
        recoverTimestamp = null;
        recoveryFuture = null;
        recoveryOperationCounter.set(0);
    }

    public synchronized void resetSuspectTimestamp() {
        firstHealthCheckFailureTimestamp = null;
    }

    public boolean hasActivityThresholdExceeded(final double failureRatio) {
        return activityCheckFailureCounter.get() > (activityCheckCounter.get() * failureRatio);
    }

    public synchronized boolean canPerformActivityCheck(final Long activityCheckInterval) {
        if (lastActivityCheckTimestamp == null || (System.currentTimeMillis() - lastActivityCheckTimestamp) >= (activityCheckInterval * 1000)) {
            lastActivityCheckTimestamp = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public boolean canRecheckActivity(final Long maxDegradedPeriod) {
        return degradedTimestamp == null || (System.currentTimeMillis() - degradedTimestamp) > (maxDegradedPeriod * 1000);
    }

    public boolean canExitRecovery(final Long maxRecoveryWaitPeriod) {
        return recoverTimestamp != null && (System.currentTimeMillis() - recoverTimestamp) > (maxRecoveryWaitPeriod * 1000);
    }

    public synchronized long getSuspectTimeStamp() {
        if (firstHealthCheckFailureTimestamp == null) {
            firstHealthCheckFailureTimestamp = System.currentTimeMillis();
        }
        return firstHealthCheckFailureTimestamp;
    }

    public synchronized void markResourceSuspected() {
        if (firstHealthCheckFailureTimestamp == null) {
            firstHealthCheckFailureTimestamp = System.currentTimeMillis();
        }
    }

    public synchronized void markResourceDegraded() {
        degradedTimestamp = System.currentTimeMillis();
    }

    public synchronized void markRecoveryStarted() {
        if (recoverTimestamp == null) {
            recoverTimestamp = System.currentTimeMillis();
        }
    }

    public synchronized void markRecoveryCompleted() {
        recoverTimestamp = null;
        recoveryFuture = null;
    }

    public void setRecoveryFuture(final Future<Boolean> future) {
        recoveryFuture = future;
    }

    public boolean canAttemptRecovery() {
        return recoveryFuture == null || recoveryFuture.isDone();
    }

    public void setFenceFuture(final Future<Boolean> future) {
        fenceFuture = future;
    }

    public boolean canAttemptFencing() {
        return fenceFuture == null || fenceFuture.isDone();
    }

}
