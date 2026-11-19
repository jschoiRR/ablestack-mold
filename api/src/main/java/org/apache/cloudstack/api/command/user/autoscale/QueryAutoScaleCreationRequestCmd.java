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
package org.apache.cloudstack.api.command.user.autoscale;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.acl.RoleType;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.response.AutoScaleCreationRequestResponse;

@APICommand(name = "queryAutoScaleCreationRequest", description = "Finds a creation stage submitted by the current user using customjobid",
        responseObject = AutoScaleCreationRequestResponse.class, authorized = {RoleType.Admin, RoleType.ResourceAdmin, RoleType.DomainAdmin, RoleType.User}, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class QueryAutoScaleCreationRequestCmd extends BaseCmd {
    @Parameter(name = "requestid", type = CommandType.STRING, required = true, description = "The customjobid of the creation stage")
    private String requestId;

    public String getRequestId() { return requestId; }

    @Override
    public void execute() {
        AutoScaleCreationRequestResponse response = _autoScaleService.queryAutoScaleCreationRequest(requestId);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    @Override
    public String getCommandName() { return "autoscalecreationrequestresponse"; }

    @Override
    public long getEntityOwnerId() { return org.apache.cloudstack.context.CallContext.current().getCallingAccount().getId(); }
}
