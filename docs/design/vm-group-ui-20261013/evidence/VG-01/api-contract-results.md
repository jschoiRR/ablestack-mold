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

# API contract results

```json
{
  "31": {
    "host": "10.10.31.10",
    "vm": "88c44092-351d-43d1-96e4-9d40d76295b7",
    "baseline": {
      "id": "88c44092-351d-43d1-96e4-9d40d76295b7",
      "instancename": "i-2-493-VM",
      "state": "Running",
      "hostid": "159baf9a-89ad-4a94-91dc-e7395a670bf4",
      "serviceofferingid": "b1bad848-428f-4973-8c2b-26df2b7cca95",
      "templateid": "8d692729-7565-4e92-965b-ebaadc1efcb0",
      "ostypeid": "9708b6ac-b253-11f1-a895-0024814c313f",
      "cpunumber": 1,
      "cpuspeed": 2000,
      "memory": 1024,
      "haenable": true,
      "deleteprotection": false,
      "displayname": "VG01-31-20261013"
    },
    "cases": [
      {
        "case": "id_connect",
        "pass": true
      },
      {
        "case": "omitted",
        "pass": true
      },
      {
        "case": "clear_false",
        "pass": true
      },
      {
        "case": "foreign_owner",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: The instance group must belong to the same account or project as the VM."
      },
      {
        "case": "deleted_id",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: Unable to find an active instance group."
      },
      {
        "case": "unknown_id",
        "pass": true,
        "errorcode": 431,
        "errortext": "Unable to execute API command updatevirtualmachine due to invalid value. Invalid parameter groupid value=00000000-0000-0000-0000-000000000000 due to incorrect long value format, or entity does not exist or due to incorrect parameter annotation for the field in api cmd class."
      },
      {
        "case": "blank_name",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: The instance group name cannot be blank. Use cleargroup=true to remove the group."
      },
      {
        "case": "spaces",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: The instance group name cannot be blank. Use cleargroup=true to remove the group."
      },
      {
        "case": "name_and_id",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: Specify only one of group, groupid or cleargroup=true."
      },
      {
        "case": "id_and_clear",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: Specify only one of group, groupid or cleargroup=true."
      },
      {
        "case": "name_and_clear",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: Specify only one of group, groupid or cleargroup=true."
      },
      {
        "case": "invalid_mixed",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: The instance group must belong to the same account or project as the VM."
      },
      {
        "case": "id_move",
        "pass": true
      },
      {
        "case": "clear_idempotence",
        "pass": true
      },
      {
        "case": "legacy_name_create",
        "pass": true
      },
      {
        "case": "group_rename_refresh",
        "pass": true
      },
      {
        "case": "concurrent_moves_and_clears",
        "pass": true,
        "rounds": 12,
        "requests": 36
      },
      {
        "case": "delete_old_group_vs_move",
        "pass": true,
        "rounds": 8
      },
      {
        "case": "delete_destination_vs_assign",
        "pass": true,
        "rounds": 8
      }
    ],
    "final_membership": null,
    "db_mapping_samples": [
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "ba7d9310-5745-42b1-8aa3-f38fb0c0ea0c"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "ba7d9310-5745-42b1-8aa3-f38fb0c0ea0c"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      }
    ]
  },
  "32": {
    "host": "10.10.32.10",
    "vm": "3766cfb9-8ecd-47b6-b093-5114dc2fb5e7",
    "baseline": {
      "id": "3766cfb9-8ecd-47b6-b093-5114dc2fb5e7",
      "instancename": "i-2-165-VM",
      "state": "Running",
      "hostid": "05bc1189-abf8-4ec3-9ef5-02558863b628",
      "serviceofferingid": "b02ac1bf-2da7-46e1-8961-d94d76dad9ab",
      "templateid": "b31d2c92-67b9-40c4-a137-4ff9778c6b0d",
      "ostypeid": "574c7ceb-b23b-11f1-990e-0024811f8935",
      "cpunumber": 2,
      "cpuspeed": 2000,
      "memory": 2048,
      "haenable": false,
      "deleteprotection": false,
      "displayname": "VG01-32-20261013"
    },
    "cases": [
      {
        "case": "id_connect",
        "pass": true
      },
      {
        "case": "omitted",
        "pass": true
      },
      {
        "case": "clear_false",
        "pass": true
      },
      {
        "case": "foreign_owner",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: The instance group must belong to the same account or project as the VM."
      },
      {
        "case": "deleted_id",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: Unable to find an active instance group."
      },
      {
        "case": "unknown_id",
        "pass": true,
        "errorcode": 431,
        "errortext": "Unable to execute API command updatevirtualmachine due to invalid value. Invalid parameter groupid value=00000000-0000-0000-0000-000000000000 due to incorrect long value format, or entity does not exist or due to incorrect parameter annotation for the field in api cmd class."
      },
      {
        "case": "blank_name",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: The instance group name cannot be blank. Use cleargroup=true to remove the group."
      },
      {
        "case": "spaces",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: The instance group name cannot be blank. Use cleargroup=true to remove the group."
      },
      {
        "case": "name_and_id",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: Specify only one of group, groupid or cleargroup=true."
      },
      {
        "case": "id_and_clear",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: Specify only one of group, groupid or cleargroup=true."
      },
      {
        "case": "name_and_clear",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: Specify only one of group, groupid or cleargroup=true."
      },
      {
        "case": "invalid_mixed",
        "pass": true,
        "errorcode": 530,
        "errortext": "Failed to update Instance, due to: The instance group must belong to the same account or project as the VM."
      },
      {
        "case": "id_move",
        "pass": true
      },
      {
        "case": "clear_idempotence",
        "pass": true
      },
      {
        "case": "legacy_name_create",
        "pass": true
      },
      {
        "case": "group_rename_refresh",
        "pass": true
      },
      {
        "case": "concurrent_moves_and_clears",
        "pass": true,
        "rounds": 12,
        "requests": 36
      },
      {
        "case": "delete_old_group_vs_move",
        "pass": true,
        "rounds": 8
      },
      {
        "case": "delete_destination_vs_assign",
        "pass": true,
        "rounds": 8
      }
    ],
    "final_membership": null,
    "db_mapping_samples": [
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "13aafed2-4184-4144-b978-b69f266ec5a9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "13aafed2-4184-4144-b978-b69f266ec5a9"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 1,
        "invalid_count": 0,
        "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
      },
      {
        "mapping_count": 0,
        "invalid_count": 0,
        "groupid": "NONE"
      }
    ]
  }
}
```
