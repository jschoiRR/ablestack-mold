/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.cloud.hypervisor.kvm.resource.wrapper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.cloudstack.utils.process.ProcessResult;
import org.junit.Test;

import com.cloud.agent.api.PcsFenceHostCommand;

public class LibvirtPcsFenceHostCommandWrapperTest {
    private final LibvirtPcsFenceHostCommandWrapper wrapper = new LibvirtPcsFenceHostCommandWrapper();
    private final PcsFenceHostCommand request = new PcsFenceHostCommand("mold-ha", "node-a", "node-b", System.currentTimeMillis() + 20000);

    @Test
    public void returnsSuccessOnlyForNewOffRequest() {
        assertTrue(wrapper.fence(request, (args, timeout) -> {
            if ("/usr/sbin/stonith_admin".equals(args.get(0))) {
                assertTrue(args.contains("--fence=node-a"));
                assertTrue(args.contains("--tolerance=0"));
            }
            return commandResult(args, 0, 0);
        }).getResult());
    }

    @Test
    public void rejectsUnconfirmedOff() {
        assertFalse(wrapper.fence(request, (args, timeout) -> commandResult(args, 0, 1)).getResult());
    }

    @Test
    public void doesNotFenceWithoutQuorum() {
        assertFalse(wrapper.fence(request, (args, timeout) -> {
            assertFalse(args.contains("/usr/sbin/stonith_admin"));
            return commandResult(args, 2, 0);
        }).getResult());
    }

    @Test
    public void doesNotFenceForWrongCluster() {
        final PcsFenceHostCommand wrongCluster = new PcsFenceHostCommand("other-cluster", "node-a", "node-b", request.getExpiresAtMillis());
        assertFalse(wrapper.fence(wrongCluster, (args, timeout) -> {
            assertFalse(args.contains("/usr/sbin/stonith_admin"));
            return commandResult(args, 0, 0);
        }).getResult());
    }

    @Test
    public void discardsExpiredFenceBeforeRunningCommands() {
        final PcsFenceHostCommand expired = new PcsFenceHostCommand("mold-ha", "node-a", "node-b", System.currentTimeMillis() - 1);
        assertFalse(wrapper.fence(expired, (args, timeout) -> {
            throw new AssertionError("Expired request ran " + args);
        }).getResult());
    }

    private ProcessResult commandResult(List<String> args, int quorumCode, int fenceCode) {
        switch (args.get(0)) {
            case "/usr/sbin/corosync-cmapctl":
                return new ProcessResult("mold-ha\n", "", 0);
            case "/usr/sbin/crm_node":
                return new ProcessResult("node-b\n", "", 0);
            case "/usr/sbin/corosync-quorumtool":
                return new ProcessResult("", "", quorumCode);
            case "/usr/sbin/stonith_admin":
                return new ProcessResult("", "", fenceCode);
            default:
                throw new AssertionError(args);
        }
    }
}
