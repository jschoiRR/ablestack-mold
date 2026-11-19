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
package com.cloud.upgrade.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import com.cloud.utils.exception.CloudRuntimeException;

public final class EuropaAutoScaleEvaluationUpgrade {
    private EuropaAutoScaleEvaluationUpgrade() { }

    public static void migrate(Connection conn) {
        addColumns(conn, new String[][] {{"evaluation", "mediumtext NULL"}, {"evaluation_time", "datetime(3) NULL"}, {"last_scaled", "datetime(3) NULL"}});
    }

    public static void migratePrecision(Connection conn) {
        addColumns(conn, new String[][] {{"evaluation_time_ms", "bigint NULL"}, {"last_scaled_ms", "bigint NULL"}});
        try (Statement statement = conn.createStatement()) {
            // Cloud GenericDao stores Date columns as UTC strings truncated to seconds.
            // Round legacy times up so migration cannot shorten an active cooldown.
            for (String name : new String[] {"evaluation_time", "last_scaled"}) {
                statement.executeUpdate("UPDATE cloud.autoscale_vmgroups SET " + name + "_ms = " +
                        "TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00', " + name + ") DIV 1000 + 999 " +
                        "WHERE " + name + "_ms IS NULL AND " + name + " IS NOT NULL");
            }
            statement.executeUpdate("UPDATE cloud.autoscale_vmgroups SET evaluation=NULL WHERE evaluation IS NOT NULL AND JSON_VALID(evaluation)=0");
        } catch (Exception e) {
            throw new CloudRuntimeException("Unable to preserve Europa AutoScale evaluation precision", e);
        }
    }

    private static void addColumns(Connection conn, String[][] columns) {
        try {
            for (String[] column : columns) {
                try (PreparedStatement query = conn.prepareStatement("SELECT 1 FROM information_schema.columns " +
                        "WHERE table_schema='cloud' AND table_name='autoscale_vmgroups' AND column_name=?")) {
                    query.setString(1, column[0]);
                    try (ResultSet result = query.executeQuery()) {
                        if (!result.next()) {
                            try (Statement statement = conn.createStatement()) {
                                statement.executeUpdate("ALTER TABLE cloud.autoscale_vmgroups ADD COLUMN " + column[0] + " " + column[1]);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new CloudRuntimeException("Unable to apply Europa AutoScale evaluation migration", e);
        }
    }
}
