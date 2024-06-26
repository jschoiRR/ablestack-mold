//
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
//

package com.cloud.hypervisor.kvm.resource.wrapper;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.VbmcAnswer;
import com.cloud.agent.api.VbmcCommand;
import com.cloud.exception.InternalErrorException;
import com.cloud.hypervisor.kvm.resource.LibvirtComputingResource;
import com.cloud.resource.ResourceWrapper;

import com.cloud.resource.CommandWrapper;

@ResourceWrapper(handles =  VbmcCommand.class)
public final class VbmcCommandWrapper extends CommandWrapper<VbmcCommand, Answer, LibvirtComputingResource> {


    @Override
    public Answer execute(final VbmcCommand command, final LibvirtComputingResource libvirtComputingResource) {
        try {
            logger.info("VbmcCommand Action Call [ instanceName : " +command.getVmName()+ ", Port : " + command.getPort() + " ]");
            if (libvirtComputingResource.ablestackVbmcCmdLine(command.getAction(), command.getVmName(), command.getPort())) {
                logger.info("VbmcCommand Action >>> Success");
                return new VbmcAnswer(command, "", true);
            } else {
                logger.info("VbmcCommand Action >>> Fail");
                return new VbmcAnswer(command, "", false);
            }
        } catch (InternalErrorException e) {
            return new VbmcAnswer(command, e);
        }
    }
}