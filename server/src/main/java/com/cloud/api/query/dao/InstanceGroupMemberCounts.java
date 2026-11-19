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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One aggregate for the ACL-filtered group page; never enumerate other owners' members. */
public final class InstanceGroupMemberCounts {
    private InstanceGroupMemberCounts() { }

    @FunctionalInterface
    public interface StatementPreparer {
        PreparedStatement prepare(String sql) throws SQLException;
    }

    public static Map<Long, Map<String, Long>> query(List<Long> visibleGroupIds, boolean includeHidden,
            StatementPreparer preparer) throws SQLException {
        Map<Long, Map<String, Long>> counts = new LinkedHashMap<>();
        if (visibleGroupIds.isEmpty()) {
            return counts;
        }
        visibleGroupIds.forEach(id -> counts.put(id, new LinkedHashMap<>()));
        String sql = "SELECT g.id, v.state, COUNT(DISTINCT v.id) "
                + "FROM instance_group g JOIN instance_group_vm_map m ON m.group_id = g.id "
                + "JOIN vm_instance v ON v.id = m.instance_id AND v.account_id = g.account_id "
                + "JOIN user_vm u ON u.id = v.id "
                + "WHERE g.removed IS NULL AND v.removed IS NULL AND v.type = 'User' "
                + "AND v.state NOT IN ('Destroyed', 'Expunging') "
                + (includeHidden ? "" : "AND v.display_vm = 1 ")
                + "AND g.id IN (" + String.join(",", Collections.nCopies(visibleGroupIds.size(), "?")) + ") "
                + "GROUP BY g.id, v.state";
        try (PreparedStatement statement = preparer.prepare(sql)) {
            for (int index = 0; index < visibleGroupIds.size(); index++) {
                statement.setLong(index + 1, visibleGroupIds.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Long> states = counts.get(result.getLong(1));
                    if (states != null) {
                        states.put(result.getString(2), result.getLong(3));
                    }
                }
            }
        }
        return counts;
    }
}
