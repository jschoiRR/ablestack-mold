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
package com.cloud.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.cloudstack.api.command.user.ssh.ListSSHKeyPairsCmd;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.SSHKeyPair;
import com.cloud.user.SSHKeyPairVO;
import com.cloud.user.dao.SSHKeyPairDao;
import com.cloud.user.dao.SSHKeyPairDaoImpl;
import com.cloud.utils.Pair;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchCriteria;

public class ManagementServerImplSSHKeyPairTest {
    private ManagementServerImpl managementServer;
    private AccountManager accountManager;
    private Account caller;
    private ListSSHKeyPairsCmd command;
    private SearchCriteria<SSHKeyPairVO> criteria;
    private Filter filter;
    private List<SSHKeyPairVO> rows;
    private int count;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        managementServer = spy(new ManagementServerImpl());
        accountManager = mock(AccountManager.class);
        caller = mock(Account.class);
        SSHKeyPairDao dao = mock(SSHKeyPairDao.class);
        SSHKeyPairDaoImpl criteriaFactory = new SSHKeyPairDaoImpl();
        managementServer._accountMgr = accountManager;
        managementServer._sshKeyPairDao = dao;
        doReturn(caller).when(managementServer).getCaller();
        command = spy(new ListSSHKeyPairsCmd());
        doReturn(3L).when(command).getDomainId();
        doReturn(true).when(command).listAll();
        doReturn(0L).when(command).getStartIndex();
        doReturn(20L).when(command).getPageSizeVal();
        when(dao.createSearchBuilder()).thenAnswer(invocation -> criteriaFactory.createSearchBuilder());
        when(dao.createSearchCriteria()).thenAnswer(invocation -> criteriaFactory.createSearchCriteria());
        doAnswer(invocation -> {
            List<Long> permittedAccounts = invocation.getArgument(4);
            permittedAccounts.add(7L);
            return null;
        }).when(accountManager).buildACLSearchParameters(eq(caller), isNull(), any(), any(), anyList(), any(), eq(true), eq(false));
        doAnswer(invocation -> {
            SearchCriteria<SSHKeyPairVO> aclCriteria = invocation.getArgument(0);
            aclCriteria.addAnd("accountId", SearchCriteria.Op.EQ, 7L);
            aclCriteria.addAnd("domainId", SearchCriteria.Op.EQ, 3L);
            return null;
        }).when(accountManager).buildACLSearchCriteria(any(), any(), anyBoolean(), anyList(), any());
        rows = Collections.emptyList();
        when(dao.searchAndCount(any(), any())).thenAnswer(invocation -> {
            criteria = invocation.getArgument(0);
            filter = invocation.getArgument(1);
            return new Pair<>(rows, count);
        });
    }

    private List<Object> values(String column) {
        return criteria.getValues().stream().filter(value -> column.equals(value.first().getColumnName()))
                .map(Pair::second).collect(Collectors.toList());
    }

    @Test
    public void idRestrictsTheExistingAclQuery() {
        ReflectionTestUtils.setField(command, "id", 42L);
        SSHKeyPairVO key = new SSHKeyPairVO();
        key.setId(42L);
        rows = Collections.singletonList(key);
        count = 1;

        Pair<List<? extends SSHKeyPair>, Integer> result = managementServer.listSSHKeyPairs(command);

        assertEquals(Collections.singletonList(42L), values("id"));
        assertEquals(Collections.singletonList(7L), values("account_id"));
        assertEquals(Collections.singletonList(3L), values("domain_id"));
        assertTrue(criteria.getWhereClause().replace("`", "").matches("(?s).*\\bid\\s*=\\s*\\?.*"));
        assertSame(rows, result.first());
        assertEquals(Integer.valueOf(1), result.second());
    }

    @Test
    public void idAndOtherFiltersAreAnIntersection() {
        ReflectionTestUtils.setField(command, "id", 42L);
        ReflectionTestUtils.setField(command, "name", "same-name");
        ReflectionTestUtils.setField(command, "fingerprint", "expected-fingerprint");
        ReflectionTestUtils.setField(command, "keyword", "needle");

        managementServer.listSSHKeyPairs(command);

        assertEquals(Collections.singletonList(42L), values("id"));
        assertEquals(List.of("same-name", "%needle%"), values("keypair_name"));
        assertEquals(List.of("expected-fingerprint", "%needle%"), values("fingerprint"));
        assertEquals(Collections.singletonList("%needle%"), values("uuid"));
        String where = criteria.getWhereClause();
        assertTrue(where.contains("AND"));
        assertTrue(where.contains("OR"));
    }

    @Test
    public void omittedIdPreservesNameAndFingerprintSearch() {
        ReflectionTestUtils.setField(command, "name", "same-name");
        ReflectionTestUtils.setField(command, "fingerprint", "expected-fingerprint");

        managementServer.listSSHKeyPairs(command);

        assertTrue(values("id").isEmpty());
        assertEquals(Collections.singletonList("same-name"), values("keypair_name"));
        assertEquals(Collections.singletonList("expected-fingerprint"), values("fingerprint"));
        assertFalse(criteria.getWhereClause().replace("`", "").matches("(?s).*\\bid\\s*=\\s*\\?.*"));
    }

    @Test
    public void omittedIdPreservesKeywordAlternatives() {
        ReflectionTestUtils.setField(command, "keyword", "needle");

        managementServer.listSSHKeyPairs(command);

        assertTrue(values("id").isEmpty());
        assertEquals(Collections.singletonList("%needle%"), values("keypair_name"));
        assertEquals(Collections.singletonList("%needle%"), values("fingerprint"));
        assertEquals(Collections.singletonList("%needle%"), values("uuid"));
    }

    @Test
    public void emptyDaoResultCannotFallBackToOtherKeys() {
        ReflectionTestUtils.setField(command, "id", 404L);

        Pair<List<? extends SSHKeyPair>, Integer> result = managementServer.listSSHKeyPairs(command);

        assertEquals(Collections.singletonList(404L), values("id"));
        assertTrue(result.first().isEmpty());
        assertEquals(Integer.valueOf(0), result.second());
    }

    @Test
    public void paginationAndTotalCountArePreserved() {
        doReturn(20L).when(command).getStartIndex();
        rows = Collections.singletonList(new SSHKeyPairVO());
        count = 35;

        Pair<List<? extends SSHKeyPair>, Integer> result = managementServer.listSSHKeyPairs(command);

        assertEquals(Long.valueOf(20), filter.getOffset());
        assertEquals(Long.valueOf(20), filter.getLimit());
        assertSame(rows, result.first());
        assertEquals(Integer.valueOf(35), result.second());
    }

    @Test
    public void idLookupStillPassesAccountAndProjectScopeToAcl() {
        ReflectionTestUtils.setField(command, "id", 42L);
        doReturn("owner").when(command).getAccountName();
        doReturn(77L).when(command).getProjectId();

        managementServer.listSSHKeyPairs(command);

        verify(accountManager).buildACLSearchParameters(eq(caller), isNull(), eq("owner"), eq(77L), anyList(), any(), eq(true), eq(false));
        verify(accountManager).buildACLSearchBuilder(any(), eq(3L), eq(false), eq(Collections.singletonList(7L)), isNull());
        verify(accountManager).buildACLSearchCriteria(any(), eq(3L), eq(false), eq(Collections.singletonList(7L)), isNull());
        assertEquals(Collections.singletonList(42L), values("id"));
    }
}
