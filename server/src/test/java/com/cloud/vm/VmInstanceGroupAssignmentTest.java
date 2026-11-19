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

import java.util.Date;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.dao.InstanceGroupDao;
import com.cloud.vm.dao.InstanceGroupVMMapDao;
import com.cloud.vm.dao.UserVmDao;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class VmInstanceGroupAssignmentTest {
    private UserVmDao vmDao;
    private InstanceGroupDao groupDao;
    private InstanceGroupVMMapDao mapDao;
    private AccountDao accountDao;
    private UserVmVO vm;
    private InstanceGroupVO group;
    private VmInstanceGroupAssignment service;
    private MockedStatic<Transaction> transaction;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        vmDao = mock(UserVmDao.class);
        groupDao = mock(InstanceGroupDao.class);
        mapDao = mock(InstanceGroupVMMapDao.class);
        accountDao = mock(AccountDao.class);
        vm = mock(UserVmVO.class);
        group = mock(InstanceGroupVO.class);
        when(vm.getAccountId()).thenReturn(7L);
        when(vmDao.findById(10L)).thenReturn(vm);
        when(vmDao.lockRow(10L, true)).thenReturn(vm);
        when(group.getId()).thenReturn(20L);
        when(group.getAccountId()).thenReturn(7L);
        when(groupDao.findById(20L)).thenReturn(group);
        when(groupDao.lockRow(20L, true)).thenReturn(group);
        when(mapDao.createSearchCriteria()).thenReturn(mock(SearchCriteria.class));
        when(mapDao.persist(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new VmInstanceGroupAssignment(vmDao, groupDao, mapDao, accountDao);
        transaction = mockStatic(Transaction.class);
        transaction.when(() -> Transaction.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
    }

    @After public void close() { transaction.close(); }

    @Test public void omissionDoesNotReadOrWriteMembership() {
        service.apply(10L, null, null, false);
        verifyNoInteractions(vmDao, groupDao, mapDao, accountDao);
    }
    @Test public void existingIdReplacesAllPreviousMappingsWithOne() {
        service.apply(10L, 20L, null, false);
        ArgumentCaptor<InstanceGroupVMMapVO> capture = ArgumentCaptor.forClass(InstanceGroupVMMapVO.class);
        verify(mapDao).persist(capture.capture());
        assertEquals(20L, capture.getValue().getGroupId());
        assertEquals(10L, capture.getValue().getInstanceId());
        verify(vmDao).lockRow(10L, true);
        verify(groupDao).lockRow(20L, true);
        verify(mapDao, times(1)).expunge(any(SearchCriteria.class));
    }
    @Test public void clearIsIdempotentAndDoesNotRemoveTheGroup() {
        service.apply(10L, null, null, true);
        service.apply(10L, null, null, true);
        verify(mapDao, times(2)).expunge(any(SearchCriteria.class));
        verify(mapDao, never()).persist(any());
        verifyNoInteractions(groupDao);
    }
    @Test public void foreignOwnerRejectedBeforeAnyMutation() {
        when(group.getAccountId()).thenReturn(8L);
        assertThrows(InvalidParameterValueException.class, () -> service.apply(10L, 20L, null, false));
        verifyNoInteractions(mapDao);
    }
    @Test public void deletedGroupRejectedBeforeAnyMutation() {
        when(group.getRemoved()).thenReturn(new Date());
        assertThrows(InvalidParameterValueException.class, () -> service.apply(10L, 20L, null, false));
        verifyNoInteractions(mapDao);
    }
    @Test public void groupDeletedBetweenValidationAndLockPreservesMapping() {
        when(groupDao.lockRow(20L, true)).thenReturn(null);
        assertThrows(InvalidParameterValueException.class, () -> service.apply(10L, 20L, null, false));
        verifyNoInteractions(mapDao);
    }
    @Test public void ownerChangeBetweenValidationAndLockPreservesMapping() {
        UserVmVO moved = mock(UserVmVO.class);
        when(moved.getAccountId()).thenReturn(8L);
        when(vmDao.lockRow(10L, true)).thenReturn(moved);
        assertThrows(InvalidParameterValueException.class, () -> service.apply(10L, 20L, null, false));
        verifyNoInteractions(mapDao);
    }
    @Test public void missingVmRejected() {
        when(vmDao.findById(10L)).thenReturn(null);
        assertThrows(InvalidParameterValueException.class, () -> service.apply(10L, null, null, true));
        verifyNoInteractions(mapDao);
    }
    @Test public void missingVmLockRejected() {
        when(vmDao.lockRow(10L, true)).thenReturn(null);
        assertThrows(InvalidParameterValueException.class, () -> service.apply(10L, null, null, true));
        verifyNoInteractions(mapDao);
    }
    @Test public void legacyNameCreatesInVmOwnerScopeAndReleasesOwnerLock() {
        AccountVO owner = mock(AccountVO.class);
        when(accountDao.acquireInLockTable(7L)).thenReturn(owner);
        when(accountDao.lockRow(7L, true)).thenReturn(owner);
        when(groupDao.persist(any())).thenAnswer(invocation -> {
            InstanceGroupVO value = invocation.getArgument(0);
            assertEquals(7L, value.getAccountId());
            assertEquals("한글 그룹", value.getName());
            return group;
        });
        service.apply(10L, null, "한글 그룹", false);
        verify(groupDao).persist(any());
        verify(accountDao).releaseFromLockTable(7L);
    }
    @Test public void ownerLockFailureDoesNotEraseOldMapping() {
        assertThrows(CloudRuntimeException.class, () -> service.apply(10L, null, "name", false));
        verifyNoInteractions(mapDao);
    }
    @Test public void existingLegacyNameUsesTheSameLockedAssignment() {
        AccountVO owner = mock(AccountVO.class);
        when(accountDao.acquireInLockTable(7L)).thenReturn(owner);
        when(accountDao.lockRow(7L, true)).thenReturn(owner);
        when(groupDao.findByAccountAndName(7L, "name")).thenReturn(group);
        service.apply(10L, null, "name", false);
        verify(groupDao, never()).persist(any());
        verify(mapDao).persist(any());
    }
    @Test public void invalidSelectorsPreserveAllMemberships() {
        assertThrows(InvalidParameterValueException.class, () -> service.apply(10L, 20L, "name", false));
        assertThrows(InvalidParameterValueException.class, () -> service.apply(10L, null, "   ", false));
        verifyNoInteractions(mapDao);
    }
    @Test public void insertionFailureIsReportedForTransactionRollback() {
        when(mapDao.persist(any())).thenReturn(null);
        assertThrows(CloudRuntimeException.class, () -> service.apply(10L, 20L, null, false));
    }
    @Test public void normalizedNameReusesExistingCanonicalGroup() {
        AccountVO owner = mock(AccountVO.class);
        when(accountDao.acquireInLockTable(7L)).thenReturn(owner);
        when(accountDao.lockRow(7L, true)).thenReturn(owner);
        when(groupDao.findByAccountAndName(7L, "한글")).thenReturn(group);
        service.apply(10L, null, " 한글 ", false);
        verify(groupDao, never()).persist(any());
        verify(mapDao).persist(any());
    }
    @Test public void overlongNamePreservesMembership() {
        assertThrows(InvalidParameterValueException.class, () -> service.apply(10L, null, "가".repeat(256), false));
        verifyNoInteractions(mapDao, accountDao);
    }
    @Test public void exactPaddedLegacyNameIsNotRewritten() {
        AccountVO owner = mock(AccountVO.class);
        when(accountDao.acquireInLockTable(7L)).thenReturn(owner);
        when(accountDao.lockRow(7L, true)).thenReturn(owner);
        when(group.getName()).thenReturn(" 기존 ");
        when(groupDao.findByAccountAndName(7L, " 기존 ")).thenReturn(group);
        service.apply(10L, null, " 기존 ", false);
        verify(groupDao, never()).persist(any());
        verify(mapDao).persist(any());
    }
}
