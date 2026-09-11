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

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import com.cloud.event.ActionEventUtils;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.ha.HAConfig;
import org.apache.cloudstack.ha.HAConfigVO;
import org.apache.cloudstack.ha.HAManager;
import org.apache.cloudstack.ha.HAResource;
import org.apache.cloudstack.ha.HAResourceCounter;
import org.apache.cloudstack.ha.provider.HACheckerException;
import org.apache.cloudstack.ha.provider.HAFenceException;
import org.apache.cloudstack.ha.provider.HAProvider;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HATaskTest {
    private HAResource resource;
    private HAProvider<HAResource> provider;
    private HAManager manager;
    private HAConfigVO config;
    private HAResourceCounter counter;
    private HAResourceCounter.TaskToken token;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        resource = mock(HAResource.class);
        provider = mock(HAProvider.class);
        manager = mock(HAManager.class);
        counter = new HAResourceCounter();
        config = new HAConfigVO();
        config.setResourceId(1L);
        config.setResourceType(HAResource.ResourceType.Host);
        config.setEnabled(true);
        config.setHastate(HAConfig.HAState.Checking);
        when(provider.getConfigValue(any(), eq(resource))).thenReturn(10L);
        when(provider.getConfigValue(HAProvider.HAProviderConfig.MaxActivityChecks, resource)).thenReturn(7L);
        when(provider.getConfigValue(HAProvider.HAProviderConfig.ActivityCheckFailureRatio, resource)).thenReturn(0.5D);
        when(provider.getConfigValue(HAProvider.HAProviderConfig.ActivityCheckSuccessThreshold, resource)).thenReturn(3L);
        when(provider.checkPowerState(resource)).thenReturn(HAProvider.PowerObservation.UNKNOWN);
        when(provider.getPowerOffConfirmations(resource)).thenReturn(3L);
        when(provider.getPowerOffMaxInterval(resource)).thenReturn(60L);
        when(manager.getCurrentHAConfig(any(), eq(counter), any())).thenAnswer(invocation ->
                config.isEnabled() && counter.isCurrentTask(invocation.getArgument(2)) ? config : null);
        when(manager.transitionHAState(any(), eq(config))).thenAnswer(invocation -> {
            HAConfig.Event event = invocation.getArgument(0);
            config.setHastate(HAConfig.HAState.getStateMachine().getNextState(config.getState(), event));
            return true;
        });
    }

    private <T extends BaseHATask> T bind(T task, HAResourceCounter.Operation operation) throws Exception {
        token = counter.tryStartTask(operation);
        assertNotNull(token);
        task.initialize(counter, token);
        Field field = BaseHATask.class.getDeclaredField("haManager");
        field.setAccessible(true);
        field.set(task, manager);
        return task;
    }

    private void activity(boolean alive, Throwable error) throws Exception {
        try (MockedStatic<ActionEventUtils> events = Mockito.mockStatic(ActionEventUtils.class);
             MockedStatic<CallContext> context = Mockito.mockStatic(CallContext.class)) {
            context.when(CallContext::current).thenReturn(mock(CallContext.class));
            activityWithEventContext(alive, error);
        }
    }

    private void activityWithEventContext(boolean alive, Throwable error) throws Exception {
        if (config.getState() == HAConfig.HAState.Suspect) {
            config.setHastate(HAConfig.HAState.getStateMachine().getNextState(config.getState(), HAConfig.Event.PerformActivityCheck));
        }
        ActivityCheckTask task = bind(new ActivityCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.ActivityCheckTimeout, null, 0), HAResourceCounter.Operation.ACTIVITY);
        try {
            task.processResult(alive, error);
        } finally {
            counter.finishTask(token);
        }
    }

    private void health(HAProvider.PowerObservation power, boolean healthy, Throwable error) throws Exception {
        when(provider.checkPowerState(resource)).thenReturn(power);
        when(provider.isHealthy(resource)).thenReturn(healthy);
        HealthCheckTask task = bind(new HealthCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.HealthCheckTimeout, null), HAResourceCounter.Operation.HEALTH);
        try {
            boolean result = task.performAction();
            task.processResult(result, error);
        } finally {
            counter.finishTask(token);
        }
    }

    @Test
    public void initialFourSuccessesDoNotEndSevenAttemptObservation() throws Exception {
        for (int i = 0; i < 4; i++) {
            activity(true, null);
            assertEquals(i < 2 ? HAConfig.HAState.Suspect : HAConfig.HAState.Degraded, config.getState());
        }
        for (int i = 0; i < 3; i++) {
            activity(false, null);
            assertEquals(HAConfig.HAState.Degraded, config.getState());
        }
        activity(false, null);
        assertEquals(HAConfig.HAState.Recovering, config.getState());
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.ActivityCheckFailureUnderThresholdRatio), any());
    }

    @Test
    public void nineAttemptSettingStillRequiresFiveConsecutiveFailures() throws Exception {
        when(provider.getConfigValue(HAProvider.HAProviderConfig.MaxActivityChecks, resource)).thenReturn(9L);
        for (int i = 0; i < 4; i++) {
            activity(true, null);
        }
        for (int i = 0; i < 4; i++) {
            activity(false, null);
            assertEquals(HAConfig.HAState.Degraded, config.getState());
        }
        activity(false, null);
        assertEquals(HAConfig.HAState.Recovering, config.getState());
    }

    @Test
    public void successAndUnknownEachBreakConsecutiveFailures() throws Exception {
        activity(false, null);
        activity(false, null);
        activity(true, null);
        activity(false, null);
        activity(false, null);
        activity(false, new TimeoutException());
        assertEquals(5, counter.getActivityCheckCounter());
        assertEquals(0, counter.getConsecutiveActivityCheckFailureCounter());
        for (int i = 0; i < 3; i++) {
            activity(false, null);
            assertEquals(HAConfig.HAState.Suspect, config.getState());
        }
    }

    @Test
    public void checkerExceptionIsUnknownRatherThanADeadSample() throws Exception {
        activity(false, new HACheckerException("witness unavailable", null));
        assertEquals(0, counter.getActivityCheckCounter());
        assertEquals(HAConfig.HAState.Suspect, config.getState());
    }

    @Test
    public void onlyConsecutiveAliveObservationsReachDegraded() throws Exception {
        activity(true, null);
        activity(true, null);
        activity(false, null);
        activity(true, null);
        activity(true, null);
        assertEquals(HAConfig.HAState.Suspect, config.getState());
        activity(false, new TimeoutException());
        assertEquals(0, counter.getConsecutiveActivityCheckSuccessCounter());
        activity(true, null);
        activity(true, null);
        assertEquals(HAConfig.HAState.Suspect, config.getState());
        activity(true, null);
        assertEquals(HAConfig.HAState.Degraded, config.getState());
        verify(manager, times(1)).transitionHAState(HAConfig.Event.ActivityCheckSuccessThresholdReached, config);
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.HealthCheckPassed), any());
    }

    @Test
    public void degradedSurvivesAliveUnknownAndSubthresholdDeadResults() throws Exception {
        config.setHastate(HAConfig.HAState.Degraded);
        activity(true, null);
        assertEquals(HAConfig.HAState.Degraded, config.getState());
        activity(false, null);
        activity(false, null);
        activity(false, new TimeoutException());
        assertEquals(HAConfig.HAState.Degraded, config.getState());
        assertEquals(0, counter.getConsecutiveActivityCheckFailureCounter());
        for (int i = 1; i <= 3; i++) {
            activity(false, null);
            assertEquals(HAConfig.HAState.Degraded, config.getState());
            assertEquals(i, counter.getConsecutiveActivityCheckFailureCounter());
        }
        activity(false, null);
        assertEquals(HAConfig.HAState.Recovering, config.getState());
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.TooFewActivityCheckSamples), any());
    }

    @Test
    public void invalidFailureConfigurationDoesNotBlockAliveEvidence() throws Exception {
        when(provider.getConfigValue(HAProvider.HAProviderConfig.MaxActivityChecks, resource)).thenReturn(0L);
        when(provider.getConfigValue(HAProvider.HAProviderConfig.ActivityCheckFailureRatio, resource)).thenReturn(Double.NaN);
        for (int i = 0; i < 3; i++) {
            activity(true, null);
        }
        assertEquals(HAConfig.HAState.Degraded, config.getState());
        for (int i = 0; i < 10; i++) {
            activity(false, null);
        }
        assertEquals(HAConfig.HAState.Degraded, config.getState());
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.ActivityCheckFailureOverThresholdRatio), any());
    }

    @Test
    public void invalidSuccessThresholdDoesNotBlockDeadRecovery() throws Exception {
        when(provider.getConfigValue(HAProvider.HAProviderConfig.ActivityCheckSuccessThreshold, resource)).thenReturn(0L);
        for (int i = 0; i < 10; i++) {
            activity(true, null);
        }
        assertEquals(HAConfig.HAState.Suspect, config.getState());
        for (int i = 0; i < 4; i++) {
            activity(false, null);
        }
        assertEquals(HAConfig.HAState.Recovering, config.getState());
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.ActivityCheckSuccessThresholdReached), any());
    }

    @Test
    public void configurableSuccessThresholdControlsDegradedEntry() throws Exception {
        when(provider.getConfigValue(HAProvider.HAProviderConfig.ActivityCheckSuccessThreshold, resource)).thenReturn(5L);
        for (int i = 0; i < 4; i++) {
            activity(true, null);
            assertEquals(HAConfig.HAState.Suspect, config.getState());
        }
        activity(true, null);
        assertEquals(HAConfig.HAState.Degraded, config.getState());
    }

    @Test
    public void disabledActivityResultCannotEnterDegraded() throws Exception {
        counter.incrActivityCounter(false);
        counter.incrActivityCounter(false);
        ActivityCheckTask task = bind(new ActivityCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.ActivityCheckTimeout, null, 0), HAResourceCounter.Operation.ACTIVITY);
        config.setEnabled(false);
        task.processResult(true, null);
        assertEquals(2, counter.getConsecutiveActivityCheckSuccessCounter());
        assertEquals(HAConfig.HAState.Checking, config.getState());
        verify(manager, never()).transitionHAState(any(), any());
    }

    @Test
    public void staleAliveResultCannotRecreateDegradedAfterCycleReset() throws Exception {
        counter.incrActivityCounter(false);
        counter.incrActivityCounter(false);
        ActivityCheckTask task = bind(new ActivityCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.ActivityCheckTimeout, null, 0), HAResourceCounter.Operation.ACTIVITY);
        counter.resetForNewCycle();
        task.processResult(true, null);
        assertEquals(0, counter.getConsecutiveActivityCheckSuccessCounter());
        verify(manager, never()).transitionHAState(any(), any());
    }

    @Test
    public void healthyDegradedHostReturnsAvailableAndClearsAliveHistory() throws Exception {
        config.setHastate(HAConfig.HAState.Degraded);
        counter.incrActivityCounter(false);
        counter.incrActivityCounter(false);
        HealthCheckTask task = bind(new HealthCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.HealthCheckTimeout, null), HAResourceCounter.Operation.HEALTH);
        task.processResult(true, null);
        assertEquals(HAConfig.HAState.Available, config.getState());
        assertEquals(0, counter.getActivityCheckCounter());
        assertEquals(0, counter.getConsecutiveActivityCheckSuccessCounter());
        assertEquals(0, counter.getConsecutiveActivityCheckFailureCounter());
        assertFalse(counter.isCurrentTask(token));
    }

    @Test
    public void failedDegradedHealthDoesNotInterruptActivityDeadSequence() throws Exception {
        config.setHastate(HAConfig.HAState.Degraded);
        activity(false, null);
        activity(false, null);
        HealthCheckTask task = bind(new HealthCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.HealthCheckTimeout, null), HAResourceCounter.Operation.HEALTH);
        task.processResult(false, null);
        counter.finishTask(token);
        assertEquals(HAConfig.HAState.Degraded, config.getState());
        assertEquals(2, counter.getConsecutiveActivityCheckFailureCounter());
        activity(false, null);
        assertEquals(HAConfig.HAState.Degraded, config.getState());
        activity(false, null);
        assertEquals(HAConfig.HAState.Recovering, config.getState());
    }

    @Test
    public void failedHealthBetweenAliveProbesDoesNotPreventDegradedEntry() throws Exception {
        activity(true, null);
        activity(true, null);
        HealthCheckTask task = bind(new HealthCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.HealthCheckTimeout, null), HAResourceCounter.Operation.HEALTH);
        task.processResult(false, null);
        counter.finishTask(token);
        assertEquals(2, counter.getConsecutiveActivityCheckSuccessCounter());
        activity(true, null);
        assertEquals(HAConfig.HAState.Degraded, config.getState());
    }

    @Test
    public void continuousObservationWritesEventsOnlyForDegradedAndRecoveryDecisions() throws Exception {
        try (MockedStatic<ActionEventUtils> events = Mockito.mockStatic(ActionEventUtils.class);
             MockedStatic<CallContext> context = Mockito.mockStatic(CallContext.class)) {
            context.when(CallContext::current).thenReturn(mock(CallContext.class));
            for (int i = 0; i < 20; i++) {
                activityWithEventContext(true, null);
            }
            assertEquals(HAConfig.HAState.Degraded, config.getState());
            for (int i = 0; i < 4; i++) {
                activityWithEventContext(false, null);
            }
            assertEquals(HAConfig.HAState.Recovering, config.getState());
            events.verify(() -> ActionEventUtils.onActionEvent(anyLong(), anyLong(), anyLong(),
                    anyString(), anyString(), anyLong(), anyString()), times(2));
            events.verifyNoMoreInteractions();
        }
    }

    @Test
    public void oldGenerationResultCannotChangeCountersOrState() throws Exception {
        ActivityCheckTask task = bind(new ActivityCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.ActivityCheckTimeout, null, 0), HAResourceCounter.Operation.ACTIVITY);
        counter.resetForNewCycle();
        task.processResult(false, null);
        assertEquals(0, counter.getActivityCheckCounter());
        verify(manager, never()).transitionHAState(any(), any());
    }

    @Test
    public void threeSeparateHealthResultsConfirmPowerOffWithoutFakeActivitySamples() throws Exception {
        config.setHastate(HAConfig.HAState.Available);
        for (int i = 1; i <= 3; i++) {
            health(HAProvider.PowerObservation.OFF, true, null);
            assertEquals(i, counter.getConsecutivePowerOffCounter());
            assertEquals(i < 3 ? HAConfig.HAState.Suspect : HAConfig.HAState.Fencing, config.getState());
            verify(provider, times(i)).checkPowerState(resource);
        }
        assertEquals(0, counter.getActivityCheckCounter());
        verify(provider, never()).isHealthy(resource);
        verify(manager, times(1)).transitionHAState(HAConfig.Event.PowerOffConfirmed, config);
    }

    @Test
    public void unknownActivityResultConsumesRequiredRecheckWithoutCreatingDeadEvidence() throws Exception {
        long now = System.nanoTime();
        counter.recordPowerOffObservation(now - TimeUnit.SECONDS.toNanos(61), 60, 3);
        counter.recordPowerOffObservation(now, 60, 3);
        assertFalse(counter.hasPendingPowerOffObservation(now, 60, 3));
        activity(false, new IllegalStateException("storage unavailable"));
        assertEquals(0, counter.getConsecutiveActivityCheckFailureCounter());
        assertTrue(counter.hasPendingPowerOffObservation(now, 60, 3));
    }

    @Test
    public void staleActivityResultCannotConsumeNewCycleRecheck() throws Exception {
        ActivityCheckTask oldTask = bind(new ActivityCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.ActivityCheckTimeout, null, 0), HAResourceCounter.Operation.ACTIVITY);
        counter.resetForNewCycle();
        long now = System.nanoTime();
        counter.recordPowerOffObservation(now - TimeUnit.SECONDS.toNanos(61), 60, 3);
        counter.recordPowerOffObservation(now, 60, 3);
        oldTask.processResult(true, null);
        assertFalse(counter.hasPendingPowerOffObservation(now, 60, 3));
        assertEquals(0, counter.getConsecutiveActivityCheckSuccessCounter());
    }

    @Test
    public void singlePowerOffProbeDoesNotCountUntilItsValidatedResultArrives() throws Exception {
        config.setHastate(HAConfig.HAState.Available);
        when(provider.checkPowerState(resource)).thenReturn(HAProvider.PowerObservation.OFF);
        HealthCheckTask task = bind(new HealthCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.HealthCheckTimeout, null), HAResourceCounter.Operation.HEALTH);
        assertFalse(task.performAction());
        assertEquals(0, counter.getConsecutivePowerOffCounter());
        task.processResult(false, null);
        assertEquals(1, counter.getConsecutivePowerOffCounter());
        assertEquals(HAConfig.HAState.Suspect, config.getState());
        verify(provider, times(1)).checkPowerState(resource);
        verify(provider, never()).isHealthy(resource);
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.PowerOffConfirmed), any());
    }

    @Test
    public void duplicatePowerOffCallbackCannotSupplyMultipleConfirmations() throws Exception {
        config.setHastate(HAConfig.HAState.Available);
        when(provider.checkPowerState(resource)).thenReturn(HAProvider.PowerObservation.OFF);
        HealthCheckTask task = bind(new HealthCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.HealthCheckTimeout, null), HAResourceCounter.Operation.HEALTH);
        task.performAction();
        task.processResult(false, null);
        task.processResult(false, null);
        task.processResult(false, null);
        assertEquals(1, counter.getConsecutivePowerOffCounter());
        assertEquals(HAConfig.HAState.Suspect, config.getState());
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.PowerOffConfirmed), any());
    }

    @Test
    public void powerOnBreaksOffSequenceEvenWhenTheHostIsStillUnhealthy() throws Exception {
        config.setHastate(HAConfig.HAState.Available);
        health(HAProvider.PowerObservation.OFF, false, null);
        health(HAProvider.PowerObservation.OFF, false, null);
        health(HAProvider.PowerObservation.ON, false, null);
        assertEquals(0, counter.getConsecutivePowerOffCounter());
        assertEquals(HAConfig.HAState.Suspect, config.getState());
        health(HAProvider.PowerObservation.OFF, false, null);
        assertEquals(1, counter.getConsecutivePowerOffCounter());
        verify(provider, times(1)).isHealthy(resource);
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.PowerOffConfirmed), any());
    }

    @Test
    public void unknownPowerBreaksOffSequenceWithoutAuthorizingFencing() throws Exception {
        config.setHastate(HAConfig.HAState.Available);
        health(HAProvider.PowerObservation.OFF, false, null);
        health(HAProvider.PowerObservation.OFF, false, null);
        health(HAProvider.PowerObservation.UNKNOWN, false, null);
        assertEquals(0, counter.getConsecutivePowerOffCounter());
        health(HAProvider.PowerObservation.OFF, false, null);
        assertEquals(1, counter.getConsecutivePowerOffCounter());
        assertEquals(HAConfig.HAState.Suspect, config.getState());
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.PowerOffConfirmed), any());
    }

    @Test
    public void unavailablePowerWitnessFallsBackToExistingHealthCheck() throws Exception {
        config.setHastate(HAConfig.HAState.Suspect);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        when(provider.checkPowerState(resource)).thenThrow(new HACheckerException("BMC unavailable", null));
        when(provider.isHealthy(resource)).thenReturn(true);
        HealthCheckTask task = bind(new HealthCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.HealthCheckTimeout, null), HAResourceCounter.Operation.HEALTH);
        assertTrue(task.performAction());
        task.processResult(true, null);
        assertEquals(0, counter.getConsecutivePowerOffCounter());
        assertEquals(HAConfig.HAState.Available, config.getState());
        verify(provider).isHealthy(resource);
    }

    @Test
    public void powerOffObservationThatTimesOutCannotFence() throws Exception {
        config.setHastate(HAConfig.HAState.Available);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        when(provider.checkPowerState(resource)).thenReturn(HAProvider.PowerObservation.OFF);
        HealthCheckTask task = bind(new HealthCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.HealthCheckTimeout, null), HAResourceCounter.Operation.HEALTH);
        task.performAction();
        task.processResult(false, new TimeoutException());
        task.processResult(false, null);
        assertEquals(0, counter.getConsecutivePowerOffCounter());
        assertEquals(HAConfig.HAState.Suspect, config.getState());
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.PowerOffConfirmed), any());
    }

    @Test
    public void degradedHostCanFenceAfterThreeFreshPowerOffResults() throws Exception {
        config.setHastate(HAConfig.HAState.Degraded);
        for (int i = 1; i <= 3; i++) {
            health(HAProvider.PowerObservation.OFF, false, null);
            assertEquals(i < 3 ? HAConfig.HAState.Degraded : HAConfig.HAState.Fencing, config.getState());
        }
        verify(provider, never()).isHealthy(resource);
        verify(manager, times(1)).transitionHAState(HAConfig.Event.PowerOffConfirmed, config);
    }

    @Test
    public void failedHealthCallbackDiscardsPreviouslyCollectedOffEvidence() throws Exception {
        config.setHastate(HAConfig.HAState.Suspect);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        health(HAProvider.PowerObservation.OFF, false, new HACheckerException("worker failed", null));
        assertEquals(0, counter.getConsecutivePowerOffCounter());
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.PowerOffConfirmed), any());
    }

    @Test
    public void stalePowerOffResultCannotRestoreEvidenceAfterCycleReset() throws Exception {
        config.setHastate(HAConfig.HAState.Suspect);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        when(provider.checkPowerState(resource)).thenReturn(HAProvider.PowerObservation.OFF);
        HealthCheckTask task = bind(new HealthCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.HealthCheckTimeout, null), HAResourceCounter.Operation.HEALTH);
        task.performAction();
        counter.resetForNewCycle();
        task.processResult(false, null);
        assertEquals(0, counter.getConsecutivePowerOffCounter());
        verify(manager, never()).transitionHAState(any(), any());
    }

    @Test
    public void disabledPowerOffCallbackCannotAddEvidenceOrFence() throws Exception {
        config.setHastate(HAConfig.HAState.Suspect);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        when(provider.checkPowerState(resource)).thenReturn(HAProvider.PowerObservation.OFF);
        HealthCheckTask task = bind(new HealthCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.HealthCheckTimeout, null), HAResourceCounter.Operation.HEALTH);
        task.performAction();
        config.setEnabled(false);
        task.processResult(false, null);
        assertTrue(counter.getConsecutivePowerOffCounter() < 3);
        verify(manager, never()).transitionHAState(any(), any());
    }

    @Test
    public void healthyHostClearsOffHistoryBeforeANewFailureSequence() throws Exception {
        config.setHastate(HAConfig.HAState.Available);
        health(HAProvider.PowerObservation.OFF, false, null);
        health(HAProvider.PowerObservation.OFF, false, null);
        health(HAProvider.PowerObservation.ON, true, null);
        assertEquals(HAConfig.HAState.Available, config.getState());
        assertEquals(0, counter.getConsecutivePowerOffCounter());
        health(HAProvider.PowerObservation.OFF, false, null);
        assertEquals(HAConfig.HAState.Suspect, config.getState());
        assertEquals(1, counter.getConsecutivePowerOffCounter());
    }

    @Test
    public void healthyAlreadyAvailableResultResetsCycleEvenWithoutStateUpdate() throws Exception {
        config.setHastate(HAConfig.HAState.Available);
        when(manager.transitionHAState(HAConfig.Event.HealthCheckPassed, config)).thenReturn(false);
        counter.incrActivityCounter(true);
        HealthCheckTask task = bind(new HealthCheckTask(resource, provider, config,
                HAProvider.HAProviderConfig.HealthCheckTimeout, null), HAResourceCounter.Operation.HEALTH);
        task.processResult(true, null);
        assertEquals(0, counter.getActivityCheckCounter());
        assertFalse(counter.isCurrentTask(token));
    }

    @Test
    public void recoveryNeverQueuesVmRestartWithoutVerifiedFencing() throws Exception {
        config.setHastate(HAConfig.HAState.Recovering);
        RecoveryTask task = bind(new RecoveryTask(resource, provider, config,
                HAProvider.HAProviderConfig.RecoveryTimeout, null), HAResourceCounter.Operation.RECOVERY);
        task.processResult(true, null);
        assertEquals(HAConfig.HAState.Recovered, config.getState());
        verify(provider, never()).fenceSubResources(resource);
    }

    @Test
    public void timeoutHoldsReservationAndBlocksNextStageAfterSwallowedInterrupt() throws Exception {
        when(provider.getConfigValue(HAProvider.HAProviderConfig.RecoveryTimeout, resource)).thenReturn(1L);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        BaseHATask task = bind(new BaseHATask(resource, provider, config,
                HAProvider.HAProviderConfig.RecoveryTimeout, null) {
            @Override
            public boolean performAction() throws HAFenceException {
                entered.countDown();
                boolean done = false;
                while (!done) {
                    try {
                        release.await();
                        done = true;
                    } catch (InterruptedException ignored) {
                        // Deliberately model a driver that cannot cancel its I/O.
                    }
                }
                if (isCurrentTask()) {
                    return provider.fence(resource);
                }
                return false;
            }
            @Override
            public void processResult(boolean result, Throwable throwable) {
                assertFalse(result);
                error.set(throwable);
            }
        }, HAResourceCounter.Operation.RECOVERY);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> outer = executor.submit(task);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertFalse(outer.get(3, TimeUnit.SECONDS));
            assertTrue(error.get() instanceof TimeoutException);
            assertTrue(counter.hasActiveTask());
            assertTrue(counter.isCurrentTask(token));
            assertNull(counter.tryStartTask(HAResourceCounter.Operation.FENCE));
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (counter.hasActiveTask() && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertFalse(counter.hasActiveTask());
            verify(provider, never()).fence(resource);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
