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
package com.cloud.hypervisor.kvm.resource;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.joda.time.Duration;
import com.cloud.storage.Storage.StoragePoolType;
import java.util.concurrent.Callable;

import com.cloud.agent.api.to.HostTO;

public class KVMHAChecker extends KVMHABase implements Callable<Boolean> {
    private List<HAStoragePool> storagePools;
    private List<HAStoragePool> gfsStoragePools;
    private List<HAStoragePool> rbdStoragePools;
    private List<HAStoragePool> clvmStoragePools;
    private HostTO host;
    private boolean reportFailureIfOneStorageIsDown;
    private String volumeList;
    private long timeoutSeconds;

    public KVMHAChecker(List<HAStoragePool> pools, List<HAStoragePool> gfspools, List<HAStoragePool> rbdpools, List<HAStoragePool> clvmpools, HostTO host, boolean reportFailureIfOneStorageIsDown, String volumeList) {
        this(pools, gfspools, rbdpools, clvmpools, host, reportFailureIfOneStorageIsDown, volumeList, 20L);
    }

    public KVMHAChecker(List<HAStoragePool> pools, List<HAStoragePool> gfspools, List<HAStoragePool> rbdpools,
            List<HAStoragePool> clvmpools, HostTO host, boolean reportFailureIfOneStorageIsDown, String volumeList, long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.storagePools = pools;
        this.gfsStoragePools = gfspools;
        this.rbdStoragePools = rbdpools;
        this.clvmStoragePools = clvmpools;
        this.host = host;
        this.reportFailureIfOneStorageIsDown = reportFailureIfOneStorageIsDown;
        this.volumeList = volumeList;
    }

    // True/false are determined heartbeat observations under the configured pool
    // policy; null means the available witnesses cannot determine the result.
    @Override
    public Boolean checkingHeartBeat() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        List<HAStoragePool> allPools = new ArrayList<>();
        allPools.addAll(storagePools);
        allPools.addAll(gfsStoragePools);
        allPools.addAll(rbdStoragePools);
        allPools.addAll(clvmStoragePools);
        boolean unknown = allPools.isEmpty();
        for (HAStoragePool pool : allPools) {
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMillis <= 0 || Thread.currentThread().isInterrupted()) {
                return null;
            }
            Boolean active = pool.getPool().getType() == StoragePoolType.RBD
                    ? pool.getPool().checkingHeartBeatRBD(pool, host, volumeList, Duration.millis(remainingMillis))
                    : pool.getPool().checkingHeartBeat(pool, host, Duration.millis(remainingMillis));
            if (active == null) {
                unknown = true;
            } else if (reportFailureIfOneStorageIsDown && !active) {
                return false;
            } else if (!reportFailureIfOneStorageIsDown && active) {
                return true;
            }
        }
        return unknown ? null : reportFailureIfOneStorageIsDown;
    }

    @Override
    public Boolean call() throws Exception {
        return checkingHeartBeat();
    }
}
