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
package com.cloud.api.query.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class InstanceGroupMemberCountsTest {
    @Test
    public void emptyAclPageNeverPreparesSql() throws Exception {
        InstanceGroupMemberCounts.StatementPreparer preparer = mock(InstanceGroupMemberCounts.StatementPreparer.class);
        assertTrue(InstanceGroupMemberCounts.query(List.of(), false, preparer).isEmpty()); verifyNoInteractions(preparer);
    }

    @Test
    public void boundPageAndOwnerLifecycleDisplayRulesAreOneQuery() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class); ResultSet result = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(result); when(result.next()).thenReturn(true, true, false);
        when(result.getLong(1)).thenReturn(11L, 999L); when(result.getString(2)).thenReturn("Running", "Stopped"); when(result.getLong(3)).thenReturn(2L, 100L);
        InstanceGroupMemberCounts.StatementPreparer preparer = mock(InstanceGroupMemberCounts.StatementPreparer.class);
        when(preparer.prepare(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            assertTrue(sql.contains("v.account_id = g.account_id")); assertTrue(sql.contains("v.removed IS NULL"));
            assertTrue(sql.contains("v.state NOT IN ('Destroyed', 'Expunging')")); assertTrue(sql.contains("v.display_vm = 1"));
            assertTrue(sql.contains("COUNT(DISTINCT v.id)")); assertTrue(sql.contains("g.id IN (?,?)")); return statement;
        });
        Map<Long, Map<String, Long>> counts = InstanceGroupMemberCounts.query(List.of(11L, 12L), false, preparer);
        assertEquals(Map.of("Running", 2L), counts.get(11L)); assertTrue(counts.get(12L).isEmpty()); assertFalse(counts.containsKey(999L));
        verify(statement).setLong(1, 11L); verify(statement).setLong(2, 12L); verify(statement, times(1)).executeQuery(); verify(statement).close(); verify(result).close();
    }

    @Test
    public void rootAdminVisibilityDoesNotRequireDisplayedVm() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class); when(statement.executeQuery()).thenReturn(mock(ResultSet.class));
        InstanceGroupMemberCounts.query(List.of(1L), true, sql -> { assertFalse(sql.contains("v.display_vm")); return statement; });
    }

    @Test(expected = SQLException.class)
    public void databaseFailureDoesNotTurnIntoAnEmptySuccess() throws Exception {
        InstanceGroupMemberCounts.query(List.of(1L), false, sql -> { throw new SQLException("unavailable"); });
    }
}
