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
package com.cloud.upgrade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.exception.CloudRuntimeException;

public class KvmHaActivityThresholdMigrationTest {
    private static final String ATTEMPTS = KvmHaActivityThresholdMigration.MAX_ATTEMPTS;
    private static final String RATIO = KvmHaActivityThresholdMigration.FAILURE_RATIO;
    private static final String THRESHOLD = KvmHaActivityThresholdMigration.FAILURE_THRESHOLD;

    private Connection connection;
    private final Map<String, String> globalValues = new LinkedHashMap<>();
    private final List<Object[]> clusterValues = new ArrayList<>();
    private final List<String> updates = new ArrayList<>();
    private String failingSql;

    @Before
    public void setUp() throws Exception {
        connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> statement(invocation.getArgument(0)));
    }

    @Test
    public void defaultsBecomeFourAndOldKeysAreRemoved() throws Exception {
        globalValues.put(ATTEMPTS, "7");
        globalValues.put(RATIO, "0.5");
        KvmHaActivityThresholdMigration.migrate(connection);
        assertEquals("4", globalValues.get(THRESHOLD));
        assertFalse(globalValues.containsKey(ATTEMPTS));
        assertFalse(globalValues.containsKey(RATIO));
        assertEquals(KvmHaActivityThresholdMigration.INSERT_GLOBAL, updates.get(0));
        assertEquals(KvmHaActivityThresholdMigration.DELETE_GLOBAL, updates.get(updates.size() - 1));
    }

    @Test
    public void freshInstallationAndNullGlobalsUseLegacyDefaults() throws Exception {
        globalValues.put(ATTEMPTS, null);
        globalValues.put(RATIO, null);
        KvmHaActivityThresholdMigration.migrate(connection);
        assertEquals("4", globalValues.get(THRESHOLD));
    }

    @Test
    public void customGlobalThresholdIsPreservedByConversion() throws Exception {
        globalValues.put(ATTEMPTS, "9");
        globalValues.put(RATIO, "0.5");
        KvmHaActivityThresholdMigration.migrate(connection);
        assertEquals("5", globalValues.get(THRESHOLD));
    }

    @Test
    public void eachPartialClusterOverrideUsesItsOwnGlobalFallback() throws Exception {
        globalValues.put(ATTEMPTS, "9");
        globalValues.put(RATIO, "0.3");
        clusterValues.add(new Object[]{1L, ATTEMPTS, "11"});
        clusterValues.add(new Object[]{2L, RATIO, "0.5"});
        clusterValues.add(new Object[]{3L, ATTEMPTS, null});
        clusterValues.add(new Object[]{3L, RATIO, "0.5"});
        KvmHaActivityThresholdMigration.migrate(connection);
        assertEquals("3", globalValues.get(THRESHOLD));
        assertEquals("4", clusterThreshold(1L));
        assertEquals("5", clusterThreshold(2L));
        assertEquals("5", clusterThreshold(3L));
        assertTrue(clusterValues.stream().allMatch(row -> THRESHOLD.equals(row[1])));
    }

    @Test
    public void explicitNewSettingsWinButLegacyClusterOverridesAreStillConverted() throws Exception {
        globalValues.put(THRESHOLD, "6");
        globalValues.put(ATTEMPTS, "9");
        globalValues.put(RATIO, "0.5");
        clusterValues.add(new Object[]{1L, THRESHOLD, "8"});
        clusterValues.add(new Object[]{1L, ATTEMPTS, "7"});
        clusterValues.add(new Object[]{2L, ATTEMPTS, "13"});
        KvmHaActivityThresholdMigration.migrate(connection);
        assertEquals("6", globalValues.get(THRESHOLD));
        assertEquals("8", clusterThreshold(1L));
        assertEquals("7", clusterThreshold(2L));
        assertFalse(updates.contains(KvmHaActivityThresholdMigration.INSERT_GLOBAL));
    }

    @Test
    public void nullOnlyLegacyClusterRowsContinueToInheritExplicitNewGlobal() throws Exception {
        globalValues.put(THRESHOLD, "6");
        clusterValues.add(new Object[]{1L, ATTEMPTS, null});
        clusterValues.add(new Object[]{1L, RATIO, null});
        KvmHaActivityThresholdMigration.migrate(connection);
        assertEquals("6", globalValues.get(THRESHOLD));
        assertTrue(clusterValues.isEmpty());
    }

    @Test
    public void existingNullOrInvalidNewValuesAreNotOverwritten() throws Exception {
        globalValues.put(THRESHOLD, null);
        globalValues.put(ATTEMPTS, "9");
        clusterValues.add(new Object[]{1L, THRESHOLD, "0"});
        clusterValues.add(new Object[]{1L, ATTEMPTS, "9"});
        clusterValues.add(new Object[]{2L, THRESHOLD, null});
        clusterValues.add(new Object[]{2L, ATTEMPTS, "9"});
        KvmHaActivityThresholdMigration.migrate(connection);
        assertTrue(globalValues.containsKey(THRESHOLD));
        assertEquals(null, globalValues.get(THRESHOLD));
        assertEquals("0", clusterThreshold(1L));
        assertEquals(null, clusterThreshold(2L));
        assertFalse(updates.contains(KvmHaActivityThresholdMigration.INSERT_GLOBAL));
        assertFalse(updates.contains(KvmHaActivityThresholdMigration.INSERT_CLUSTER));
    }

    @Test
    public void invalidLegacySettingsBecomeZeroWithoutFallingBackToFour() throws Exception {
        globalValues.put(ATTEMPTS, "");
        clusterValues.add(new Object[]{1L, ATTEMPTS, "9"});
        clusterValues.add(new Object[]{1L, RATIO, "NaN"});
        KvmHaActivityThresholdMigration.migrate(connection);
        assertEquals("0", globalValues.get(THRESHOLD));
        assertEquals("0", clusterThreshold(1L));
    }

    @Test
    public void duplicateLegacyRowsOnlyFailClosedWhenTheirValuesConflict() throws Exception {
        clusterValues.add(new Object[]{1L, ATTEMPTS, "9"});
        clusterValues.add(new Object[]{1L, ATTEMPTS, "9"});
        clusterValues.add(new Object[]{2L, ATTEMPTS, "9"});
        clusterValues.add(new Object[]{2L, ATTEMPTS, "7"});
        clusterValues.add(new Object[]{2L, ATTEMPTS, "9"});
        KvmHaActivityThresholdMigration.migrate(connection);
        assertEquals("5", clusterThreshold(1L));
        assertEquals("0", clusterThreshold(2L));
    }

    @Test
    public void repeatedMigrationDoesNotChangeConvertedOrExplicitValues() throws Exception {
        globalValues.put(ATTEMPTS, "9");
        clusterValues.add(new Object[]{1L, RATIO, "0.25"});
        KvmHaActivityThresholdMigration.migrate(connection);
        String globalThreshold = globalValues.get(THRESHOLD);
        String scopedThreshold = clusterThreshold(1L);
        updates.clear();
        KvmHaActivityThresholdMigration.migrate(connection);
        assertEquals(globalThreshold, globalValues.get(THRESHOLD));
        assertEquals(scopedThreshold, clusterThreshold(1L));
        assertFalse(updates.contains(KvmHaActivityThresholdMigration.INSERT_GLOBAL));
        assertFalse(updates.contains(KvmHaActivityThresholdMigration.INSERT_CLUSTER));
    }

    @Test
    public void conversionRetainsJavaDoubleFloorSemantics() {
        assertEquals(29L, KvmHaActivityThresholdMigration.convertThreshold("50", "0.58", "test"));
        assertEquals(1L, KvmHaActivityThresholdMigration.convertThreshold(Long.toString(Long.MAX_VALUE), "0", "test"));
    }

    @Test
    public void invalidOrOverflowingLegacyNumbersNeverProduceValidThreshold() {
        String[][] invalid = {{"0", "0.5"}, {"-1", "0.5"}, {"9223372036854775808", "0.5"},
                {"7", "NaN"}, {"7", "Infinity"}, {"7", "-0.1"}, {"7", "1"}, {"7", ""}};
        for (String[] values : invalid) {
            assertEquals(0L, KvmHaActivityThresholdMigration.convertThreshold(values[0], values[1], "test"));
        }
    }

    @Test
    public void startupCommitsOnlyAfterMigrationCompletes() throws Exception {
        TransactionLegacy transaction = mock(TransactionLegacy.class);
        when(transaction.getConnection()).thenReturn(connection);
        try (MockedStatic<TransactionLegacy> transactions = mockStatic(TransactionLegacy.class)) {
            transactions.when(() -> TransactionLegacy.open("KvmHaActivityThresholdMigration")).thenReturn(transaction);
            KvmHaActivityThresholdMigration.migrate();
        }
        verify(transaction).start();
        verify(transaction).commit();
        verify(transaction, never()).rollback();
        verify(transaction).close();
        assertEquals("4", globalValues.get(THRESHOLD));
    }

    @Test
    public void failedClusterWriteRollsBackAndPreventsLegacyDeletion() throws Exception {
        clusterValues.add(new Object[]{1L, ATTEMPTS, "9"});
        failingSql = KvmHaActivityThresholdMigration.INSERT_CLUSTER;
        TransactionLegacy transaction = mock(TransactionLegacy.class);
        when(transaction.getConnection()).thenReturn(connection);
        try (MockedStatic<TransactionLegacy> transactions = mockStatic(TransactionLegacy.class)) {
            transactions.when(() -> TransactionLegacy.open("KvmHaActivityThresholdMigration")).thenReturn(transaction);
            try {
                KvmHaActivityThresholdMigration.migrate();
                fail("Database errors must stop management server startup");
            } catch (CloudRuntimeException expected) {
                assertTrue(expected.getCause() instanceof SQLException);
            }
        }
        verify(transaction).rollback();
        verify(transaction, never()).commit();
        verify(transaction).close();
        assertFalse(updates.contains(KvmHaActivityThresholdMigration.DELETE_GLOBAL));
        assertFalse(updates.contains(KvmHaActivityThresholdMigration.DELETE_CLUSTERS));
    }

    private String clusterThreshold(long clusterId) {
        for (Object[] row : clusterValues) {
            if (row[0].equals(clusterId) && row[1].equals(THRESHOLD)) {
                return (String) row[2];
            }
        }
        return null;
    }

    private PreparedStatement statement(String sql) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        Map<Integer, Object> parameters = new HashMap<>();
        doAnswer(invocation -> {
            parameters.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(statement).setString(anyInt(), nullable(String.class));
        doAnswer(invocation -> {
            parameters.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(statement).setLong(anyInt(), anyLong());
        when(statement.executeQuery()).thenAnswer(invocation -> {
            assertEquals(ATTEMPTS, parameters.get(1));
            assertEquals(RATIO, parameters.get(2));
            assertEquals(THRESHOLD, parameters.get(3));
            List<Object[]> rows = new ArrayList<>();
            if (KvmHaActivityThresholdMigration.SELECT_GLOBAL.equals(sql)) {
                globalValues.forEach((name, value) -> rows.add(new Object[]{null, name, value}));
            } else {
                assertEquals(KvmHaActivityThresholdMigration.SELECT_CLUSTERS, sql);
                rows.addAll(clusterValues);
            }
            return result(rows);
        });
        when(statement.executeUpdate()).thenAnswer(invocation -> {
            if (sql.equals(failingSql)) {
                throw new SQLException("simulated write failure");
            }
            updates.add(sql);
            if (KvmHaActivityThresholdMigration.INSERT_GLOBAL.equals(sql)) {
                assertEquals(THRESHOLD, parameters.get(1));
                globalValues.put((String) parameters.get(1), (String) parameters.get(2));
            } else if (KvmHaActivityThresholdMigration.INSERT_CLUSTER.equals(sql)) {
                assertEquals(THRESHOLD, parameters.get(2));
                clusterValues.add(new Object[]{parameters.get(1), parameters.get(2), parameters.get(3)});
            } else {
                assertEquals(ATTEMPTS, parameters.get(1));
                assertEquals(RATIO, parameters.get(2));
                if (KvmHaActivityThresholdMigration.DELETE_GLOBAL.equals(sql)) {
                    globalValues.remove(ATTEMPTS);
                    globalValues.remove(RATIO);
                } else {
                    assertEquals(KvmHaActivityThresholdMigration.DELETE_CLUSTERS, sql);
                    clusterValues.removeIf(row -> ATTEMPTS.equals(row[1]) || RATIO.equals(row[1]));
                }
            }
            return 1;
        });
        return statement;
    }

    private ResultSet result(List<Object[]> rows) throws SQLException {
        ResultSet result = mock(ResultSet.class);
        AtomicInteger position = new AtomicInteger(-1);
        when(result.next()).thenAnswer(invocation -> position.incrementAndGet() < rows.size());
        when(result.getString(anyString())).thenAnswer(invocation ->
                rows.get(position.get())["name".equals(invocation.getArgument(0)) ? 1 : 2]);
        when(result.getLong("cluster_id")).thenAnswer(invocation -> rows.get(position.get())[0]);
        return result;
    }
}
