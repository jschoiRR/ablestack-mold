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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.cloud.network.router.VirtualRouterAutoScale.AutoScaleValueType;
import com.cloud.server.ResourceTag;

public class AutoScaleMetricWindowTest {
    private final Set<Long> one = Collections.singleton(1L);
    private final Set<Long> two = new HashSet<>(Arrays.asList(1L, 2L));

    private AutoScaleVmGroupStatisticsVO sample(long vm, long time, double value, AutoScaleValueType type) {
        return new AutoScaleVmGroupStatisticsVO(1, 1, 1, vm, ResourceTag.ResourceObjectType.UserVm, value, type, new Date(time));
    }

    private List<AutoScaleVmGroupStatisticsVO> window(boolean aggregate) {
        List<AutoScaleVmGroupStatisticsVO> rows = new ArrayList<>();
        for (long t = 0; t <= 60000; t += 10000) {
            rows.add(sample(1, t, aggregate ? t / 1000d : 0.8, aggregate ? AutoScaleValueType.AGGREGATED_VM_GROUP : AutoScaleValueType.INSTANT_VM));
        }
        return rows;
    }

    private AutoScaleMetricWindow.Result evaluate(List<AutoScaleVmGroupStatisticsVO> rows, Set<Long> resources, long now) {
        return AutoScaleMetricWindow.evaluate(rows, resources, 60, 10, now, 100, false, resources.size());
    }

    @Test
    public void sixtySecondsBeforeExactlyAndAfterUseDecimalMilliseconds() {
        List<AutoScaleVmGroupStatisticsVO> rows = window(false);
        Assert.assertEquals("INSUFFICIENT_DURATION", evaluate(rows.subList(0, 6), one, 59999).getReason());
        Assert.assertTrue(evaluate(rows, one, 60000).isReady());
        Assert.assertTrue(evaluate(rows, one, 60001).isReady());
    }

    @Test
    public void averagesAllVmsAtEachRoundWithoutBiasFromDuplicates() {
        List<AutoScaleVmGroupStatisticsVO> rows = window(false);
        for (long t = 0; t <= 60000; t += 10000) { rows.add(sample(2, t, 0.2, AutoScaleValueType.INSTANT_VM)); }
        rows.add(rows.get(0));
        Collections.reverse(rows);
        AutoScaleMetricWindow.Result result = evaluate(rows, two, 60000);
        Assert.assertTrue(result.isReady());
        Assert.assertEquals(50d, result.getValue(), 0.001);
        Assert.assertEquals(14, result.getSamples());
    }

    @Test
    public void missingVmCannotBeTreatedAsZeroOrAValidPartialAverage() {
        Assert.assertEquals("MISSING_RESOURCE", evaluate(window(false), two, 60000).getReason());
    }

    @Test
    public void missingCollectionRoundFromOneVmCannotBeHiddenByOtherVms() {
        List<AutoScaleVmGroupStatisticsVO> rows = window(false);
        for (long t = 0; t <= 60000; t += 10000) {
            if (t != 30000) { rows.add(sample(2, t, 0.2, AutoScaleValueType.INSTANT_VM)); }
        }
        Assert.assertEquals("MISSING_RESOURCE", evaluate(rows, two, 60000).getReason());
    }

    @Test
    public void staleAndLargeGapSamplesAreBlocked() {
        Assert.assertEquals("STALE_SAMPLE", evaluate(window(false), one, 90000).getReason());
        List<AutoScaleVmGroupStatisticsVO> rows = window(false);
        rows.remove(2); rows.remove(2);
        Assert.assertEquals("MISSING_SAMPLE", evaluate(rows, one, 60000).getReason());
    }

