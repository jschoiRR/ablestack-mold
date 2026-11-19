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

# Cluster 31 runtime evidence

## backend-health

```json
{
  "parameters": [
    "customid",
    "displayname",
    "details",
    "deleteprotection",
    "groupid",
    "cleanupextraconfig",
    "dhcpoptionsnetworklist",
    "leaseexpiryaction",
    "cleargroup",
    "ostypeid",
    "instancename",
    "cleanupdetails",
    "userdata",
    "userdataid",
    "displayvm",
    "name",
    "id",
    "group",
    "haenable",
    "leaseduration",
    "userdatadetails",
    "securitygroupids",
    "extraconfig",
    "isdynamicallyscalable",
    "securitygroupnames"
  ],
  "vm": {
    "id": "88c44092-351d-43d1-96e4-9d40d76295b7",
    "state": "Running",
    "hostid": "159baf9a-89ad-4a94-91dc-e7395a670bf4",
    "groupid": null,
    "group": null,
    "serviceofferingid": "b1bad848-428f-4973-8c2b-26df2b7cca95"
  }
}
```

## deployed-verification

```json
{"source": "6e633381107820b7851fb341242ee3fe73155e45", "jar": "/usr/share/cloudstack-management/lib/cloudstack-4.23.0.0-ABLESTACK.Mold.jar", "jar_sha256": "5d47fc82e18064a6570c92962eef0dc88495001b0979006dcd8eac451550caec", "verified_classes": 39, "verified_static_files": 843, "config_sha256": "b7de5f6a1f7a3a17310ee7d34ca801bb316fc6f841039f865fbabf154cd59a13", "web_inf": true, "meta_inf": false, "markers": {"blockingLoadingState": true, "fetchSyncProgress": true, "extractJobId": true, "cleargroup": true}, "component_loading_errors": {"UnsatisfiedDependencyException": 0, "NoClassDefFoundError": 0, "NoSuchMethodError": 0, "ClassNotFoundException": 0}, "rollback": "/root/epic1357-vg01-deploy-20261013-144755"}

```

## additional-contract

```json
{
  "legacy_group_on_vm_creation": {
    "pass": true,
    "id": "c7eafff9-f50c-4f83-8b60-7b2493edc79d",
    "state": "Stopped",
    "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
  },
  "cross_project_group": {
    "pass": true,
    "errorcode": 530,
    "errortext": "Failed to update Instance, due to: The instance group must belong to the same account or project as the VM."
  },
  "clear_preserves_group": {
    "pass": true
  },
  "overlength_name_preserves_previous_mapping": {
    "pass": true,
    "errorcode": 431,
    "errortext": "Unable to execute API command updatevirtualmachine due to invalid value. Value greater than max allowed length 255 for param: group"
  },
  "stopped_vm_move_clear_preserves_settings": {
    "pass": true,
    "baseline": {
      "id": "c7eafff9-f50c-4f83-8b60-7b2493edc79d",
      "state": "Stopped",
      "hostid": null,
      "serviceofferingid": "b1bad848-428f-4973-8c2b-26df2b7cca95",
      "templateid": "8d692729-7565-4e92-965b-ebaadc1efcb0",
      "cpunumber": 1,
      "memory": 1024,
      "haenable": true,
      "displayname": "VG01-31-create-20261013"
    }
  },
  "same_legacy_name_concurrent_creation": {
    "pass": true,
    "requests": 6,
    "groups_created": 1,
    "elapsed_seconds": 39.316,
    "first_31_attempt": "40-second client timeout during global DB redo flush wait"
  }
}
```

## db-baseline

```text
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root  383G   54G  329G  15% /
VM	493	88c44092-351d-43d1-96e4-9d40d76295b7	i-2-493-VM	VG01-31-20261013	Running	1	12	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	1	0
VOLUME	88c44092-351d-43d1-96e4-9d40d76295b7	ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873	ROOT	Ready	1	ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873	107374182400	13
MAP	88c44092-351d-43d1-96e4-9d40d76295b7	0	NULL
FTCTL	0

```

## ui-after-A

