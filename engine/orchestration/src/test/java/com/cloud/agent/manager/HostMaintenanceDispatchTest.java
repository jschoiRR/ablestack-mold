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
package com.cloud.agent.manager;

import com.cloud.agent.api.CheckHealthCommand;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.transport.Request;
import com.cloud.agent.transport.Response;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.HostDao;
import com.cloud.hypervisor.Hypervisor;
import com.cloud.resource.ResourceState;
import com.cloud.utils.nio.Link;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class HostMaintenanceDispatchTest {
    private AgentManagerImpl manager;
    private HostVO host;
    private StartCommand start;

    @Before
    public void setUp() {
        manager = new AgentManagerImpl();
        manager._hostDao = mock(HostDao.class);
        host = new HostVO("host-7");
        host.setResourceState(ResourceState.Maintenance);
        when(manager._hostDao.findById(7L)).thenReturn(host);
        start = mock(StartCommand.class);
        when(start.isBypassHostMaintenance()).thenReturn(true);
    }

    @Test(expected = AgentUnavailableException.class)
    public void directDispatchRejectsStartAfterFastReconnectWithStaleCachedState() throws Exception {
        Link link = mock(Link.class);
        ConnectedAgentAttache attache = new ConnectedAgentAttache(manager, 7L, "host-7", "host-7", Hypervisor.HypervisorType.KVM, link, false);
        attache.ready();
        Request request = mock(Request.class);
        when(request.getCommands()).thenReturn(new Command[] {start});
        try {
            attache.send(request);
        } finally {
            verify(link, never()).send(any(ByteBuffer[].class));
        }
    }

    @Test
    public void queuedStartIsRecheckedAndDoesNotBlockLaterHealthCommand() {
        final List<Request> sent = new ArrayList<>();
        AgentAttache attache = new AgentAttache(manager, 7L, "host-7", "host-7", Hypervisor.HypervisorType.KVM, false) {
            @Override public void send(Request request) { sent.add(request); }
            @Override public void disconnect(Status status) { }
            @Override protected boolean isClosed() { return false; }
        };
        attache.ready();
        Request queuedStart = mock(Request.class);
        when(queuedStart.getSequence()).thenReturn(2L);
        when(queuedStart.getCommands()).thenReturn(new Command[] {start});
        Request queuedHealth = mock(Request.class);
        when(queuedHealth.getSequence()).thenReturn(3L);
        when(queuedHealth.getCommands()).thenReturn(new Command[] {new CheckHealthCommand()});
        attache._requests.add(queuedStart);
        attache._requests.add(queuedHealth);

        attache.sendNext(1L);

        assertEquals(List.of(queuedHealth), sent);
        assertEquals(Long.valueOf(3L), attache._currentSequence);
        assertTrue(attache._requests.isEmpty());
    }

    @Test
    public void enabledHostCanStillStartVms() throws Exception {
        host.setResourceState(ResourceState.Enabled);
        manager.checkHostMaintenanceBeforeStart(7L, new Command[] {start});
    }

    @Test
    public void routineHealthDoesNotAddADatabaseLookup() throws Exception {
        manager.checkHostMaintenanceBeforeStart(7L, new Command[] {new CheckHealthCommand()});
        verifyNoInteractions(manager._hostDao);
    }

    @Test
    public void startupAnswersAreNotParsedAsVmStartRequests() throws Exception {
        ConnectedAgentAttache attache = new ConnectedAgentAttache(manager, 7L, "host-7", "host-7",
                Hypervisor.HypervisorType.KVM, mock(Link.class), false);
        Response response = mock(Response.class);
        attache.send(response);
        verify(response, never()).getCommands();
        verifyNoInteractions(manager._hostDao);
    }
}
