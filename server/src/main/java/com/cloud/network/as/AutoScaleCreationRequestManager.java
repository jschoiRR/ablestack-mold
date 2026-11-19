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
package com.cloud.network.as;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import javax.inject.Inject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.cloudstack.acl.ControlledEntity;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiServerService.AsyncCmdResult;
import org.apache.cloudstack.api.BaseAsyncCmd;
import org.apache.cloudstack.api.BaseAsyncCreateCmd;
import org.apache.cloudstack.api.InternalIdentity;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.command.user.autoscale.CreateAutoScalePolicyCmd;
import org.apache.cloudstack.api.command.user.autoscale.CreateAutoScaleVmGroupCmd;
import org.apache.cloudstack.api.command.user.autoscale.CreateAutoScaleVmProfileCmd;
import org.apache.cloudstack.api.command.user.autoscale.CreateConditionCmd;
import org.apache.cloudstack.api.response.AutoScaleCreationRequestResponse;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.jobs.dao.AsyncJobDao;
import org.apache.cloudstack.framework.jobs.impl.AsyncJobVO;
import org.springframework.stereotype.Component;

import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.network.as.dao.AutoScaleCreationRequestDao;
import com.cloud.user.AccountManager;
import com.cloud.utils.db.EntityManager;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import org.apache.cloudstack.api.command.user.autoscale.BaseAutoScaleCreateCmd;
import com.cloud.utils.exception.CloudRuntimeException;
import com.google.gson.Gson;

/** Durable, user-scoped receipts for the existing Cloud async creation APIs. */
@Component
public class AutoScaleCreationRequestManager implements PluggableService {
    @Override
    public List<Class<?>> getCommands() {
        return Collections.singletonList(org.apache.cloudstack.api.command.user.autoscale.QueryAutoScaleCreationRequestCmd.class);
    }

    @Inject AutoScaleCreationRequestDao requestDao;
    @Inject AsyncJobDao jobDao;
    @Inject EntityManager entityMgr;
    @Inject AccountManager accountMgr;

    @FunctionalInterface
    public interface Work<T> {
        T run() throws Exception;
    }

    public static boolean supports(BaseAsyncCmd cmd) {
        return cmd instanceof CreateAutoScaleVmProfileCmd || cmd instanceof CreateConditionCmd
                || cmd instanceof CreateAutoScalePolicyCmd || cmd instanceof CreateAutoScaleVmGroupCmd;
    }

    static String requestId(String value) {
        try {
            String canonical = UUID.fromString(value).toString();
            if (!canonical.equals(value)) {
                throw new IllegalArgumentException();
            }
            return canonical;
        } catch (RuntimeException e) {
            throw new InvalidParameterValueException("AutoScale customjobid must be a canonical UUID");
        }
    }

    // The submit lock includes resource creation AND job insertion, without holding an
    // outer DB transaction while AsyncJobManager schedules execution on another thread.
    public AsyncCmdResult submit(BaseAsyncCmd cmd, Map<String, String> params, Work<AsyncCmdResult> work) throws Exception {
        if (!supports(cmd) || params.get(ApiConstants.CUSTOM_JOB_ID) == null) {
            return work.run();
        }
        return withLock("submit-" + requestId(params.get(ApiConstants.CUSTOM_JOB_ID)), work);
    }

    protected <T> T withLock(String key, Work<T> work) throws Exception {
        GlobalLock lock = GlobalLock.getInternLock("autoscale-creation-" + key);
        boolean locked = false;
        try {
            locked = lock.lock(30);
            if (!locked) {
                throw new CloudRuntimeException("AutoScale creation request is busy; query the existing request");
            }
            return work.run();
        } finally {
            if (locked) {
                lock.unlock();
            }
            lock.releaseRef();
        }
    }

    protected <T> T transaction(Supplier<T> work) {
        return Transaction.execute((TransactionCallback<T>) status -> work.get());
    }

