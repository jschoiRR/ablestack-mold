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

package org.apache.cloudstack.ha;

import org.junit.Test;
import static org.junit.Assert.*;

public class HAResourceCounterTest {
    @Test
    public void invalidationKeepsRunningReservationUntilWorkerFinishes() {
        HAResourceCounter counter = new HAResourceCounter();
        HAResourceCounter.TaskToken old = counter.tryStartTask(HAResourceCounter.Operation.FENCE);
        counter.resetForNewCycle();
        assertFalse(counter.isCurrentTask(old));
        assertNull(counter.tryStartTask(HAResourceCounter.Operation.FENCE));
        counter.finishTask(old);
        HAResourceCounter.TaskToken next = counter.tryStartTask(HAResourceCounter.Operation.FENCE);
        assertNotNull(next);
        counter.finishTask(old);
        assertTrue(counter.isCurrentTask(next));
    }

    @Test
    public void unknownBreaksSequenceWithoutCountingADeadSample() {
        HAResourceCounter counter = new HAResourceCounter();
        counter.incrActivityCounter(true);
        counter.incrActivityCounter(true);
        counter.breakActivitySequences();
        assertEquals(2, counter.getActivityCheckCounter());
        assertEquals(2, counter.getActivityCheckFailureCounter());
        assertEquals(0, counter.getConsecutiveActivityCheckFailureCounter());
        counter.incrActivityCounter(true);
        assertEquals(1, counter.getConsecutiveActivityCheckFailureCounter());
    }

    @Test
    public void aliveAndDeadObservationsResetTheOppositeSequence() {
        HAResourceCounter counter = new HAResourceCounter();
        counter.incrActivityCounter(false);
        counter.incrActivityCounter(false);
        assertEquals(2, counter.getConsecutiveActivityCheckSuccessCounter());
        assertEquals(0, counter.getConsecutiveActivityCheckFailureCounter());
        counter.incrActivityCounter(true);
        assertEquals(0, counter.getConsecutiveActivityCheckSuccessCounter());
        assertEquals(1, counter.getConsecutiveActivityCheckFailureCounter());
        counter.incrActivityCounter(false);
        assertEquals(1, counter.getConsecutiveActivityCheckSuccessCounter());
        assertEquals(0, counter.getConsecutiveActivityCheckFailureCounter());
        assertEquals(4, counter.getActivityCheckCounter());
    }

    @Test
    public void unknownBreaksAliveSequenceWithoutInventingAnObservation() {
        HAResourceCounter counter = new HAResourceCounter();
        counter.incrActivityCounter(false);
        counter.incrActivityCounter(false);
        counter.breakActivitySequences();
        assertEquals(2, counter.getActivityCheckCounter());
        assertEquals(0, counter.getActivityCheckFailureCounter());
        assertEquals(0, counter.getConsecutiveActivityCheckSuccessCounter());
        assertEquals(0, counter.getConsecutiveActivityCheckFailureCounter());
    }

    @Test
    public void activityAndCycleResetsClearAliveSequence() {
        HAResourceCounter counter = new HAResourceCounter();
        counter.incrActivityCounter(false);
        counter.resetActivityCounter();
        assertEquals(0, counter.getConsecutiveActivityCheckSuccessCounter());
        assertEquals(0, counter.getActivityCheckCounter());
        counter.incrActivityCounter(false);
        counter.resetForNewCycle();
        assertEquals(0, counter.getConsecutiveActivityCheckSuccessCounter());
        assertEquals(0, counter.getActivityCheckCounter());
    }

    @Test
    public void suspectTimestampIsStableAndNewCycleResetsIt() throws Exception {
        HAResourceCounter counter = new HAResourceCounter();
        counter.markResourceSuspected();
        long first = counter.getSuspectTimeStamp();
        java.lang.reflect.Field field = HAResourceCounter.class.getDeclaredField("firstHealthCheckFailureTimestamp");
        field.setAccessible(true);
        field.set(counter, first - 1000);
        counter.markResourceSuspected();
        assertEquals(first - 1000, counter.getSuspectTimeStamp());
        counter.resetForNewCycle();
        assertTrue(counter.getSuspectTimeStamp() >= first);
    }

    @Test
    public void activityCompletionRequiresHealthBeforeAnotherActivity() {
        HAResourceCounter counter = new HAResourceCounter();
        HAResourceCounter.TaskToken token = counter.tryStartTask(HAResourceCounter.Operation.ACTIVITY);
        counter.finishTask(token);
        assertTrue(counter.needsHealthCheck());
        token = counter.tryStartTask(HAResourceCounter.Operation.HEALTH);
        counter.finishTask(token);
        assertFalse(counter.needsHealthCheck());
    }
}
