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

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CheckOnHostAnswerTest {
    @Test public void kvmAlivePreservesLegacyInvertedResult() {
        CheckOnHostAnswer answer = CheckOnHostAnswer.forKvm(null, true, "alive");
        assertFalse(answer.getResult());
        assertTrue(answer.isDetermined());
        assertTrue(answer.isAlive());
    }
    @Test public void kvmDeadPreservesLegacyInvertedResult() {
        CheckOnHostAnswer answer = CheckOnHostAnswer.forKvm(null, false, "dead");
        assertTrue(answer.getResult());
        assertTrue(answer.isDetermined());
        assertFalse(answer.isAlive());
    }
    @Test public void kvmUnknownIsNeverLegacyDead() {
        CheckOnHostAnswer answer = CheckOnHostAnswer.forKvm(null, null, "unknown");
        assertFalse(answer.getResult());
        assertFalse(answer.isDetermined());
    }
    @Test public void existingConstructorSemanticsAreUnchanged() {
        assertTrue(new CheckOnHostAnswer(null, Boolean.TRUE, "other hypervisor").getResult());
        assertTrue(new CheckOnHostAnswer(null, Boolean.FALSE, "other hypervisor").getResult());
    }
}
