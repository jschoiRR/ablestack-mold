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

import org.apache.cloudstack.api.BaseAsyncCreateCmd;
import org.apache.cloudstack.api.Parameter;

/** Optional logical-operation correlation; customjobid still identifies each stage. */
public abstract class BaseAutoScaleCreateCmd extends BaseAsyncCreateCmd {
    @Parameter(name = "creationoperationid", type = CommandType.STRING, description = "The UUID of the containing creation operation")
    private String creationOperationId;
    @Parameter(name = "creationstage", type = CommandType.STRING, description = "The stage key within the creation operation")
    private String creationStage;

    public String getCreationOperationId() { return creationOperationId; }
    public String getCreationStage() { return creationStage; }
}
