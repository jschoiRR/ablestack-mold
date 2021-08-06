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

package com.cloud.desktop.cluster.actionworkers;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.cloudstack.context.CallContext;
import org.apache.commons.collections.CollectionUtils;
import org.apache.log4j.Level;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.ManagementServerException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.desktop.cluster.DesktopCluster;
import com.cloud.desktop.cluster.DesktopClusterVO;
import com.cloud.desktop.cluster.DesktopClusterVmMapVO;
import com.cloud.desktop.cluster.DesktopClusterIpRangeVO;
import com.cloud.desktop.cluster.DesktopClusterVmMap;
import com.cloud.desktop.cluster.DesktopClusterManagerImpl;
import com.cloud.network.Network;
import com.cloud.network.IpAddress;
import com.cloud.network.dao.NetworkVO;
import com.cloud.user.AccountManager;
import com.cloud.uservm.UserVm;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.UserVmVO;

public class DesktopClusterDestroyWorker extends DesktopClusterResourceModifierActionWorker {

    @Inject
    protected AccountManager accountManager;

    private List<DesktopClusterVmMapVO> clusterVMs;

    public DesktopClusterDestroyWorker(final DesktopCluster desktopCluster, final DesktopClusterManagerImpl clusterManager) {
        super(desktopCluster, clusterManager);
    }

    private void validateClusterSate() {
        if (!(desktopCluster.getState().equals(DesktopCluster.State.Running)
                || desktopCluster.getState().equals(DesktopCluster.State.Stopped)
                || desktopCluster.getState().equals(DesktopCluster.State.Alert)
                || desktopCluster.getState().equals(DesktopCluster.State.Error)
                || desktopCluster.getState().equals(DesktopCluster.State.Destroying))) {
            String msg = String.format("Cannot perform delete operation on cluster : %s in state: %s",
            desktopCluster.getName(), desktopCluster.getState());
            LOGGER.warn(msg);
            throw new PermissionDeniedException(msg);
        }
    }

