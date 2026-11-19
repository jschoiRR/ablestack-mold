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

package com.cloud.vm.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.exception.CloudRuntimeException;

public class VMInstanceDetailsSSHKeyPairTest {
    private final VMInstanceDetailsDaoImpl dao = new VMInstanceDetailsDaoImpl();

    @Test
    public void emptyNameDoesNotReadDatabase() {
        try (MockedStatic<TransactionLegacy> transactions = Mockito.mockStatic(TransactionLegacy.class)) {
            assertEquals(Collections.emptyList(), dao.listVmIdsBySSHKeyPairName(null, Collections.emptyList()));
            assertEquals(Collections.emptyList(), dao.listVmIdsBySSHKeyPairName("", Collections.singletonList(1L)));
            transactions.verifyNoInteractions();
        }
    }

    @Test
    public void ownerIdsAndLiteralNameAreBoundAndResourcesClosed() throws Exception {
        TransactionLegacy txn = mock(TransactionLegacy.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        String name = "ssh-name' OR 1=1 --";
        String sql = VMInstanceDetailsDaoImpl.VM_IDS_BY_SSH_KEYPAIR + " AND vm.account_id IN (?,?)";
        try (MockedStatic<TransactionLegacy> transactions = Mockito.mockStatic(TransactionLegacy.class)) {
            transactions.when(TransactionLegacy::currentTxn).thenReturn(txn);
            when(txn.prepareStatement(sql)).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(results);
            when(results.next()).thenReturn(true, true, false);
            when(results.getLong(1)).thenReturn(8L, 9L);
            assertEquals(Arrays.asList(8L, 9L), dao.listVmIdsBySSHKeyPairName(name, Arrays.asList(2L, 3L)));
            verify(statement).setString(1, name);
            verify(statement).setLong(2, 2L);
            verify(statement).setLong(3, 3L);
            verify(results).close();
            verify(statement).close();
        }
    }

    @Test
    public void globalLookupBindsOnlyNameAndNoMatchesReturnsEmpty() throws Exception {
        TransactionLegacy txn = mock(TransactionLegacy.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        try (MockedStatic<TransactionLegacy> transactions = Mockito.mockStatic(TransactionLegacy.class)) {
            transactions.when(TransactionLegacy::currentTxn).thenReturn(txn);
            when(txn.prepareStatement(VMInstanceDetailsDaoImpl.VM_IDS_BY_SSH_KEYPAIR)).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(results);
            when(results.next()).thenReturn(false);
            assertEquals(Collections.emptyList(), dao.listVmIdsBySSHKeyPairName("unused", null));
            verify(statement).setString(1, "unused");
            verify(statement, never()).setLong(anyInt(), anyLong());
        }
    }

    @Test
    public void sqlFailureIsAnErrorInsteadOfAnUnfilteredLookup() throws Exception {
        TransactionLegacy txn = mock(TransactionLegacy.class);
        try (MockedStatic<TransactionLegacy> transactions = Mockito.mockStatic(TransactionLegacy.class)) {
            transactions.when(TransactionLegacy::currentTxn).thenReturn(txn);
            when(txn.prepareStatement(anyString())).thenThrow(new SQLException("lookup failed"));
            try {
                dao.listVmIdsBySSHKeyPairName("target", Collections.emptyList());
                fail("Database failures must propagate");
            } catch (CloudRuntimeException expected) {
                assertEquals("lookup failed", expected.getCause().getMessage());
            }
        }
    }

    @Test
    public void resultFailurePropagatesAndClosesStatement() throws Exception {
        TransactionLegacy txn = mock(TransactionLegacy.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        try (MockedStatic<TransactionLegacy> transactions = Mockito.mockStatic(TransactionLegacy.class)) {
            transactions.when(TransactionLegacy::currentTxn).thenReturn(txn);
            when(txn.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenThrow(new SQLException("read failed"));
            try {
                dao.listVmIdsBySSHKeyPairName("target", Collections.emptyList());
                fail("Database failures must propagate");
            } catch (CloudRuntimeException expected) {
                verify(statement).close();
            }
        }
    }
}
