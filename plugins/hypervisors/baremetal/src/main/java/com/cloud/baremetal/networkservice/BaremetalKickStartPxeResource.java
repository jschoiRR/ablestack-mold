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
// Automatically generated by addcopyright.py at 01/29/2013
package com.cloud.baremetal.networkservice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.ConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.trilead.ssh2.SCPClient;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.HostVmStateReportEntry;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.PingRoutingCommand;
import com.cloud.agent.api.routing.VmDataCommand;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.script.Script;
import com.cloud.utils.ssh.SSHCmdHelper;

public class BaremetalKickStartPxeResource extends BaremetalPxeResourceBase {
    private static final Logger s_logger = Logger.getLogger(BaremetalKickStartPxeResource.class);
    private static final String Name = "BaremetalKickStartPxeResource";
    String _tftpDir;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        _tftpDir = (String)params.get(BaremetalPxeService.PXE_PARAM_TFTP_DIR);
        if (_tftpDir == null) {
            throw new ConfigurationException("No tftp directory specified");
        }

        com.trilead.ssh2.Connection sshConnection = new com.trilead.ssh2.Connection(_ip, 22);

        s_logger.debug(String.format("Trying to connect to kickstart PXE server(IP=%1$s, username=%2$s, password=%3$s", _ip, _username, "******"));
        try {
            sshConnection.connect(null, 60000, 60000);
            if (!sshConnection.authenticateWithPassword(_username, _password)) {
                s_logger.debug("SSH Failed to authenticate");
                throw new ConfigurationException(String.format("Cannot connect to kickstart PXE server(IP=%1$s, username=%2$s, password=%3$s", _ip, _username, "******"));
            }

            String cmd = String.format("[ -f /%1$s/pxelinux.0 ]", _tftpDir);
            if (!SSHCmdHelper.sshExecuteCmd(sshConnection, cmd)) {
                throw new ConfigurationException("Miss files in TFTP directory at " + _tftpDir + " check if pxelinux.0 are here");
            }

            SCPClient scp = new SCPClient(sshConnection);
            String prepareScript = "scripts/network/ping/prepare_kickstart_bootfile.py";
            String prepareScriptPath = Script.findScript("", prepareScript);
            if (prepareScriptPath == null) {
                throw new ConfigurationException("Can not find prepare_kickstart_bootfile.py at " + prepareScript);
            }
            scp.put(prepareScriptPath, "/usr/bin/", "0755");

            String cpScript = "scripts/network/ping/prepare_kickstart_kernel_initrd.py";
            String cpScriptPath = Script.findScript("", cpScript);
            if (cpScriptPath == null) {
                throw new ConfigurationException("Can not find prepare_kickstart_kernel_initrd.py at " + cpScript);
            }
            scp.put(cpScriptPath, "/usr/bin/", "0755");

            String userDataScript = "scripts/network/ping/baremetal_user_data.py";
            String userDataScriptPath = Script.findScript("", userDataScript);
            if (userDataScriptPath == null) {
                throw new ConfigurationException("Can not find baremetal_user_data.py at " + userDataScript);
            }
            scp.put(userDataScriptPath, "/usr/bin/", "0755");

            return true;
        } catch (Exception e) {
            throw new CloudRuntimeException(e);
        } finally {
            if (sshConnection != null) {
                sshConnection.close();
            }
        }
    }

    @Override
    public PingCommand getCurrentStatus(long id) {
        com.trilead.ssh2.Connection sshConnection = SSHCmdHelper.acquireAuthorizedConnection(_ip, _username, _password);
        if (sshConnection == null) {
            return null;
        } else {
            SSHCmdHelper.releaseSshConnection(sshConnection);
            return new PingRoutingCommand(getType(), id, new HashMap<String, HostVmStateReportEntry>());
        }
    }

    private Answer execute(VmDataCommand cmd) {
        com.trilead.ssh2.Connection sshConnection = new com.trilead.ssh2.Connection(_ip, 22);
        try {
            List<String[]> vmData = cmd.getVmData();
            StringBuilder sb = new StringBuilder();
            for (String[] data : vmData) {
                String folder = data[0];
                String file = data[1];
                String contents = (data[2] == null) ? "none" : data[2];
                sb.append(cmd.getVmIpAddress());
                sb.append(",");
                sb.append(folder);
                sb.append(",");
                sb.append(file);
                sb.append(",");
                sb.append(contents);
                sb.append(";");
            }
            String arg = StringUtils.stripEnd(sb.toString(), ";");

            sshConnection.connect(null, 60000, 60000);
            if (!sshConnection.authenticateWithPassword(_username, _password)) {
                s_logger.debug("SSH Failed to authenticate");
                throw new ConfigurationException(String.format("Cannot connect to PING PXE server(IP=%1$s, username=%2$s, password=%3$s", _ip, _username, _password));
            }

            String script = String.format("python /usr/bin/baremetal_user_data.py '%s'", arg);
            if (!SSHCmdHelper.sshExecuteCmd(sshConnection, script)) {
                return new Answer(cmd, false, "Failed to add user data, command:" + script);
            }

            return new Answer(cmd, true, "Success");
        } catch (Exception e) {
            s_logger.debug("Prepare for creating baremetal template failed", e);
            return new Answer(cmd, false, e.getMessage());
        } finally {
            if (sshConnection != null) {
                sshConnection.close();
            }
        }
    }

    @Override
    public Answer executeRequest(Command cmd) {
        if (cmd instanceof PrepareKickstartPxeServerCommand) {
            return execute((PrepareKickstartPxeServerCommand)cmd);
        } else if (cmd instanceof VmDataCommand) {
            return execute((VmDataCommand)cmd);
        } else {
            return super.executeRequest(cmd);
        }
    }

    private Answer execute(PrepareKickstartPxeServerCommand cmd) {
        com.trilead.ssh2.Connection sshConnection = new com.trilead.ssh2.Connection(_ip, 22);
        try {
            sshConnection.connect(null, 60000, 60000);
            if (!sshConnection.authenticateWithPassword(_username, _password)) {
                s_logger.debug("SSH Failed to authenticate");
                throw new ConfigurationException(String.format("Cannot connect to PING PXE server(IP=%1$s, username=%2$s, password=%3$s", _ip, _username, _password));
            }

            String copyTo = String.format("%s/%s", _tftpDir, cmd.getTemplateUuid());
            String script = String.format("python /usr/bin/prepare_kickstart_kernel_initrd.py %s %s %s", cmd.getKernel(), cmd.getInitrd(), copyTo);

            if (!SSHCmdHelper.sshExecuteCmd(sshConnection, script)) {
                return new Answer(cmd, false, "prepare kickstart at pxe server " + _ip + " failed, command:" + script);
            }

            String kernelPath = String.format("%s/vmlinuz", cmd.getTemplateUuid());
            String initrdPath = String.format("%s/initrd.img", cmd.getTemplateUuid());
            script =
                String.format("python /usr/bin/prepare_kickstart_bootfile.py %s %s %s %s %s %s", _tftpDir, cmd.getMac(), kernelPath, initrdPath, cmd.getKsFile(),
                    cmd.getMac());
            if (!SSHCmdHelper.sshExecuteCmd(sshConnection, script)) {
                return new Answer(cmd, false, "prepare kickstart at pxe server " + _ip + " failed, command:" + script);
            }

            s_logger.debug("Prepare kickstart PXE server successfully");
            return new Answer(cmd, true, "Success");
        } catch (Exception e) {
            s_logger.debug("Prepare for kickstart server failed", e);
            return new Answer(cmd, false, e.getMessage());
        } finally {
            if (sshConnection != null) {
                sshConnection.close();
            }
        }
    }
}