    @Test
    public void futureNanNegativeAndInactiveValuesAreNotZero() {
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            List<AutoScaleVmGroupStatisticsVO> rows = window(false); rows.add(sample(1, 55000, value, AutoScaleValueType.INSTANT_VM));
            Assert.assertEquals("INVALID_SAMPLE", evaluate(rows, one, 60000).getReason());
        }
        List<AutoScaleVmGroupStatisticsVO> rows = window(false); rows.add(sample(1, 70000, 0, AutoScaleValueType.INSTANT_VM));
        Assert.assertEquals("INVALID_SAMPLE", evaluate(rows, one, 60000).getReason());
        rows = window(false); rows.get(3).setState(AutoScaleVmGroupStatisticsVO.State.INACTIVE);
        Assert.assertEquals("INVALID_SAMPLE", evaluate(rows, one, 60000).getReason());
    }

    @Test
    public void realZeroIsAValidObservation() {
        List<AutoScaleVmGroupStatisticsVO> rows = new ArrayList<>();
        for (long t = 0; t <= 60000; t += 10000) { rows.add(sample(1, t, 0, AutoScaleValueType.INSTANT_VM)); }
        AutoScaleMetricWindow.Result result = evaluate(rows, one, 60000);
        Assert.assertTrue(result.isReady()); Assert.assertEquals(0d, result.getValue(), 0);
    }

    @Test
    public void networkRateUsesElapsedSecondsAndCurrentMemberDenominator() {
        AutoScaleMetricWindow.Result result = AutoScaleMetricWindow.evaluate(window(true), one, 60, 10, 60000, 1, true, 2);
        Assert.assertTrue(result.isReady()); Assert.assertEquals(0.5, result.getValue(), 0.001);
    }

    @Test
    public void counterResetAndTypeChangeInvalidateTheWholeWindow() {
        List<AutoScaleVmGroupStatisticsVO> rows = window(true);
        rows.set(3, sample(1, 30000, 0, AutoScaleValueType.AGGREGATED_VM_GROUP));
        Assert.assertEquals("COUNTER_RESET", evaluate(rows, one, 60000).getReason());
        rows = window(false); rows.set(3, sample(1, 30000, 0.1, AutoScaleValueType.INSTANT_VM_GROUP));
        Assert.assertEquals("COUNTER_RESET", evaluate(rows, one, 60000).getReason());
    }

    @Test
    public void lbConnectionsAreAGroupTotalDividedByMembers() {
        List<AutoScaleVmGroupStatisticsVO> rows = new ArrayList<>();
        for (long t = 0; t <= 60000; t += 10000) { rows.add(sample(1, t, 12, AutoScaleValueType.INSTANT_VM_GROUP)); }
        Assert.assertEquals(4d, AutoScaleMetricWindow.evaluate(rows, one, 60, 10, 60000, 1, true, 3).getValue(), 0.001);
    }

    @Test
    public void fiveOperatorsIncludeExactBoundariesAndRejectNonFinite() {
        Assert.assertTrue(AutoScaleMetricWindow.compare(5, Condition.Operator.EQ, 5));
        Assert.assertTrue(AutoScaleMetricWindow.compare(5, Condition.Operator.GE, 5));
        Assert.assertTrue(AutoScaleMetricWindow.compare(5, Condition.Operator.LE, 5));
        Assert.assertFalse(AutoScaleMetricWindow.compare(5, Condition.Operator.GT, 5));
        Assert.assertFalse(AutoScaleMetricWindow.compare(5, Condition.Operator.LT, 5));
        Assert.assertTrue(AutoScaleMetricWindow.compare(6, Condition.Operator.GT, 5));
        Assert.assertTrue(AutoScaleMetricWindow.compare(4, Condition.Operator.LT, 5));
        Assert.assertFalse(AutoScaleMetricWindow.compare(Double.NaN, Condition.Operator.EQ, 0));
    }

    @Test
    public void averageAboveThresholdDoesNotHideAnInterruptedDuration() {
        List<AutoScaleVmGroupStatisticsVO> rows = window(false);
        rows.set(2, sample(1, 20000, 0, AutoScaleValueType.INSTANT_VM));
        AutoScaleMetricWindow.Result result = evaluate(rows, one, 60000);
        Assert.assertTrue(result.getValue() > 40);
        Assert.assertFalse(result.getPoints().stream().allMatch(v -> AutoScaleMetricWindow.compare(v, Condition.Operator.GT, 40)));
    }

    @Test
    public void integerTimeUpperBoundDoesNotOverflowMilliseconds() {
        long now = java.util.concurrent.TimeUnit.SECONDS.toMillis(Integer.MAX_VALUE) + 60000;
        List<AutoScaleVmGroupStatisticsVO> rows = Collections.singletonList(sample(1, now, 0.5, AutoScaleValueType.INSTANT_VM));
        Assert.assertEquals("INSUFFICIENT_DURATION", AutoScaleMetricWindow.evaluate(rows, one, Integer.MAX_VALUE, 10, now, 100, false, 1).getReason());
        Assert.assertEquals("INVALID_CONFIGURATION", AutoScaleMetricWindow.evaluate(rows, one, 5, 10, now, 100, false, 1).getReason());
        Assert.assertEquals("NO_SAMPLES", evaluate(Collections.emptyList(), one, 60000).getReason());
    }
}
