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

import java.util.ArrayList;
import java.util.List;
import org.mockito.Mockito;
import com.cloud.hypervisor.kvm.resource.KVMHABase.HAStoragePool;
import com.cloud.hypervisor.kvm.storage.KVMStoragePool;
import com.cloud.agent.api.to.HostTO;
import com.cloud.storage.Storage.StoragePoolType;
import org.joda.time.Duration;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

public class KVMHACheckerTest {
    private HAStoragePool pool(Boolean active) {
        HAStoragePool monitored = mock(HAStoragePool.class);
        KVMStoragePool pool = mock(KVMStoragePool.class);
        when(monitored.getPool()).thenReturn(pool);
        when(pool.getType()).thenReturn(StoragePoolType.NetworkFilesystem);
        when(pool.hasHeartBeat(eq(monitored), any(HostTO.class), any(Duration.class))).thenReturn(active);
        return monitored;
    }
    private Boolean check(boolean anyFailure, Boolean first, Boolean second) {
        return new KVMHAChecker(Collections.singletonList(pool(first)), Collections.singletonList(pool(second)),
                Collections.emptyList(), Collections.emptyList(), mock(HostTO.class), anyFailure, "", 5).hasHeartBeat();
    }
    @Test public void failureInEarlierPoolTypeIsNotOverwritten() { assertEquals(Boolean.FALSE, check(true, false, true)); }
    @Test public void anyAliveSatisfiesAllFailedPolicy() { assertEquals(Boolean.TRUE, check(false, true, false)); }
    @Test public void unknownDoesNotProveAllPoolsAlive() { assertNull(check(true, true, null)); }
    @Test public void unknownDoesNotProveAllPoolsDead() { assertNull(check(false, false, null)); }
    @Test public void allFailuresAreKnownInactive() { assertEquals(Boolean.FALSE, check(false, false, false)); }
    @Test public void noPoolsIsUnknown() {
        assertNull(new KVMHAChecker(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), mock(HostTO.class), false, "").hasHeartBeat());
    }
    @Test
    public void combinesEveryStorageFamilyWithoutOverwritingEarlierResults() {
        for (boolean reportOneFailure : new boolean[]{false, true}) {
            for (int mask = 0; mask < 16; mask++) {
                HostTO host = Mockito.mock(HostTO.class);
                List<List<HAStoragePool>> groups = new ArrayList<>();
                for (int family = 0; family < 4; family++) {
                    HAStoragePool pool = Mockito.mock(HAStoragePool.class);
                    KVMStoragePool storage = Mockito.mock(KVMStoragePool.class);
                    Mockito.when(pool.getPool()).thenReturn(storage);
                    boolean alive = (mask & (1 << family)) != 0;
                    if (family == 2) {
                        Mockito.when(storage.getType()).thenReturn(StoragePoolType.RBD);
                        Mockito.when(storage.checkingHeartBeatRBD(eq(pool), eq(host), eq("volume-a,volume-b"), any(Duration.class))).thenReturn(alive);
                    } else {
                        Mockito.when(storage.hasHeartBeat(eq(pool), eq(host), any(Duration.class))).thenReturn(alive);
                    }
                    groups.add(List.of(pool));
                }
                KVMHAChecker checker = new KVMHAChecker(groups.get(0), groups.get(1), groups.get(2), groups.get(3),
                        host, reportOneFailure, "volume-a,volume-b");
                assertEquals("mask=" + mask + ", reportOneFailure=" + reportOneFailure,
                        reportOneFailure ? mask == 15 : mask != 0, checker.hasHeartBeat());
            }
        }
    }

    @Test
    public void noStorageObservationIsUndetermined() {
        for (boolean reportOneFailure : new boolean[]{false, true}) {
            assertNull(new KVMHAChecker(List.of(), List.of(), List.of(), List.of(), null, reportOneFailure, null).hasHeartBeat());
        }
    }

    @Test
    public void unknownStorageReplyIsNotProofOfDeath() {
        HostTO host = Mockito.mock(HostTO.class);
        HAStoragePool pool = Mockito.mock(HAStoragePool.class);
        KVMStoragePool storage = Mockito.mock(KVMStoragePool.class);
        Mockito.when(pool.getPool()).thenReturn(storage);
        Mockito.when(storage.hasHeartBeat(eq(pool), eq(host), any(Duration.class))).thenReturn(null);
        for (boolean reportOneFailure : new boolean[]{false, true}) {
            assertNull(new KVMHAChecker(List.of(pool), List.of(), List.of(), List.of(), host, reportOneFailure, null).hasHeartBeat());
        }
    }
}
