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

package com.cloud.kubernetes.cluster.actionworkers;

import com.cloud.kubernetes.cluster.KubernetesCluster;
import com.cloud.kubernetes.cluster.KubernetesClusterVO;
import com.cloud.kubernetes.cluster.KubernetesClusterDetailsVO;
import com.cloud.kubernetes.cluster.KubernetesClusterManagerImpl;
import com.cloud.kubernetes.cluster.dao.KubernetesClusterDao;
import com.cloud.kubernetes.cluster.dao.KubernetesClusterDetailsDao;
import com.cloud.uservm.UserVm;
import com.cloud.vm.UserVmManager;
import org.apache.cloudstack.api.command.user.kubernetes.cluster.CreateKubernetesClusterCmd;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.jobs.AsyncJobExecutionContext;
import org.apache.cloudstack.framework.jobs.impl.AsyncJobVO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class KubernetesNodeProvisioningFailureTest {
    private KubernetesClusterStartWorker worker;
    private KubernetesClusterVO cluster;
    private KubernetesClusterDetailsDao details;
    private AsyncJobVO job;

    @Before public void setup() {
        cluster = Mockito.mock(KubernetesClusterVO.class);
        Mockito.when(cluster.getId()).thenReturn(8L);
        Mockito.when(cluster.getState()).thenReturn(KubernetesCluster.State.Starting);
        Mockito.when(cluster.getClusterType()).thenReturn(KubernetesCluster.ClusterType.CloudManaged);
        worker = Mockito.spy(new KubernetesClusterStartWorker(cluster, Mockito.mock(KubernetesClusterManagerImpl.class)));
        worker.kubernetesClusterDao = Mockito.mock(KubernetesClusterDao.class);
        Mockito.when(worker.kubernetesClusterDao.findById(8L)).thenReturn(cluster);
        details = Mockito.mock(KubernetesClusterDetailsDao.class); worker.kubernetesClusterDetailsDao = details;
        Mockito.doReturn(true).when(worker).stateTransitTo(Mockito.eq(8L), Mockito.any());
        phase("Nodes");
        job = Mockito.mock(AsyncJobVO.class);
        Mockito.when(job.getCmd()).thenReturn(CreateKubernetesClusterCmd.class.getName());
        Mockito.when(job.getInstanceType()).thenReturn("KubernetesCluster");
        Mockito.when(job.getInstanceId()).thenReturn(8L); Mockito.when(job.getUuid()).thenReturn("actual-create-job");
        AsyncJobExecutionContext.setCurrentExecutionContext(new AsyncJobExecutionContext(job));
    }
    @After public void clearContext() { AsyncJobExecutionContext.unregister(); }
    private void phase(String phase) {
        Mockito.when(details.findDetail(8L, "lifecycle.provisioning.phase"))
                .thenReturn(new KubernetesClusterDetailsVO(8L, "lifecycle.provisioning.phase", phase, false));
    }
    private void verifiesReceipt() {
        Mockito.verify(details).addDetail(8L, "lifecycle.creation.failed.job", "actual-create-job", false);
        Mockito.verify(details).addDetail(8L, "lifecycle.provisioning.phase", "NodeProvisioningFailed", false);
    }
    private void rejectsReceipt() {
        worker.recordCreationOperationFailure();
        Mockito.verify(details, Mockito.never()).addDetail(Mockito.anyLong(), Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean());
    }
    @Test public void unexpectedRuntimeDuringNodesRecordsActualCreationReceipt() {
        worker.recordCreationOperationFailure(); verifiesReceipt();
        Mockito.verify(worker).stateTransitTo(8L, KubernetesCluster.Event.CreateFailed);
    }
    @Test public void checkedProvisioningFailureAlreadyInErrorStillRecordsReceipt() {
        Mockito.when(cluster.getState()).thenReturn(KubernetesCluster.State.Error);
        worker.recordCreationOperationFailure(); verifiesReceipt();
        Mockito.verify(worker, Mockito.never()).stateTransitTo(Mockito.anyLong(), Mockito.any());
    }
    @Test public void asyncVmStartRuntimePropagatesAndRecordsParentCreateJob() throws Exception {
        worker.userVmManager = Mockito.mock(UserVmManager.class);
        UserVm vm = Mockito.mock(UserVm.class);
        RuntimeException failure = new RuntimeException(new java.util.concurrent.ExecutionException(new StringIndexOutOfBoundsException("injected VM start")));
        Mockito.doThrow(failure).when(worker.userVmManager).startVirtualMachine(Mockito.eq(vm), Mockito.isNull());
        com.cloud.utils.db.EntityManager entityManager = Mockito.mock(com.cloud.utils.db.EntityManager.class);
        com.cloud.user.User user = Mockito.mock(com.cloud.user.User.class);
        com.cloud.user.Account account = Mockito.mock(com.cloud.user.Account.class);
        Mockito.when(user.getId()).thenReturn(1L); Mockito.when(account.getId()).thenReturn(1L);
        Mockito.when(entityManager.findById(com.cloud.user.User.class, 1L)).thenReturn(user);
        Mockito.when(entityManager.findById(com.cloud.user.Account.class, 1L)).thenReturn(account);
        java.lang.reflect.Field field = CallContext.class.getDeclaredField("s_entityMgr"); field.setAccessible(true);
        Object previous = field.get(null); field.set(null, entityManager);
        CallContext.register(user, account);
        try {
            try { worker.startKubernetesVM(vm, null, null, com.cloud.kubernetes.cluster.KubernetesServiceHelper.KubernetesClusterNodeType.CONTROL); fail("VM start failure must propagate"); }
            catch (RuntimeException actual) { assertSame(failure, actual); worker.recordCreationOperationFailure(); }
            verifiesReceipt();
        } finally { CallContext.unregister(); field.set(null, previous); }
    }
    @Test public void preflightAndBootstrapPhasesAreNotRelabelledAsNodeFailure() {
        phase("Preflight"); rejectsReceipt(); phase("Bootstrap"); rejectsReceipt(); phase("Ready"); rejectsReceipt();
    }
    @Test public void bootstrapMarkerEvenWithoutEndpointPreservesNormalCleanup() {
        Mockito.when(details.findDetail(8L, "lifecycle.bootstrap.started")).thenReturn(new KubernetesClusterDetailsVO(8L, "lifecycle.bootstrap.started", "v1", false));
        rejectsReceipt();
    }
    @Test public void publishedEndpointPreservesNormalCleanup() {
        Mockito.when(cluster.getEndpoint()).thenReturn("https://192.0.2.1:6443/"); rejectsReceipt();
    }
    @Test public void providerOwnershipPreservesNormalCleanup() {
        Mockito.when(details.findDetail(8L, "provider.ownership.v1")).thenReturn(new KubernetesClusterDetailsVO(8L, "provider.ownership.v1", "true", false));
        rejectsReceipt();
    }
    @Test public void wrongCommandOrResourceCannotSupplyFailureReceipt() {
        Mockito.when(job.getInstanceId()).thenReturn(99L); rejectsReceipt();
        Mockito.when(job.getInstanceId()).thenReturn(8L); Mockito.when(job.getCmd()).thenReturn("StartVirtualMachineCmd"); rejectsReceipt();
    }
    @Test public void missingExecutionContextDoesNotInventAJobReceipt() {
        AsyncJobExecutionContext.unregister(); rejectsReceipt();
    }
    @Test public void successfulClusterIsNotReclassified() {
        Mockito.when(cluster.getState()).thenReturn(KubernetesCluster.State.Running); rejectsReceipt();
    }
}
