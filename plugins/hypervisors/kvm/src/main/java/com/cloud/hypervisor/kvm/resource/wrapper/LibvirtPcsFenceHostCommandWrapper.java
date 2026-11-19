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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.apache.cloudstack.utils.process.ProcessResult;
import org.apache.cloudstack.utils.process.ProcessRunner;
import org.joda.time.Duration;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.PcsFenceHostCommand;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

@ResourceWrapper(handles = PcsFenceHostCommand.class)
public final class LibvirtPcsFenceHostCommandWrapper extends CommandWrapper<PcsFenceHostCommand, Answer, LibvirtComputingResource> {
    @Override
    public Answer execute(PcsFenceHostCommand command, LibvirtComputingResource resource) {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return fence(command, new ProcessRunner(executor)::executeCommands);
        } finally {
            executor.shutdownNow();
        }
    }

    Answer fence(PcsFenceHostCommand command, BiFunction<List<String>, Duration, ProcessResult> run) {
        if (!PcsFenceHostCommand.isValidName(command.getClusterName())
                || !PcsFenceHostCommand.isValidName(command.getTargetNode())
                || !PcsFenceHostCommand.isValidName(command.getExecutorNode())
                || command.getTargetNode().equalsIgnoreCase(command.getExecutorNode())) {
            return new Answer(command, false, "Invalid Pacemaker fencing cluster or node mapping");
        }

        final ProcessResult cluster = runProbe(command, run,
                List.of("/usr/sbin/corosync-cmapctl", "-q", "-g", "totem.cluster_name"));
        if (!matchesOutput(cluster, command.getClusterName())) {
            return new Answer(command, false, "Could not verify the local Corosync cluster name");
        }
        final ProcessResult node = runProbe(command, run, List.of("/usr/sbin/crm_node", "--name"));
        if (!matchesOutput(node, command.getExecutorNode())) {
            return new Answer(command, false, "Could not verify the local Pacemaker node name");
        }
        final ProcessResult quorum = runProbe(command, run, List.of("/usr/sbin/corosync-quorumtool", "-s"));
        if (quorum == null || !quorum.isSuccess()) {
            return new Answer(command, false, "The local Corosync partition does not have confirmed quorum");
        }

        final long remainingMillis = remainingMillis(command);
        final long timeoutSeconds = TimeUnit.MILLISECONDS.toSeconds(remainingMillis) - 1;
        if (timeoutSeconds < 1 || Thread.currentThread().isInterrupted()) {
            return new Answer(command, false, "Pacemaker fencing request expired");
        }
        // --fence requests off. Zero tolerance requires a new result instead of a recent history entry.
        final ProcessResult result = run.apply(List.of("/usr/sbin/stonith_admin", "--fence=" + command.getTargetNode(),
                "--timeout=" + timeoutSeconds, "--tolerance=0"), Duration.millis(remainingMillis));
        if (!result.isSuccess() || remainingMillis(command) <= 0 || Thread.currentThread().isInterrupted()) {
            return new Answer(command, false, "Pacemaker did not confirm the requested power-off");
        }
        return new Answer(command, true, "Pacemaker confirmed power-off for " + command.getTargetNode());
    }

    private ProcessResult runProbe(PcsFenceHostCommand command, BiFunction<List<String>, Duration, ProcessResult> run, List<String> args) {
        final long timeout = Math.min(2000L, remainingMillis(command));
        return timeout > 0 && !Thread.currentThread().isInterrupted()
                ? run.apply(args, Duration.millis(timeout)) : null;
    }

    private boolean matchesOutput(ProcessResult result, String expected) {
        return result != null && result.isSuccess() && result.getStdOutput() != null && expected.equals(result.getStdOutput().trim());
    }

    private long remainingMillis(PcsFenceHostCommand command) {
        return Math.min(ProcessRunner.DEFAULT_MAX_TIMEOUT.getMillis(), command.getExpiresAtMillis() - System.currentTimeMillis());
    }
}
