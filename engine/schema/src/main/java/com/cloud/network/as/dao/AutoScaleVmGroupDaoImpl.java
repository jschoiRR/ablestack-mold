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
package com.cloud.network.as.dao;

import java.util.List;
import java.util.Date;
import java.util.Objects;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;

import org.springframework.stereotype.Component;

import com.cloud.network.as.AutoScaleVmGroup;
import com.cloud.network.as.AutoScaleVmGroupVO;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.GenericSearchBuilder;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Func;

import javax.annotation.PostConstruct;

@Component
public class AutoScaleVmGroupDaoImpl extends GenericDaoBase<AutoScaleVmGroupVO, Long> implements AutoScaleVmGroupDao {

    SearchBuilder<AutoScaleVmGroupVO> AllFieldsSearch;

    SearchBuilder<AutoScaleVmGroupVO> OperationSearch;
    SearchBuilder<AutoScaleVmGroupVO> IdleSearch;

    @PostConstruct
    public void init() {
        AllFieldsSearch = createSearchBuilder();
        AllFieldsSearch.and("id", AllFieldsSearch.entity().getId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("loadBalancerId", AllFieldsSearch.entity().getLoadBalancerId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("profileId", AllFieldsSearch.entity().getProfileId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("state", AllFieldsSearch.entity().getState(), SearchCriteria.Op.EQ);
        AllFieldsSearch.and("accountId", AllFieldsSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        AllFieldsSearch.done();
        OperationSearch = createSearchBuilder();
        OperationSearch.and("id", OperationSearch.entity().getId(), SearchCriteria.Op.EQ);
        OperationSearch.and("token", OperationSearch.entity().getOperationUuid(), SearchCriteria.Op.EQ);
        OperationSearch.done();
        IdleSearch = createSearchBuilder();
        IdleSearch.and("id", IdleSearch.entity().getId(), SearchCriteria.Op.EQ);
        IdleSearch.and("state", IdleSearch.entity().getState(), SearchCriteria.Op.EQ);
        IdleSearch.and("idle", IdleSearch.entity().getOperationUuid(), SearchCriteria.Op.NULL);
        IdleSearch.done();
    }

    @Override
    public List<AutoScaleVmGroupVO> listByAll(Long loadBalancerId, Long profileId) {
        SearchCriteria<AutoScaleVmGroupVO> sc = AllFieldsSearch.create();

        if (loadBalancerId != null)
            sc.setParameters("loadBalancerId", loadBalancerId);

        if (profileId != null)
            sc.setParameters("profileId", profileId);

        return listBy(sc);
    }

    @Override
    public boolean isProfileInUse(long profileId) {
        SearchCriteria<AutoScaleVmGroupVO> sc = AllFieldsSearch.create();
        sc.setParameters("profileId", profileId);
        return findOneBy(sc) != null;
    }

    @Override
    public boolean isAutoScaleLoadBalancer(Long loadBalancerId) {
        GenericSearchBuilder<AutoScaleVmGroupVO, Long> countByLoadBalancer = createSearchBuilder(Long.class);
        countByLoadBalancer.select(null, Func.COUNT, null);
        countByLoadBalancer.and("loadBalancerId", countByLoadBalancer.entity().getLoadBalancerId(), SearchCriteria.Op.EQ);

        SearchCriteria<Long> sc = countByLoadBalancer.create();
        sc.setParameters("loadBalancerId", loadBalancerId);
        return customSearch(sc, null).get(0) > 0;
    }

    @Override
    public boolean updateState(long groupId, AutoScaleVmGroup.State oldState, AutoScaleVmGroup.State newState) {
        if (oldState == newState) {
            return false;
        }
        SearchCriteria<AutoScaleVmGroupVO> sc = AllFieldsSearch.create();
        sc.setParameters("id", groupId);
        sc.setParameters("state", oldState);
        AutoScaleVmGroupVO update = createForUpdate(groupId);
        update.setState(newState);
        return update(update, sc) == 1;
    }

    @Override
    public boolean beginOperation(long groupId, String token, String action) {
        SearchCriteria<AutoScaleVmGroupVO> sc = IdleSearch.create();
        sc.setParameters("id", groupId);
        sc.setParameters("state", AutoScaleVmGroup.State.ENABLED);
        AutoScaleVmGroupVO update = createForUpdate(groupId);
        update.setState(AutoScaleVmGroup.State.SCALING);
        update.setOperationUuid(token);
        update.setOperationAction(action);
        update.setOperationPhase("ACCEPTED");
        return update(update, sc) == 1;
    }

    @Override
    public boolean beginCleanupOperation(long groupId, String token) {
        return Transaction.execute((TransactionCallback<Boolean>) status -> {
            AutoScaleVmGroupVO current = lockRow(groupId, true);
            if (current == null || current.isOperationInProgress()) { return false; }
            AutoScaleVmGroupVO update = createForUpdate(groupId);
            update.setState(AutoScaleVmGroup.State.DISABLED);
            update.setOperationUuid(token);
            update.setOperationAction("CLEANUP");
            update.setOperationPhase("ACCEPTED");
            update.setOperationVmId(null);
            update.setOperationDeadline(null);
            return update(groupId, update);
        });
    }

    @Override
    public boolean checkpointOperation(long groupId, String token, String phase, Long vmId, Date deadline) {
        SearchCriteria<AutoScaleVmGroupVO> sc = OperationSearch.create();
        sc.setParameters("id", groupId);
        sc.setParameters("token", token);
        AutoScaleVmGroupVO update = createForUpdate(groupId);
        update.setOperationPhase(phase);
        update.setOperationVmId(vmId);
        update.setOperationDeadline(deadline);
        return update(update, sc) == 1;
    }

    @Override
    public boolean finishOperation(long groupId, String token) {
        return Transaction.execute((TransactionCallback<Boolean>) status -> {
            AutoScaleVmGroupVO current = lockRow(groupId, true);
            if (current == null || token == null || !Objects.equals(token, current.getOperationUuid())) {
                return false;
            }
            AutoScaleVmGroupVO update = createForUpdate(groupId);
            if (current.getState() == AutoScaleVmGroup.State.SCALING) {
                update.setState(AutoScaleVmGroup.State.ENABLED);
            }
            update.setOperationUuid(null);
            update.setOperationAction(null);
            update.setOperationPhase(null);
            update.setOperationVmId(null);
            update.setOperationDeadline(null);
            return update(groupId, update);
        });
    }

    @Override
    public boolean updateEvaluation(long groupId, String evaluation, Date timestamp) {
        return Transaction.execute((TransactionCallback<Boolean>) status -> {
            AutoScaleVmGroupVO current = lockRow(groupId, true);
            if (current == null || (current.getEvaluationTime() != null && current.getEvaluationTime().after(timestamp))) { return false; }
            AutoScaleVmGroupVO update = createForUpdate(groupId);
            update.setEvaluation(evaluation);
            update.setEvaluationTime(timestamp);
            return update(groupId, update);
        });
    }

    @Override
    public boolean updateLastScaled(long groupId, Date timestamp) {
        AutoScaleVmGroupVO update = createForUpdate(groupId);
        update.setLastScaled(timestamp);
        return update(groupId, update);
    }

    @Override
    public boolean updateLastInterval(long groupId, Date interval) {
        SearchCriteria<AutoScaleVmGroupVO> sc = IdleSearch.create();
        sc.setParameters("id", groupId);
        sc.setParameters("state", AutoScaleVmGroup.State.ENABLED);
        AutoScaleVmGroupVO update = createForUpdate(groupId);
        update.setLastInterval(interval);
        return update(update, sc) == 1;
    }

    @Override
    public boolean updateNextVmSeq(long groupId, long sequence) {
        AutoScaleVmGroupVO update = createForUpdate(groupId);
        update.setNextVmSeq(sequence);
        return update(groupId, update);
    }

    @Override
    public List<AutoScaleVmGroupVO> listByLoadBalancer(Long loadBalancerId) {
        SearchCriteria<AutoScaleVmGroupVO> sc = AllFieldsSearch.create();
        sc.setParameters("loadBalancerId", loadBalancerId);
        return listBy(sc);
    }

    @Override
    public List<AutoScaleVmGroupVO> listByProfile(Long profileId) {
        SearchCriteria<AutoScaleVmGroupVO> sc = AllFieldsSearch.create();
        sc.setParameters("profileId", profileId);
        return listBy(sc);
    }

    @Override
    public List<AutoScaleVmGroupVO> listByAccount(Long accountId) {
        SearchCriteria<AutoScaleVmGroupVO> sc = AllFieldsSearch.create();
        sc.setParameters("accountId", accountId);
        return listBy(sc);
    }
}
