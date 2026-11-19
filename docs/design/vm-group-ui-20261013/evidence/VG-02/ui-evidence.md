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

# VG 02 실제 UI 요청 및 소유 범위 근거

2026-10-13, 배포 소스 a47650a804ff208d57d1da2d6d2b0fe79bba9726. 실제 Chrome UI에서 실행하고 탭의 Network 요청을 인증 필드 제외 후 수집했다. API와 SSH 관리 서버의 로컬 cloud DB를 독립 비교했다.

## 계정·프로젝트별 후보와 연결

| 사이트 | 소유 범위 | 같은 이름의 그룹 UUID | UI 저장 |
| --- | --- | --- | --- |
| 31 | admin / ROOT | 2354666d-20f7-488b-b347-11c720fb15cb | admin VM에 groupid 연결 |
| 31 | vg02-31-user-20261013 / ROOT | aa3b80d0-9887-4664-8ac3-6caf6311967c | admin 타계정 편집과 일반 사용자 연결 모두 이 UUID |
| 31 | project 0ccc19e2-bc97-4de7-941f-1f1e66115110 | 6b29450d-104a-447c-b436-38a16fbd2274 | 구성원 편집·최종 프로젝트 VM 생성 |
| 32 | admin / ROOT | ade5ac95-0ae4-4152-9f40-596debd93479 | admin VM에 groupid 연결 |
| 32 | vg02-32-user-20261013 / ROOT | d73a431e-f010-4e0b-ae7b-df6739cfe737 | admin 타계정 편집과 일반 사용자 연결 모두 이 UUID |
| 32 | project 15372a30-59f0-4f12-b594-57180d7977fe | add855da-7bdd-45bd-8043-4a5ac9a0c0aa | 구성원 편집·최종 프로젝트 VM 생성 |

이름은 각 사이트에서 `VG02-same-31/32-20261013`으로 같게 만들었다. 편집 후보는 account + domainid 또는 projectid로 제한했다. 31 ROOT domain은 7ef6b29a-b253-11f1-a895-0024814c313f, 32는 2246448b-b23b-11f1-990e-0024811f8935다.

배포 전 31 타계정 VM 편집 요청:

```text
listInstanceGroups account=admin domainid=31_ROOT listall=true
```

배포 후 31 동일 VM의 실제 요청:

```text
GET listInstanceGroups account=vg02-31-user-20261013 domainid=31_ROOT listall=true page=1 pagesize=500
POST updateVirtualMachine id=0bde265f-70c6-4672-993e-b61a545c0bf8 groupid=aa3b80d0-9887-4664-8ac3-6caf6311967c
```

일반 사용자 UI도 동일 후보를 조회하고 cleargroup=true로 해제한 뒤 해당 UUID에 다시 연결했다. 프로젝트 편집은 groupid와 대상 VM id에 공통 전송 문맥 projectid만 추가했다. OS/HA/동적 확장/UserData/볼륨 인자는 그룹만 바꾸는 요청에 없었다.

listInstanceGroups만 거부한 User 역할을 별도로 복제했다. 양 사이트에서 그룹 입력은 disabled이고 권한 없음 안내를 표시했으며 후보 GET은 0개였다. 실제 제한 계정 API는 432를 반환했다. 서버의 일반 사용자 타계정/타그룹 ACL 거부는 사이트별 3건씩 531로 확인했다.

## 정상 빈 결과와 생성

개인 계정의 시험 그룹을 정확한 UUID로 삭제한 뒤 해당 계정 후보 0개를 API와 실제 편집 UI에서 확인했다. UI는 empty 안내와 활성 입력을 제공했고 다음 그룹을 개인 계정에 생성·연결했다.

| 사이트 | UI의 group 이름 | 실제 UUID | 소유 |
| --- | --- | --- | --- |
| 31 | VG02-empty-new-31-20261013 | f30901fd-9922-40ec-8994-7a3a982f2209 | vg02-31-user-20261013 |
| 32 | VG02-empty-new-32-20261013 | de665c11-0f8d-4074-9a86-25b3f2f97547 | vg02-32-user-20261013 |

동시에 남아 있던 admin/프로젝트 그룹을 연결하거나 그 범위에 새 그룹을 만들지 않았다.

## 생성 문맥·조회 경합

