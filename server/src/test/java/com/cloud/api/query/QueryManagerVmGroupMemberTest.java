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
package com.cloud.api.query;

import java.util.List;
import java.util.Map;
import com.cloud.api.query.dao.InstanceGroupJoinDao;
import com.cloud.api.query.vo.InstanceGroupJoinVO;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.User;
import com.google.gson.Gson;
import org.apache.cloudstack.api.response.InstanceGroupResponse;
import org.apache.cloudstack.context.CallContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class QueryManagerVmGroupMemberTest {
    private QueryManagerImpl manager;
    private InstanceGroupJoinDao dao;
    private AccountManager accountManager;
    private User user;
    private Account account;

    @Before
    public void setup() {
        manager = new QueryManagerImpl(); dao = mock(InstanceGroupJoinDao.class);
        accountManager = mock(AccountManager.class);
        account = mock(Account.class); user = mock(User.class); when(account.getId()).thenReturn(2L);
        CallContext.register(user, account);
        ReflectionTestUtils.setField(manager, "accountMgr", accountManager);
        ReflectionTestUtils.setField(manager, "_vmGroupJoinDao", dao);
    }

    @After
    public void teardown() { CallContext.unregister(); }

    private InstanceGroupJoinVO row(long id, String uuid) {
        InstanceGroupJoinVO group = mock(InstanceGroupJoinVO.class);
        when(group.getId()).thenReturn(id); when(group.getUuid()).thenReturn(uuid); return group;
    }

    private InstanceGroupResponse response(String uuid) {
        InstanceGroupResponse response = new InstanceGroupResponse(); response.setId(uuid); return response;
    }

    @Test
    public void visiblePageIsAggregatedOnceAndAssociatedByUuid() {
        InstanceGroupResponse first = response("first"), second = response("second");
        when(dao.listMemberStateCounts(List.of(41L, 42L), false)).thenReturn(Map.of(41L, Map.of("Running", 2L), 42L, Map.of()));
        manager.attachVmGroupMemberCounts(List.of(row(41L, "first"), row(42L, "second")), List.of(second, first), "fixture-key");
        verify(dao, times(1)).listMemberStateCounts(List.of(41L, 42L), false);
        verify(accountManager).checkApiAccessForUser(user, "listVirtualMachines", "fixture-key");
        assertEquals(2L, new Gson().toJsonTree(first).getAsJsonObject().get("virtualmachinecount").getAsLong());
        assertEquals(0L, new Gson().toJsonTree(second).getAsJsonObject().get("virtualmachinecount").getAsLong());
    }

    @Test
    public void deniedVmApiDoesNotExposeMemberCountsOrQueryMembers() {
        doThrow(new PermissionDeniedException("restricted role")).when(accountManager).checkApiAccessForUser(user, "listVirtualMachines", null);
        InstanceGroupResponse response = response("group");
        manager.attachVmGroupMemberCounts(List.of(row(1L, "group")), List.of(response), null);
        verifyNoInteractions(dao);
        assertFalse(new Gson().toJsonTree(response).getAsJsonObject().has("virtualmachinecount"));
    }

    @Test
    public void deniedRestrictedApiKeyDoesNotExposeMemberCounts() {
        doThrow(new PermissionDeniedException("restricted key")).when(accountManager)
                .checkApiAccessForUser(user, "listVirtualMachines", "restricted-key");
        manager.attachVmGroupMemberCounts(List.of(row(1L, "group")), List.of(response("group")), "restricted-key");
        verifyNoInteractions(dao);
    }

    @Test
    public void noVisibleGroupsDoNotIssueAnAggregateQuery() {
        manager.attachVmGroupMemberCounts(List.of(), List.of(), null); verifyNoInteractions(dao, accountManager);
    }

    @Test
    public void rootAdminUsesItsExistingHiddenVmVisibility() {
        when(accountManager.isRootAdmin(2L)).thenReturn(true);
        when(dao.listMemberStateCounts(List.of(1L), true)).thenReturn(Map.of(1L, Map.of()));
        manager.attachVmGroupMemberCounts(List.of(row(1L, "group")), List.of(response("group")), null);
        verify(dao).listMemberStateCounts(List.of(1L), true);
    }
}
