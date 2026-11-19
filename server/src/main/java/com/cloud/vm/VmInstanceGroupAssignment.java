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
package com.cloud.vm;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.dao.InstanceGroupDao;
import com.cloud.vm.dao.InstanceGroupVMMapDao;
import com.cloud.vm.dao.UserVmDao;

/** Serializes VM classification changes without invoking VM lifecycle or storage operations. */
@Component
public class VmInstanceGroupAssignment {
    private final UserVmDao vmDao;
    private final InstanceGroupDao groupDao;
    private final InstanceGroupVMMapDao mapDao;
    private final AccountDao accountDao;

    @Inject
    public VmInstanceGroupAssignment(UserVmDao vmDao, InstanceGroupDao groupDao,
                                    InstanceGroupVMMapDao mapDao, AccountDao accountDao) {
        this.vmDao = vmDao;
        this.groupDao = groupDao;
        this.mapDao = mapDao;
        this.accountDao = accountDao;
    }

    public void validate(long vmId, Long groupId, String name, boolean clear) {
        int selectors = (groupId != null ? 1 : 0) + (name != null ? 1 : 0) + (clear ? 1 : 0);
        if (selectors > 1) {
            throw new InvalidParameterValueException("Specify one non-blank group name, groupid or cleargroup=true.");
        }
        UserVmVO vm = requireVm(vmDao.findById(vmId));
        if (name != null) {
            VmInstanceGroupNames.forExisting(name, groupDao.findByAccountAndName(vm.getAccountId(), name));
        }
        if (groupId != null) {
            requireGroup(groupDao.findById(groupId), vm.getAccountId());
        }
    }

    public void apply(long vmId, Long groupId, String name, boolean clear) {
        if (groupId == null && name == null && !clear) {
            return;
        }
        validate(vmId, groupId, name, clear);
        long owner = requireVm(vmDao.findById(vmId)).getAccountId();
        AccountVO accountLock = name == null ? null : accountDao.acquireInLockTable(owner);
        if (name != null && accountLock == null) {
            throw new CloudRuntimeException("Unable to lock the VM owner for instance group creation.");
        }
        try {
            Transaction.execute((TransactionCallback<Void>) status -> {
                if (name != null && accountDao.lockRow(owner, true) == null) {
                    throw new CloudRuntimeException("Unable to lock the VM owner for instance group creation.");
                }
                UserVmVO vm = requireVm(vmDao.lockRow(vmId, true));
                if (vm.getAccountId() != owner) {
                    throw new InvalidParameterValueException("The VM owner changed. Refresh the VM before changing its group.");
                }
                InstanceGroupVO target = null;
                if (!clear) {
                    if (groupId != null) {
                        target = requireGroup(groupDao.lockRow(groupId, true), owner);
                    } else {
                        InstanceGroupVO existing = groupDao.findByAccountAndName(owner, name);
                        String normalized = VmInstanceGroupNames.forExisting(name, existing);
                        target = existing != null && name.equals(existing.getName()) ? existing : groupDao.findByAccountAndName(owner, normalized);
                        if (target == null) {
                            target = groupDao.persist(new InstanceGroupVO(normalized, owner));
                            if (target == null) {
                                throw new CloudRuntimeException("Unable to create the instance group.");
                            }
                        }
                        target = requireGroup(groupDao.lockRow(target.getId(), true), owner);
                    }
                }
                SearchCriteria<InstanceGroupVMMapVO> sc = mapDao.createSearchCriteria();
                sc.addAnd("instanceId", SearchCriteria.Op.EQ, vmId);
                mapDao.expunge(sc);
                if (target != null && mapDao.persist(new InstanceGroupVMMapVO(target.getId(), vmId)) == null) {
                    throw new CloudRuntimeException("Unable to assign the VM to its instance group.");
                }
                return null;
            });
        } finally {
            if (accountLock != null) {
                accountDao.releaseFromLockTable(owner);
            }
        }
    }

    private UserVmVO requireVm(UserVmVO vm) {
        if (vm == null || vm.getRemoved() != null) {
            throw new InvalidParameterValueException("Unable to find an active VM for instance group assignment.");
        }
        return vm;
    }

    private InstanceGroupVO requireGroup(InstanceGroupVO group, long owner) {
        if (group == null || group.getRemoved() != null) {
            throw new InvalidParameterValueException("Unable to find an active instance group.");
        }
        if (group.getAccountId() != owner) {
            throw new InvalidParameterValueException("The instance group must belong to the same account or project as the VM.");
        }
        return group;
    }
}
