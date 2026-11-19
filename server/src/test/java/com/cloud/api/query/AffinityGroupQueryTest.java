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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.cloudstack.affinity.AffinityGroupDomainMapVO;
import org.apache.cloudstack.affinity.dao.AffinityGroupDomainMapDao;
import org.apache.cloudstack.api.command.user.affinitygroup.ListAffinityGroupsCmd;
import org.apache.cloudstack.context.CallContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.cloud.api.query.dao.AffinityGroupJoinDao;
import com.cloud.api.query.dao.AffinityGroupJoinDaoImpl;
import com.cloud.api.query.vo.AffinityGroupJoinVO;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.projects.Project.ListProjectResourcesCriteria;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.DomainManager;
import com.cloud.user.User;
import com.cloud.user.UserVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.Pair;
import com.cloud.utils.Ternary;
import com.cloud.utils.db.Filter;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AffinityGroupQueryTest {
    @Mock private AffinityGroupJoinDao groups;
    @Mock private AffinityGroupDomainMapDao domainMaps;
    @Mock private AccountManager accountManager;
    @Mock private AccountDao accounts;
    @Mock private DomainManager domains;
    @Mock private DomainDao domainDao;
    @Mock private UserVmDao vms;
    @Mock private ListAffinityGroupsCmd cmd;

    private QueryManagerImpl manager;
    private AccountVO caller;
    private Long scopeDomain;
    private boolean recursive;
    private ListProjectResourcesCriteria projectCriteria;
    private final List<Long> permitted = new ArrayList<>();

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        manager = new QueryManagerImpl();
        ReflectionTestUtils.setField(manager, "_affinityGroupJoinDao", groups);
        ReflectionTestUtils.setField(manager, "_affinityGroupDomainMapDao", domainMaps);
        ReflectionTestUtils.setField(manager, "accountMgr", accountManager);
        ReflectionTestUtils.setField(manager, "_accountDao", accounts);
        ReflectionTestUtils.setField(manager, "_domainMgr", domains);
        ReflectionTestUtils.setField(manager, "_domainDao", domainDao);
        ReflectionTestUtils.setField(manager, "userVmDao", vms);
        caller = new AccountVO("domain-admin", 11L, null, Account.Type.DOMAIN_ADMIN, "caller");
        caller.setId(55L);
        CallContext.register(new UserVO(55L, "test", "unused", "test", "test", "test@example.invalid", "UTC", "user", User.Source.UNKNOWN), caller);
        scopeDomain = 11L;
        recursive = true;
        projectCriteria = null;
        permitted.clear();
        when(cmd.getVirtualMachineId()).thenReturn(null);
        when(cmd.getId()).thenReturn(null);
        when(cmd.getDomainId()).thenReturn(null);
        when(cmd.getProjectId()).thenReturn(null);
        when(cmd.getStartIndex()).thenReturn(0L);
        when(cmd.getPageSizeVal()).thenReturn(2L);
        doAnswer(invocation -> {
            ((List<Long>) invocation.getArgument(4)).addAll(permitted);
            Ternary<Long, Boolean, ListProjectResourcesCriteria> acl = invocation.getArgument(5);
            acl.first(scopeDomain); acl.second(recursive); acl.third(projectCriteria);
            return null;
        }).when(accountManager).buildACLSearchParameters(any(), any(), any(), any(), anyList(), any(), anyBoolean(), anyBoolean());
        new AffinityGroupJoinDaoImpl() { };
        when(groups.createSearchBuilder()).thenAnswer(invocation -> new SearchBuilder<>(AffinityGroupJoinVO.class));
        when(groups.createSearchCriteria()).thenAnswer(invocation -> new SearchBuilder<>(AffinityGroupJoinVO.class).create());
        DomainVO domain = new DomainVO(); domain.setPath("/ROOT/11/");
        when(domainDao.findById(11L)).thenReturn(domain);
        when(domains.getDomainParentIds(11L)).thenReturn(new HashSet<>(Arrays.asList(9L, 11L)));
        when(domainMaps.listByDomain(any(Object[].class))).thenReturn(Arrays.asList(map(91, 9, true), map(92, 9, false), map(93, 11, false), map(91, 9, true)));
        List<AffinityGroupJoinVO> page = Arrays.asList(row(1), row(2));
        when(groups.searchAndCount(any(), any(Filter.class))).thenReturn(new Pair<>(page, 4));
        when(groups.searchByIds(any(Long[].class))).thenAnswer(invocation -> new ArrayList<>(Arrays.asList(row(2), row(1), row(1))));
    }

    @After
    public void teardown() { CallContext.unregister(); }

    private static AffinityGroupJoinVO row(long id) {
        AffinityGroupJoinVO row = mock(AffinityGroupJoinVO.class);
        when(row.getId()).thenReturn(id);
        return row;
    }

    private static AffinityGroupDomainMapVO map(long group, long domain, boolean inherited) {
        return new AffinityGroupDomainMapVO(group, domain, inherited);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private SearchCriteria<AffinityGroupJoinVO> capturedQuery() {
        ArgumentCaptor<SearchCriteria> criteria = ArgumentCaptor.forClass(SearchCriteria.class);
        ArgumentCaptor<Filter> filter = ArgumentCaptor.forClass(Filter.class);
        verify(groups, times(1)).searchAndCount(criteria.capture(), filter.capture());
        assertEquals(cmd.getStartIndex(), filter.getValue().getOffset());
        assertEquals(cmd.getPageSizeVal(), filter.getValue().getLimit());
        assertTrue(filter.getValue().getOrderBy().contains("id ASC"));
        SearchCriteria<AffinityGroupJoinVO> sc = criteria.getValue();
        StringBuilder select = new StringBuilder(); sc.getSelect(select, 0);
        assertTrue(select.toString().toUpperCase().contains("DISTINCT"));
        return sc;
    }

    private static List<Object> values(SearchCriteria<?> sc) {
        sc.getWhereClause();
        return sc.getValues().stream().map(Pair::second).collect(Collectors.toList());
    }

    private static void assertNoTopLevelOr(String sql) {
        int depth = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '(') depth++;
            if (sql.charAt(i) == ')') depth--;
            assertFalse("A shared branch must not bypass global filters: " + sql, depth == 0 && sql.startsWith(" OR ", i));
        }
        assertEquals(0, depth);
    }

    @Test
    public void domainSharedOverlapCountsAndPagesDistinctGroupsOnceWithoutDroppingVmRows() {
        Pair<List<AffinityGroupJoinVO>, Integer> result = manager.searchForAffinityGroupsInternal(cmd);
        assertEquals(Integer.valueOf(4), result.second());
        assertEquals(Arrays.asList(1L, 1L, 2L), result.first().stream().map(AffinityGroupJoinVO::getId).collect(Collectors.toList()));
        SearchCriteria<?> sc = capturedQuery();
        assertNoTopLevelOr(sc.getWhereClause());
        assertTrue(values(sc).containsAll(Arrays.asList(91L, 93L, "/ROOT/11/%", "Domain")));
        assertFalse(values(sc).contains(92L));
    }

    @Test
    public void emptyPagePreservesTheDistinctTotalAndSkipsDetailFetch() {
        when(cmd.getStartIndex()).thenReturn(4L);
        when(groups.searchAndCount(any(), any(Filter.class))).thenReturn(new Pair<>(Collections.emptyList(), 4));
        Pair<List<AffinityGroupJoinVO>, Integer> result = manager.searchForAffinityGroupsInternal(cmd);
        assertTrue(result.first().isEmpty()); assertEquals(Integer.valueOf(4), result.second());
        capturedQuery(); verify(groups, never()).searchByIds(any(Long[].class));
    }

    @Test
    public void idNameTypeAndKeywordApplyOutsideBothVisibilityBranches() {
        when(cmd.getId()).thenReturn(7L);
        when(cmd.getAffinityGroupName()).thenReturn("target");
        when(cmd.getAffinityGroupType()).thenReturn("host affinity");
        when(cmd.getKeyword()).thenReturn("needle");
        manager.searchForAffinityGroupsInternal(cmd);
        SearchCriteria<?> sc = capturedQuery(); assertNoTopLevelOr(sc.getWhereClause());
        List<Object> params = values(sc);
        assertTrue(params.containsAll(Arrays.asList(7L, "target", "host affinity")));
        assertEquals(3, Collections.frequency(params, "%needle%"));
        assertEquals(1, Collections.frequency(params, "target"));
    }

    @Test
    public void multipleAccountsInOneDomainDoNotRepeatTheSharedScopeOrCount() {
        scopeDomain = null; permitted.addAll(Arrays.asList(55L, 56L));
        when(accounts.findById(anyLong())).thenReturn(caller);
        manager.searchForAffinityGroupsInternal(cmd);
        List<Object> params = values(capturedQuery());
        assertTrue(params.containsAll(Arrays.asList(55L, 56L, 91L, 93L)));
        assertEquals(1, Collections.frequency(params, 91L));
        verify(domains, times(1)).getDomainParentIds(11L);
    }

    @Test
    public void projectOnlyRestrictionsRemainOnBothVisibilityBranches() {
        projectCriteria = ListProjectResourcesCriteria.ListProjectResourcesOnly;
        manager.searchForAffinityGroupsInternal(cmd);
        SearchCriteria<?> sc = capturedQuery(); assertNoTopLevelOr(sc.getWhereClause());
        assertEquals(2, Collections.frequency(values(sc), Account.Type.PROJECT));
        assertFalse(sc.getWhereClause().contains("account_type !="));
    }

    @Test
    public void skipProjectRestrictionsRemainOnBothVisibilityBranches() {
        projectCriteria = ListProjectResourcesCriteria.SkipProjectResources;
        manager.searchForAffinityGroupsInternal(cmd);
        assertEquals(2, Collections.frequency(values(capturedQuery()), Account.Type.PROJECT));
    }

    @Test
    public void noSharedGroupsRetainsTheOriginalOwnerScope() {
        when(domainMaps.listByDomain(any(Object[].class))).thenReturn(Collections.emptyList());
        manager.searchForAffinityGroupsInternal(cmd);
        SearchCriteria<?> sc = capturedQuery();
        assertTrue(values(sc).contains("/ROOT/11/%")); assertFalse(sc.getWhereClause().contains(" OR "));
    }

    @Test
    public void unrestrictedRootScopeDoesNotAddAnEmptyParenthesizedCondition() {
        scopeDomain = null;
        manager.searchForAffinityGroupsInternal(cmd);
        assertEquals("", capturedQuery().getWhereClause());
        verify(domains, never()).getDomainParentIds(anyLong());
    }

    @Test
    public void nonrecursiveDomainScopeDoesNotExpandToInheritedGroups() {
        recursive = false;
        manager.searchForAffinityGroupsInternal(cmd);
        assertTrue(values(capturedQuery()).contains(11L));
        verify(domains, never()).getDomainParentIds(anyLong());
    }

    @Test(expected = PermissionDeniedException.class)
    public void vmLookupStillChecksAccessBeforeReadingGroupMappings() {
        when(cmd.getVirtualMachineId()).thenReturn(88L);
        UserVmVO vm = mock(UserVmVO.class); when(vms.findById(88L)).thenReturn(vm);
        doThrow(new PermissionDeniedException("denied")).when(accountManager).checkAccess(eq(caller), eq(null), eq(true), eq(vm));
        try { manager.searchForAffinityGroupsInternal(cmd); }
        finally { verify(groups, never()).searchAndCount(any(), any()); }
    }

    @Test(expected = InvalidParameterValueException.class)
    public void nonexistentVmDoesNotFallBackToUnscopedGroupSearch() {
        when(cmd.getVirtualMachineId()).thenReturn(88L);
        try { manager.searchForAffinityGroupsInternal(cmd); }
        finally { verify(groups, never()).searchAndCount(any(), any()); }
    }
}
