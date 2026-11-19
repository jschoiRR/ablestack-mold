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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.apache.cloudstack.api.command.user.autoscale.CreateConditionCmd;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.jobs.dao.AsyncJobDao;
import org.apache.cloudstack.framework.jobs.impl.AsyncJobVO;
import org.apache.cloudstack.jobs.JobInfo;
import com.cloud.network.as.dao.AutoScaleCreationRequestDao;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.User;
import com.cloud.user.AccountManager;
import com.cloud.utils.db.EntityManager;
import com.google.gson.Gson;

public class AutoScaleCreationRequestManagerTest {
    static class Manager extends AutoScaleCreationRequestManager {
        @Override protected <T> T withLock(String key, Work<T> work) throws Exception { return work.run(); }
        @Override protected <T> T transaction(Supplier<T> work) { return work.get(); }
    }
    Manager manager;
    Map<String, AutoScaleCreationRequestVO> receipts;
    CreateConditionCmd cmd;
    ConditionVO condition;
    String id = UUID.randomUUID().toString();
    String operationId = UUID.randomUUID().toString();
    static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    void context(long userId) {
        CallContext.unregister();
        User user = mock(User.class); Account account = mock(Account.class);
        when(user.getId()).thenReturn(userId); when(account.getId()).thenReturn(2L);
        CallContext.register(user, account);
    }
    @Before public void setup() throws Exception {
        context(10L); manager = new Manager(); receipts = new HashMap<>();
        manager.requestDao = mock(AutoScaleCreationRequestDao.class);
        manager.jobDao = mock(AsyncJobDao.class); manager.entityMgr = mock(EntityManager.class); manager.accountMgr = mock(AccountManager.class);
        when(manager.requestDao.findByUuid(anyString())).thenAnswer(i -> receipts.get(i.getArgument(0)));
        when(manager.requestDao.persist(any())).thenAnswer(i -> { AutoScaleCreationRequestVO row = i.getArgument(0); field(row, "id", 7L); receipts.put(row.getUuid(), row); return row; });
        when(manager.requestDao.update(anyLong(), any())).thenReturn(true);
        cmd = new CreateConditionCmd(); AccountService accounts = mock(AccountService.class); when(accounts.finalizeAccountId(null, null, null, true)).thenReturn(2L); field(cmd, "_accountService", accounts); field(cmd, "injectedJobId", id); field(cmd, "threshold", 10L); field(cmd, "relationalOperator", "GT");
        field(cmd, "creationOperationId", operationId); field(cmd, "creationStage", "up.0.condition.0");
        condition = mock(ConditionVO.class); when(condition.getId()).thenReturn(5L); when(condition.getUuid()).thenReturn(UUID.randomUUID().toString());
        when(manager.entityMgr.findById(ConditionVO.class, 5L)).thenReturn(condition);
        when(manager.entityMgr.findById(Condition.class, 5L)).thenReturn(condition);
    }
    @After public void teardown() { CallContext.unregister(); }
    @Test public void replayReturnsSameResourceWithoutCallingCreator() {
        AtomicInteger calls = new AtomicInteger(); Supplier<ConditionVO> create = () -> { calls.incrementAndGet(); return condition; };
        assertSame(condition, manager.create(cmd, ConditionVO.class, create));
        assertSame(condition, manager.create(cmd, ConditionVO.class, create)); assertEquals(1, calls.get());
        assertEquals(operationId, receipts.get(id).getOperationUuid());
        assertEquals("up.0.condition.0", receipts.get(id).getStageKey());
    }
    @Test public void changedSpecificationCannotReplay() throws Exception {
        manager.create(cmd, ConditionVO.class, () -> condition); field(cmd, "threshold", 11L);
        assertThrows(RuntimeException.class, () -> manager.create(cmd, ConditionVO.class, () -> condition));
    }
    @Test public void differentUserCannotReplayOrQuery() {
        manager.create(cmd, ConditionVO.class, () -> condition); context(11L);
        assertThrows(RuntimeException.class, () -> manager.create(cmd, ConditionVO.class, () -> condition));
        assertTrue(new Gson().toJson(manager.query(id)).contains("NOTFOUND"));
    }
    @Test public void removedResourceCannotRecreate() {
        manager.create(cmd, ConditionVO.class, () -> condition); when(manager.entityMgr.findById(ConditionVO.class, 5L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> manager.create(cmd, ConditionVO.class, () -> condition));
    }
    @Test public void failedCreationHasNoResourceAndCannotReplay() {
        assertThrows(RuntimeException.class, () -> manager.create(cmd, ConditionVO.class, () -> { throw new IllegalArgumentException("fixture"); }));
        assertEquals("FAILED", receipts.get(id).getStatus()); assertNull(receipts.get(id).getResourceId());
        assertThrows(RuntimeException.class, () -> manager.create(cmd, ConditionVO.class, () -> condition));
    }
    @Test public void receiptCommitFailureDoesNotClaimRolledBackResource() {
        when(manager.requestDao.update(anyLong(), any())).thenReturn(false);
        assertThrows(RuntimeException.class, () -> manager.create(cmd, ConditionVO.class, () -> condition));
        assertNull(receipts.get(id).getResourceId()); assertNull(receipts.get(id).getResourceUuid());
    }
    @Test public void canonicalUuidRequiredBeforeCreation() throws Exception {
        field(cmd, "injectedJobId", "1-1-1-1-1");
        assertThrows(RuntimeException.class, () -> manager.create(cmd, ConditionVO.class, () -> condition)); assertTrue(receipts.isEmpty());
    }
    @Test public void operationAndStageMustBothBeValid() throws Exception {
        field(cmd, "creationStage", "../secret");
        assertThrows(RuntimeException.class, () -> manager.create(cmd, ConditionVO.class, () -> condition)); assertTrue(receipts.isEmpty());
    }
    @Test public void legacyRequestWithoutCustomIdStillWorks() throws Exception {
        field(cmd, "injectedJobId", null); assertSame(condition, manager.create(cmd, ConditionVO.class, () -> condition)); assertTrue(receipts.isEmpty());
    }
    @Test public void hashIsStableAndContainsNoRawSpecification() throws Exception {
        String first = AutoScaleCreationRequestManager.fingerprint(cmd); assertEquals(64, first.length());
        field(cmd, "injectedJobId", UUID.randomUUID().toString()); assertEquals(first, AutoScaleCreationRequestManager.fingerprint(cmd));
        field(cmd, "threshold", 22L); assertNotEquals(first, AutoScaleCreationRequestManager.fingerprint(cmd));
    }
    @Test public void expiredSimpleJobCanBeRecoveredFromCommittedResource() {
        manager.create(cmd, ConditionVO.class, () -> condition);
        String json = new Gson().toJson(manager.query(id)); assertTrue(json.contains("SUCCESS")); assertTrue(json.contains(condition.getUuid())); assertTrue(json.contains(operationId));
    }
    @Test public void receiptQueryDetectsDeletedResource() {
        manager.create(cmd, ConditionVO.class, () -> condition); when(manager.entityMgr.findById(Condition.class, 5L)).thenReturn(null);
        assertTrue(new Gson().toJson(manager.query(id)).contains("REMOVED"));
    }
    @Test public void pendingAndTerminalJobStatusesAreNotConflated() {
        manager.create(cmd, ConditionVO.class, () -> condition);
        AsyncJobVO job = mock(AsyncJobVO.class); when(job.getUuid()).thenReturn(id); when(job.getUserId()).thenReturn(10L);
        when(job.getInstanceId()).thenReturn(5L); when(job.getInstanceType()).thenReturn("Condition");
        when(manager.jobDao.findByUuidIncludingRemoved(id)).thenReturn(job);
        when(job.getStatus()).thenReturn(JobInfo.Status.IN_PROGRESS);
        assertTrue(new Gson().toJson(manager.query(id)).contains("PENDING"));
        when(job.getStatus()).thenReturn(JobInfo.Status.SUCCEEDED); when(job.getRemoved()).thenReturn(new java.util.Date());
        assertTrue(new Gson().toJson(manager.query(id)).contains("SUCCESS"));
        when(job.getStatus()).thenReturn(JobInfo.Status.FAILED);
        assertTrue(new Gson().toJson(manager.query(id)).contains("FAILED"));
    }
    @Test public void jobReplayRequiresCallerCommandAndResourceToMatch() {
        AsyncJobVO job = mock(AsyncJobVO.class); when(job.getUserId()).thenReturn(10L); when(job.getAccountId()).thenReturn(2L);
        when(job.getInstanceId()).thenReturn(5L); when(job.getCmd()).thenReturn(cmd.getClass().getName());
        when(job.getStatus()).thenReturn(JobInfo.Status.SUCCEEDED); when(job.getRemoved()).thenReturn(new java.util.Date());
        when(manager.jobDao.findByUuidIncludingRemoved(id)).thenReturn(job);
        assertSame(job, manager.submittedJob(cmd, 5L, 10L, 2L));
        assertThrows(RuntimeException.class, () -> manager.submittedJob(cmd, 6L, 10L, 2L));
        assertThrows(RuntimeException.class, () -> manager.submittedJob(cmd, 5L, 11L, 2L));
    }
    @Test public void newGroupWithExpiredJobIsNotReportedSuccessful() {
        AutoScaleCreationRequestVO row = new AutoScaleCreationRequestVO(id, 10L, 2L, "createAutoScaleVmGroup", "hash", "AutoScaleVmGroup");
        row.setResource(9L, "group-uuid"); receipts.put(id, row);
        AutoScaleVmGroup group = mock(AutoScaleVmGroup.class); when(group.getState()).thenReturn(AutoScaleVmGroup.State.NEW);
        when(manager.entityMgr.findById(AutoScaleVmGroup.class, 9L)).thenReturn(group);
        assertTrue(new Gson().toJson(manager.query(id)).contains("CREATED"));
        when(group.getState()).thenReturn(AutoScaleVmGroup.State.DISABLED);
        assertTrue(new Gson().toJson(manager.query(id)).contains("SUCCESS"));
    }

    @Test public void receiptUsesTheCloudApiNamedObjectEnvelope() {
        manager.create(cmd, ConditionVO.class, () -> condition);
        org.apache.cloudstack.api.response.AutoScaleCreationRequestResponse response = manager.query(id);
        response.setResponseName("autoscalecreationrequestresponse");
        String json = com.cloud.api.response.ApiResponseSerializer.toSerializedString(response, "json");
        com.google.gson.JsonObject receipt = new Gson().fromJson(json, com.google.gson.JsonObject.class)
                .getAsJsonObject("autoscalecreationrequestresponse").getAsJsonObject("autoscalecreationrequest");
        assertEquals(id, receipt.get("requestid").getAsString());
        assertEquals("SUCCESS", receipt.get("status").getAsString());
    }

}
