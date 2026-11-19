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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.cloudstack.api.command.user.vm.ListVMsCmd;
import org.apache.cloudstack.context.CallContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.utils.Pair;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDetailsDao;

public class QueryManagerSSHKeyPairTest {
    private QueryManagerImpl manager;
    private UserVmDao vmDao;
    private VMInstanceDetailsDao detailsDao;
    private AccountManager accounts;
    private SearchBuilder<UserVmVO> builder;
    private SearchCriteria<UserVmVO> criteria;
    private ListVMsCmd command;

    @Before
    public void setup() {
        manager = new QueryManagerImpl();
        vmDao = mock(UserVmDao.class);
        detailsDao = mock(VMInstanceDetailsDao.class);
        accounts = mock(AccountManager.class);
        ReflectionTestUtils.setField(manager, "userVmDao", vmDao);
        ReflectionTestUtils.setField(manager, "vmInstanceDetailsDao", detailsDao);
        ReflectionTestUtils.setField(manager, "accountMgr", accounts);
        AccountVO account = new AccountVO("ssh-test", 1L, null, Account.Type.ADMIN, UUID.randomUUID().toString());
        account.setId(1L);
        CallContext.register(new UserVO(1L, "ssh-test", "unused", "SSH", "Test", "test@example.invalid", "UTC", UUID.randomUUID().toString(), User.Source.UNKNOWN), account);
        when(accounts.isAdmin(1L)).thenReturn(true);
        when(accounts.isRootAdmin(1L)).thenReturn(true);
        builder = mock(SearchBuilder.class, Mockito.RETURNS_SELF);
        criteria = mock(SearchCriteria.class);
        when(builder.entity()).thenReturn(mock(UserVmVO.class));
        when(builder.create()).thenReturn(criteria);
        when(vmDao.createSearchBuilder()).thenReturn(builder);
        UserVmVO vm = mock(UserVmVO.class);
        when(vm.getId()).thenReturn(8L);
        when(vmDao.searchAndDistinctCount(eq(criteria), any(Filter.class), any(String[].class))).thenReturn(new Pair<>(Collections.singletonList(vm), 3));
        command = Mockito.spy(new ListVMsCmd());
        Mockito.doReturn(1L).when(command).getStartIndex();
        Mockito.doReturn(1L).when(command).getPageSizeVal();
    }

    @After
    public void cleanup() {
        CallContext.unregister();
    }

    private Pair<List<Long>, Integer> lookup() {
        return ReflectionTestUtils.invokeMethod(manager, "searchForUserVMIdsAndCount", command);
    }

    @Test
    public void linkedIdsAreIntersectedWithExistingIdAndAclFiltersBeforePagination() {
        when(command.getKeyPairName()).thenReturn("same-name");
        when(command.getIds()).thenReturn(Arrays.asList(8L, 9L));
        doAnswer(invocation -> {
            List<Long> owners = invocation.getArgument(4);
            owners.add(2L);
            return null;
        }).when(accounts).buildACLSearchParameters(any(), any(), any(), any(), anyList(), any(), Mockito.anyBoolean(), Mockito.anyBoolean());
        when(detailsDao.listVmIdsBySSHKeyPairName(eq("same-name"), eq(Collections.singletonList(2L)))).thenReturn(Arrays.asList(8L, 10L));
        Pair<List<Long>, Integer> result = lookup();
        assertEquals(Collections.singletonList(8L), result.first());
        assertEquals(Integer.valueOf(3), result.second());
        verify(criteria).setParameters("sshKeyPairVmIds", 8L, 10L);
        verify(criteria).setParameters("idIN", 8L, 9L);
        verify(accounts).buildACLSearchCriteria(eq(criteria), any(), Mockito.anyBoolean(), eq(Collections.singletonList(2L)), any());
        ArgumentCaptor<Filter> filter = ArgumentCaptor.forClass(Filter.class);
        verify(vmDao).searchAndDistinctCount(eq(criteria), filter.capture(), any(String[].class));
        assertEquals(Long.valueOf(1L), filter.getValue().getOffset());
        assertEquals(Long.valueOf(1L), filter.getValue().getLimit());
    }

    @Test
    public void unusedOrMissingKeyReturnsZeroWithoutQueryingUnfilteredVMs() {
        when(command.getKeyPairName()).thenReturn("unused");
        when(detailsDao.listVmIdsBySSHKeyPairName(eq("unused"), anyList())).thenReturn(Collections.emptyList());
        Pair<List<Long>, Integer> result = lookup();
        assertEquals(Collections.emptyList(), result.first());
        assertEquals(Integer.valueOf(0), result.second());
        verify(vmDao, never()).searchAndDistinctCount(any(), any(), any());
    }

    @Test
    public void noKeyFilterRetainsTheExistingVmQuery() {
        Pair<List<Long>, Integer> result = lookup();
        assertEquals(Collections.singletonList(8L), result.first());
        assertEquals(Integer.valueOf(3), result.second());
        verify(detailsDao, never()).listVmIdsBySSHKeyPairName(anyString(), anyList());
    }

    @Test
    public void lookupFailureDoesNotFallBackToAllVirtualMachines() {
        when(command.getKeyPairName()).thenReturn("target");
        when(detailsDao.listVmIdsBySSHKeyPairName(eq("target"), anyList())).thenThrow(new CloudRuntimeException("lookup failed"));
        try {
            lookup();
            fail("Database errors must propagate");
        } catch (CloudRuntimeException expected) {
            verify(vmDao, never()).searchAndDistinctCount(any(), any(), any());
        }
    }
}
