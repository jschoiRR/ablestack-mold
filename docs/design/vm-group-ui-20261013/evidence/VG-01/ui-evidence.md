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

# UI 저장 요청과 화면 근거

브라우저의 실제 VM 편집 폼에서 연결·이동·입력 해제를 수행했다. 아래 요청은 Network.requestWillBeSent에서 인증 값과 전송용 파라미터를 제거한 기록이다.

```json
[
  {
    "label": "31-ungrouped-to-A",
    "method": "POST",
    "parameters": {
      "id": "88c44092-351d-43d1-96e4-9d40d76295b7",
      "groupid": "50b1d447-8b72-4858-b7bd-3dc0378b1133"
    }
  },
  {
    "label": "31-A-to-B",
    "method": "POST",
    "parameters": {
      "id": "88c44092-351d-43d1-96e4-9d40d76295b7",
      "groupid": "eddc50c6-e9d0-440f-94b4-0c6009a0fa51"
    }
  },
  {
    "label": "31-B-to-ungrouped",
    "method": "POST",
    "parameters": {
      "id": "88c44092-351d-43d1-96e4-9d40d76295b7",
      "cleargroup": "true"
    }
  },
  {
    "label": "32-ungrouped-to-A",
    "method": "POST",
    "parameters": {
      "id": "3766cfb9-8ecd-47b6-b093-5114dc2fb5e7",
      "groupid": "47c7c8da-f097-484d-a8ad-6d5cd5a527b9"
    }
  },
  {
    "label": "32-A-to-B",
    "method": "POST",
    "parameters": {
      "id": "3766cfb9-8ecd-47b6-b093-5114dc2fb5e7",
      "groupid": "00b28aaf-d4e8-438d-9925-3d094a69bfb7"
    }
  },
  {
    "label": "32-B-to-ungrouped",
    "method": "POST",
    "parameters": {
      "id": "3766cfb9-8ecd-47b6-b093-5114dc2fb5e7",
      "cleargroup": "true"
    }
  }
]
```

31과 32 모두 무소속→A→B→무소속을 실행했고, 각 단계에서 그룹 링크의 표시/제거와 Running 상태를 확인했다. API/DB 매핑은 runtime-31.md/runtime-32.md에 기록했다. 그룹만 변경하는 요청에는 VM의 다른 설정이 포함되지 않았다.

31의 공백 이름은 한국어 오류를 표시하고 3칸 입력을 보존했으며 updateVirtualMachine을 전송하지 않았다. 구 서버의 미지원 해제와 다른 설정을 변경한 혼합 폼은 집중 UI unit에서 검사했다.

| 화면 | 31 | 32 |
|---|---|---|
| 그룹 A 연결 | [화면](31-after-A.png) | [화면](32-after-A.png) |
| 그룹 B 이동 | [화면](31-after-B.png) | [화면](32-after-B.png) |
| 입력 해제·재시작 안내 | [화면](31-clear-group-form.png) | [화면](32-clear-group-form.png) |
| 해제 후 Running | [화면](31-after-clear.png) | [화면](32-after-clear.png) |
| 공백 입력 거부 | [화면](31-whitespace-rejected.png) | unit/동일 코드 |

재기동 직후 첫 로그인/기능 검색의 console 관찰은 다음과 같다. 31의 기록은 첫 그룹 저장 이전이며, 이후 그룹 흐름의 추가 error는 관찰되지 않았다. 32 error는 0건이다. 초기화 안정성 전체를 통과로 주장하지 않는다.
```json
{
  "31": [
    {
      "timestamp": "2026-10-13T05:50:09.571Z",
      "category": "startup zones.filter is not a function"
    },
    {
      "timestamp": "2026-10-13T05:50:10.664Z",
      "category": "startup zones.filter is not a function"
    },
    {
      "timestamp": "2026-10-13T05:50:11.625Z",
      "category": "startup minified API error"
    },
    {
      "timestamp": "2026-10-13T05:50:12.807Z",
      "category": "startup minified API error"
    },
    {
      "timestamp": "2026-10-13T05:50:12.808Z",
      "category": "startup minified API error"
    },
    {
      "timestamp": "2026-10-13T05:50:12.808Z",
      "category": "startup minified API error"
    },
    {
      "timestamp": "2026-10-13T05:50:12.808Z",
      "category": "startup minified API error"
    }
  ],
  "32": []
}
```
