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

package com.cloud.vm;

import static org.mockito.ArgumentMatchers.any;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cloud.network.dao.NetworkVO;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.dao.NicDao;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class NicAdditionRollbackTest {
    private UserVmManagerImpl manager;
    private VirtualMachineManager vmManager;
    private NicDao nics;
    private UserVmVO vm;
    private NetworkVO network;
    private NicProfile added;
    private NicVO row;

    @Before
    public void setup() {
        manager = new UserVmManagerImpl();
        vmManager = mock(VirtualMachineManager.class);
        nics = mock(NicDao.class);
        ReflectionTestUtils.setField(manager, "_itMgr", vmManager);
        ReflectionTestUtils.setField(manager, "_nicDao", nics);
        vm = mock(UserVmVO.class);
        network = mock(NetworkVO.class);
        when(vm.getId()).thenReturn(10L);
        when(network.getId()).thenReturn(20L);
        added = mock(NicProfile.class);
        when(added.getId()).thenReturn(100L);
        when(added.getUuid()).thenReturn("new-nic-uuid");
        row = mock(NicVO.class);
        when(row.getInstanceId()).thenReturn(10L);
        when(row.getNetworkId()).thenReturn(20L);
        when(row.getUuid()).thenReturn("new-nic-uuid");
        when(nics.findById(100L)).thenReturn(row);
    }

    @Test
    public void rollsBackExactNicInsteadOfAnyNicInNetwork() throws Exception {
        when(vmManager.removeNicFromVm(vm, row)).thenReturn(true);
        manager.rollbackAddedNic(vm, network, added, new CloudRuntimeException("DHCP failed"));
        verify(vmManager).removeNicFromVm(vm, row);
        verify(vmManager, never()).removeVmFromNetwork(any(), any(), any());
    }

    @Test
    public void removedNicIsNotTouchedAgain() {
        when(nics.findById(100L)).thenReturn(null);
        manager.rollbackAddedNic(vm, network, added, new CloudRuntimeException("failed"));
        verifyNoInteractions(vmManager);
    }

    @Test
    public void reusedUuidOrForeignVmCannotBeRemoved() {
        when(row.getUuid()).thenReturn("other-nic");
        RuntimeException original = new CloudRuntimeException("original");
        manager.rollbackAddedNic(vm, network, added, original);
        assertEquals(1, original.getSuppressed().length);
        verifyNoInteractions(vmManager);
    }

    @Test
    public void failedRollbackCannotReplacePrimaryReason() throws Exception {
        when(vmManager.removeNicFromVm(vm, row)).thenReturn(false);
        RuntimeException original = new CloudRuntimeException("specific original reason");
        manager.rollbackAddedNic(vm, network, added, original);
        assertEquals("specific original reason", original.getMessage());
        assertEquals(1, original.getSuppressed().length);
    }

    @Test
    public void foreignNetworkCannotBeRemoved() {
        when(row.getNetworkId()).thenReturn(21L);
        RuntimeException original = new CloudRuntimeException("original");
        manager.rollbackAddedNic(vm, network, added, original);
        verifyNoInteractions(vmManager);
        assertEquals(1, original.getSuppressed().length);
    }
}
