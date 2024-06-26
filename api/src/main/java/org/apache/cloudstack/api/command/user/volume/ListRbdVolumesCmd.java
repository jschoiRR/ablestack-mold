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
package org.apache.cloudstack.api.command.user.volume;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ResponseObject.ResponseView;
import org.apache.cloudstack.api.command.user.UserCmd;
import org.apache.cloudstack.api.response.VolumeResponse;

import com.cloud.storage.Volume;

@APICommand(name = "listVolumes", description = "Lists all volumes.", responseObject = VolumeResponse.class, responseView = ResponseView.Restricted, entityType = {
        Volume.class}, requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListRbdVolumesCmd extends ListVolumesCmd implements UserCmd {

    @Parameter(name = ApiConstants.CUSTOMIMAGES, type = CommandType.STRING, description = "state of the volume. Possible values are: Ready, Allocated, Destroy, Expunging, Expunged.")
    private Boolean customImages;

    public Boolean getCustomImages() {
    return customImages;
    }
}