```text
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root  383G   55G  329G  15% /
VM	493	88c44092-351d-43d1-96e4-9d40d76295b7	i-2-493-VM	VG01-31-20261013	Running	1	12	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	1	0
VOLUME	88c44092-351d-43d1-96e4-9d40d76295b7	ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873	ROOT	Ready	1	ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873	107374182400	13
MAP	88c44092-351d-43d1-96e4-9d40d76295b7	1	50b1d447-8b72-4858-b7bd-3dc0378b1133
FTCTL	0

```

## ui-after-B

```text
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root  383G   55G  329G  15% /
VM	493	88c44092-351d-43d1-96e4-9d40d76295b7	i-2-493-VM	VG01-31-20261013	Running	1	12	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	1	0
VOLUME	88c44092-351d-43d1-96e4-9d40d76295b7	ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873	ROOT	Ready	1	ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873	107374182400	13
MAP	88c44092-351d-43d1-96e4-9d40d76295b7	1	eddc50c6-e9d0-440f-94b4-0c6009a0fa51
FTCTL	0

```

## ui-after-clear

```text
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root  383G   55G  329G  15% /
VM	493	88c44092-351d-43d1-96e4-9d40d76295b7	i-2-493-VM	VG01-31-20261013	Running	1	12	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	1	0
VOLUME	88c44092-351d-43d1-96e4-9d40d76295b7	ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873	ROOT	Ready	1	ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873	107374182400	13
MAP	88c44092-351d-43d1-96e4-9d40d76295b7	0	NULL
FTCTL	0

```

## host-baseline

```text
ablecube31-1
running

88c44092-351d-43d1-96e4-9d40d76295b7

 Type   Device   Target   Source
------------------------------------------------------------------------------
 file   disk     sda      /mnt/glue-gfs/ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873
 file   cdrom    hdc      -
 file   cdrom    hdd      -

e34ca18815746796303a9f9addbf85f01fb9ebdeb4d73312bea5c3c5b89d6fe8  -
2017184

```

## host-after-ui

```text
ablecube31-1
running

88c44092-351d-43d1-96e4-9d40d76295b7

 Type   Device   Target   Source
------------------------------------------------------------------------------
 file   disk     sda      /mnt/glue-gfs/ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873
 file   cdrom    hdc      -
 file   cdrom    hdd      -

e34ca18815746796303a9f9addbf85f01fb9ebdeb4d73312bea5c3c5b89d6fe8  -
2017184

```

## host-after-contract

```text
ablecube31-1
running

88c44092-351d-43d1-96e4-9d40d76295b7

 Type   Device   Target   Source
------------------------------------------------------------------------------
 file   disk     sda      /mnt/glue-gfs/ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873
 file   cdrom    hdc      -
 file   cdrom    hdd      -

e34ca18815746796303a9f9addbf85f01fb9ebdeb4d73312bea5c3c5b89d6fe8  -
2017184

```

## db-after-contract-final

```text
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root  383G   55G  329G  15% /
VM	493	88c44092-351d-43d1-96e4-9d40d76295b7	i-2-493-VM	VG01-31-20261013	Running	1	12	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	1	0
VM	494	c7eafff9-f50c-4f83-8b60-7b2493edc79d	i-2-494-VM	VG01-31-create-20261013	Stopped	NULL	12	317	e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855	1	1	0
VOLUME	88c44092-351d-43d1-96e4-9d40d76295b7	ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873	ROOT	Ready	1	ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873	107374182400	13
VOLUME	c7eafff9-f50c-4f83-8b60-7b2493edc79d	758cd9c0-f9e7-491d-a3f1-257842704b9f	ROOT	Allocated	NULL	NULL	107374182400	13
MAP	88c44092-351d-43d1-96e4-9d40d76295b7	0	NULL
MAP	c7eafff9-f50c-4f83-8b60-7b2493edc79d	0	NULL
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
VOLUME_STATUS	ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873	Expunged	2026-10-13 06:16:07
VOLUME_STATUS	758cd9c0-f9e7-491d-a3f1-257842704b9f	Destroy	2026-10-13 06:16:16
active
CLIENT_HTTP=200
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root  383G   55G  329G  15% /

```

## cleanup-host

```text
TARGET_DOMAINS=0
TARGET_GFS2_FILES=0

```
