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

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
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
        if (outOfBandManagementService.isOutOfBandManagementEnabled(host)){
            return !isInMaintenanceMode(host) && !isDisabled(host) &&
                    hostActivityChecker.getNeighbors(host).length > 0 &&
                    hostActivityChecker.hasStoragePoolHeartbeatEnabled(host) &&
                    (Hypervisor.HypervisorType.KVM.equals(host.getHypervisorType()) ||
                            Hypervisor.HypervisorType.LXC.equals(host.getHypervisorType()));
        }
        return false;
    }

    @Override
    public boolean isHealthy(final Host r) throws HACheckerException {
        return hostActivityChecker.isHealthy(r);
    }

    @Override
    public boolean isPowerOffConfirmed(final Host host) throws HACheckerException {
        if (!isPowerOffCheckEnabled(host) || !outOfBandManagementService.isOutOfBandManagementEnabled(host)) {
            return false;
        }
        try {
            final long budget = getHealthCheckTimeout(host);
            final PowerCheckSettings settings = powerCheckSettings(host, budget);
            return confirmPowerOff(host, settings, deadlineAfterSeconds(budget - 1), false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HACheckerException("Interrupted while confirming host power state", e);
        } catch (Exception e) {
            // An unavailable BMC never proves that the host is dead. Continue the
            // independent health/activity path instead of inventing a DEAD sample.
            logger.warn("Unable to confirm fresh power-off evidence for {}: {}", host, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean hasActivity(final Host r, final DateTime suspectTime) throws HACheckerException {
        return hostActivityChecker.isActive(r, suspectTime);
    }

    @Override
    public boolean recover(Host r) throws HARecoveryException {
        try {
            if (outOfBandManagementService.isOutOfBandManagementEnabled(r)){
                logger.warn("OOBM recover operation skiped for the host " + r.getName());
                return false;
            } else {
                logger.warn("OOBM recover operation failed for the host {}", r);
                return false;
            }
        } catch (Exception e){
            logger.warn("OOBM service is not configured or enabled for this host {} error is {}", r, e.getMessage());
            throw new HARecoveryException(String.format(" OOBM service is not configured or enabled for this host %s", r), e);
        }
    }

    @Override
    public boolean fence(Host r) throws HAFenceException {
        try {
            if (!outOfBandManagementService.isOutOfBandManagementEnabled(r)) {
                logger.warn("Cannot fence {} without enabled out-of-band management", r);
                return false;
            }
            final long budget = getFenceTimeout(r);
            final PowerCheckSettings settings = powerCheckSettings(r, budget);
            if (budget <= settings.confirmations * settings.timeout + (settings.confirmations - 1) * settings.interval + 21) {
                throw new IllegalArgumentException("Fencing timeout must allow OFF, repeated status verification and ON before power is changed");
            }
            final long deadline = deadlineAfterSeconds(budget - 1);

            // Preserve reboot semantics, but expose the OFF phase: accepting a
            // CYCLE request alone is not evidence that the old VMs have stopped.
            executeFencingPowerOperation(r, PowerOperation.OFF, deadline);
            final long verificationDeadline = deadline - TimeUnit.SECONDS.toNanos(10);
            if (!confirmPowerOff(r, settings, verificationDeadline, true)) {
                logger.warn("Host {} remains quarantined: power off was not confirmed", r);
                return false;
            }
            executeFencingPowerOperation(r, PowerOperation.ON, deadline);
            logger.info("Verified power-off phase and requested reboot for {}; Maintenance remains enabled", r);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HAFenceException("Interrupted while fencing host " + r.getId(), e);
        } catch (Exception e) {
            throw new HAFenceException("Unable to verify fencing and reboot of host " + r.getId(), e);
        }
    }

    private void executeFencingPowerOperation(Host host, PowerOperation operation, long deadline) throws InterruptedException {
        checkInterrupted();
        final HostVO currentHost = kvmHostDao.findById(host.getId());
        final HAConfig currentHA = kvmHAConfigDao.findHAResource(host.getId(), HAResource.ResourceType.Host);
        if (currentHost == null || currentHost.getResourceState() != ResourceState.Maintenance || currentHA == null
                || !kvmHAManager.isHAEligible(currentHost)
                || !currentHA.isEnabled() || currentHA.getState() != HAConfig.HAState.Fencing
                || currentHA.getManagementServerId() == null
                || currentHA.getManagementServerId() != ManagementServerNode.getManagementServerId()) {
            throw new IllegalStateException("Host must remain in Maintenance with an active fencing operation before " + operation);
        }
        final long timeout = Math.min(10L, remainingSeconds(deadline));
        if (timeout < 1) {
            throw new IllegalStateException("Fencing deadline expired before " + operation);
        }
        final OutOfBandManagementResponse response = outOfBandManagementService.executePowerOperation(currentHost, operation, timeout);
        checkInterrupted();
        if (remainingSeconds(deadline) < 0) {
            throw new IllegalStateException("Fencing deadline expired during " + operation);
        }
        if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
            throw new IllegalStateException("BMC did not acknowledge fencing operation " + operation);
        }
    }

    private boolean confirmPowerOff(Host host, PowerCheckSettings settings, long deadline, boolean waitForShutdown) throws InterruptedException {
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
                if (!waitForShutdown) {
                    return false;
                }
            }
            if (remainingSeconds(deadline) < settings.interval + settings.timeout) {
                return false;
            }
            waitForPowerObservation(settings.interval);
        }
        return false;
    }

    private PowerCheckSettings powerCheckSettings(Host host, long budget) {
        final long confirmations = getPowerOffConfirmations(host);
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

    protected long getPowerOffConfirmations(Host host) {
        return KVMHAConfig.KvmHAPowerOffConfirmations.valueIn(host.getClusterId());
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
            case MaxActivityChecks:
                return KVMHAConfig.KvmHAActivityCheckMaxAttempts.valueIn(clusterId);
            case ActivityCheckFailureRatio:
                return KVMHAConfig.KvmHAActivityCheckFailureThreshold.valueIn(clusterId);
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
            KVMHAConfig.KvmHAActivityCheckInterval,
            KVMHAConfig.KvmHAActivityCheckMaxAttempts,
            KVMHAConfig.KvmHAActivityCheckFailureThreshold,
            KVMHAConfig.KvmHADegradedMaxPeriod,
            KVMHAConfig.KvmHARecoverWaitPeriod,
            KVMHAConfig.KvmHARecoverAttemptThreshold,
            KVMHAConfig.KvmHAPowerOffCheckEnabled,
            KVMHAConfig.KvmHAPowerOffConfirmations,
            KVMHAConfig.KvmHAPowerCheckInterval,
            KVMHAConfig.KvmHAPowerCheckTimeout,
        };
    }
}
