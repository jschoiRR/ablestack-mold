<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements. See the NOTICE file
distributed with this work for additional information
regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the
specific language governing permissions and limitations
under the License.
-->

# Cluster 32 runtime evidence

## backend-health

```json
{
  "parameters": [
    "leaseexpiryaction",
    "haenable",
    "cleanupextraconfig",
    "userdata",
    "dhcpoptionsnetworklist",
    "cleanupdetails",
    "details",
    "customid",
    "group",
    "cleargroup",
    "name",
    "securitygroupnames",
    "ostypeid",
    "userdataid",
    "isdynamicallyscalable",
    "leaseduration",
    "instancename",
    "deleteprotection",
    "displayvm",
    "displayname",
    "userdatadetails",
    "groupid",
    "id",
    "extraconfig",
    "securitygroupids"
  ],
  "vm": {
    "id": "3766cfb9-8ecd-47b6-b093-5114dc2fb5e7",
    "state": "Running",
    "hostid": "05bc1189-abf8-4ec3-9ef5-02558863b628",
    "groupid": null,
    "group": null,
    "serviceofferingid": "b02ac1bf-2da7-46e1-8961-d94d76dad9ab"
  }
}
```

## deployed-verification

```json
{"source": "6e633381107820b7851fb341242ee3fe73155e45", "jar": "/usr/share/cloudstack-management/lib/cloudstack-4.23.0.0.jar", "jar_sha256": "4e2c97126c1865c7048d15b8d8f0833745f76ad20516a6cf4860c918d8c34863", "verified_classes": 39, "verified_static_files": 843, "config_sha256": "e3839c761e59e77f960b9f9f717055ea91882682a3a036b07e4c69277aac8687", "web_inf": true, "meta_inf": false, "markers": {"blockingLoadingState": true, "fetchSyncProgress": true, "extractJobId": true, "cleargroup": true}, "component_loading_errors": {"UnsatisfiedDependencyException": 0, "NoClassDefFoundError": 0, "NoSuchMethodError": 0, "ClassNotFoundException": 0}, "rollback": "/root/epic1357-vg01-deploy-20261013-145714"}

```

## additional-contract

```json
{
  "legacy_group_on_vm_creation": {
    "pass": true,
    "id": "b03a8939-c8e3-4e68-8d0d-7fac95dd6324",
    "state": "Stopped",
    "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
  },
  "cross_project_group": {
    "pass": true,
    "errorcode": 530,
    "errortext": "Failed to update Instance, due to: The instance group must belong to the same account or project as the VM."
  },
  "overlength_name_preserves_previous_mapping": {
    "pass": true,
    "errorcode": 431,
    "errortext": "Unable to execute API command updatevirtualmachine due to invalid value. Value greater than max allowed length 255 for param: group"
  },
  "clear_preserves_group": {
    "pass": true
  },
  "stopped_vm_move_clear_preserves_settings": {
    "pass": true,
    "baseline": {
      "id": "b03a8939-c8e3-4e68-8d0d-7fac95dd6324",
      "state": "Stopped",
      "hostid": null,
      "serviceofferingid": "b02ac1bf-2da7-46e1-8961-d94d76dad9ab",
      "templateid": "b31d2c92-67b9-40c4-a137-4ff9778c6b0d",
      "cpunumber": 2,
      "memory": 2048,
      "haenable": false,
      "displayname": "VG01-32-create-20261013"
    }
  },
  "same_legacy_name_concurrent_creation": {
    "pass": true,
    "requests": 6,
    "groups_created": 1,
    "elapsed_seconds": 10.124,
    "first_31_attempt": "not executed before 31 failure"
  }
}
```

## db-baseline

```text
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root   83G   51G   32G  62% /
VM	165	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	i-2-165-VM	VG01-32-20261013	Running	3	14	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	0	0
VOLUME	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	32f1bf14-d326-4467-b8ef-2d16f7729cec	ROOT	Ready	3	32f1bf14-d326-4467-b8ef-2d16f7729cec	107374182400	18
MAP	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	0	NULL
FTCTL	0

```

## ui-after-A

```text
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root   83G   52G   32G  63% /
VM	165	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	i-2-165-VM	VG01-32-20261013	Running	3	14	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	0	0
VOLUME	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	32f1bf14-d326-4467-b8ef-2d16f7729cec	ROOT	Ready	3	32f1bf14-d326-4467-b8ef-2d16f7729cec	107374182400	18
MAP	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	1	47c7c8da-f097-484d-a8ad-6d5cd5a527b9
FTCTL	0

```

