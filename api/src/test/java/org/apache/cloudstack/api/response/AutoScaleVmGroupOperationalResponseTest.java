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

import java.util.Date;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class AutoScaleVmGroupOperationalResponseTest {
    private JsonObject json(AutoScaleVmGroupResponse response) {
        return new Gson().toJsonTree(response).getAsJsonObject();
    }

    @Test
    public void corruptEvaluationDoesNotBreakGroupVisibility() {
        AutoScaleVmGroupResponse response = new AutoScaleVmGroupResponse();
        response.setEvaluation("{broken");
        assertTrue(json(response).get("evaluationerror").getAsBoolean());
        assertFalse(json(response).has("evaluation"));
        response.setEvaluation("{\"status\":\"WAITING\"}");
        assertFalse(json(response).get("evaluationerror").getAsBoolean());
        assertEquals("WAITING", json(response).getAsJsonObject("evaluation").get("status").getAsString());
    }

    @Test
    public void missingEvaluationIsNotAZeroMetric() {
        AutoScaleVmGroupResponse response = new AutoScaleVmGroupResponse();
        response.setEvaluation(null);
        assertFalse(json(response).has("evaluation"));
        assertFalse(json(response).get("evaluationerror").getAsBoolean());
        response.setEvaluation("[]");
        assertTrue(json(response).get("evaluationerror").getAsBoolean());
    }

    @Test
    public void operationalTimesPreserveMillisecondsAndOmitUnknownSuccess() {
        AutoScaleVmGroupResponse response = new AutoScaleVmGroupResponse();
        assertFalse(json(response).has("lastscaledat"));
        response.setLastScaledAt(new Date(1791712345678L));
        response.setOperationDeadline(new Date(1791712389123L));
        assertEquals(1791712345678L, json(response).get("lastscaledat").getAsLong());
        assertEquals(1791712389123L, json(response).get("operationdeadline").getAsLong());
    }

    @Test
    public void summaryContainsCountsWithoutVmSecretsOrOperationOwnershipToken() {
        AutoScaleVmGroupResponse response = new AutoScaleVmGroupResponse();
        response.setMemberStateCounts(Map.of("Running", 1, "Stopped", 2, "Removed", 1));
        JsonObject value = json(response);
        assertEquals(2, value.getAsJsonObject("memberstatecounts").get("Stopped").getAsInt());
        assertFalse(value.has("operationuuid"));
        assertFalse(value.has("operationvmid"));
        assertFalse(value.has("userdata"));
    }
}
