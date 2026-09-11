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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CheckOnHostCommand;
import com.cloud.agent.api.CheckOnHostAnswer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.cloud.agent.api.to.HostTO;
import com.cloud.hypervisor.kvm.resource.KVMHABase.HAStoragePool;
import com.cloud.hypervisor.kvm.resource.KVMHAChecker;
import com.cloud.hypervisor.kvm.resource.KVMHAMonitor;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;

@ResourceWrapper(handles =  CheckOnHostCommand.class)
public final class LibvirtCheckOnHostCommandWrapper extends CommandWrapper<CheckOnHostCommand, Answer, LibvirtComputingResource> {
    @Override
    public Answer execute(final CheckOnHostCommand command, final LibvirtComputingResource libvirtComputingResource) {
        final ExecutorService executors = Executors.newSingleThreadExecutor();
        final KVMHAMonitor monitor = libvirtComputingResource.getMonitor();

        final List<HAStoragePool> pools = monitor.getStoragePools();
        final List<HAStoragePool> gfspools = monitor.getGfsStoragePools();
        final List<HAStoragePool> rbdpools = monitor.getRbdStoragePools();
        final List<HAStoragePool> clvmpools = monitor.getClvmStoragePools();
        final HostTO host = command.getHost();
        final String volumeList = command.getVolumeList();
        final long timeoutSeconds = command.getWait() > 0 ? command.getWait() : 20L;
        final KVMHAChecker ha = new KVMHAChecker(pools, gfspools, rbdpools, clvmpools, host,
                command.isCheckFailedOnOneStorage(), volumeList, timeoutSeconds);

        final Future<Boolean> future = executors.submit(ha);
        try {
            final Boolean result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            return CheckOnHostAnswer.forKvm(command, result, "Storage heartbeat observation");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return CheckOnHostAnswer.forKvm(command, null, "Heartbeat check interrupted");
        } catch (final ExecutionException | TimeoutException e) {
            return CheckOnHostAnswer.forKvm(command, null, "Heartbeat check failed or timed out");
        } finally {
            if (!future.isDone()) {
                future.cancel(true);
            }
            executors.shutdownNow();
        }
    }
}
