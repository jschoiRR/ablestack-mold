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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.exception.CloudRuntimeException;

/** Converts legacy KVM activity settings before ConfigDepot registers the new default. */
public final class KvmHaActivityThresholdMigration {
    private static final Logger LOGGER = LogManager.getLogger(KvmHaActivityThresholdMigration.class);
    static final String MAX_ATTEMPTS = "kvm.ha.activity.check.max.attempts";
    static final String FAILURE_RATIO = "kvm.ha.activity.check.failure.ratio";
    static final String FAILURE_THRESHOLD = "kvm.ha.activity.check.failure.threshold";

    static final String SELECT_GLOBAL = "SELECT `name`, `value` FROM `cloud`.`configuration` WHERE `name` IN (?, ?, ?) FOR UPDATE";
    static final String SELECT_CLUSTERS = "SELECT `cluster_id`, `name`, `value` FROM `cloud`.`cluster_details` WHERE `name` IN (?, ?, ?) FOR UPDATE";
    static final String INSERT_GLOBAL = "INSERT INTO `cloud`.`configuration` "
            + "(`category`, `instance`, `component`, `name`, `value`, `default_value`, `description`, `scope`, `is_dynamic`, `updated`) "
            + "VALUES ('Advanced', 'DEFAULT', 'KVMHAConfig', ?, ?, '4', ?, 4, 1, CURRENT_TIMESTAMP)";
    static final String INSERT_CLUSTER = "INSERT INTO `cloud`.`cluster_details` (`cluster_id`, `name`, `value`) VALUES (?, ?, ?)";
    static final String DELETE_GLOBAL = "DELETE FROM `cloud`.`configuration` WHERE `name` IN (?, ?)";
    static final String DELETE_CLUSTERS = "DELETE FROM `cloud`.`cluster_details` WHERE `name` IN (?, ?)";

    private KvmHaActivityThresholdMigration() {
    }

    /** Called while DatabaseUpgradeChecker holds the database upgrade lock, including same-version starts. */
    public static void migrate() {
        TransactionLegacy transaction = TransactionLegacy.open("KvmHaActivityThresholdMigration");
        try {
            transaction.start();
            migrate(transaction.getConnection());
            transaction.commit();
        } catch (SQLException | RuntimeException e) {
            transaction.rollback();
            throw new CloudRuntimeException("Unable to migrate KVM HA activity failure settings", e);
        } finally {
            transaction.close();
        }
    }

    static void migrate(Connection connection) throws SQLException {
        Map<String, String> global = readGlobal(connection);
        Map<Long, Map<String, String>> clusters = readClusters(connection);
        String globalAttempts = valueOrDefault(global.get(MAX_ATTEMPTS), "7");
        String globalRatio = valueOrDefault(global.get(FAILURE_RATIO), "0.5");

        if (!global.containsKey(FAILURE_THRESHOLD)) {
            long threshold = convertThreshold(globalAttempts, globalRatio, "global");
            try (PreparedStatement statement = connection.prepareStatement(INSERT_GLOBAL)) {
                statement.setString(1, FAILURE_THRESHOLD);
                statement.setString(2, Long.toString(threshold));
                statement.setString(3, "Consecutive DEAD activity observations required to begin recovery. Must be positive. "
                        + "ALIVE or UNKNOWN resets the sequence; activity observation has no total attempt limit.");
                statement.executeUpdate();
                LOGGER.info("Migrating global KVM HA activity failure threshold to {}", threshold);
            }
        }

        for (Map.Entry<Long, Map<String, String>> cluster : clusters.entrySet()) {
            Map<String, String> values = cluster.getValue();
            if (values.containsKey(FAILURE_THRESHOLD)
                    || (values.get(MAX_ATTEMPTS) == null && values.get(FAILURE_RATIO) == null)) {
                continue;
            }
            // Resolve each legacy key independently: a cluster may override only one of them.
            String attempts = valueOrDefault(values.get(MAX_ATTEMPTS), globalAttempts);
            String ratio = valueOrDefault(values.get(FAILURE_RATIO), globalRatio);
            long threshold = convertThreshold(attempts, ratio, "cluster " + cluster.getKey());
            try (PreparedStatement statement = connection.prepareStatement(INSERT_CLUSTER)) {
                statement.setLong(1, cluster.getKey());
                statement.setString(2, FAILURE_THRESHOLD);
                statement.setString(3, Long.toString(threshold));
                statement.executeUpdate();
                LOGGER.info("Migrating KVM HA activity failure threshold for cluster {} to {}", cluster.getKey(), threshold);
            }
        }

        // Remove obsolete controls only after every replacement is safely persisted in this transaction.
        deleteLegacySettings(connection, DELETE_CLUSTERS);
        deleteLegacySettings(connection, DELETE_GLOBAL);
    }

    private static Map<String, String> readGlobal(Connection connection) throws SQLException {
        Map<String, String> values = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_GLOBAL)) {
            bindKeys(statement);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    values.put(result.getString("name"), result.getString("value"));
                }
            }
        }
        return values;
    }

    private static Map<Long, Map<String, String>> readClusters(Connection connection) throws SQLException {
        Map<Long, Map<String, String>> values = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_CLUSTERS)) {
            bindKeys(statement);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long clusterId = result.getLong("cluster_id");
                    Map<String, String> clusterValues = values.computeIfAbsent(clusterId, ignored -> new HashMap<>());
                    String name = result.getString("name");
                    String value = result.getString("value");
                    if (!FAILURE_THRESHOLD.equals(name) && clusterValues.containsKey(name)
                            && !Objects.equals(clusterValues.get(name), value)) {
                        LOGGER.warn("Conflicting legacy KVM HA activity settings for cluster {}; migrating to invalid threshold 0", clusterId);
                        clusterValues.put(name, "");
                    } else {
                        clusterValues.put(name, value);
                    }
                }
            }
        }
        return values;
    }

    private static void bindKeys(PreparedStatement statement) throws SQLException {
        statement.setString(1, MAX_ATTEMPTS);
        statement.setString(2, FAILURE_RATIO);
        statement.setString(3, FAILURE_THRESHOLD);
    }

    private static void deleteLegacySettings(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MAX_ATTEMPTS);
            statement.setString(2, FAILURE_RATIO);
            statement.executeUpdate();
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    static long convertThreshold(String attemptsValue, String ratioValue, String scope) {
        try {
            long attempts = Long.parseLong(attemptsValue);
            double ratio = Double.parseDouble(ratioValue);
            double failures = Math.floor(attempts * ratio);
            if (attempts > 0 && Double.isFinite(ratio) && ratio >= 0 && ratio < 1
                    && Double.isFinite(failures) && failures >= 0 && failures < Long.MAX_VALUE) {
                return (long) failures + 1;
            }
        } catch (NumberFormatException e) {
            // Invalid legacy settings must never silently become a lower, valid fencing threshold.
        }
        LOGGER.warn("Invalid legacy KVM HA activity failure settings for {}; migrated threshold to 0. "
                + "Activity-based recovery remains blocked until a positive failure threshold is configured.", scope);
        return 0;
    }
}
