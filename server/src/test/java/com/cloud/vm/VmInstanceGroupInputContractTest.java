// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements. See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership. The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.vm;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.cloud.api.dispatch.ParamProcessWorker;
import com.cloud.exception.InvalidParameterValueException;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.command.user.vm.BaseDeployVMCmd;
import org.apache.cloudstack.api.command.user.vm.DeployVMCmd;
import org.apache.cloudstack.api.command.user.vm.DeployVMVolumeCmd;
import org.apache.cloudstack.api.command.user.vm.UpdateVMCmd;
import org.apache.cloudstack.api.command.user.vmgroup.CreateVMGroupCmd;
import org.apache.cloudstack.api.command.user.vmgroup.UpdateVMGroupCmd;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Exercises the actual API STRING dispatcher before the shared name policy. */
public class VmInstanceGroupInputContractTest {
    private Field dispatch(BaseCmd command, Class<?> declaringClass, String fieldName, String value) throws Exception {
        Field field = declaringClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        Method setter = ParamProcessWorker.class.getDeclaredMethod("setFieldValue", Field.class, BaseCmd.class, Object.class, Parameter.class);
        setter.setAccessible(true);
        setter.invoke(new ParamProcessWorker(), field, command, value, field.getAnnotation(Parameter.class));
        return field;
    }

    @Test public void createAllows255CharactersWithBoundaryPadding() throws Exception {
        CreateVMGroupCmd command = new CreateVMGroupCmd();
        dispatch(command, CreateVMGroupCmd.class, "groupName", " " + "가".repeat(255) + " ");
        assertEquals("가".repeat(255), VmInstanceGroupNames.normalize(command.getGroupName()));
    }
    @Test public void renamePreservesSupplementaryNameAtTheStorageBoundary() throws Exception {
        UpdateVMGroupCmd command = new UpdateVMGroupCmd();
        String name = "\ud83d\ude00".repeat(127) + "A";
        dispatch(command, UpdateVMGroupCmd.class, "groupName", " " + name + " ");
        assertEquals(name, VmInstanceGroupNames.normalize(command.getGroupName()));
    }
    @Test public void vmUpdateUsesTheSameInputBoundary() throws Exception {
        UpdateVMCmd command = new UpdateVMCmd();
        dispatch(command, UpdateVMCmd.class, "group", " " + "가".repeat(255) + " ");
        assertEquals("가".repeat(255), VmInstanceGroupNames.normalize(command.getGroup()));
    }
    @Test public void vmDeployUsesTheSameInputBoundary() throws Exception {
        DeployVMCmd command = new DeployVMCmd();
        dispatch(command, BaseDeployVMCmd.class, "group", " " + "가".repeat(255) + " ");
        assertEquals("가".repeat(255), VmInstanceGroupNames.normalize(command.getGroup()));
    }
    @Test public void volumeVmDeployUsesTheSameInputBoundary() throws Exception {
        DeployVMVolumeCmd command = new DeployVMVolumeCmd();
        dispatch(command, DeployVMVolumeCmd.class, "group", " " + "가".repeat(255) + " ");
        assertEquals("가".repeat(255), VmInstanceGroupNames.normalize(command.getGroup()));
    }
    @Test public void rawInputIsStillBoundedBeforeAssignment() throws Exception {
        CreateVMGroupCmd command = new CreateVMGroupCmd();
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> dispatch(command, CreateVMGroupCmd.class, "groupName", " ".repeat(1024) + "A"));
        assertTrue(failure.getCause() instanceof InvalidParameterValueException);
        assertNull(command.getGroupName());
    }
    @Test public void unrelatedVmStringBoundaryIsUnchanged() throws Exception {
        UpdateVMCmd command = new UpdateVMCmd();
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> dispatch(command, UpdateVMCmd.class, "displayName", "A".repeat(256)));
        assertTrue(failure.getCause() instanceof InvalidParameterValueException);
    }
}