    private boolean destroyClusterVMs() {
        boolean vmDestroyed = true;
        if (!CollectionUtils.isEmpty(clusterVMs)) {
            for (DesktopClusterVmMapVO clusterVM : clusterVMs) {
                long vmID = clusterVM.getVmId();

                // delete only if VM exists and is not removed
                UserVmVO userVM = userVmDao.findById(vmID);
                if (userVM == null || userVM.isRemoved()) {
                    continue;
                }
                try {
                    UserVm vm = userVmService.destroyVm(vmID, true);
                    if (!userVmManager.expunge(userVM, CallContext.current().getCallingUserId(), CallContext.current().getCallingAccount())) {
                        LOGGER.warn(String.format("Unable to expunge VM %s : %s, destroying Desktop cluster will probably fail",
                            vm.getInstanceName() , vm.getUuid()));
                    }
                    desktopClusterVmMapDao.expunge(clusterVM.getId());
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info(String.format("Destroyed VM : %s as part of Desktop cluster : %s cleanup", vm.getDisplayName(), desktopCluster.getName()));
                    }
                } catch (ResourceUnavailableException | ConcurrentOperationException e) {
                    LOGGER.warn(String.format("Failed to destroy VM : %s part of the Desktop cluster : %s cleanup. Moving on with destroying remaining resources provisioned for the Desktop cluster", userVM.getDisplayName(), desktopCluster.getName()), e);
                    return false;
                }
            }
        }
        return vmDestroyed;
    }

    private boolean updateDesktopClusterEntryForGC() {
        DesktopClusterVO desktopClusterVO = desktopClusterDao.findById(desktopCluster.getId());
        desktopClusterVO.setCheckForGc(true);
        return desktopClusterDao.update(desktopCluster.getId(), desktopClusterVO);
    }

    private void deleteDesktopClusterNetworkRules() throws ManagementServerException {
        NetworkVO network = networkDao.findById(desktopCluster.getNetworkId());
        if (network == null) {
            return;
        }
        List<Long> removedVmIds = new ArrayList<>();
        if (!CollectionUtils.isEmpty(clusterVMs)) {
            for (DesktopClusterVmMapVO clusterVM : clusterVMs) {
                removedVmIds.add(clusterVM.getVmId());
            }
        }
        IpAddress publicIp = getSourceNatIp(network);
        if (publicIp == null) {
            throw new ManagementServerException(String.format("No source NAT IP addresses found for network : %s", network.getName()));
        }
        try {
            removePortForwardingRules(publicIp, network, owner, removedVmIds);
        } catch (ResourceUnavailableException e) {
            throw new ManagementServerException(String.format("Failed to DesktopCluster port forwarding rules for network : %s", network.getName()));
        }
    }

    private void validateClusterVMsDestroyed() {
        if(clusterVMs!=null  && !clusterVMs.isEmpty()) { // Wait for few seconds to get all VMs really expunged
            final int maxRetries = 3;
            int retryCounter = 0;
            while (retryCounter < maxRetries) {
                boolean allVMsRemoved = true;
                for (DesktopClusterVmMap clusterVM : clusterVMs) {
                    UserVmVO userVM = userVmDao.findById(clusterVM.getVmId());
                    if (userVM != null && !userVM.isRemoved()) {
                        allVMsRemoved = false;
                        break;
                    }
                }
                if (allVMsRemoved) {
                    break;
                }
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ie) {}
                retryCounter++;
            }
        }
    }

    private void checkForRulesToDelete() throws ManagementServerException {
        NetworkVO desktopClusterNetwork = networkDao.findById(desktopCluster.getNetworkId());
        if (desktopClusterNetwork != null && desktopClusterNetwork.getGuestType() != Network.GuestType.Shared) {
            deleteDesktopClusterNetworkRules();
        }
    }

    private boolean destroyClusterIps() {
        boolean ipDestroyed = true;
        List<DesktopClusterIpRangeVO> ipRangeList = desktopClusterIpRangeDao.listByDesktopClusterId(desktopCluster.getId());
            for (DesktopClusterIpRangeVO iprange : ipRangeList) {
                boolean deletedIp = desktopClusterIpRangeDao.remove(iprange.getId());
                if (!deletedIp) {
                    logMessage(Level.WARN, String.format("Failed to delete Desktop cluster ip range : %s", desktopCluster.getName()), null);
                    return false;
                }
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(String.format("Desktop cluster Ip Range : %s is successfully deleted", desktopCluster.getName()));
                }
            }
        return ipDestroyed;
    }

    private boolean destroyDesktopVMs() {
        boolean desktopDestroyed = true;
        // Tag or Instance Group
        return desktopDestroyed;
    }

    public boolean destroy() throws CloudRuntimeException {
        init();
        validateClusterSate();
        this.clusterVMs = desktopClusterVmMapDao.listByDesktopClusterId(desktopCluster.getId());
        boolean cleanupNetwork = true;
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Destroying Desktop cluster : %s", desktopCluster.getName()));
        }
        stateTransitTo(desktopCluster.getId(), DesktopCluster.Event.DestroyRequested);
        boolean vmsDestroyed = destroyClusterVMs();
        // if there are VM's that were not expunged, we can not delete the network
        if (vmsDestroyed) {
            if (cleanupNetwork) {
                validateClusterVMsDestroyed();
            } else {
                try {
                    checkForRulesToDelete();
                } catch (ManagementServerException e) {
                    String msg = String.format("Failed to remove network rules of Desktop cluster : %s", desktopCluster.getName());
                    LOGGER.warn(msg, e);
                    updateDesktopClusterEntryForGC();
                    throw new CloudRuntimeException(msg, e);
                }
            }
        } else {
            String msg = String.format("Failed to destroy one or more VMs as part of Desktop cluster : %s cleanup",desktopCluster.getName());
            LOGGER.warn(msg);
            updateDesktopClusterEntryForGC();
            throw new CloudRuntimeException(msg);
        }
        stateTransitTo(desktopCluster.getId(), DesktopCluster.Event.OperationSucceeded);
        final String accessType = "internal";
        // Desktop Cluster IP Range remove
        if (desktopCluster.getAccessType().equals(accessType)) {
            boolean ipDestroyed = destroyClusterIps();
        }
        // Desktop VM remove
        boolean desktopVmDestroyed = destroyDesktopVMs();
        boolean deleted = desktopClusterDao.remove(desktopCluster.getId());
        if (!deleted) {
            logMessage(Level.WARN, String.format("Failed to delete Desktop cluster : %s", desktopCluster.getName()), null);
            updateDesktopClusterEntryForGC();
            return false;
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Desktop cluster : %s is successfully deleted", desktopCluster.getName()));
        }
        return true;
    }
}
