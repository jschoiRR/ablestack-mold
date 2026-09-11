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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.dc.ClusterDetailsDao;
import com.cloud.dc.dao.DataCenterDetailsDao;
import com.cloud.resource.ResourceState;
import com.cloud.utils.component.ComponentContext;
import org.apache.cloudstack.ha.dao.HAConfigDao;
import org.apache.cloudstack.ha.provider.HAProvider;
import org.apache.cloudstack.ha.task.BaseHATask;
import org.apache.cloudstack.kernel.Partition;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class HAManagerImplTest {
    private HAManagerImpl manager;
    private HAConfigVO config;
    private HAConfigDao dao;
    private HostVO host;
    private HAProvider<HAResource> provider;
    private HAResourceCounter counter;
    private ExecutorService executor;
    private Map<String, Object> previousExecutors = new HashMap<>();

    private static void field(Object target, String name, Object value) throws Exception {
        Field f = HAManagerImpl.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        manager = spy(new HAManagerImpl());
        config = new HAConfigVO();
        config.setResourceId(1L);
        config.setResourceType(HAResource.ResourceType.Host);
        config.setHaProvider("kvm");
        config.setEnabled(true);
        config.setHastate(HAConfig.HAState.Suspect);
        config.setManagementServerId(ManagementServerNode.getManagementServerId());
        dao = mock(HAConfigDao.class);
        host = mock(HostVO.class);
        HostDao hostDao = mock(HostDao.class);
        when(hostDao.findById(1L)).thenReturn(host);
        when(dao.findHAResource(1L, HAResource.ResourceType.Host)).thenReturn(config);
        field(manager, "haConfigDao", dao);
        field(manager, "hostDao", hostDao);
        field(manager, "clusterDetailsDao", mock(ClusterDetailsDao.class));
        field(manager, "dataCenterDetailsDao", mock(DataCenterDetailsDao.class));
        provider = mock(HAProvider.class);
        when(provider.getConfigValue(any(), eq(host))).thenReturn(5L);
        when(provider.getConfigValue(HAProvider.HAProviderConfig.ActivityCheckFailureRatio, host)).thenReturn(0.5D);
        when(provider.getConfigValue(HAProvider.HAProviderConfig.ActivityCheckSuccessThreshold, host)).thenReturn(3L);
        when(provider.getPowerOffConfirmations(host)).thenReturn(3L);
        when(provider.getPowerOffMaxInterval(host)).thenReturn(60L);
        executor = mock(ExecutorService.class);
        when(executor.submit(any(Callable.class))).thenReturn(mock(Future.class));
        for (String name : new String[] {"healthCheckExecutor", "activityCheckExecutor", "recoveryExecutor", "fenceExecutor"}) {
            Field f = HAManagerImpl.class.getDeclaredField(name);
            f.setAccessible(true);
            previousExecutors.put(name, f.get(null));
            f.set(null, executor);
        }
        counter = manager.getHACounter(1L, HAResource.ResourceType.Host);
        counter.synchronizePowerObservationProvider("kvm");
        doReturn(true).when(manager).transitionHAState(any(), any());
    }

    @After
    public void restoreExecutors() throws Exception {
        for (Map.Entry<String, Object> entry : previousExecutors.entrySet()) {
            field(null, entry.getKey(), entry.getValue());
        }
    }

    @Test
    public void currentTaskChecksFreshOwnerEnabledStateAndGeneration() {
        config.setHastate(HAConfig.HAState.Fencing);
        HAResourceCounter.TaskToken token = counter.tryStartTask(HAResourceCounter.Operation.FENCE);
        assertSame(config, manager.getCurrentHAConfig(config, counter, token));
        config.setHastate(HAConfig.HAState.Fenced);
        assertSame(config, manager.getCurrentHAConfig(config, counter, token));
        config.setManagementServerId(ManagementServerNode.getManagementServerId() + 1);
        assertNull(manager.getCurrentHAConfig(config, counter, token));
        config.setManagementServerId(null);
        assertNull(manager.getCurrentHAConfig(config, counter, token));
        config.setManagementServerId(ManagementServerNode.getManagementServerId());
        config.setEnabled(false);
        assertNull(manager.getCurrentHAConfig(config, counter, token));
        config.setEnabled(true);
        config.setHastate(HAConfig.HAState.Available);
        assertNull(manager.getCurrentHAConfig(config, counter, token));
        config.setHastate(HAConfig.HAState.Fenced);
        counter.resetForNewCycle();
        assertNull(manager.getCurrentHAConfig(config, counter, token));
    }

    @Test
    public void stoppedManagerCannotReclaimOwnershipThroughALateHealthResult() {
        config.setHastate(HAConfig.HAState.Available);
        HAResourceCounter.TaskToken token = counter.tryStartTask(HAResourceCounter.Operation.HEALTH);
        manager.stop();
        config.setManagementServerId(null);
        assertNull(manager.getCurrentHAConfig(config, counter, token));
        manager.processHAResource(config, host, provider);
        verifyNoInteractions(executor);
    }

    @Test
    public void queuedActivityCannotApplyAfterHostBecomesAvailable() {
        HAConfigVO expected = new HAConfigVO();
        expected.setResourceId(1L);
        expected.setResourceType(HAResource.ResourceType.Host);
        expected.setHaProvider("kvm");
        expected.setHastate(HAConfig.HAState.Checking);
        HAResourceCounter.TaskToken token = counter.tryStartTask(HAResourceCounter.Operation.ACTIVITY);
        config.setHastate(HAConfig.HAState.Available);
        assertNull(manager.getCurrentHAConfig(expected, counter, token));
    }

    @Test
    public void activityResultsAcceptDegradedButRejectDisabledOrChangedOwner() {
        config.setHastate(HAConfig.HAState.Degraded);
        HAResourceCounter.TaskToken token = counter.tryStartTask(HAResourceCounter.Operation.ACTIVITY);
        assertSame(config, manager.getCurrentHAConfig(config, counter, token));
        config.setEnabled(false);
        assertNull(manager.getCurrentHAConfig(config, counter, token));
        config.setEnabled(true);
        config.setManagementServerId(ManagementServerNode.getManagementServerId() + 1);
        assertNull(manager.getCurrentHAConfig(config, counter, token));
        config.setManagementServerId(null);
        assertNull(manager.getCurrentHAConfig(config, counter, token));
        config.setManagementServerId(ManagementServerNode.getManagementServerId());
        config.setHastate(HAConfig.HAState.Fencing);
        assertNull(manager.getCurrentHAConfig(config, counter, token));
    }

    @Test
    public void purgingCounterDoesNotUnlockStillRunningDestructiveTask() {
        HAResourceCounter.TaskToken token = counter.tryStartTask(HAResourceCounter.Operation.FENCE);
        manager.purgeHACounter(1L, HAResource.ResourceType.Host);
        assertSame(counter, manager.getHACounter(1L, HAResource.ResourceType.Host));
        assertFalse(counter.isCurrentTask(token));
        assertNull(counter.tryStartTask(HAResourceCounter.Operation.FENCE));
    }

    @Test
    public void checkingWithoutLiveTaskResumesSuspectAfterRestart() {
        config.setHastate(HAConfig.HAState.Checking);
        manager.processHAResource(config, host, provider);
        verify(manager).transitionHAState(HAConfig.Event.TooFewActivityCheckSamples, config);
        verifyNoInteractions(executor);
    }

    @Test
    public void checkingWithLiveTaskIsNotResetByPoll() {
        config.setHastate(HAConfig.HAState.Checking);
        counter.tryStartTask(HAResourceCounter.Operation.ACTIVITY);
        manager.processHAResource(config, host, provider);
        verify(manager, never()).transitionHAState(any(), any());
    }

    @Test
    public void completedActivityGetsHealthTurnEvenWhenActivityIsImmediatelyDue() {
        HAResourceCounter.TaskToken token = counter.tryStartTask(HAResourceCounter.Operation.ACTIVITY);
        counter.finishTask(token);
        doReturn(true).when(manager).submitHATask(any(), any(), any(), any(), any());
        manager.processHAResource(config, host, provider);
        verify(manager).submitHATask(host, provider, config, counter, HAResourceCounter.Operation.HEALTH);
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.PerformActivityCheck), any());
    }

    @Test
    public void suspectWithPendingOffEvidencePrioritizesAnotherHealthPoll() {
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        assertFalse(counter.needsHealthCheck());
        doReturn(true).when(manager).submitHATask(any(), any(), any(), any(), any());
        manager.processHAResource(config, host, provider);
        verify(manager).submitHATask(host, provider, config, counter, HAResourceCounter.Operation.HEALTH);
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.PerformActivityCheck), any());
        verify(manager, never()).submitHATask(any(), any(), any(), any(), eq(HAResourceCounter.Operation.ACTIVITY));
    }

    @Test
    public void degradedWithPendingOffEvidencePrioritizesHealthWithoutLosingState() {
        config.setHastate(HAConfig.HAState.Degraded);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        assertFalse(counter.needsHealthCheck());
        doReturn(true).when(manager).submitHATask(any(), any(), any(), any(), any());
        manager.processHAResource(config, host, provider);
        verify(manager).submitHATask(host, provider, config, counter, HAResourceCounter.Operation.HEALTH);
        verify(manager, never()).submitHATask(any(), any(), any(), any(), eq(HAResourceCounter.Operation.ACTIVITY));
        verify(manager, never()).transitionHAState(any(), any());
        assertEquals(HAConfig.HAState.Degraded, config.getState());
    }

    @Test
    public void repeatedPollsDoNotQueueDuplicatePowerObservationTasks() {
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        try (MockedStatic<ComponentContext> context = Mockito.mockStatic(ComponentContext.class)) {
            context.when(() -> ComponentContext.inject(any(BaseHATask.class))).thenAnswer(i -> i.getArgument(0));
            for (int i = 0; i < 5; i++) {
                manager.processHAResource(config, host, provider);
            }
            verify(executor, times(1)).submit(any(Callable.class));
            assertTrue(counter.hasActiveTask());
            assertEquals(1, counter.getConsecutivePowerOffCounter());
            verify(manager, times(1)).submitHATask(host, provider, config, counter, HAResourceCounter.Operation.HEALTH);
        }
    }

    @Test
    public void foreignOwnershipDiscardsLocalOffEvidenceBeforeLaterReclaim() throws Exception {
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        config.setManagementServerId(ManagementServerNode.getManagementServerId() + 1);
        Method checkOwnership = HAManagerImpl.class.getDeclaredMethod("checkHAOwnership", HAConfig.class);
        checkOwnership.setAccessible(true);
        assertEquals(false, checkOwnership.invoke(manager, config));
        assertEquals(0, counter.getConsecutivePowerOffCounter());
        config.setManagementServerId(ManagementServerNode.getManagementServerId());
        assertEquals(true, checkOwnership.invoke(manager, config));
        assertEquals(1, counter.recordPowerOffObservation(System.nanoTime(), 60, 3));
    }

    @Test
    public void changedProviderInvalidatesPowerEvidenceFromQueuedHealthTask() {
        HAConfigVO expected = new HAConfigVO();
        expected.setResourceId(1L);
        expected.setResourceType(HAResource.ResourceType.Host);
        expected.setHaProvider("kvm");
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        HAResourceCounter.TaskToken token = counter.tryStartTask(HAResourceCounter.Operation.HEALTH);
        config.setHaProvider("other");
        assertNull(manager.getCurrentHAConfig(expected, counter, token));
        assertEquals(0, counter.getConsecutivePowerOffCounter());
    }

    @Test
    public void disabledConfigurationDiscardsOffEvidenceFromQueuedHealthTask() {
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        HAResourceCounter.TaskToken token = counter.tryStartTask(HAResourceCounter.Operation.HEALTH);
        config.setEnabled(false);
        assertNull(manager.getCurrentHAConfig(config, counter, token));
        assertEquals(0, counter.getConsecutivePowerOffCounter());
    }

    @Test
    public void ownerlessPollCannotReusePreviousOwnersOffEvidence() {
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        config.setManagementServerId(null);
        manager.processHAResource(config, host, provider);
        assertEquals(0, counter.getConsecutivePowerOffCounter());
        verify(manager, never()).submitHATask(any(), any(), any(), any(), eq(HAResourceCounter.Operation.HEALTH));
    }

    @Test
    public void missingProviderDiscardsPreviouslyCollectedOffEvidence() throws Exception {
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        field(manager, "haProviderMap", new HashMap<String, HAProvider<HAResource>>());
        Method validate = HAManagerImpl.class.getDeclaredMethod("validateAndFindHAProvider", HAConfig.class, HAResource.class);
        validate.setAccessible(true);
        assertNull(validate.invoke(manager, config, host));
        assertEquals(0, counter.getConsecutivePowerOffCounter());
    }

    @Test
    public void changedProviderBetweenPollsCannotContinuePreviousOffSequence() {
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        counter.recordPowerOffObservation(System.nanoTime(), 60, 3);
        config.setHaProvider("other");
        manager.processHAResource(config, host, provider);
        assertEquals(0, counter.getConsecutivePowerOffCounter());
        verify(manager, never()).submitHATask(any(), any(), any(), any(), eq(HAResourceCounter.Operation.HEALTH));
    }

    @Test
    public void degradedPollSubmitsActivityWithoutChangingToSuspectOrChecking() {
        config.setHastate(HAConfig.HAState.Degraded);
        doReturn(true).when(manager).submitHATask(any(), any(), any(), any(), any());
        manager.processHAResource(config, host, provider);
        verify(manager).submitHATask(host, provider, config, counter, HAResourceCounter.Operation.ACTIVITY);
        verify(manager, never()).transitionHAState(any(), any());
        assertEquals(HAConfig.HAState.Degraded, config.getState());
    }

    @Test
    public void degradedActivityCompletionStillAllowsHealthRecoveryAndPowerWitness() {
        config.setHastate(HAConfig.HAState.Degraded);
        HAResourceCounter.TaskToken token = counter.tryStartTask(HAResourceCounter.Operation.ACTIVITY);
        counter.finishTask(token);
        doReturn(true).when(manager).submitHATask(any(), any(), any(), any(), any());
        manager.processHAResource(config, host, provider);
        verify(manager).submitHATask(host, provider, config, counter, HAResourceCounter.Operation.HEALTH);
        verify(manager, never()).submitHATask(any(), any(), any(), any(), eq(HAResourceCounter.Operation.ACTIVITY));
        assertEquals(HAConfig.HAState.Degraded, config.getState());
    }

    @Test
    public void degradedWaitsForConfiguredActivityIntervalWithoutLosingDeadStreak() {
        config.setHastate(HAConfig.HAState.Degraded);
        counter.incrActivityCounter(true);
        counter.incrActivityCounter(true);
        assertTrue(counter.canPerformActivityCheck(60L));
        when(provider.getConfigValue(HAProvider.HAProviderConfig.MaxActivityCheckInterval, host)).thenReturn(60L);
        doReturn(true).when(manager).submitHATask(any(), any(), any(), any(), any());
        manager.processHAResource(config, host, provider);
        verify(manager).submitHATask(host, provider, config, counter, HAResourceCounter.Operation.HEALTH);
        verify(manager, never()).submitHATask(any(), any(), any(), any(), eq(HAResourceCounter.Operation.ACTIVITY));
        assertEquals(2, counter.getConsecutiveActivityCheckFailureCounter());
        assertEquals(HAConfig.HAState.Degraded, config.getState());
    }

    @Test
    public void availableNeverStartsActivityEvenAfterPreviousActivityTurn() {
        config.setHastate(HAConfig.HAState.Available);
        HAResourceCounter.TaskToken token = counter.tryStartTask(HAResourceCounter.Operation.ACTIVITY);
        counter.finishTask(token);
        doReturn(true).when(manager).submitHATask(any(), any(), any(), any(), any());
        manager.processHAResource(config, host, provider);
        verify(manager).submitHATask(host, provider, config, counter, HAResourceCounter.Operation.HEALTH);
        verify(manager, never()).submitHATask(any(), any(), any(), any(), eq(HAResourceCounter.Operation.ACTIVITY));
        verify(manager, never()).transitionHAState(any(), any());
    }

    @Test
    public void duplicateDegradedPollCannotSubmitOverlappingActivity() {
        config.setHastate(HAConfig.HAState.Degraded);
        try (MockedStatic<ComponentContext> context = Mockito.mockStatic(ComponentContext.class)) {
            context.when(() -> ComponentContext.inject(any(BaseHATask.class))).thenAnswer(i -> i.getArgument(0));
            manager.processHAResource(config, host, provider);
            manager.processHAResource(config, host, provider);
            verify(executor, times(1)).submit(any(Callable.class));
            assertTrue(counter.hasActiveTask());
            assertEquals(HAConfig.HAState.Degraded, config.getState());
        }
    }

    @Test
    public void degradedMeansDisconnectedButDoesNotAuthorizeLegacyVmRestart() throws Exception {
        when(host.getId()).thenReturn(1L);
        config.setHastate(HAConfig.HAState.Degraded);
        assertEquals(Status.Disconnected, manager.getHostStatus(host));
        assertEquals(Boolean.TRUE, manager.isVMAliveOnHost(host));
    }

    @Test
    public void degradedClaimEventKeepsThePersistedDegradedState() throws Exception {
        assertEquals(HAConfig.HAState.Degraded, HAConfig.HAState.getStateMachine().getNextState(
                HAConfig.HAState.Degraded, HAConfig.Event.PeriodicRecheckResourceActivity));
    }

    @Test
    public void fencedPollResumesFinalizationInsteadOfHealthCheck() {
        config.setHastate(HAConfig.HAState.Fenced);
        doReturn(true).when(manager).submitHATask(any(), any(), any(), any(), any());
        manager.processHAResource(config, host, provider);
        verify(manager).submitHATask(host, provider, config, counter, HAResourceCounter.Operation.FENCE);
        verify(manager, never()).submitHATask(any(), any(), any(), any(), eq(HAResourceCounter.Operation.HEALTH));
    }

    @Test
    public void fencingMaintenanceDoesNotInvalidateItsOwnProvider() throws Exception {
        when(host.getResourceState()).thenReturn(ResourceState.Maintenance);
        Map<String, HAProvider<HAResource>> providers = new HashMap<>();
        providers.put("kvm", provider);
        field(manager, "haProviderMap", providers);
        Method validate = HAManagerImpl.class.getDeclaredMethod("validateAndFindHAProvider", HAConfig.class, HAResource.class);
        validate.setAccessible(true);
        for (HAConfig.HAState state : new HAConfig.HAState[] {HAConfig.HAState.Fencing, HAConfig.HAState.Fenced}) {
            config.setHastate(state);
            assertSame(provider, validate.invoke(manager, config, host));
        }
        verify(provider, never()).isEligible(host);
        verify(manager, never()).transitionHAState(eq(HAConfig.Event.Ineligible), any());
    }

    @Test
    public void duplicatePollSubmitsOnlyOneRecoveryTask() {
        config.setHastate(HAConfig.HAState.Recovering);
        try (MockedStatic<ComponentContext> context = Mockito.mockStatic(ComponentContext.class)) {
            context.when(() -> ComponentContext.inject(any(BaseHATask.class))).thenAnswer(i -> i.getArgument(0));
            manager.processHAResource(config, host, provider);
            manager.processHAResource(config, host, provider);
            verify(executor, times(1)).submit(any(Callable.class));
            assertTrue(counter.hasActiveTask());
        }
    }

    @Test
    public void executorRejectionReleasesReservationForRetry() {
        config.setHastate(HAConfig.HAState.Fencing);
        when(executor.submit(any(Callable.class))).thenThrow(new RejectedExecutionException());
        try (MockedStatic<ComponentContext> context = Mockito.mockStatic(ComponentContext.class)) {
            context.when(() -> ComponentContext.inject(any(BaseHATask.class))).thenAnswer(i -> i.getArgument(0));
            assertFalse(manager.submitHATask(host, provider, config, counter, HAResourceCounter.Operation.FENCE));
            assertFalse(counter.hasActiveTask());
        }
    }

    @Test
    public void ownerlessFinalizationMustClaimDatabaseOwnershipBeforeSubmission() {
        config.setHastate(HAConfig.HAState.Fenced);
        config.setManagementServerId(null);
        assertFalse(manager.submitHATask(host, provider, config, counter, HAResourceCounter.Operation.FENCE));
        verify(manager).transitionHAState(HAConfig.Event.RetryFencing, config);
        verifyNoInteractions(executor);
    }

    @Test
    public void ownerlessDegradedActivityMustClaimOwnershipBeforeSubmission() {
        config.setHastate(HAConfig.HAState.Degraded);
        config.setManagementServerId(null);
        manager.processHAResource(config, host, provider);
        verify(manager).transitionHAState(HAConfig.Event.PeriodicRecheckResourceActivity, config);
        verify(manager, never()).submitHATask(any(), any(), any(), any(), any());
        verifyNoInteractions(executor);
        assertFalse(counter.hasActiveTask());
        assertEquals(HAConfig.HAState.Degraded, config.getState());
    }

    @Test
    public void parentDisableAttemptsEveryHostAfterOneTransitionFails() throws Exception {
        HostVO secondHost = mock(HostVO.class);
        when(host.getId()).thenReturn(1L);
        when(host.resourceType()).thenReturn(HAResource.ResourceType.Host);
        when(secondHost.getId()).thenReturn(2L);
        when(secondHost.resourceType()).thenReturn(HAResource.ResourceType.Host);
        HostDao hostDao = mock(HostDao.class);
        when(hostDao.findByClusterId(9L)).thenReturn(Arrays.asList(host, secondHost));
        field(manager, "hostDao", hostDao);
        HAConfigVO secondConfig = new HAConfigVO();
        secondConfig.setResourceId(2L);
        when(dao.findHAResource(2L, HAResource.ResourceType.Host)).thenReturn(secondConfig);
        doReturn(false).when(manager).transitionHAState(HAConfig.Event.Disabled, config);
        doReturn(true).when(manager).transitionHAState(HAConfig.Event.Disabled, secondConfig);
        Partition cluster = mock(Partition.class);
        when(cluster.partitionType()).thenReturn(Partition.PartitionType.Cluster);
        when(cluster.getId()).thenReturn(9L);
        Method disable = HAManagerImpl.class.getDeclaredMethod("transitionResourceStateToDisabled", Partition.class);
        disable.setAccessible(true);
        assertEquals(false, disable.invoke(manager, cluster));
        verify(manager).transitionHAState(HAConfig.Event.Disabled, config);
        verify(manager).transitionHAState(HAConfig.Event.Disabled, secondConfig);
    }

    @Test
    public void preTransitionNeverSubmitsDestructiveWorkBeforeCas() {
        config.setHastate(HAConfig.HAState.Fencing);
        assertTrue(manager.preStateTransitionEvent(HAConfig.HAState.Fencing, HAConfig.Event.RetryFencing,
                HAConfig.HAState.Fencing, config, true, null));
        verifyNoInteractions(executor);
    }
}
