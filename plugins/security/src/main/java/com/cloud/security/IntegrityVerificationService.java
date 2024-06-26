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

package com.cloud.security;

import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.api.command.admin.DeleteIntegrityVerificationFinalResultCmd;
import org.apache.cloudstack.api.command.admin.GetIntegrityVerificationCmd;
import org.apache.cloudstack.api.command.admin.GetIntegrityVerificationFinalResultCmd;
import org.apache.cloudstack.api.command.admin.RunIntegrityVerificationCmd;
import org.apache.cloudstack.api.response.GetIntegrityVerificationFinalResultListResponse;
import org.apache.cloudstack.api.response.GetIntegrityVerificationResponse;
import org.apache.cloudstack.api.response.ListResponse;

import java.security.NoSuchAlgorithmException;
import java.util.List;


public interface IntegrityVerificationService {

    List<GetIntegrityVerificationResponse> listIntegrityVerifications(GetIntegrityVerificationCmd cmd);

    ListResponse<GetIntegrityVerificationFinalResultListResponse> listIntegrityVerificationFinalResults(GetIntegrityVerificationFinalResultCmd cmd);

    boolean runIntegrityVerificationCommand(RunIntegrityVerificationCmd runIntegrityVerificationCmd) throws NoSuchAlgorithmException;

    boolean deleteIntegrityVerificationFinalResults(DeleteIntegrityVerificationFinalResultCmd deleteIntegrityVerificationFinalResultCmd) throws CloudRuntimeException;
}
