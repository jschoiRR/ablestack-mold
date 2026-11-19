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

import com.cloud.network.as.AutoScaleVmGroup;
import com.cloud.network.as.AutoScaleVmGroupVO;
import com.cloud.utils.db.GenericDao;

public interface AutoScaleVmGroupDao extends GenericDao<AutoScaleVmGroupVO, Long> {
    List<AutoScaleVmGroupVO> listByAll(Long loadBalancerId, Long profileId);

    boolean isProfileInUse(long profileId);

    boolean isAutoScaleLoadBalancer(Long loadBalancerId);

    boolean beginOperation(long groupId, String token, String action);

    boolean beginCleanupOperation(long groupId, String token);

    boolean checkpointOperation(long groupId, String token, String phase, Long vmId, java.util.Date deadline);

    boolean finishOperation(long groupId, String token);

    boolean updateEvaluation(long groupId, String evaluation, Date timestamp);

    boolean updateLastScaled(long groupId, Date timestamp);

    boolean updateLastInterval(long groupId, java.util.Date interval);

    boolean updateNextVmSeq(long groupId, long sequence);

    boolean updateState(long groupId, AutoScaleVmGroup.State oldState, AutoScaleVmGroup.State newState);

    List<AutoScaleVmGroupVO> listByLoadBalancer(Long loadBalancerId);

    List<AutoScaleVmGroupVO> listByProfile(Long profileId);

    List<AutoScaleVmGroupVO> listByAccount(Long accountId);
}
