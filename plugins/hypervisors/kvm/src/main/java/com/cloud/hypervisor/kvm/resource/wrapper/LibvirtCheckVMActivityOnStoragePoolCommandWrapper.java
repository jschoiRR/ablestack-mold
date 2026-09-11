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

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CheckVMActivityOnStoragePoolCommand;
import com.cloud.agent.api.CheckVMActivityOnStoragePoolAnswer;
import com.cloud.agent.api.CheckVMActivityOnStoragePoolAnswer.ActivityState;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.cloud.agent.api.to.StorageFilerTO;
import com.cloud.hypervisor.kvm.resource.KVMHABase.HAStoragePool;
import com.cloud.hypervisor.kvm.storage.KVMStoragePool;
import com.cloud.hypervisor.kvm.storage.KVMStoragePoolManager;
import com.cloud.hypervisor.kvm.resource.KVMHAMonitor;
import com.cloud.hypervisor.kvm.resource.KVMHAVMActivityChecker;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.CommandWrapper;
import com.cloud.resource.ResourceWrapper;
import com.cloud.storage.Storage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@ResourceWrapper(handles = CheckVMActivityOnStoragePoolCommand.class)
public final class LibvirtCheckVMActivityOnStoragePoolCommandWrapper extends CommandWrapper<CheckVMActivityOnStoragePoolCommand, Answer, LibvirtComputingResource> {

    @Override
    public Answer execute(final CheckVMActivityOnStoragePoolCommand command, final LibvirtComputingResource libvirtComputingResource) {
        final ExecutorService executors = Executors.newSingleThreadExecutor();
        Future<Boolean> future = null;
        try {
            final KVMHAMonitor monitor = libvirtComputingResource.getMonitor();
            final StorageFilerTO pool = command.getPool();
            final KVMStoragePoolManager storagePoolMgr = libvirtComputingResource.getStoragePoolMgr();
            HAStoragePool haStoragePool = getMonitoredStoragePool(monitor, pool);
            if (haStoragePool == null) {
                return new CheckVMActivityOnStoragePoolAnswer(command, ActivityState.UNKNOWN, "HA pool not found");
            }
            KVMStoragePool primaryPool = storagePoolMgr.getStoragePool(pool.getType(), pool.getUuid());
            if (primaryPool == null || !primaryPool.isPoolSupportHA()) {
                return new CheckVMActivityOnStoragePoolAnswer(command, ActivityState.UNKNOWN, "Unsupported storage");
            }
            String vmActivityCheckPath = getVmActivityCheckPath(libvirtComputingResource, pool);
            final KVMHAVMActivityChecker ha = new KVMHAVMActivityChecker(haStoragePool, command.getHost(), command.getVolumeList(),
                    vmActivityCheckPath, command.getSuspectTimeInSeconds(), command.getActivityTimeoutSeconds());
            future = executors.submit(ha);
            final Boolean result = future.get(command.getActivityTimeoutSeconds(), TimeUnit.SECONDS);
            return new CheckVMActivityOnStoragePoolAnswer(command,
                    result == null ? ActivityState.UNKNOWN : result ? ActivityState.ALIVE : ActivityState.DEAD,
                    "VM activity check completed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CheckVMActivityOnStoragePoolAnswer(command, ActivityState.UNKNOWN, "VM activity check interrupted");
        } catch (TimeoutException | ExecutionException | RuntimeException e) {
            logger.warn("Unable to establish VM activity", e);
            return new CheckVMActivityOnStoragePoolAnswer(command, ActivityState.UNKNOWN, "VM activity check failed or timed out");
        } finally {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
            executors.shutdownNow();
        }
    }

    protected HAStoragePool getMonitoredStoragePool(final KVMHAMonitor monitor, final StorageFilerTO pool) {
        if (Storage.StoragePoolType.NetworkFilesystem == pool.getType()) {
            return monitor.getStoragePool(pool.getUuid());
        } else if (Storage.StoragePoolType.SharedMountPoint == pool.getType()) {
            return monitor.getGfsStoragePool(pool.getUuid());
        } else if (Storage.StoragePoolType.RBD == pool.getType()) {
            return monitor.getRbdStoragePool(pool.getUuid());
        } else if (Storage.StoragePoolType.CLVM == pool.getType()) {
            return monitor.getClvmStoragePool(pool.getUuid());
        }
        return null;
    }

    protected String getVmActivityCheckPath(final LibvirtComputingResource libvirtComputingResource, final StorageFilerTO pool) {
        if (Storage.StoragePoolType.NetworkFilesystem == pool.getType()) {
            return libvirtComputingResource.getVmActivityCheckPath();
        } else if (Storage.StoragePoolType.SharedMountPoint == pool.getType()) {
            return libvirtComputingResource.getVmActivityCheckPathGfs();
        } else if (Storage.StoragePoolType.RBD == pool.getType()) {
            return libvirtComputingResource.getVmActivityCheckPathRbd();
        } else if (Storage.StoragePoolType.CLVM == pool.getType()) {
            return libvirtComputingResource.getVmActivityCheckPathClvm();
        }
        return "";
    }
}
