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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import com.cloud.network.router.VirtualRouterAutoScale.AutoScaleValueType;

/** Validates a complete, fresh observation window before evaluating a policy. */
public final class AutoScaleMetricWindow {
    private AutoScaleMetricWindow() { }

    public static class Result {
        private final String reason;
        private final List<Double> points;
        private final Long collectedAt;
        private final int samples;

        Result(String reason, List<Double> points, Long collectedAt, int samples) {
            this.reason = reason;
            this.points = points;
            this.collectedAt = collectedAt;
            this.samples = samples;
        }

        public String getReason() { return reason; }
        public List<Double> getPoints() { return points; }
        public Long getCollectedAt() { return collectedAt; }
        public int getSamples() { return samples; }
        public Double getValue() {
            return points.isEmpty() ? null : points.stream().mapToDouble(Double::doubleValue).average().getAsDouble();
        }
        public boolean isReady() { return reason == null; }
    }

    private static Result blocked(String reason) {
        return new Result(reason, new ArrayList<>(), null, 0);
    }

    public static boolean compare(double value, Condition.Operator operator, long threshold) {
        if (!Double.isFinite(value) || operator == null) { return false; }
        switch (operator) {
            case EQ: return value == threshold;
            case GE: return value >= threshold;
            case GT: return value > threshold;
            case LE: return value <= threshold;
            case LT: return value < threshold;
            default: return false;
        }
    }

    public static Result evaluate(List<AutoScaleVmGroupStatisticsVO> records, Set<Long> expectedResources,
                                  int duration, int interval, long now, double multiplier, boolean divideByMembers, int members) {
        if (duration <= 0 || interval <= 0 || duration < interval || !Double.isFinite(multiplier) || multiplier <= 0) {
            return blocked("INVALID_CONFIGURATION");
        }
        if (expectedResources.isEmpty() || (divideByMembers && members <= 0)) { return blocked("NO_RESOURCES"); }
        if (records == null || records.isEmpty()) { return blocked("NO_SAMPLES"); }
        long intervalMs = TimeUnit.SECONDS.toMillis(interval);
        long windowStart = now - TimeUnit.SECONDS.toMillis(duration);
        long freshLimit = intervalMs * 2;
        Map<Long, TreeMap<Long, AutoScaleVmGroupStatisticsVO>> byResource = new HashMap<>();
        AutoScaleValueType valueType = null;
        List<AutoScaleVmGroupStatisticsVO> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparing(AutoScaleVmGroupStatisticsVO::getCreated, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingLong(AutoScaleVmGroupStatisticsVO::getId));
        for (AutoScaleVmGroupStatisticsVO record : sorted) {
            if (!expectedResources.contains(record.getResourceId())) { continue; }
            if (record.getCreated() == null || record.getCreated().getTime() > now || record.getRawValue() == null ||
                    !Double.isFinite(record.getRawValue()) || record.getRawValue() < 0 ||
                    record.getState() != AutoScaleVmGroupStatisticsVO.State.ACTIVE || record.getValueType() == null) {
                return blocked("INVALID_SAMPLE");
            }
            if (valueType != null && valueType != record.getValueType()) { return blocked("COUNTER_RESET"); }
            valueType = record.getValueType();
            byResource.computeIfAbsent(record.getResourceId(), id -> new TreeMap<>()).putIfAbsent(record.getCreated().getTime(), record);
        }
        if (!byResource.keySet().containsAll(expectedResources)) { return blocked("MISSING_RESOURCE"); }
        boolean aggregated = valueType == AutoScaleValueType.AGGREGATED_VM || valueType == AutoScaleValueType.AGGREGATED_VM_GROUP;
        Long collectedAt = null;
        boolean fullDuration = true;
        for (TreeMap<Long, AutoScaleVmGroupStatisticsVO> resource : byResource.values()) {
            if (now - resource.lastKey() > freshLimit) { return blocked("STALE_SAMPLE"); }
            collectedAt = collectedAt == null ? resource.lastKey() : Math.min(collectedAt, resource.lastKey());
            Long previousTime = null;
            Double previousValue = null;
            for (Map.Entry<Long, AutoScaleVmGroupStatisticsVO> entry : resource.entrySet()) {
                if (previousTime != null && entry.getKey() - previousTime > freshLimit) { return blocked("MISSING_SAMPLE"); }
                if (aggregated && previousValue != null && entry.getValue().getRawValue() < previousValue) { return blocked("COUNTER_RESET"); }
                previousTime = entry.getKey();
                previousValue = entry.getValue().getRawValue();
            }
        }
        // Cover a full duration ending at the latest common collection, not at the later evaluation job time.
        windowStart = collectedAt - TimeUnit.SECONDS.toMillis(duration);
        for (TreeMap<Long, AutoScaleVmGroupStatisticsVO> resource : byResource.values()) {
            if (resource.floorKey(windowStart) == null) { fullDuration = false; }
        }
        // One VM/router is the reference timeline; match other resources from the same collection round.
        TreeMap<Long, AutoScaleVmGroupStatisticsVO> reference = byResource.get(expectedResources.stream().min(Long::compareTo).get());
        Long anchor = reference.floorKey(windowStart);
        long firstTime = anchor == null ? reference.firstKey() : anchor;
        List<Double> points = new ArrayList<>();
        Map<Long, AutoScaleVmGroupStatisticsVO> previous = new HashMap<>();
        int samples = 0;
        for (Long time : reference.tailMap(firstTime, true).keySet()) {
            double sum = 0;
            boolean baseline = aggregated && previous.isEmpty();
            for (Long resourceId : expectedResources) {
                TreeMap<Long, AutoScaleVmGroupStatisticsVO> resource = byResource.get(resourceId);
                Map.Entry<Long, AutoScaleVmGroupStatisticsVO> before = resource.floorEntry(time);
                Map.Entry<Long, AutoScaleVmGroupStatisticsVO> after = resource.ceilingEntry(time);
                Map.Entry<Long, AutoScaleVmGroupStatisticsVO> nearest = before;
                if (nearest == null || (after != null && after.getKey() - time < time - nearest.getKey())) { nearest = after; }
                if (nearest == null || Math.abs(nearest.getKey() - time) > Math.max(1000L, intervalMs / 2)) {
                    return blocked("MISSING_RESOURCE");
                }
                AutoScaleVmGroupStatisticsVO record = nearest.getValue();
                if (aggregated && !baseline) {
                    AutoScaleVmGroupStatisticsVO old = previous.get(resourceId);
                    long elapsed = record.getCreated().getTime() - old.getCreated().getTime();
                    if (elapsed <= 0) { return blocked("MISSING_SAMPLE"); }
                    sum += (record.getRawValue() - old.getRawValue()) * 1000d / elapsed;
                } else {
                    sum += record.getRawValue();
                }
                previous.put(resourceId, record);
                samples++;
            }
            if (!baseline) {
                double point = sum * multiplier / expectedResources.size();
                if (divideByMembers) { point /= members; }
                if (!Double.isFinite(point)) { return blocked("INVALID_SAMPLE"); }
                points.add(point);
            }
        }
        // The anchor establishes that the whole duration has elapsed; at least two rounds are required.
        String reason = !fullDuration || reference.tailMap(firstTime, true).size() < 2 || points.isEmpty() ? "INSUFFICIENT_DURATION" : null;
        return new Result(reason, points, collectedAt, samples);
    }
}
