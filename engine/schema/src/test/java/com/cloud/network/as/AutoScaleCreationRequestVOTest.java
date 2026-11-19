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
package com.cloud.network.as;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import java.sql.PreparedStatement;
import org.junit.Test;
import com.cloud.network.as.dao.AutoScaleCreationRequestDaoImpl;
import com.cloud.utils.db.SqlGenerator;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.UpdateBuilder;

public class AutoScaleCreationRequestVOTest {
    private static class Dao extends AutoScaleCreationRequestDaoImpl {
        void bindUpdated(PreparedStatement statement, AutoScaleCreationRequestVO row) throws Exception {
            prepareAttribute(1, statement, new SqlGenerator(AutoScaleCreationRequestVO.class).findAttribute("updated"), row.getUpdated());
        }
    }
    @Test public void receiptTimestampBindsAValueToThePreparedStatement() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        AutoScaleCreationRequestVO row = new AutoScaleCreationRequestVO("request", 1L, 2L, "createCondition", "hash", "Condition");
        new Dao().bindUpdated(statement, row);
        verify(statement).setString(eq(1), anyString());
    }
    @Test public void resourceLinkAndRollbackAreTrackedByTheRealDaoUpdateBuilder() {
        Dao dao = new Dao();
        AutoScaleCreationRequestVO row = dao.createForUpdate(7L);
        row.setResource(5L, "resource-uuid");
        UpdateBuilder update = GenericDaoBase.getUpdateBuilder(row);
        assertTrue(update.has("resourceId"));
        assertTrue(update.has("resourceUuid"));
        assertTrue(update.has("status"));
        assertTrue(update.has("updated"));
        update.clear();
        row.clearResource();
        assertTrue(update.has("resourceId"));
        assertTrue(update.has("resourceUuid"));
    }
}
