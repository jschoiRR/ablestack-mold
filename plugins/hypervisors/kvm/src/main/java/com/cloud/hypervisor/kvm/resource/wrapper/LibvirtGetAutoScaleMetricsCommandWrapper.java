//
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
//

package com.cloud.hypervisor.kvm.resource.wrapper;

import java.util.ArrayList;
import java.util.List;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.routing.GetAutoScaleMetricsAnswer;
import com.cloud.agent.api.routing.GetAutoScaleMetricsCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.network.router.VirtualRouterAutoScale;
import com.cloud.network.router.VirtualRouterAutoScale.AutoScaleMetrics;
import com.cloud.network.router.VirtualRouterAutoScale.AutoScaleMetricsValue;
import com.cloud.network.router.VirtualRouterAutoScale.AutoScaleValueType;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

@ResourceWrapper(handles = GetAutoScaleMetricsCommand.class)
public class LibvirtGetAutoScaleMetricsCommandWrapper extends CommandWrapper<GetAutoScaleMetricsCommand, Answer, LibvirtComputingResource> {
    static final String NETWORK_USAGE_GET_OPTION = "get";

    @Override
    public Answer execute(final GetAutoScaleMetricsCommand command, final LibvirtComputingResource resource) {
        long[] network = null;
        Long connections = null;
        // Read the raw result here: the generic usage methods turn a failed read into zero.
        try {
            String raw = command.isForVpc() ? resource.configureVPCNetworkUsage(command.getPrivateIP(), command.getPublicIP(), NETWORK_USAGE_GET_OPTION, null) :
                    resource.networkUsage(command.getPrivateIP(), NETWORK_USAGE_GET_OPTION, null, command.getPublicIP());
            network = parseNetwork(raw);
        } catch (RuntimeException ignored) { /* Missing metrics are persisted as INACTIVE by the management server. */ }
        try {
            String raw = resource.getHaproxyStats(command.getPrivateIP(), command.getPublicIP(), command.getPort());
            if (raw != null && !raw.trim().isEmpty()) {
                long value = Long.parseLong(raw.split(",")[0].trim());
                if (value >= 0) { connections = value; }
            }
        } catch (RuntimeException ignored) { /* Do not report an unavailable counter as zero. */ }
        List<AutoScaleMetricsValue> values = new ArrayList<>();
        for (AutoScaleMetrics metrics : command.getMetrics()) {
            switch (metrics.getCounter()) {
                case NETWORK_RECEIVED_AVERAGE_MBPS:
                    if (network != null) { values.add(new AutoScaleMetricsValue(metrics, AutoScaleValueType.AGGREGATED_VM_GROUP, (double) network[1] / VirtualRouterAutoScale.MBITS_TO_BYTES)); }
                    break;
                case NETWORK_TRANSMIT_AVERAGE_MBPS:
                    if (network != null) { values.add(new AutoScaleMetricsValue(metrics, AutoScaleValueType.AGGREGATED_VM_GROUP, (double) network[0] / VirtualRouterAutoScale.MBITS_TO_BYTES)); }
                    break;
                case LB_AVERAGE_CONNECTIONS:
                    if (connections != null) { values.add(new AutoScaleMetricsValue(metrics, AutoScaleValueType.INSTANT_VM_GROUP, connections.doubleValue())); }
                    break;
                default: break;
            }
        }
        return new GetAutoScaleMetricsAnswer(command, true, values);
    }

    protected long[] parseNetwork(String raw) {
        if (raw == null || raw.trim().isEmpty()) { return null; }
        String[] parts = raw.trim().split(":");
        if (parts.length < 2 || parts.length % 2 != 0) { return null; }
        long[] totals = new long[2];
        try {
            for (int i = 0; i < parts.length; i++) {
                long value = Long.parseLong(parts[i].trim());
                if (value < 0) { return null; }
                totals[i % 2] = Math.addExact(totals[i % 2], value);
            }
        } catch (RuntimeException ignored) { return null; }
        return totals;
    }
}