양 사이트에서 admin 생성 화면의 그룹 초안을 입력하고 계정/프로젝트 소유를 전환했다. 초안은 빈 값으로 바뀌고 조회 중 입력은 disabled가 됐다. 전용 빈 race 프로젝트에서 목표 프로젝트로 전환할 때 겹친 **2개 XHR**을 보류했다. 최신 요청을 실제 서버로 보내 목표 프로젝트 그룹을 먼저 표시하고, 이전 요청에는 나중에 count=0을 반환했다. 이후에도 ready 안내·활성 입력·목표 프로젝트 그룹 후보가 유지됐다. 주입은 XHR에 제한하고 완료 후 해제했다.

실제 생성 POST는 목표 projectid + 같은 이름의 기존 그룹 + startvm=false였다. API/DB 및 프로젝트 VM 상세의 링크로 확인한 결과:

| 사이트 | UI 생성 VM UUID | 상태 / host | projectid | groupid |
| --- | --- | --- | --- | --- |
| 31 | e228398a-7f4b-4b93-ad8c-3289e82fdfbe | Stopped / 없음 | 0ccc19e2-bc97-4de7-941f-1f1e66115110 | 6b29450d-104a-447c-b436-38a16fbd2274 |
| 32 | ef4ae344-0e35-4f7a-85f7-72e5c65c41b0 | Stopped / 없음 | 15372a30-59f0-4f12-b594-57180d7977fe | add855da-7bdd-45bd-8043-4a5ac9a0c0aa |

31은 공개 Rocky ISO와 16GiB root, 32는 공개 Rocky cloud template와 100GiB root를 사용했다. private ISO 접근 거부를 해결하기 위해 기존 ISO 권한을 확대하지 않았다. 네트워크는 전용 프로젝트 L2이며 VM을 시작하지 않았다.

## 실패·보조 조회

- 31: 그룹 XHR에 HTTP 503을 주입했다. error 안내·입력 차단 후 업데이트 버튼으로 같은 소유 범위를 다시 조회해 정상 후보를 복원했다.
- 32: 템플릿/오퍼링 조회를 20초 timeout까지 보류했다. 동적 확장 설정이 disabled로 유지됐다. 그룹 XHR에 HTTP 403 + JSON errorcode 531을 주입하면 forbidden 안내를 표시했다. 그룹 조회만 정상 재시도한 뒤 메타데이터는 실패한 상태로 `id + cleargroup=true`만 저장했다. 해당 Runtime.exceptionThrown은 0건이었다.
- 실제 빈 ISO 템플릿 및 일반 사용자/프로젝트의 비어 있는 오퍼링 조회에서도 폼이 유지되고 그룹 연결이 동작했다. 원래 checked 값은 보존하고 의존 설정을 변경하지 않았다.

## 화면 및 판정 경계

아래 PNG는 라이트/다크 및 폼 배치·필드 보호의 보조 자료다. 일부는 조회 또는 CSS 전환 중 프레임을 포함한다. 저장·연결 완료 판정은 위 실제 요청, UI 갱신 링크, API 응답 및 DB 매핑을 대조해 수행했다.

- [31 후보와 보조 설정](31-admin-other-correct-owner.png) · [32 다크 폼](32-admin-other-dark.png)
- [31 프로젝트 구성원](31-project-member.png) · [32 프로젝트 구성원](32-project-member.png)
- [31 제한 역할](31-restricted-role.png) · [32 제한 역할](32-restricted-role.png)
- [31 빈 후보](31-empty-owner.png) · [32 빈 후보](32-empty-owner.png)
- [31 실패 주입](31-group-query-failure.png) · [32 메타데이터 timeout](32-metadata-timeout.png) · [32 조회 입력 보호](32-permission-and-metadata-failure.png)
- [31 지연 응답](31-creation-late-response-ignored.png) · [32 지연 응답](32-creation-late-response-ignored.png)
- [31 생성 설정](31-project-vm-create-ready.png) · [32 생성 설정](32-project-vm-create-ready.png)
- [31 생성 VM](31-project-vm-created.png) · [32 생성 VM](32-project-vm-created.png)

원본 인증 자료와 로그인 세션/비밀번호는 Git에 포함하지 않았다. 모든 시험 자원은 완료 후 정리했으며 현재 살아 있는 VM/그룹을 의미하는 화면은 아니다.
