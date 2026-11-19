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

public final class EuropaAutoScaleOperationUpgrade {
    private EuropaAutoScaleOperationUpgrade() { }

    public static void migrate(Connection conn) {
        String[][] columns = {{"operation_uuid", "varchar(36) NULL"}, {"operation_action", "varchar(16) NULL"},
                {"operation_phase", "varchar(24) NULL"}, {"operation_vm_id", "bigint unsigned NULL"},
                {"operation_deadline", "datetime NULL"}};
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
            // Existing interrupted operations are fenced before monitors or a disable request can see them.
            try (Statement statement = conn.createStatement()) {
                statement.executeUpdate("UPDATE cloud.autoscale_vmgroups SET operation_uuid=UUID(), " +
                        "operation_action='RECOVERY', operation_phase='RECOVERY' " +
                        "WHERE state='SCALING' AND operation_uuid IS NULL AND removed IS NULL");
            }
        } catch (Exception e) {
            throw new CloudRuntimeException("Unable to apply Europa AutoScale operation migration", e);
        }
    }
}