    static String fingerprint(BaseAsyncCreateCmd cmd) {
        Map<String, Object> fields = new TreeMap<>();
        for (Class<?> type = cmd.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                Parameter parameter = field.getAnnotation(Parameter.class);
                if (parameter == null || ApiConstants.CUSTOM_JOB_ID.equals(parameter.name()) || "starteventid".equals(parameter.name())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(cmd);
                    if (value != null) {
                        fields.put(parameter.name(), new Gson().toJsonTree(value));
                    }
                } catch (IllegalAccessException e) {
                    throw new CloudRuntimeException("Unable to fingerprint AutoScale creation request", e);
                }
            }
        }
        // Recursively order map keys as indexed API maps can arrive in a different order.
        return DigestUtils.sha256Hex(canonical(new Gson().toJsonTree(fields)).toString());
    }

    private static com.google.gson.JsonElement canonical(com.google.gson.JsonElement value) {
        if (value.isJsonObject()) {
            com.google.gson.JsonObject result = new com.google.gson.JsonObject();
            Map<String, com.google.gson.JsonElement> sorted = new TreeMap<>();
            value.getAsJsonObject().entrySet().forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
            sorted.forEach((key, element) -> result.add(key, canonical(element)));
            return result;
        }
        if (value.isJsonArray()) {
            com.google.gson.JsonArray result = new com.google.gson.JsonArray();
            value.getAsJsonArray().forEach(element -> result.add(canonical(element)));
            return result;
        }
        return value;
    }

    public <T extends ControlledEntity> T create(BaseAsyncCreateCmd cmd, Class<? extends T> resourceClass, Supplier<T> work) {
        if (cmd.getInjectedJobId() == null) {
            return work.get();
        }
        String id = requestId(cmd.getInjectedJobId());
        try {
            return withLock("resource-" + id, () -> createLocked(cmd, resourceClass, work, id));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CloudRuntimeException("Unable to process AutoScale creation request", e);
        }
    }

    protected <T extends ControlledEntity> T createLocked(BaseAsyncCreateCmd cmd, Class<? extends T> resourceClass, Supplier<T> work, String id) {
        String hash = fingerprint(cmd);
        String command = cmd.getClass().getAnnotation(APICommand.class).name();
        long userId = CallContext.current().getCallingUserId();
        long ownerId = cmd.getEntityOwnerId();
        BaseAutoScaleCreateCmd stageCmd = (BaseAutoScaleCreateCmd) cmd;
        String operationId = stageCmd.getCreationOperationId();
        String stage = stageCmd.getCreationStage();
        if (operationId != null || stage != null) {
            requestId(operationId);
            if (stage == null || !stage.matches("[A-Za-z0-9._-]{1,96}")) {
                throw new InvalidParameterValueException("AutoScale creationstage is invalid");
            }
        }
        AutoScaleCreationRequestVO receipt = requestDao.findByUuid(id);
        if (receipt != null) {
            if (receipt.getUserId() != userId || receipt.getAccountId() != ownerId || !command.equals(receipt.getCommand())
                    || !hash.equals(receipt.getFingerprint())) {
                throw new InvalidParameterValueException("AutoScale creation request belongs to another user, owner, command or specification");
            }
            if (receipt.getResourceId() != null) {
                T resource = entityMgr.findById(resourceClass, receipt.getResourceId());
                if (resource == null || !receipt.getResourceUuid().equals(uuid(resource))) {
                    throw new InvalidParameterValueException("The resource of this AutoScale creation request was removed; start a new request");
                }
                accountMgr.checkAccess(CallContext.current().getCallingAccount(), null, true, resource);
                return resource;
            }
            if ("FAILED".equals(receipt.getStatus())) {
                throw new InvalidParameterValueException("This AutoScale creation request failed; start a new request after cleanup");
            }
        } else {
            if (jobDao.findByUuidIncludingRemoved(id) != null) {
                throw new InvalidParameterValueException("customjobid is already in use");
            }
            receipt = transaction(() -> {
                AutoScaleCreationRequestVO newReceipt = new AutoScaleCreationRequestVO(id, userId, ownerId, command, hash,
                        cmd.getApiResourceType().toString());
                newReceipt.setOperation(operationId, stage);
                return requestDao.persist(newReceipt);
            });
        }
        final AutoScaleCreationRequestVO current = receipt;
        try {
            return transaction(() -> {
                T resource = work.get();
                current.setResource(((InternalIdentity) resource).getId(), uuid(resource));
                if (!requestDao.update(current.getId(), current)) {
                    throw new CloudRuntimeException("Unable to commit AutoScale creation receipt");
                }
                CallContext.current().setEventDetails("requestid=" + id + ", resourceid=" + current.getResourceUuid());
                return resource;
            });
        } catch (RuntimeException error) {
            transaction(() -> {
                current.clearResource();
                current.setStatus("FAILED");
                requestDao.update(current.getId(), current);
                return null;
            });
            throw error;
        }
    }

    public AsyncJobVO submittedJob(BaseAsyncCmd cmd, Long resourceId, long userId, long accountId) {
        if (!supports(cmd) || cmd.getInjectedJobId() == null) {
            return null;
        }
        AsyncJobVO job = jobDao.findByUuidIncludingRemoved(cmd.getInjectedJobId());
        if (job != null && (job.getUserId() != userId || job.getAccountId() != accountId
                || !cmd.getClass().getName().equals(job.getCmd()) || !Objects.equals(resourceId, job.getInstanceId())
                || (job.getRemoved() != null && job.getStatus().ordinal() == 0))) {
            throw new InvalidParameterValueException("The async job of this AutoScale creation request is not available for replay");
        }
        return job;
    }

    private static String uuid(ControlledEntity resource) {
        if (resource instanceof AutoScaleVmProfile) {
            return ((AutoScaleVmProfile) resource).getUuid();
        }
        if (resource instanceof AutoScaleVmGroup) {
            return ((AutoScaleVmGroup) resource).getUuid();
        }
        if (resource instanceof AutoScalePolicy) {
            return ((AutoScalePolicy) resource).getUuid();
        }
        return ((Condition) resource).getUuid();
    }

    private ControlledEntity resource(AutoScaleCreationRequestVO receipt) {
        if (receipt.getResourceId() == null) {
            return null;
        }
        switch (receipt.getResourceType()) {
            case "AutoScaleVmProfile": return entityMgr.findById(AutoScaleVmProfile.class, receipt.getResourceId());
            case "AutoScaleVmGroup": return entityMgr.findById(AutoScaleVmGroup.class, receipt.getResourceId());
            case "AutoScalePolicy": return entityMgr.findById(AutoScalePolicy.class, receipt.getResourceId());
            case "Condition": return entityMgr.findById(Condition.class, receipt.getResourceId());
            default: return null;
        }
    }

    public AutoScaleCreationRequestResponse query(String value) {
        String id = requestId(value);
        AutoScaleCreationRequestResponse response = new AutoScaleCreationRequestResponse();
        response.setRequestId(id);
        response.setStatus("NOTFOUND");
        AutoScaleCreationRequestVO receipt = requestDao.findByUuid(id);
        if (receipt == null || receipt.getUserId() != CallContext.current().getCallingUserId()) {
            return response;
        }
        ControlledEntity resource = resource(receipt);
        if (resource != null) {
            try {
                accountMgr.checkAccess(CallContext.current().getCallingAccount(), null, true, resource);
            } catch (PermissionDeniedException e) {
                return response;
            }
        }
        response.setCreationOperationId(receipt.getOperationUuid());
        response.setCreationStage(receipt.getStageKey());
        response.setCommand(receipt.getCommand());
        response.setResourceType(receipt.getResourceType());
        response.setResourceId(receipt.getResourceUuid());
        response.setStatus(receipt.getStatus());
        if (receipt.getResourceId() != null && resource == null) {
            response.setStatus("REMOVED");
        }
        AsyncJobVO job = jobDao.findByUuidIncludingRemoved(id);
        if (job != null && job.getUserId() == receipt.getUserId()
                && Objects.equals(job.getInstanceId(), receipt.getResourceId())
                && Objects.equals(job.getInstanceType(), receipt.getResourceType())) {
            response.setJobId(job.getUuid());
            response.setJobStatus(job.getStatus().ordinal());
            if (resource != null) {
                response.setStatus(job.getStatus().ordinal() == 0 ? "PENDING" : job.getStatus().ordinal() == 1 ? "SUCCESS" : "FAILED");
            }
        } else if (resource != null) {
            // The job may have expired. Committed simple resources are complete;
            // a NEW group is still waiting for configuration and must not be called successful.
            if (!(resource instanceof AutoScaleVmGroup)) {
                response.setStatus("SUCCESS");
            } else if (((AutoScaleVmGroup) resource).getState() == AutoScaleVmGroup.State.NEW) {
                response.setStatus("CREATED");
            } else if (((AutoScaleVmGroup) resource).getState() == AutoScaleVmGroup.State.ENABLED
                    || ((AutoScaleVmGroup) resource).getState() == AutoScaleVmGroup.State.SCALING
                    || ((AutoScaleVmGroup) resource).getState() == AutoScaleVmGroup.State.DISABLED) {
                response.setStatus("SUCCESS");
            } else {
                response.setStatus("UNKNOWN");
            }
        }
        if (resource instanceof AutoScaleVmGroup) {
            response.setResourceState(((AutoScaleVmGroup) resource).getState().toString());
        }
        return response;
    }
}
