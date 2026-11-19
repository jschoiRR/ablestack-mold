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
package org.apache.cloudstack.api.response;

import com.google.gson.annotations.SerializedName;
import org.apache.cloudstack.api.BaseResponse;
import com.cloud.serializer.Param;

/** A creation receipt contains identifiers only, never deployment credentials or UserData. */
public class AutoScaleCreationRequestResponse extends BaseResponse {
    public AutoScaleCreationRequestResponse() { super("autoscalecreationrequest"); }
    @SerializedName("requestid")
    @Param(description = "The customjobid supplied for this creation stage")
    private String requestId;
    @SerializedName("creationoperationid")
    @Param(description = "The containing creation operation UUID, when supplied")
    private String creationOperationId;
    @SerializedName("creationstage")
    @Param(description = "The stage key within the creation operation")
    private String creationStage;
    @SerializedName("status")
    @Param(description = "NOTFOUND, STARTED, CREATED, PENDING, SUCCESS, FAILED, REMOVED, or UNKNOWN")
    private String status;
    @SerializedName("command")
    @Param(description = "The creation command")
    private String command;
    @SerializedName("resourceid")
    @Param(description = "The UUID of the resource created by this request")
    private String resourceId;
    @SerializedName("resourcetype")
    @Param(description = "The resource type")
    private String resourceType;
    @SerializedName("resourcestate")
    @Param(description = "The current group state, when applicable")
    private String resourceState;
    public void setRequestId(String value) { requestId = value; }
    public void setCreationOperationId(String value) { creationOperationId = value; }
    public void setCreationStage(String value) { creationStage = value; }
    public void setStatus(String value) { status = value; }
    public String getStatus() { return status; }
    public void setCommand(String value) { command = value; }
    public void setResourceId(String value) { resourceId = value; }
    public String getResourceId() { return resourceId; }
    public void setResourceType(String value) { resourceType = value; }
    public void setResourceState(String value) { resourceState = value; }
}
