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

package com.cloud.agent.api;

import java.util.regex.Pattern;

public class PcsFenceHostCommand extends Command {
    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,254}");

    private final String clusterName;
    private final String targetNode;
    private final String executorNode;
    private final long expiresAtMillis;

    public PcsFenceHostCommand(String clusterName, String targetNode, String executorNode, long expiresAtMillis) {
        this.clusterName = clusterName;
        this.targetNode = targetNode;
        this.executorNode = executorNode;
        this.expiresAtMillis = expiresAtMillis;
    }

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getTargetNode() {
        return targetNode;
    }

    public String getExecutorNode() {
        return executorNode;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }

    @Override
    public boolean allowCaching() {
        return false;
    }
}
