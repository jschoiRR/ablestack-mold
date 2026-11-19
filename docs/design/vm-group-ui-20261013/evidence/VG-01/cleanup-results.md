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

# Test resource cleanup

```json
{
  "31": {
    "vms": [
      {
        "id": "88c44092-351d-43d1-96e4-9d40d76295b7",
        "name": "VG01-31-20261013",
        "instancename": "i-2-493-VM",
        "volumes": [
          {
            "id": "ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873",
            "path": "ea9e30f3-6d8a-4756-85fb-a7fe9a7d9873",
            "state": "Ready",
            "storageid": "1842fb9f-02e0-4da9-b082-422c5a184de0",
            "storage": "Primary"
          }
        ],
        "destroy_jobid": "c3b0bb76-8cc5-4c0a-8f73-45c924712567",
        "result": null
      },
      {
        "id": "c7eafff9-f50c-4f83-8b60-7b2493edc79d",
        "name": "VG01-31-create-20261013",
        "instancename": "i-2-494-VM",
        "volumes": [
          {
            "id": "758cd9c0-f9e7-491d-a3f1-257842704b9f",
            "path": null,
            "state": "Allocated",
            "storageid": null,
            "storage": null
          }
        ],
        "destroy_jobid": "692597c8-3dc3-413d-a185-ef5e703bb4db",
        "result": null
      }
    ],
    "groups": [
      {
        "id": "50b1d447-8b72-4858-b7bd-3dc0378b1133",
        "name": "VG01-31-A"
      },
      {
        "id": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51",
        "name": "VG01-31-B"
      },
      {
        "id": "ba7d9310-5745-42b1-8aa3-f38fb0c0ea0c",
        "name": "VG01-31-한글 Legacy"
      },
      {
        "id": "16aa5d90-6700-4f0d-a6ce-467ce665be49",
        "name": "VG01-31-same-name-race"
      },
      {
        "id": "b1dcaf18-6972-458d-b3e9-252a7177bc7a",
        "name": "VG01-31-same-name-race-retry"
      },
      {
        "id": "9b2a6bc4-ddd8-4e07-b948-15940a6b0447",
        "name": "VG01-31-foreign"
      },
      {
        "id": "e6247457-529b-4d53-87fe-6d574e1faa37",
        "name": "VG01-31-project-foreign"
      }
    ],
    "active_test_vms": 0
  },
  "32": {
    "vms": [
      {
        "id": "3766cfb9-8ecd-47b6-b093-5114dc2fb5e7",
        "name": "VG01-32-20261013",
        "instancename": "i-2-165-VM",
        "volumes": [
          {
            "id": "32f1bf14-d326-4467-b8ef-2d16f7729cec",
            "path": "32f1bf14-d326-4467-b8ef-2d16f7729cec",
            "state": "Ready",
            "storageid": "91cae554-3fce-3f93-89d1-cefaf9bf8122",
            "storage": "Primary"
          }
        ],
        "destroy_jobid": "d7d4dbfb-91cb-4a49-ad35-16a6530573a0",
        "result": null
      },
      {
        "id": "b03a8939-c8e3-4e68-8d0d-7fac95dd6324",
        "name": "VG01-32-create-20261013",
        "instancename": "i-2-166-VM",
        "volumes": [
          {
            "id": "15f23491-69d2-4cbc-ad15-f783dc829cd2",
            "path": null,
            "state": "Allocated",
            "storageid": null,
            "storage": null
          }
        ],
        "destroy_jobid": "78c2b7d0-8223-4981-9cdd-2ed41cc82641",
        "result": null
      }
    ],
    "groups": [
      {
        "id": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9",
        "name": "VG01-32-A"
      },
      {
        "id": "00b28aaf-d4e8-438d-9925-3d094a69bfb7",
        "name": "VG01-32-B"
      },
      {
        "id": "13aafed2-4184-4144-b978-b69f266ec5a9",
        "name": "VG01-32-한글 Legacy"
      },
      {
        "id": "0d7d147a-74f3-44aa-83ec-f3bccc88cfb3",
        "name": "VG01-32-same-name-race-retry"
      },
      {
        "id": "1cbec2f9-15dc-4a37-93a1-dc69612b3ea5",
        "name": "VG01-32-foreign"
      },
      {
        "id": "4c691863-0420-42b9-9e9f-11da8a86196a",
        "name": "VG01-32-project-foreign"
      }
    ],
    "active_test_vms": 0
  }
}
```
