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

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "autoscale_creation_requests")
public class AutoScaleCreationRequestVO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;
    @Column(name = "uuid")
    private String uuid;
    @Column(name = "operation_uuid")
    private String operationUuid;
    @Column(name = "stage_key")
    private String stageKey;
    @Column(name = "user_id")
    private long userId;
    @Column(name = "account_id")
    private long accountId;
    @Column(name = "command")
    private String command;
    @Column(name = "fingerprint")
    private String fingerprint;
    @Column(name = "status")
    private String status;
    @Column(name = "resource_id")
    private Long resourceId;
    @Column(name = "resource_uuid")
    private String resourceUuid;
    @Column(name = "resource_type")
    private String resourceType;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created")
    private Date created;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated")
    private Date updated;

    protected AutoScaleCreationRequestVO() { }

    public AutoScaleCreationRequestVO(String uuid, long userId, long accountId, String command, String fingerprint, String resourceType) {
        this.uuid = uuid;
        this.userId = userId;
        this.accountId = accountId;
        this.command = command;
        this.fingerprint = fingerprint;
        this.resourceType = resourceType;
        this.status = "STARTED";
        this.updated = new Date();
    }

    public void setOperation(String operationUuid, String stageKey) { this.operationUuid = operationUuid; this.stageKey = stageKey; }
    public String getOperationUuid() { return operationUuid; }
    public String getStageKey() { return stageKey; }
    public long getId() { return id; }
    public String getUuid() { return uuid; }
    public long getUserId() { return userId; }
    public long getAccountId() { return accountId; }
    public String getCommand() { return command; }
    public String getFingerprint() { return fingerprint; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; setUpdated(new Date()); }
    public void setUpdated(Date value) { updated = value; }
    public void setResourceId(Long value) { resourceId = value; }
    public void setResourceUuid(String value) { resourceUuid = value; }
    public Long getResourceId() { return resourceId; }
    public String getResourceUuid() { return resourceUuid; }
    public String getResourceType() { return resourceType; }
    public void clearResource() { setResourceId(null); setResourceUuid(null); }
    public void setResource(long id, String uuid) { setResourceId(id); setResourceUuid(uuid); setStatus("CREATED"); }
    public Date getCreated() { return created; }
    public Date getUpdated() { return updated; }
}
