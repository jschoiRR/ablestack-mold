// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.vm;

import java.util.Date;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.dao.InstanceGroupDao;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class VmInstanceGroupNamesTest {
    private InstanceGroupDao groups;
    private AccountDao accounts;
    private InstanceGroupVO group;
    private MockedStatic<Transaction> transaction;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        groups = mock(InstanceGroupDao.class);
        accounts = mock(AccountDao.class);
        AccountVO owner = mock(AccountVO.class);
        when(accounts.acquireInLockTable(7L)).thenReturn(owner);
        when(accounts.lockRow(7L, true)).thenReturn(owner);
        group = mock(InstanceGroupVO.class);
        when(group.getId()).thenReturn(20L);
        when(group.getAccountId()).thenReturn(7L);
        when(group.getName()).thenReturn("원래 이름");
        when(groups.lockRow(20L, true)).thenReturn(group);
        when(groups.findById(20L)).thenReturn(group);
        transaction = mockStatic(Transaction.class);
        transaction.when(() -> Transaction.execute(any(TransactionCallback.class)))
                .thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
    }

    @After public void close() { transaction.close(); }

    @Test public void nullEmptyAndUnicodeWhitespaceAreRejected() {
        for (String name : new String[] {null, "", " \t\r\n", "\u00a0\u3000\ufeff", "\u2000\u202f\u205f"}) {
            assertThrows(InvalidParameterValueException.class, () -> VmInstanceGroupNames.normalize(name));
        }
    }
    @Test public void trimsBoundaryWhitespaceAndPreservesInternalCharacters() {
        assertEquals("한글  그룹", VmInstanceGroupNames.normalize("\u00a0 한글  그룹 \u3000"));
    }
    @Test public void accepts255KoreanCharacters() {
        assertEquals("가".repeat(255), VmInstanceGroupNames.normalize(" " + "가".repeat(255) + " "));
    }
    @Test public void rejects256Characters() {
        assertThrows(InvalidParameterValueException.class, () -> VmInstanceGroupNames.normalize("가".repeat(256)));
    }
    @Test public void supplementaryCharactersRespectTheOrmUtf16Boundary() {
        assertEquals(255, VmInstanceGroupNames.normalize("\ud83d\ude00".repeat(127) + "A").length());
        assertThrows(InvalidParameterValueException.class, () -> VmInstanceGroupNames.normalize("\ud83d\ude00".repeat(128)));
    }
    @Test public void exactLegacySelectionIsPreserved() {
        when(group.getName()).thenReturn(" 기존 ");
        assertEquals(" 기존 ", VmInstanceGroupNames.forExisting(" 기존 ", group));
        assertEquals("새 이름", VmInstanceGroupNames.forExisting(" 새 이름 ", group));
    }
    @Test public void createNormalizesBeforePersisting() {
        when(groups.persist(any())).thenAnswer(invocation -> invocation.getArgument(0));
        InstanceGroupVO result = VmInstanceGroupNames.create(groups, accounts, 7L, " 새 그룹 ");
        assertEquals("새 그룹", result.getName());
        assertEquals(7L, result.getAccountId());
        verify(accounts).releaseFromLockTable(7L);
    }
    @Test public void invalidCreationDoesNotAcquireLocksOrPersist() {
        assertThrows(InvalidParameterValueException.class, () -> VmInstanceGroupNames.create(groups, accounts, 7L, " "));
        verifyNoInteractions(groups, accounts);
    }
    @Test public void duplicateCreationHasSafeStableMessageAndReleasesLock() {
        when(groups.findByAccountAndName(7L, "사용 중")).thenReturn(group);
        InvalidParameterValueException failure = assertThrows(InvalidParameterValueException.class,
                () -> VmInstanceGroupNames.create(groups, accounts, 7L, " 사용 중 "));
        assertTrue(failure.getMessage().startsWith("[VM_GROUP_NAME_DUPLICATE]"));
        verify(groups, never()).persist(any());
        verify(accounts).releaseFromLockTable(7L);
    }
    @Test public void ownerLockFailureDoesNotMutateGroups() {
        when(accounts.acquireInLockTable(7L)).thenReturn(null);
        assertThrows(CloudRuntimeException.class, () -> VmInstanceGroupNames.create(groups, accounts, 7L, "valid"));
        verifyNoInteractions(groups);
    }
    @Test public void accountRowFailureReleasesLock() {
        when(accounts.lockRow(7L, true)).thenReturn(null);
        assertThrows(CloudRuntimeException.class, () -> VmInstanceGroupNames.rename(groups, accounts, 7L, 20L, "new"));
        verifyNoInteractions(groups);
        verify(accounts).releaseFromLockTable(7L);
    }
    @Test public void renameNormalizesAndDoesNotChangeOwnership() {
        assertSame(group, VmInstanceGroupNames.rename(groups, accounts, 7L, 20L, " 변경 "));
        verify(groups).updateVmGroup(20L, "변경");
        verify(groups, never()).persist(any());
    }
    @Test public void renameTrimCollisionPreservesOldName() {
        InstanceGroupVO collision = mock(InstanceGroupVO.class);
        when(collision.getId()).thenReturn(21L);
        when(groups.findByAccountAndName(7L, "other")).thenReturn(collision);
        assertThrows(InvalidParameterValueException.class, () -> VmInstanceGroupNames.rename(groups, accounts, 7L, 20L, " other "));
        verify(groups, never()).updateVmGroup(anyLong(), anyString());
        verify(accounts).releaseFromLockTable(7L);
    }
    @Test public void unchangedNullAndExactLegacyRenameDoNotWrite() {
        when(group.getName()).thenReturn(" 기존 ");
        assertSame(group, VmInstanceGroupNames.rename(groups, accounts, 7L, 20L, null));
        assertSame(group, VmInstanceGroupNames.rename(groups, accounts, 7L, 20L, " 기존 "));
        verify(groups, never()).updateVmGroup(anyLong(), anyString());
    }
    @Test public void sameIdCollationMatchAllowsRename() {
        when(groups.findByAccountAndName(7L, "renamed")).thenReturn(group);
        VmInstanceGroupNames.rename(groups, accounts, 7L, 20L, "renamed");
        verify(groups).updateVmGroup(20L, "renamed");
    }
    @Test public void deletedTargetDoesNotRename() {
        when(group.getRemoved()).thenReturn(new Date());
        assertThrows(InvalidParameterValueException.class, () -> VmInstanceGroupNames.rename(groups, accounts, 7L, 20L, "new"));
        verify(groups, never()).updateVmGroup(anyLong(), anyString());
    }
    @Test public void missingTargetDoesNotRename() {
        when(groups.lockRow(20L, true)).thenReturn(null);
        assertThrows(InvalidParameterValueException.class, () -> VmInstanceGroupNames.rename(groups, accounts, 7L, 20L, "new"));
        verify(groups, never()).updateVmGroup(anyLong(), anyString());
    }
    @Test public void changedOwnerDoesNotRename() {
        when(group.getAccountId()).thenReturn(8L);
        assertThrows(InvalidParameterValueException.class, () -> VmInstanceGroupNames.rename(groups, accounts, 7L, 20L, "new"));
        verify(groups, never()).updateVmGroup(anyLong(), anyString());
    }
    @Test public void sameNameInAnotherOwnerIsAllowed() {
        when(groups.persist(any())).thenAnswer(invocation -> invocation.getArgument(0));
        VmInstanceGroupNames.create(groups, accounts, 7L, "name");
        verify(groups).findByAccountAndName(7L, "name");
        verify(groups).persist(any());
    }
    @Test public void failedPersistenceIsReported() {
        assertThrows(CloudRuntimeException.class, () -> VmInstanceGroupNames.create(groups, accounts, 7L, "valid"));
        verify(accounts).releaseFromLockTable(7L);
    }
    @Test public void acceptsTheRawBoundaryAfterTrimming() {
        assertEquals("A", VmInstanceGroupNames.normalize(" ".repeat(1023) + "A"));
    }
    @Test public void rejectsOversizedRawInputEvenWhenTrimmedNameWouldFit() {
        assertThrows(InvalidParameterValueException.class, () -> VmInstanceGroupNames.normalize(" ".repeat(1024) + "A"));
    }
    @Test public void rejectsInternalAsciiControls() {
        assertThrows(InvalidParameterValueException.class, () -> VmInstanceGroupNames.normalize("한\t글"));
    }
}
