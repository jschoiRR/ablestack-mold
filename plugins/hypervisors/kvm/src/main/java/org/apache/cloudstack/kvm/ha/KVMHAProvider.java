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

package org.apache.cloudstack.kvm.ha;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.PcsFenceHostCommand;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.resource.ResourceState;

import org.apache.cloudstack.api.response.OutOfBandManagementResponse;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.Configurable;
import org.apache.cloudstack.ha.HAResource;
import org.apache.cloudstack.ha.HAConfig;
import org.apache.cloudstack.ha.HAManager;
import org.apache.cloudstack.ha.dao.HAConfigDao;
import org.apache.cloudstack.utils.identity.ManagementServerNode;
import org.apache.cloudstack.ha.provider.HACheckerException;
import org.apache.cloudstack.ha.provider.HAFenceException;
import org.apache.cloudstack.ha.provider.HAProvider;
import org.apache.cloudstack.ha.provider.HARecoveryException;
import org.apache.cloudstack.ha.provider.host.HAAbstractHostProvider;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagement.PowerOperation;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagementService;
import org.apache.cloudstack.outofbandmanagement.OutOfBandManagement.PowerState;
import org.joda.time.DateTime;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class KVMHAProvider extends HAAbstractHostProvider implements HAProvider<Host>, Configurable {

    @Inject
    protected KVMHostActivityChecker hostActivityChecker;
    @Inject
    protected OutOfBandManagementService outOfBandManagementService;
    @Inject
    protected HostDao kvmHostDao;
    @Inject
    protected HAConfigDao kvmHAConfigDao;
    @Inject
    protected HAManager kvmHAManager;

    @Override
    public boolean isEligible(final Host host) {
        return outOfBandManagementService.isOutOfBandManagementEnabled(host)
                && !isInMaintenanceMode(host) && !isDisabled(host)
                && hostActivityChecker.getNeighbors(host).length > 0
                && hostActivityChecker.hasStoragePoolHeartbeatEnabled(host)
                && (Hypervisor.HypervisorType.KVM.equals(host.getHypervisorType())
                        || Hypervisor.HypervisorType.LXC.equals(host.getHypervisorType()));
    }

    @Override
    public boolean isHealthy(final Host r) throws HACheckerException {
        return hostActivityChecker.isHealthy(r);
    }

    @Override
    public PowerObservation checkPowerState(final Host host) throws HACheckerException {
        if (!isPowerOffCheckEnabled(host) || !outOfBandManagementService.isOutOfBandManagementEnabled(host)) {
            return PowerObservation.UNKNOWN;
        }
        try {
            final long budget = getHealthCheckTimeout(host);
            final long timeout = getPowerCheckTimeout(host);
            final long maxInterval = getPowerOffMaxInterval(host);
            if (budget < 2 || budget > 3600 || timeout < 1 || timeout >= budget - 1
                    || getPowerOffConfirmations(host) < 3 || maxInterval < 1 || maxInterval > 3600) {
                throw new IllegalArgumentException("Invalid single power observation configuration");
            }
            checkInterrupted();
            final long deadline = deadlineAfterSeconds(timeout);
            final OutOfBandManagementResponse response = outOfBandManagementService.executePowerOperation(host, PowerOperation.STATUS, timeout);
            checkInterrupted();
            if (nanoTime() > deadline || response == null || !Boolean.TRUE.equals(response.getSuccess())) {
                return PowerObservation.UNKNOWN;
            }
            if (response.getPowerState() == PowerState.Off) {
                return PowerObservation.OFF;
            }
            return response.getPowerState() == PowerState.On ? PowerObservation.ON : PowerObservation.UNKNOWN;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HACheckerException("Interrupted while observing host power state", e);
        } catch (Exception e) {
            // An unavailable BMC never proves that the host is dead. Continue the
            // independent health/activity path instead of inventing a DEAD sample.
            logger.warn("Unable to obtain a fresh power observation for {}: {}", host, e.getMessage());
            return PowerObservation.UNKNOWN;
        }
    }

    @Override
    public boolean hasActivity(final Host r, final DateTime suspectTime) throws HACheckerException {
        return hostActivityChecker.isActive(r, suspectTime);
    }

    @Override
    public boolean recover(Host r) throws HARecoveryException {
        logger.debug("Recover the host {}", r);
        try {
            if (outOfBandManagementService.isOutOfBandManagementEnabled(r)){
                logger.warn("OOBM recover operation skipped for the host " + r.getName());
                return false;
            } else {
                logger.warn("OOBM recover operation failed for the host {}", r);
                return false;
            }
        } catch (Exception e) {
            logger.warn("OOBM service is not configured or enabled for this host {} error is {}", r, e.getMessage());
            throw new HARecoveryException(String.format("OOBM service is not configured or enabled for this host %s", r), e);
        }
    }

    @Override
    public boolean fence(Host host) throws HAFenceException {
        try {
            final HAConfig attempt = requireCurrentFence(host, null);
            final long budget = getFenceTimeout(host);
            if (budget < 2 || budget > 3600) {
                throw new IllegalArgumentException("Fencing timeout must be between 2 and 3600 seconds");
            }
            final long deadline = deadlineAfterSeconds(budget - 1);
            final boolean pcsConfigured = pcsFenceConfigured(host);
            if (outOfBandManagementService.isOutOfBandManagementEnabled(host)) {
                // Leave time for Pacemaker if both fencing methods are configured.
                final long ipmiDeadline = pcsConfigured ? deadline - TimeUnit.SECONDS.toNanos(budget / 2) : deadline;
                if (fenceWithIpmi(host, ipmiDeadline)) {
                    requireCurrentFence(host, attempt);
                    logger.info("Verified IPMI power-off for {}; Maintenance remains enabled", host);
                    return true;
                }
            }
            if (pcsConfigured && fenceWithPcs(host, attempt, deadline)) {
                return true;
            }
            logger.warn("Host {} remains quarantined: power off was not confirmed", host);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HAFenceException("Interrupted while fencing host " + host.getId(), e);
        } catch (Exception e) {
            throw new HAFenceException("Unable to verify fencing of host " + host.getId(), e);
        }
    }

    private boolean fenceWithIpmi(Host host, long deadline) throws InterruptedException {
        try {
            final long budget = remainingSeconds(deadline);
            final PowerCheckSettings settings = powerCheckSettings(host, budget);
            final long observationBudget = settings.confirmations * settings.timeout + (settings.confirmations - 1) * settings.interval;
            final long timeout = Math.min(10L, budget - observationBudget - 1);
            final OutOfBandManagementResponse response = outOfBandManagementService.executePowerOperation(host, PowerOperation.OFF, timeout);
            checkInterrupted();
            return response != null && Boolean.TRUE.equals(response.getSuccess()) && confirmPowerOff(host, settings, deadline);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            checkInterrupted();
            logger.warn("IPMI could not confirm fencing for {}: {}", host, e.getMessage());
            return false;
        }
    }

    private boolean pcsFenceConfigured(Host host) {
        final String cluster = KVMHAConfig.KvmHAPcsFenceCluster.valueIn(host.getClusterId());
        final String nodeMap = KVMHAConfig.KvmHAPcsFenceNodeMap.valueIn(host.getClusterId());
        return PcsFenceHostCommand.isValidName(cluster) && nodeMap != null && !nodeMap.isBlank();
    }

    private boolean fenceWithPcs(Host host, HAConfig attempt, long deadline) throws InterruptedException {
        final String cluster = KVMHAConfig.KvmHAPcsFenceCluster.valueIn(host.getClusterId());
        final Map<UUID, String> nodes = parsePcsNodeMap(KVMHAConfig.KvmHAPcsFenceNodeMap.valueIn(host.getClusterId()));
        final String targetNode = nodes.get(UUID.fromString(host.getUuid()));
        if (targetNode == null) {
            throw new IllegalArgumentException("No Pacemaker node mapping for host " + host.getUuid());
        }
        for (long peerId : hostActivityChecker.getNeighbors(host)) {
            final HostVO peer = kvmHostDao.findById(peerId);
            if (peer == null || peerId == host.getId() || peer.getState() != Status.Up
                    || !Objects.equals(peer.getClusterId(), host.getClusterId())) {
                continue;
            }
            final String executorNode = nodes.get(UUID.fromString(peer.getUuid()));
            if (executorNode == null) {
                continue;
            }
            requireCurrentFence(host, attempt);
            final long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - nanoTime());
            if (remainingMillis < 2000) {
                return false;
            }
            final PcsFenceHostCommand command = new PcsFenceHostCommand(cluster, targetNode, executorNode,
                    System.currentTimeMillis() + remainingMillis);
            final Answer answer;
            try {
                answer = hostActivityChecker.sendProbe(peerId, command, deadline);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                checkInterrupted();
                logger.warn("Pacemaker fencing request for {} via {} failed: {}", host, peer, e.getMessage());
                continue;
            }
            requireCurrentFence(host, attempt);
            if (answer != null && answer.getResult() && nanoTime() < deadline) {
                logger.info("Verified Pacemaker power-off for {} (node {}, via {}); Maintenance remains enabled", host, targetNode, peer);
                return true;
            }
            logger.warn("Pacemaker did not confirm fencing for {} via {}: {}", host, peer, answer == null ? "no response" : answer.getDetails());
        }
        return false;
    }

    static Map<UUID, String> parsePcsNodeMap(String value) {
        final Map<UUID, String> nodes = new HashMap<>();
        final Set<String> nodeNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String mapping : Objects.toString(value, "").split(",", -1)) {
            final String[] pair = mapping.split("=", -1);
            if (pair.length != 2 || !PcsFenceHostCommand.isValidName(pair[1].trim())) {
                throw new IllegalArgumentException("Expected a comma-separated list of Mold host UUID=Pacemaker node mappings");
            }
            final String node = pair[1].trim();
            if (!nodeNames.add(node) || nodes.putIfAbsent(UUID.fromString(pair[0].trim()), node) != null) {
                throw new IllegalArgumentException("Duplicate host UUID or Pacemaker node in fencing mappings");
            }
        }
        return nodes;
    }

    private HAConfig requireCurrentFence(Host host, HAConfig attempt) throws InterruptedException {
        checkInterrupted();
        final HostVO currentHost = kvmHostDao.findById(host.getId());
        final HAConfig current = kvmHAConfigDao.findHAResource(host.getId(), HAResource.ResourceType.Host);
        if (currentHost == null || currentHost.getResourceState() != ResourceState.Maintenance
                || !Objects.equals(host.getUuid(), currentHost.getUuid()) || current == null
                || !kvmHAManager.isHAEligible(currentHost) || !current.isEnabled() || current.getState() != HAConfig.HAState.Fencing
                || !Objects.equals(current.getManagementServerId(), ManagementServerNode.getManagementServerId())
                || attempt != null && (current.getId() != attempt.getId() || current.getUpdateCount() != attempt.getUpdateCount())) {
            throw new IllegalStateException("Host must remain in Maintenance with the same active fencing operation");
        }
        return current;
    }

    private boolean confirmPowerOff(Host host, PowerCheckSettings settings, long deadline) throws InterruptedException {
        long consecutiveOff = 0;
        while (remainingSeconds(deadline) >= settings.timeout) {
            checkInterrupted();
            PowerState state = PowerState.Unknown;
            try {
                final OutOfBandManagementResponse response = outOfBandManagementService.executePowerOperation(host, PowerOperation.STATUS, settings.timeout);
                if (response != null && Boolean.TRUE.equals(response.getSuccess()) && response.getPowerState() != null) {
                    state = response.getPowerState();
                }
            } catch (Exception e) {
                logger.debug("Fresh BMC status unavailable for {}: {}", host, e.getMessage());
            }
            checkInterrupted();
            if (remainingSeconds(deadline) < 0) {
                return false;
            }
            if (state == PowerState.Off) {
                consecutiveOff++;
                logger.debug("Fresh power-off confirmation for {}: {}/{}", host, consecutiveOff, settings.confirmations);
                if (consecutiveOff >= settings.confirmations) {
                    return true;
                }
            } else {
                consecutiveOff = 0;
            }
            if (remainingSeconds(deadline) < settings.interval + settings.timeout) {
                return false;
            }
            waitForPowerObservation(settings.interval);
        }
        return false;
    }

    private PowerCheckSettings powerCheckSettings(Host host, long budget) {
        final long confirmations = getFencePowerOffConfirmations(host);
        final long interval = getPowerCheckInterval(host);
        final long timeout = getPowerCheckTimeout(host);
        if (budget < 2 || budget > 3600 || confirmations < 3 || interval < 1 || timeout < 1) {
            throw new IllegalArgumentException("Invalid KVM HA power observation configuration");
        }
        final long observationBudget = Math.addExact(Math.multiplyExact(confirmations, timeout), Math.multiplyExact(confirmations - 1, interval));
        if (observationBudget >= budget - 1) {
            throw new IllegalArgumentException("Repeated power observations must fit within the HA timeout with a safety margin");
        }
        return new PowerCheckSettings(confirmations, interval, timeout);
    }

    protected boolean isPowerOffCheckEnabled(Host host) {
        return Boolean.TRUE.equals(KVMHAConfig.KvmHAPowerOffCheckEnabled.valueIn(host.getClusterId()));
    }

    @Override
    public long getPowerOffConfirmations(Host host) {
        return KVMHAConfig.KvmHAPowerOffConfirmations.valueIn(host.getClusterId());
    }

    @Override
    public long getPowerOffMaxInterval(Host host) {
        return KVMHAConfig.KvmHAPowerOffMaxInterval.valueIn(host.getClusterId());
    }

    protected long getFencePowerOffConfirmations(Host host) {
        return KVMHAConfig.KvmHAFencePowerOffConfirmations.valueIn(host.getClusterId());
    }

    protected long getPowerCheckInterval(Host host) {
        return KVMHAConfig.KvmHAPowerCheckInterval.valueIn(host.getClusterId());
    }

    protected long getPowerCheckTimeout(Host host) {
        return KVMHAConfig.KvmHAPowerCheckTimeout.valueIn(host.getClusterId());
    }

    protected long getHealthCheckTimeout(Host host) {
        return KVMHAConfig.KvmHAHealthCheckTimeout.valueIn(host.getClusterId());
    }

    protected long getFenceTimeout(Host host) {
        return KVMHAConfig.KvmHAFenceTimeout.valueIn(host.getClusterId());
    }

    protected void waitForPowerObservation(long seconds) throws InterruptedException {
        TimeUnit.SECONDS.sleep(seconds);
    }

    protected long nanoTime() {
        return System.nanoTime();
    }

    private long deadlineAfterSeconds(long seconds) {
        return nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    }

    private long remainingSeconds(long deadline) {
        return Math.floorDiv(deadline - nanoTime(), TimeUnit.SECONDS.toNanos(1));
    }

    private void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("HA power operation interrupted");
        }
    }

    private static final class PowerCheckSettings {
        final long confirmations;
        final long interval;
        final long timeout;

        PowerCheckSettings(long confirmations, long interval, long timeout) {
            this.confirmations = confirmations;
            this.interval = interval;
            this.timeout = timeout;
        }
    }

    @Override
    public HAResource.ResourceSubType resourceSubType() {
        return HAResource.ResourceSubType.KVM;
    }

    @Override
    public Object getConfigValue(final HAProviderConfig name, final Host host) {
        final Long clusterId = host.getClusterId();
        switch (name) {
            case HealthCheckTimeout:
                return KVMHAConfig.KvmHAHealthCheckTimeout.valueIn(clusterId);
            case ActivityCheckTimeout:
                return KVMHAConfig.KvmHAActivityCheckTimeout.valueIn(clusterId);
            case MaxActivityCheckInterval:
                return KVMHAConfig.KvmHAActivityCheckInterval.valueIn(clusterId);
            case ActivityCheckFailureThreshold:
                return KVMHAConfig.KvmHAActivityCheckFailureThreshold.valueIn(clusterId);
            case ActivityCheckSuccessThreshold:
                return KVMHAConfig.KvmHAActivityCheckSuccessThreshold.valueIn(clusterId);
            case RecoveryWaitTimeout:
                return KVMHAConfig.KvmHARecoverWaitPeriod.valueIn(clusterId);
            case RecoveryTimeout:
                return KVMHAConfig.KvmHARecoverTimeout.valueIn(clusterId);
            case FenceTimeout:
                return KVMHAConfig.KvmHAFenceTimeout.valueIn(clusterId);
            case MaxRecoveryAttempts:
                return KVMHAConfig.KvmHARecoverAttemptThreshold.valueIn(clusterId);
            case MaxDegradedWaitTimeout:
                return KVMHAConfig.KvmHADegradedMaxPeriod.valueIn(clusterId);
            default:
                throw new InvalidParameterValueException("Unknown HAProviderConfig " + name.toString());
        }
    }

    @Override
    public String getConfigComponentName() {
        return KVMHAConfig.class.getSimpleName();
    }

    @Override
    public ConfigKey<?>[] getConfigKeys() {
        return new ConfigKey<?>[] {
            KVMHAConfig.KvmHAHealthCheckTimeout,
            KVMHAConfig.KvmHAActivityCheckTimeout,
            KVMHAConfig.KvmHARecoverTimeout,
            KVMHAConfig.KvmHAFenceTimeout,
            KVMHAConfig.KvmHAPcsFenceCluster,
            KVMHAConfig.KvmHAPcsFenceNodeMap,
            KVMHAConfig.KvmHAActivityCheckInterval,
            KVMHAConfig.KvmHAActivityCheckFailureThreshold,
            KVMHAConfig.KvmHAActivityCheckSuccessThreshold,
            KVMHAConfig.KvmHADegradedMaxPeriod,
            KVMHAConfig.KvmHARecoverWaitPeriod,
            KVMHAConfig.KvmHARecoverAttemptThreshold,
            KVMHAConfig.KvmHAPowerOffCheckEnabled,
            KVMHAConfig.KvmHAPowerOffConfirmations,
            KVMHAConfig.KvmHAPowerOffMaxInterval,
            KVMHAConfig.KvmHAFencePowerOffConfirmations,
            KVMHAConfig.KvmHAPowerCheckInterval,
            KVMHAConfig.KvmHAPowerCheckTimeout,
        };
    }
}
