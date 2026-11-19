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

import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.cloudstack.api.ApiConstants;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.user.AccountVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.dao.InstanceGroupDao;

/** Name policy shared by explicit creation, rename and name-based VM assignment. */
public final class VmInstanceGroupNames {
    public static final int MAX_LENGTH = 255;
    private static final Pattern ASCII_CONTROLS = Pattern.compile("[\\u0000-\\u001f\\u007f]");
    // The UI and ORM both count UTF-16 units; reject before GenericDaoBase can truncate.
    private static final Pattern BOUNDARY_WHITESPACE = Pattern.compile(
            "^[\\u0009-\\u000d\\u0020\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+"
            + "|[\\u0009-\\u000d\\u0020\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]+$");

    private VmInstanceGroupNames() { }

    public static String normalize(String value) {
        if (value != null && value.length() > ApiConstants.INSTANCE_GROUP_NAME_INPUT_MAX_LENGTH) {
            throw new InvalidParameterValueException("[VM_GROUP_NAME_INPUT_TOO_LONG] Instance group name input must be at most 1024 UTF-16 units.");
        }
        String name = value == null ? "" : BOUNDARY_WHITESPACE.matcher(value).replaceAll("");
        if (name.isEmpty()) {
            throw new InvalidParameterValueException("[VM_GROUP_NAME_REQUIRED] Instance group name is required.");
        }
        if (ASCII_CONTROLS.matcher(name).find()) {
            throw new InvalidParameterValueException("[VM_GROUP_NAME_INVALID_CHARACTERS] Instance group name must not contain ASCII control characters.");
        }
        if (name.length() > MAX_LENGTH) {
            throw new InvalidParameterValueException("[VM_GROUP_NAME_TOO_LONG] Instance group name must be at most 255 UTF-16 units.");
        }
        return name;
    }

    public static String forExisting(String value, InstanceGroupVO existing) {
        return existing != null && value != null && value.equals(existing.getName()) ? value : normalize(value);
    }

    public static InstanceGroupVO create(InstanceGroupDao groups, AccountDao accounts, long owner, String value) {
        String name = normalize(value);
        return withOwnerLock(accounts, owner, () -> {
            if (groups.findByAccountAndName(owner, name) != null) {
                throw duplicate();
            }
            InstanceGroupVO created = groups.persist(new InstanceGroupVO(name, owner));
            if (created == null) {
                throw new CloudRuntimeException("Unable to create the instance group.");
            }
            return created;
        });
    }

    public static InstanceGroupVO rename(InstanceGroupDao groups, AccountDao accounts, long owner, long id, String value) {
        return withOwnerLock(accounts, owner, () -> {
            InstanceGroupVO group = groups.lockRow(id, true);
            if (group == null || group.getRemoved() != null || group.getAccountId() != owner) {
                throw new InvalidParameterValueException("[VM_GROUP_NOT_FOUND] Unable to find the active instance group.");
            }
            if (value == null || value.equals(group.getName())) {
                return group;
            }
            String name = normalize(value);
            InstanceGroupVO collision = groups.findByAccountAndName(owner, name);
            if (collision != null && collision.getId() != id) {
                throw duplicate();
            }
            if (!name.equals(group.getName())) {
                groups.updateVmGroup(id, name);
            }
            return groups.findById(id);
        });
    }

    private static InvalidParameterValueException duplicate() {
        return new InvalidParameterValueException("[VM_GROUP_NAME_DUPLICATE] An instance group with this name already exists for this owner.");
    }

    private static <T> T withOwnerLock(AccountDao accounts, long owner, Supplier<T> operation) {
        AccountVO lock = accounts.acquireInLockTable(owner);
        if (lock == null) {
            throw new CloudRuntimeException("Unable to lock the instance group owner.");
        }
        try {
            return Transaction.execute((TransactionCallback<T>) status -> {
                if (accounts.lockRow(owner, true) == null) {
                    throw new CloudRuntimeException("Unable to lock the instance group owner.");
                }
                return operation.get();
            });
        } finally {
            accounts.releaseFromLockTable(owner);
        }
    }
}
