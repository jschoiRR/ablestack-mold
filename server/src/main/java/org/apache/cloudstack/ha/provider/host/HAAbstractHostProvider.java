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

package org.apache.cloudstack.ha.provider.host;

import com.cloud.agent.AgentManager;
import com.cloud.alert.AlertManager;
import com.cloud.ha.HighAvailabilityManager;
import com.cloud.host.Host;
import com.cloud.host.DetailVO;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.Status.Event;
import com.cloud.host.dao.HostDao;
import com.cloud.host.dao.HostDetailsDao;
import com.cloud.resource.ResourceManager;
import com.cloud.resource.ResourceState;
import com.cloud.utils.component.AdapterBase;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.fsm.NoTransitionException;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.VMInstanceDao;
import org.apache.cloudstack.alert.AlertService;
import org.apache.cloudstack.ha.HAConfig;
import org.apache.cloudstack.ha.HAResource;
import org.apache.cloudstack.ha.provider.HAProvider;
import org.apache.cloudstack.utils.identity.ManagementServerNode;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class HAAbstractHostProvider extends AdapterBase implements HAProvider<Host> {
    static final String FENCE_ROSTER = "ha.fence.roster";
    static final String FENCE_VM_PREFIX = "ha.fence.vm.";

    @Inject
    private AlertManager alertManager;
    @Inject
    protected AgentManager agentManager;
    @Inject
    protected ResourceManager resourceManager;
    @Inject
    protected HighAvailabilityManager oldHighAvailabilityManager;
    @Inject
    protected HostDao hostDao;
    @Inject
    protected HostDetailsDao hostDetailsDao;
    @Inject
    protected VMInstanceDao vmInstanceDao;


    @Override
    public HAResource.ResourceType resourceType() {
        return HAResource.ResourceType.Host;
    }

    public HAResource.ResourceSubType resourceSubType() {
        return HAResource.ResourceSubType.Unknown;
    }

    @Override
    public boolean isDisabled(final Host host) {
        return host.isDisabled();
    }

    @Override
    public boolean isInMaintenanceMode(final Host host) {
        return host.isInMaintenanceStates();
    }

    @Override
    public void prepareFenceSubResources(final Host host) {
        final HostVO currentHost = findHost(host);
        requireMaintenance(currentHost);
        final Map<String, String> existing = hostDetailsDao.findDetails(host.getId());
        final Map<Long, String> roster = readFenceRoster(existing);
        for (VMInstanceVO vm : vmInstanceDao.listByHostId(host.getId())) {
            if (vm.getUuid() == null || vm.getUuid().isBlank()) {
                throw new CloudRuntimeException("Cannot record fenced VM without UUID: " + vm.getId());
            }
            roster.putIfAbsent(vm.getId(), vm.getUuid());
        }
        // One small row per VM avoids the host_details value length limit on large hosts.
        final Map<String, String> details = new HashMap<>();
        details.put(FENCE_ROSTER, "1");
        roster.forEach((id, uuid) -> details.put(FENCE_VM_PREFIX + id, uuid));
        hostDetailsDao.persist(host.getId(), details);
        final Map<String, String> persisted = hostDetailsDao.findDetails(host.getId());
        if (!details.entrySet().stream().allMatch(entry -> entry.getValue().equals(persisted.get(entry.getKey())))) {
            throw new CloudRuntimeException("Failed to persist the VM roster before fencing host " + host.getId());
        }
    }

    @Override
    public void fenceSubResources(final Host host) {
        final HostVO currentHost = findHost(host);
        requireMaintenance(currentHost);
        if (currentHost.getState() != Status.Down) {
            agentManager.disconnectWithoutInvestigation(currentHost.getId(), Event.HostDown);
        }
        // Reboot state reports may already have cleared host_id: use the roster recorded before power-off.
        final Map<String, String> details = hostDetailsDao.findDetails(host.getId());
        final Map<Long, String> roster = readFenceRoster(details);
        if (!details.containsKey(FENCE_ROSTER)) {
            return; // Finalization already registered every durable job and removed the roster.
        }
        oldHighAvailabilityManager.scheduleRestartForFencedVms(currentHost, roster);
        for (Long vmId : roster.keySet()) {
            removeFenceDetail(host.getId(), FENCE_VM_PREFIX + vmId);
        }
        // Delete the marker last; a partial cleanup can safely retry already registered jobs.
        removeFenceDetail(host.getId(), FENCE_ROSTER);
    }

    @Override
    public void enableMaintenance(final Host r) {
        final HostVO currentHost = findHost(r);
        try {
            if (currentHost.getResourceState() != ResourceState.Maintenance
                    && !resourceManager.resourceStateTransitTo(currentHost, ResourceState.Event.InternalEnterMaintenance,
                            ManagementServerNode.getManagementServerId())) {
                throw new CloudRuntimeException("Failed to persist HA maintenance for host " + r.getId());
            }
        } catch (NoTransitionException e) {
            throw new CloudRuntimeException("Cannot enter HA maintenance for host " + r.getId(), e);
        }
        if (findHost(r).getResourceState() != ResourceState.Maintenance) {
            throw new CloudRuntimeException("HA maintenance was not retained for host " + r.getId());
        }
        agentManager.pullAgentToMaintenance(r.getId());
    }

    private HostVO findHost(final Host host) {
        final HostVO currentHost = hostDao.findById(host.getId());
        if (currentHost == null || currentHost.getRemoved() != null) {
            throw new CloudRuntimeException("HA host no longer exists: " + host.getId());
        }
        return currentHost;
    }

    private void requireMaintenance(final Host host) {
        if (host.getResourceState() != ResourceState.Maintenance) {
            throw new CloudRuntimeException("Fenced host is no longer in maintenance: " + host.getId());
        }
    }

    private Map<Long, String> readFenceRoster(final Map<String, String> details) {
        final Map<Long, String> roster = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : details.entrySet()) {
            if (!entry.getKey().startsWith(FENCE_VM_PREFIX)) {
                continue;
            }
            try {
                final long vmId = Long.parseLong(entry.getKey().substring(FENCE_VM_PREFIX.length()));
                if (vmId <= 0 || entry.getValue() == null || entry.getValue().isBlank()) {
                    throw new IllegalArgumentException("Invalid VM roster entry");
                }
                roster.put(vmId, entry.getValue());
            } catch (IllegalArgumentException e) {
                throw new CloudRuntimeException("Invalid persisted fencing roster entry: " + entry.getKey(), e);
            }
        }
        if (details.containsKey(FENCE_ROSTER) && !"1".equals(details.get(FENCE_ROSTER))
                || !roster.isEmpty() && !details.containsKey(FENCE_ROSTER)) {
            throw new CloudRuntimeException("Invalid persisted fencing roster marker");
        }
        return roster;
    }

    private void removeFenceDetail(final long hostId, final String name) {
        final DetailVO detail = hostDetailsDao.findDetail(hostId, name);
        if (detail != null && !hostDetailsDao.remove(detail.getId())) {
            throw new CloudRuntimeException("Failed to clear completed fencing roster for host " + hostId);
        }
    }

    @Override
    public void sendAlert(final Host host, final HAConfig.HAState nextState) {
        String subject = "HA operation performed for host";
        String body = subject;
        if (HAConfig.HAState.Fencing.equals(nextState)) {
            subject = String.format("HA Fencing of host id=%d, in dc id=%d performed", host.getId(), host.getDataCenterId());
            body = String.format("HA Fencing has been performed for host id=%d, uuid=%s in datacenter id=%d", host.getId(), host.getUuid(), host.getDataCenterId());
        } else if (HAConfig.HAState.Recovering.equals(nextState)) {
            subject = String.format("HA Recovery of host id=%d, in dc id=%d performed", host.getId(), host.getDataCenterId());
            body = String.format("HA Recovery has been performed for host id=%d, uuid=%s in datacenter id=%d", host.getId(), host.getUuid(), host.getDataCenterId());
        }
        alertManager.sendAlert(AlertService.AlertType.ALERT_TYPE_HA_ACTION, host.getDataCenterId(), host.getPodId(), subject, body);
    }

}
