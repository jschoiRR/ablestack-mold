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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import com.cloud.network.as.dao.AutoScaleVmGroupDaoImpl;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.UpdateBuilder;

public class AutoScaleVmGroupOperationTest {
    private static class Dao extends AutoScaleVmGroupDaoImpl {
        AutoScaleVmGroupVO current = new AutoScaleVmGroupVO();
        AutoScaleVmGroupVO updated;
        SearchCriteria<AutoScaleVmGroupVO> criteria;
        int affected = 1;
        void bind(java.sql.PreparedStatement statement, String field, Object value) throws Exception {
            prepareAttribute(1, statement, new com.cloud.utils.db.Attribute(AutoScaleVmGroupVO.class, null,
                    AutoScaleVmGroupVO.class.getDeclaredField(field), "autoscale_vmgroups", false, false), value);
        }
        @Override public int update(AutoScaleVmGroupVO row, SearchCriteria<AutoScaleVmGroupVO> sc) {
            updated = row; criteria = sc; return affected;
        }
        @Override public AutoScaleVmGroupVO lockRow(Long id, Boolean lock) { return current; }
        @Override public boolean update(Long id, AutoScaleVmGroupVO row) { updated = row; return true; }
    }
    @Test public void stateTransitionIsOneConditionalUpdateAndScalingSelfTransitionIsRejected() {
        Dao dao = new Dao(); dao.init();
        assertTrue(dao.updateState(1, AutoScaleVmGroup.State.ENABLED, AutoScaleVmGroup.State.SCALING));
        assertTrue(dao.criteria.getWhereClause().contains("state"));
        UpdateBuilder update = GenericDaoBase.getUpdateBuilder(dao.updated);
        assertTrue(update.has("state")); assertFalse(update.has("name"));
        dao.updated = null;
        assertFalse(dao.updateState(1, AutoScaleVmGroup.State.SCALING, AutoScaleVmGroup.State.SCALING));
        assertNull(dao.updated);
        dao.affected = 0;
        assertFalse(dao.updateState(1, AutoScaleVmGroup.State.SCALING, AutoScaleVmGroup.State.ENABLED));
    }
    @Test public void claimRequiresEnabledAndNoExistingOwnerAndTracksEveryPersistentField() {
        Dao dao = new Dao(); dao.init();
        assertTrue(dao.beginOperation(1, "token", "SCALEUP"));
        String where = dao.criteria.getWhereClause().toLowerCase();
        assertTrue(where.contains("state")); assertTrue(where.contains("operation_uuid") && where.contains("is null"));
        UpdateBuilder update = GenericDaoBase.getUpdateBuilder(dao.updated);
        assertTrue(update.has("state")); assertTrue(update.has("operationUuid"));
        assertTrue(update.has("operationAction")); assertTrue(update.has("operationPhase"));
        assertTrue(dao.checkpointOperation(1, "token", "DRAINING", 2L, new java.util.Date()));
        assertTrue(dao.criteria.getWhereClause().contains("operation_uuid"));
        update = GenericDaoBase.getUpdateBuilder(dao.updated);
        assertTrue(update.has("operationVmId")); assertTrue(update.has("operationDeadline"));
        assertFalse(update.has("state"));
    }
    @Test public void staleCompletionCannotReleaseOwnerAndDisabledIntentSurvivesCompletion() {
        Dao dao = new Dao(); dao.init();
        dao.current.setOperationUuid("current"); dao.current.setState(AutoScaleVmGroup.State.DISABLED);
        assertFalse(dao.finishOperation(1, "old")); assertNull(dao.updated);
        assertTrue(dao.finishOperation(1, "current"));
        UpdateBuilder update = GenericDaoBase.getUpdateBuilder(dao.updated);
        assertFalse(update.has("state"));
        assertTrue(update.has("operationUuid")); assertTrue(update.has("operationVmId"));
        assertTrue(update.has("operationDeadline")); assertNull(dao.updated.getOperationUuid());
        dao.current.setState(AutoScaleVmGroup.State.SCALING);
        assertTrue(dao.finishOperation(1, "current"));
        assertEquals(AutoScaleVmGroup.State.ENABLED, dao.updated.getState());
    }
    @Test public void intervalAndSequenceUpdatesCannotOverwriteUserStateOrBounds() {
        Dao dao = new Dao(); dao.init();
        assertTrue(dao.updateLastInterval(1, new java.util.Date()));
        UpdateBuilder update = GenericDaoBase.getUpdateBuilder(dao.updated);
        assertTrue(update.has("lastInterval")); assertFalse(update.has("state")); assertFalse(update.has("maxMembers"));
        dao.updateNextVmSeq(1, 9);
        update = GenericDaoBase.getUpdateBuilder(dao.updated);
        assertTrue(update.has("nextVmSeq")); assertFalse(update.has("state")); assertFalse(update.has("operationUuid"));
    }
    @Test public void olderEvaluationCannotOverwriteLatestAndOnlyEvaluationFieldsChange() {
        Dao dao = new Dao(); dao.init();
        dao.current.setEvaluationTime(new java.util.Date(2000));
        assertFalse(dao.updateEvaluation(1, "old", new java.util.Date(1999)));
        assertNull(dao.updated);
        assertTrue(dao.updateEvaluation(1, "new", new java.util.Date(2001)));
        UpdateBuilder update = GenericDaoBase.getUpdateBuilder(dao.updated);
        assertTrue(update.has("evaluation")); assertTrue(update.has("evaluationTimeMillis"));
        assertTrue(update.getChanges().stream().anyMatch(change -> Long.valueOf(2001).equals(change.third())));
        assertFalse(update.has("state")); assertFalse(update.has("lastScaledMillis"));
        assertTrue(dao.updateLastScaled(1, new java.util.Date(3000)));
        update = GenericDaoBase.getUpdateBuilder(dao.updated);
        assertTrue(update.has("lastScaledMillis"));
        assertTrue(update.getChanges().stream().anyMatch(change -> Long.valueOf(3000).equals(change.third())));
        assertFalse(update.has("evaluation")); assertFalse(update.has("state"));
    }
    @Test public void jdbcBindingPreservesFullEvaluationAndMillisecondTimestamps() throws Exception {
        Dao dao = new Dao(); dao.init();
        java.sql.PreparedStatement statement = org.mockito.Mockito.mock(java.sql.PreparedStatement.class);
        String json = "{\"countername\":\"" + String.join("", java.util.Collections.nCopies(1000, "메모리")) + "\"}";
        dao.bind(statement, "evaluation", json);
        org.mockito.Mockito.verify(statement).setBytes(1, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        dao.bind(statement, "lastScaledMillis", 1791693120508L);
        org.mockito.Mockito.verify(statement).setObject(1, 1791693120508L);
        dao.current.setLastScaled(new java.util.Date(1791693120508L));
        assertEquals(1791693120508L, dao.current.getLastScaled().getTime());
        dao.current.setEvaluationTime(new java.util.Date(1791693120509L));
        assertEquals(1791693120509L, dao.current.getEvaluationTime().getTime());
    }


    @Test public void cleanupClaimDisablesEvaluationAndPreservesUnrelatedConfiguration() {
        Dao dao = new Dao(); dao.init();
        dao.current.setState(AutoScaleVmGroup.State.ENABLED);
        assertTrue(dao.beginCleanupOperation(1, "cleanup-owner"));
        assertEquals(AutoScaleVmGroup.State.DISABLED, dao.updated.getState());
        assertEquals("CLEANUP", dao.updated.getOperationAction());
        UpdateBuilder update = GenericDaoBase.getUpdateBuilder(dao.updated);
        assertTrue(update.has("operationUuid")); assertTrue(update.has("operationDeadline"));
        assertFalse(update.has("evaluation")); assertFalse(update.has("minMembers"));
        dao.current.setOperationUuid("other-owner"); dao.updated = null;
        assertFalse(dao.beginCleanupOperation(1, "cleanup-owner"));
        assertNull(dao.updated);
    }
}
