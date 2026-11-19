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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import com.cloud.agent.api.to.LoadBalancerTO.AutoScalePolicyTO;
import com.cloud.agent.api.to.LoadBalancerTO.AutoScaleVmGroupTO;
import com.cloud.agent.api.to.LoadBalancerTO.ConditionTO;
import com.cloud.agent.api.to.LoadBalancerTO.CounterTO;
import com.cloud.network.as.dao.AutoScaleVmGroupDao;
import com.cloud.network.as.dao.AutoScaleVmGroupStatisticsDao;
import com.cloud.network.as.dao.AutoScaleVmGroupPolicyMapDao;
import com.cloud.network.as.dao.AutoScalePolicyDao;
import com.cloud.network.router.VirtualRouterAutoScale.AutoScaleValueType;
import com.cloud.server.ResourceTag;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AutoScalePolicyEvaluationTest {
    @Spy @InjectMocks private AutoScaleManagerImpl manager;
    @Mock private AutoScaleVmGroupDao groups;
    @Mock private AutoScaleVmGroupStatisticsDao statistics;
    @Mock private AutoScaleVmGroupPolicyMapDao mappings;
    @Mock private AutoScalePolicyDao policyDao;
    private AutoScaleVmGroupTO group;
    private AutoScaleVmGroupVO row;

    @Before
    public void prepare() {
        group = Mockito.mock(AutoScaleVmGroupTO.class);
        row = Mockito.mock(AutoScaleVmGroupVO.class);
        Mockito.when(group.getId()).thenReturn(1L);
        Mockito.when(group.getInterval()).thenReturn(10);
        Mockito.when(group.getMinMembers()).thenReturn(1);
        Mockito.when(group.getMaxMembers()).thenReturn(3);
        Mockito.when(groups.findById(1L)).thenReturn(row);
        Mockito.doReturn(60000L).when(manager).currentTimeMillis();
        Map<Long, List<Long>> hosts = new HashMap<>(); hosts.put(1L, Arrays.asList(1L, 2L));
        Mockito.doReturn(hosts).when(manager).getHostAndVmIdsMap(group);
    }

    private AutoScalePolicyTO policy(long id, AutoScalePolicy.Action action, Condition.Operator operator, long threshold, double cpu) {
        AutoScalePolicyTO policy = Mockito.mock(AutoScalePolicyTO.class);
        Mockito.when(policy.getId()).thenReturn(id);
        Mockito.when(policy.getUuid()).thenReturn("policy-" + id);
        Mockito.when(policy.getAction()).thenReturn(action);
        Mockito.when(policy.getDuration()).thenReturn(60);
        Mockito.when(policy.getQuietTime()).thenReturn(10);
        ConditionTO condition = Mockito.mock(ConditionTO.class);
        CounterTO counter = Mockito.mock(CounterTO.class);
        Mockito.when(counter.getId()).thenReturn(id);
        Mockito.when(counter.getSource()).thenReturn(Counter.Source.CPU);
        Mockito.when(counter.getName()).thenReturn("CPU average");
        Mockito.when(condition.getCounter()).thenReturn(counter);
        Mockito.when(condition.getUuid()).thenReturn("condition-" + id);
        Mockito.when(condition.getRelationalOperator()).thenReturn(operator);
        Mockito.when(condition.getThreshold()).thenReturn(threshold);
        Mockito.when(policy.getConditions()).thenReturn(Collections.singletonList(condition));
        List<AutoScaleVmGroupStatisticsVO> samples = new ArrayList<>();
        for (long time = 0; time <= 60000; time += 10000) {
            for (long vm : new long[]{1, 2}) { samples.add(new AutoScaleVmGroupStatisticsVO(1, id, id, vm, ResourceTag.ResourceObjectType.UserVm, cpu, AutoScaleValueType.INSTANT_VM, new Date(time))); }
        }
        Mockito.when(statistics.listByVmGroupAndPolicyAndCounter(Mockito.eq(1L), Mockito.eq(id), Mockito.eq(id), Mockito.any())).thenReturn(samples);
        return policy;
    }

    private JsonObject result() {
        ArgumentCaptor<String> captured = ArgumentCaptor.forClass(String.class);
        Mockito.verify(groups, Mockito.atLeastOnce()).updateEvaluation(Mockito.eq(1L), captured.capture(), Mockito.any());
        return JsonParser.parseString(captured.getValue()).getAsJsonObject();
    }

    @Test
    public void simultaneousDirectionsAlwaysPreferScaleUpIndependentOfListOrder() {
        AutoScalePolicyTO down = policy(1, AutoScalePolicy.Action.SCALEDOWN, Condition.Operator.LE, 80, 0.8);
        AutoScalePolicyTO up = policy(2, AutoScalePolicy.Action.SCALEUP, Condition.Operator.GE, 80, 0.8);
        Mockito.when(group.getPolicies()).thenReturn(Arrays.asList(down, up));
        Assert.assertEquals(AutoScalePolicy.Action.SCALEUP, manager.evaluateAutoScalePolicies(group));
        Assert.assertEquals("SCALEUP", result().get("status").getAsString());
    }

    @Test
    public void anyMatchingPolicyInTheSameDirectionCanTrigger() {
        AutoScalePolicyTO first = policy(1, AutoScalePolicy.Action.SCALEUP, Condition.Operator.GT, 99, 0.8);
        AutoScalePolicyTO second = policy(2, AutoScalePolicy.Action.SCALEUP, Condition.Operator.GT, 70, 0.8);
        Mockito.when(group.getPolicies()).thenReturn(Arrays.asList(first, second));
        Assert.assertEquals(AutoScalePolicy.Action.SCALEUP, manager.evaluateAutoScalePolicies(group));
    }

    @Test
    public void conditionsWithinOnePolicyAreAndNotOr() {
        AutoScalePolicyTO up = policy(1, AutoScalePolicy.Action.SCALEUP, Condition.Operator.GT, 70, 0.8);
        AutoScalePolicyTO other = policy(2, AutoScalePolicy.Action.SCALEUP, Condition.Operator.GT, 90, 0.8);
        List<ConditionTO> combined = Arrays.asList(up.getConditions().get(0), other.getConditions().get(0));
        Mockito.when(up.getConditions()).thenReturn(combined);
        List<AutoScaleVmGroupStatisticsVO> second = statistics.listByVmGroupAndPolicyAndCounter(1L, 2L, 2L, new Date(0));
        Mockito.when(statistics.listByVmGroupAndPolicyAndCounter(Mockito.eq(1L), Mockito.eq(1L), Mockito.eq(2L), Mockito.any())).thenReturn(second);
        Mockito.when(group.getPolicies()).thenReturn(Collections.singletonList(up));
        Assert.assertNull(manager.evaluateAutoScalePolicies(group));
    }

    @Test
    public void uncertainScaleUpPreventsScaleDownFromMissingData() {
        AutoScalePolicyTO up = policy(1, AutoScalePolicy.Action.SCALEUP, Condition.Operator.GT, 90, 0.8);
        AutoScalePolicyTO down = policy(2, AutoScalePolicy.Action.SCALEDOWN, Condition.Operator.LT, 90, 0.8);
        Mockito.when(statistics.listByVmGroupAndPolicyAndCounter(Mockito.eq(1L), Mockito.eq(1L), Mockito.eq(1L), Mockito.any())).thenReturn(Collections.emptyList());
        Mockito.when(group.getPolicies()).thenReturn(Arrays.asList(up, down));
        Assert.assertNull(manager.evaluateAutoScalePolicies(group));
        Assert.assertEquals("SCALEUP_UNCERTAIN", result().get("status").getAsString());
    }

    @Test
    public void groupCooldownUsesTheLongestPolicyInBothDirectionsAndExactBoundary() {
        AutoScalePolicyTO up = policy(1, AutoScalePolicy.Action.SCALEUP, Condition.Operator.GT, 70, 0.8);
        AutoScalePolicyTO down = policy(2, AutoScalePolicy.Action.SCALEDOWN, Condition.Operator.LT, 90, 0.8);
        Mockito.when(down.getQuietTime()).thenReturn(30);
        Mockito.when(group.getPolicies()).thenReturn(Arrays.asList(up, down));
        Mockito.when(row.getLastScaled()).thenReturn(new Date(40000));
        Assert.assertNull(manager.evaluateAutoScalePolicies(group));
        Assert.assertEquals("COOLDOWN", result().get("status").getAsString());
        Mockito.doReturn(69999L).when(manager).currentTimeMillis(); Assert.assertNull(manager.evaluateAutoScalePolicies(group));
        Mockito.doReturn(70000L).when(manager).currentTimeMillis(); Assert.assertEquals(AutoScalePolicy.Action.SCALEUP, manager.evaluateAutoScalePolicies(group));
    }

    @Test
    public void sharedPolicyLastQuietTimeDoesNotThrottleAnotherGroup() {
        AutoScalePolicyTO up = policy(1, AutoScalePolicy.Action.SCALEUP, Condition.Operator.GT, 70, 0.8);
        Mockito.when(up.getLastQuietTime()).thenReturn(new Date(59999));
        Mockito.when(group.getPolicies()).thenReturn(Collections.singletonList(up));
        Assert.assertEquals(AutoScalePolicy.Action.SCALEUP, manager.evaluateAutoScalePolicies(group));
    }

    @Test
    public void inactiveAndMembershipMarkersBlockOtherwiseMatchingPolicies() {
        AutoScalePolicyTO up = policy(1, AutoScalePolicy.Action.SCALEUP, Condition.Operator.GT, 70, 0.8);
        Mockito.when(group.getPolicies()).thenReturn(Collections.singletonList(up));
        AutoScaleVmGroupStatisticsVO invalid = new AutoScaleVmGroupStatisticsVO(1, 1, 1, 1L, ResourceTag.ResourceObjectType.UserVm, AutoScaleValueType.INSTANT_VM, new Date(30000));
        Mockito.when(statistics.listInactiveByVmGroupAndPolicy(Mockito.eq(1L), Mockito.eq(1L), Mockito.any())).thenReturn(Collections.singletonList(invalid));
        Assert.assertNull(manager.evaluateAutoScalePolicies(group));
        Assert.assertEquals("BLOCKED", result().getAsJsonArray("policies").get(0).getAsJsonObject().get("status").getAsString());
        Mockito.when(statistics.listInactiveByVmGroupAndPolicy(Mockito.eq(1L), Mockito.eq(1L), Mockito.any())).thenReturn(Collections.emptyList());
        Mockito.when(statistics.listDummyRecordsByVmGroup(Mockito.eq(1L), Mockito.any())).thenReturn(Collections.singletonList(invalid));
        Assert.assertNull(manager.evaluateAutoScalePolicies(group));
    }

    @Test
    public void maximumAndMinimumBoundsDoNotRequestExtraActions() {
        AutoScalePolicyTO up = policy(1, AutoScalePolicy.Action.SCALEUP, Condition.Operator.GT, 70, 0.8);
        Mockito.when(group.getPolicies()).thenReturn(Collections.singletonList(up));
        Mockito.when(group.getMaxMembers()).thenReturn(2);
        Assert.assertNull(manager.evaluateAutoScalePolicies(group));
        Assert.assertEquals("MAX_MEMBERS", result().get("status").getAsString());
        AutoScalePolicyTO down = policy(2, AutoScalePolicy.Action.SCALEDOWN, Condition.Operator.LT, 90, 0.8);
        Mockito.when(group.getPolicies()).thenReturn(Collections.singletonList(down));
        Mockito.when(group.getMinMembers()).thenReturn(2);
        Assert.assertNull(manager.evaluateAutoScalePolicies(group));
        Assert.assertEquals("MIN_MEMBERS", result().get("status").getAsString());
    }
    @Test
    public void monitorWaitsForIntervalSecondsAndAcceptsExactBoundary() {
        Mockito.when(row.getId()).thenReturn(1L);
        Mockito.when(row.getInterval()).thenReturn(10);
        Mockito.when(row.getLastInterval()).thenReturn(new Date(0));
        Mockito.doReturn(true).when(manager).checkAsGroupMaxAndMinMembers(row);
        Mockito.doReturn(9999L).when(manager).currentTimeMillis();
        manager.monitorAutoscalingGroup(row);
        Mockito.verify(groups, Mockito.never()).updateLastInterval(Mockito.anyLong(), Mockito.any());
        Mockito.doReturn(10000L).when(manager).currentTimeMillis();
        manager.monitorAutoscalingGroup(row);
        Mockito.verify(groups).updateLastInterval(Mockito.eq(1L), Mockito.any());
    }
}
