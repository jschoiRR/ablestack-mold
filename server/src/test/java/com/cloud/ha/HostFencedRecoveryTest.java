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
package com.cloud.ha;

import com.cloud.ha.HighAvailabilityManager.ReasonType;
import com.cloud.ha.HighAvailabilityManager.Step;
import com.cloud.ha.HighAvailabilityManager.WorkType;
import com.cloud.ha.dao.HighAvailabilityDao;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.resource.ResourceState;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.VirtualMachineManager;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.VMInstanceDetailsDao;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HostFencedRecoveryTest {
    private HighAvailabilityManagerImpl manager;
    private VMInstanceVO vm;
    private HaWorkVO work;
    private ConfigKey<Boolean> originalHaEnabled;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        manager = spy(new HighAvailabilityManagerImpl());
        manager._haDao = mock(HighAvailabilityDao.class);
        manager._hostDao = mock(HostDao.class);
        manager._itMgr = mock(VirtualMachineManager.class);
        manager._instanceDetailsDao = mock(VMInstanceDetailsDao.class);
        manager._instanceDao = mock(VMInstanceDao.class);
        manager._workers = new HighAvailabilityManagerImpl.WorkerThread[0];
        manager._maxRetries = 5;
        originalHaEnabled = HighAvailabilityManagerImpl.VmHaEnabled;
        HighAvailabilityManagerImpl.VmHaEnabled = mock(ConfigKey.class);
        when(HighAvailabilityManagerImpl.VmHaEnabled.valueIn(anyLong())).thenReturn(true);
        vm = mock(VMInstanceVO.class);
        when(vm.getId()).thenReturn(7L);
        when(vm.getUuid()).thenReturn("vm-7");
        when(vm.getType()).thenReturn(VirtualMachine.Type.User);
        when(vm.getHostId()).thenReturn(1L);
        when(vm.getLastHostId()).thenReturn(1L);
        when(vm.getState()).thenReturn(VirtualMachine.State.Running);
        when(vm.getUpdated()).thenReturn(2L);
        work = spy(new HaWorkVO(7L, VirtualMachine.Type.User, WorkType.HA, Step.Stopping,
                1L, VirtualMachine.State.Running, 0, 2L, ReasonType.HostFenced));
        doReturn(10L).when(work).getId();
    }

    @After
    public void restoreConfig() {
        HighAvailabilityManagerImpl.VmHaEnabled = originalHaEnabled;
    }

    @Test
    public void registersTrustedWorkBeforeAnyStopCanClearHostId() throws Exception {
        when(manager._haDao.persist(any(HaWorkVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        manager.scheduleRestart(vm, false, ReasonType.HostFenced);
        ArgumentCaptor<HaWorkVO> capture = ArgumentCaptor.forClass(HaWorkVO.class);
        verify(manager._haDao).persist(capture.capture());
        assertEquals(Step.Stopping, capture.getValue().getStep());
        assertEquals(ReasonType.HostFenced, capture.getValue().getReasonType());
        assertEquals(1L, capture.getValue().getHostId());
        verify(manager._itMgr, never()).advanceStop(any(), anyBoolean());
    }

    @Test(expected = CloudRuntimeException.class)
    public void queuePersistenceFailureCannotStopVm() throws Exception {
        try {
            manager.scheduleRestart(vm, false, ReasonType.HostFenced);
        } finally {
            verify(manager._itMgr, never()).advanceStop(any(), anyBoolean());
        }
    }

    @Test
    public void resumesCrashAfterStopWithoutStoppingAgain() throws Exception {
        when(vm.getState()).thenReturn(VirtualMachine.State.Stopped);
        when(vm.getHostId()).thenReturn(null);
        when(vm.getUpdated()).thenReturn(3L);
        when(manager._haDao.update(10L, work)).thenReturn(true);
        assertSame(vm, manager.prepareFencedVmForRestart(work, vm));
        assertEquals(Step.Scheduled, work.getStep());
        assertEquals(3L, work.getUpdateTime());
        assertEquals(1L, work.getHostId());
        verify(manager._itMgr, never()).advanceStop(any(), anyBoolean());
    }

    @Test
    public void workerStopsOnlyAfterMaintenanceAndCheckpointsBeforeRestart() throws Exception {
        HostVO source = mock(HostVO.class);
        when(source.getResourceState()).thenReturn(ResourceState.Maintenance);
        when(manager._hostDao.findById(1L)).thenReturn(source);
        VMInstanceVO stopped = mock(VMInstanceVO.class);
        when(stopped.getState()).thenReturn(VirtualMachine.State.Stopped);
        when(stopped.getHostId()).thenReturn(null);
        when(stopped.getUpdated()).thenReturn(3L);
        when(manager._itMgr.findById(7L)).thenReturn(stopped);
        when(manager._haDao.update(10L, work)).thenReturn(true);

        assertSame(stopped, manager.prepareFencedVmForRestart(work, vm));

        org.mockito.InOrder order = inOrder(manager._hostDao, manager._itMgr, manager._haDao);
        order.verify(manager._hostDao).findById(1L);
        order.verify(manager._itMgr).advanceStop("vm-7", true);
        order.verify(manager._itMgr).findById(7L);
        order.verify(manager._haDao).update(10L, work);
        assertEquals(Step.Scheduled, work.getStep());
    }

    @Test
    public void workerResumesTrustedWorkAfterBootstrapChangesStepToInvestigating() {
        work.setStep(Step.Investigating);
        when(manager._itMgr.findById(7L)).thenReturn(vm);
        doReturn(null).when(manager).prepareFencedVmForRestart(work, vm);
        assertNull(manager.restart(work));
        verify(manager).prepareFencedVmForRestart(work, vm);
    }

    @Test
    public void newerWorkCancelsTrustedStopBeforeAnyVmMutation() throws Exception {
        when(manager._haDao.listFutureHaWorkForVm(7L, 10L)).thenReturn(java.util.List.of(work));
        assertNull(manager.restart(work));
        verify(manager._itMgr, never()).advanceStop(any(), anyBoolean());
        verify(manager, never()).prepareFencedVmForRestart(any(), any());
    }

    @Test
    public void runningWorkDoesNotWaitForItself() {
        when(manager._haDao.listRunningHaWorkForVm(7L)).thenReturn(java.util.List.of(work));
        when(manager._itMgr.findById(7L)).thenReturn(vm);
        doReturn(null).when(manager).prepareFencedVmForRestart(work, vm);
        assertNull(manager.restart(work));
        verify(manager).prepareFencedVmForRestart(work, vm);
    }

    @Test
    public void laterUnverifiedInvestigationCannotEraseConfirmedRecovery() {
        HaWorkVO later = mock(HaWorkVO.class);
        when(later.getReasonType()).thenReturn(ReasonType.HostDown);
        when(manager._haDao.listFutureHaWorkForVm(7L, 10L)).thenReturn(java.util.List.of(later));
        when(manager._itMgr.findById(7L)).thenReturn(vm);
        doReturn(null).when(manager).prepareFencedVmForRestart(work, vm);
        assertNull(manager.restart(work));
        verify(manager).prepareFencedVmForRestart(work, vm);
    }

    @Test
    public void anotherRunningJobDelaysStop() throws Exception {
        HaWorkVO other = mock(HaWorkVO.class);
        when(other.getId()).thenReturn(11L);
        when(manager._haDao.listRunningHaWorkForVm(7L)).thenReturn(java.util.List.of(work, other));
        org.junit.Assert.assertNotNull(manager.restart(work));
        verify(manager._itMgr, never()).advanceStop(any(), anyBoolean());
    }

    @Test
    public void movedVmCannotBeForceStoppedByOldFencingEvidence() throws Exception {
        when(vm.getHostId()).thenReturn(8L);
        assertNull(manager.prepareFencedVmForRestart(work, vm));
        verify(manager._itMgr, never()).advanceStop(any(), anyBoolean());
    }

    @Test(expected = CloudRuntimeException.class)
    public void releasedMaintenanceCannotAuthorizeADeferredForceStop() throws Exception {
        HostVO host = mock(HostVO.class);
        when(host.getResourceState()).thenReturn(ResourceState.Enabled);
        when(manager._hostDao.findById(1L)).thenReturn(host);
        try {
            manager.prepareFencedVmForRestart(work, vm);
        } finally {
            verify(manager._itMgr, never()).advanceStop(any(), anyBoolean());
        }
    }

    @Test(expected = CloudRuntimeException.class)
    public void failedStopCheckpointRemainsResumable() {
        when(vm.getState()).thenReturn(VirtualMachine.State.Stopped);
        when(vm.getHostId()).thenReturn(null);
        try {
            manager.prepareFencedVmForRestart(work, vm);
        } finally {
            assertEquals(Step.Stopping, work.getStep());
        }
    }

    @Test
    public void rosterRecoversVmAfterRebootReportClearedItsHostId() throws Exception {
        when(vm.getHostId()).thenReturn(null);
        when(vm.getState()).thenReturn(VirtualMachine.State.Stopped);
        when(manager._instanceDao.findById(7L)).thenReturn(vm);
        when(manager._haDao.persist(any(HaWorkVO.class))).thenAnswer(invocation -> invocation.getArgument(0));
        HostVO source = mock(HostVO.class);
        when(source.getId()).thenReturn(1L);
        manager.scheduleRestartForFencedVms(source, java.util.Map.of(7L, "vm-7"));
        ArgumentCaptor<HaWorkVO> capture = ArgumentCaptor.forClass(HaWorkVO.class);
        verify(manager._haDao).persist(capture.capture());
        assertEquals(1L, capture.getValue().getHostId());
        assertEquals(Step.Stopping, capture.getValue().getStep());
        verify(manager._instanceDao, never()).listByHostId(anyLong());
        verify(manager._itMgr, never()).advanceStop(any(), anyBoolean());
    }

    @Test
    public void hostFailureParametersKeepSourceExclusionAcrossBothRecoveryPaths() {
        assertEquals(1L, manager.getHaRestartParameters(work).get(VirtualMachineProfile.Param.HaSourceHostId));
        work.setReasonType(ReasonType.HostDown);
        assertEquals(1L, manager.getHaRestartParameters(work).get(VirtualMachineProfile.Param.HaSourceHostId));
        work.setReasonType(ReasonType.Unknown);
        assertNull(manager.getHaRestartParameters(work).get(VirtualMachineProfile.Param.HaSourceHostId));
    }

    @Test
    public void finalizerRetryDoesNotDuplicateAlreadyDurableVmJobs() throws Exception {
        when(manager._instanceDao.findById(7L)).thenReturn(vm);
        when(manager._haDao.listPendingHAWorkForHost(1L)).thenReturn(java.util.List.of(work));
        HostVO source = mock(HostVO.class);
        when(source.getId()).thenReturn(1L);
        manager.scheduleRestartForFencedVms(source, java.util.Map.of(7L, "vm-7"));
        verify(manager._haDao, never()).persist(any(HaWorkVO.class));
        verify(manager._itMgr, never()).advanceStop(any(), anyBoolean());
    }
}
