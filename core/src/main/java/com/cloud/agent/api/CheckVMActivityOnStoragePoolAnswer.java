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

/** A failed probe is not evidence that a VM has stopped. */
public class CheckVMActivityOnStoragePoolAnswer extends Answer {
    public enum ActivityState { ALIVE, DEAD, UNKNOWN }

    private ActivityState activityState;

    protected CheckVMActivityOnStoragePoolAnswer() {
    }

    public CheckVMActivityOnStoragePoolAnswer(CheckVMActivityOnStoragePoolCommand command, ActivityState state, String details) {
        // Preserve the historical inverted result for older management servers.
        super(command, state == ActivityState.DEAD, details);
        activityState = state;
    }

    public ActivityState getActivityState() {
        return activityState == null ? ActivityState.UNKNOWN : activityState;
    }
}