## ui-after-B

```text
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root   83G   52G   32G  63% /
VM	165	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	i-2-165-VM	VG01-32-20261013	Running	3	14	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	0	0
VOLUME	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	32f1bf14-d326-4467-b8ef-2d16f7729cec	ROOT	Ready	3	32f1bf14-d326-4467-b8ef-2d16f7729cec	107374182400	18
MAP	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	1	00b28aaf-d4e8-438d-9925-3d094a69bfb7
FTCTL	0

```

## ui-after-clear

```text
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root   83G   52G   32G  63% /
VM	165	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	i-2-165-VM	VG01-32-20261013	Running	3	14	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	0	0
VOLUME	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	32f1bf14-d326-4467-b8ef-2d16f7729cec	ROOT	Ready	3	32f1bf14-d326-4467-b8ef-2d16f7729cec	107374182400	18
MAP	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	0	NULL
FTCTL	0

```

## host-baseline

```text
ablecube32-3
running

3766cfb9-8ecd-47b6-b093-5114dc2fb5e7

 Type    Device   Target   Source
------------------------------------------------------------------------------
 block   disk     sda      /dev/rbd/rbd/32f1bf14-d326-4467-b8ef-2d16f7729cec
 file    cdrom    hdc      -
 file    cdrom    hdd      -

ab35d9e4b80c3c6b77ef75d624ffcaf44d6546cf6ac98a78741e79f26b5835aa  -
3167801

```

## host-after-ui

```text
ablecube32-3
running

3766cfb9-8ecd-47b6-b093-5114dc2fb5e7

 Type    Device   Target   Source
------------------------------------------------------------------------------
 block   disk     sda      /dev/rbd/rbd/32f1bf14-d326-4467-b8ef-2d16f7729cec
 file    cdrom    hdc      -
 file    cdrom    hdd      -

ab35d9e4b80c3c6b77ef75d624ffcaf44d6546cf6ac98a78741e79f26b5835aa  -
3167801

```

## host-after-contract

```text
ablecube32-3
running

3766cfb9-8ecd-47b6-b093-5114dc2fb5e7

 Type    Device   Target   Source
------------------------------------------------------------------------------
 block   disk     sda      /dev/rbd/rbd/32f1bf14-d326-4467-b8ef-2d16f7729cec
 file    cdrom    hdc      -
 file    cdrom    hdd      -

ab35d9e4b80c3c6b77ef75d624ffcaf44d6546cf6ac98a78741e79f26b5835aa  -
3167801

```

## db-after-contract-final

```text
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root   83G   52G   32G  63% /
VM	165	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	i-2-165-VM	VG01-32-20261013	Running	3	14	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	0	0
VM	166	b03a8939-c8e3-4e68-8d0d-7fac95dd6324	i-2-166-VM	VG01-32-create-20261013	Stopped	NULL	14	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	0	0
VOLUME	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	32f1bf14-d326-4467-b8ef-2d16f7729cec	ROOT	Ready	3	32f1bf14-d326-4467-b8ef-2d16f7729cec	107374182400	18
VOLUME	b03a8939-c8e3-4e68-8d0d-7fac95dd6324	15f23491-69d2-4cbc-ad15-f783dc829cd2	ROOT	Allocated	NULL	NULL	107374182400	18
MAP	3766cfb9-8ecd-47b6-b093-5114dc2fb5e7	0	NULL
MAP	b03a8939-c8e3-4e68-8d0d-7fac95dd6324	0	NULL
FTCTL	0
DUPLICATE_MAPPINGS	0
ORPHAN_OR_FOREIGN	0

```

## cleanup-db

```text
ACTIVE_TEST_VM	0
ACTIVE_TEST_VOLUME	0
TEST_MAPPING	0
ACTIVE_TEST_GROUP	0
VOLUME_STATUS	32f1bf14-d326-4467-b8ef-2d16f7729cec	Expunged	2026-10-13 06:16:29
VOLUME_STATUS	15f23491-69d2-4cbc-ad15-f783dc829cd2	Destroy	2026-10-13 06:16:36
active
CLIENT_HTTP=200
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root   83G   52G   32G  63% /

```

## cleanup-host

```text
TARGET_DOMAINS=0
TARGET_RBD_IMAGES=0
TARGET_KRBD_MAPPINGS=0

```
