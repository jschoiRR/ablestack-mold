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

# VG 02 소유 범위 및 보조 조회 개선 검증

2026-10-13 / Epic #1357 / issue #1359 / 누적 Draft PR #1364.

**결과:** 대상 소유 범위의 그룹 조회·연결, 생성 소유 문맥 전환, 정상 빈 결과·권한 없음·실패 구분, 지연 응답 거절과 편집 보조 조회 방어를 구현하고 31/32에 배포·검증했다. 완료 기준 6개를 충족했다. 시험 자원은 정리했다.

## 소스와 설계

작업 시작 시 최신 Upstream Europa `9fa65ce2a27b977e848440cb7c787411c11d585b`가 Epic 브랜치에 포함됨을 재확인했다. 별도 dhslove 작업 트리에서 작업했고 기존 FTCTL 변경을 보존했다. 배포 소스는 **`a47650a804ff208d57d1da2d6d2b0fe79bba9726`**이다. 이후 검증 문서 커밋은 배포 코드와 구분한다.

[설계](VG-02-design.ko.md)에 소유·역할 행렬, 상태·조회 세대, 저장 방어와 배포 기준을 기록했다.

- 편집 후보는 VM resource의 account/domain/project, 생성 후보는 최종 owner를 사용한다. 소유 정보가 없으면 넓은 조회나 로그인 계정 폴백을 사용하지 않는다.
- 공통 `vmGroupLookup`이 페이지 전체와 실소유를 검증하고 loading/ready/empty/forbidden/error/unavailable 상태를 게시한다. 재시도·문맥 변경·종료 후 이전 응답은 무효화한다.
- 생성 소유 변경은 그룹 초안을 지운다. VM 교체의 폼 초기화 이전과 비동기 생성 보조 검증 이후에도 제출 문맥을 재검증한다.
- 템플릿·오퍼링 빈/실패 조회에서 객체 기본값과 명시 상태를 유지한다. 동적 확장·HA의 의존 정보가 없으면 변경을 막으며 그룹 작업은 독립적으로 처리한다.
- Zone/OS/보안 그룹/네트워크/UserData의 빈 응답·실패·문맥 변경을 방어한다. 기존 서버 ACL과 VG-01 groupid/cleargroup 계약은 유지한다.

## 빌드와 집중 검사

WSL ext4 `/home/ablecloud/work/dhslove/cloud-epic-1357-vm-group-ui`에서 다음을 통과했다.

| 검사 | 결과 |
| --- | --- |
| VmGroupAssignment + VmGroupLookup + VmGroupOwnership Jest | **3 suites / 54 tests PASS** |
| 변경 UI/테스트 파일 lint --no-fix | PASS |
| UI npm production build | PASS, source a47650a804f |
| 소스 diff --check | PASS |
| Epic 변경 파일 RAT | PASS, 아래 격리 검사 범위 |

54건에는 VG-01 UI 9건과 이번 소유·권한·빈/실패·다중 페이지·부분 실패·중복 페이지·지연 성공/실패·dispose·보조 조회·제출 경합 45건이 포함된다. 변경 파일 lint와 집중 테스트이며 UI 전체 테스트/전체 lint 결과로 확대하지 않는다. Java/API/DB 스키마 변경이 없으므로 재빌드할 Maven 모듈은 없다. 전체 Cloud 빌드와 Actions 수동 실행은 수행하지 않았다. 기존 Browserslist·번들 크기 경고는 남았다.

추가 `mvn -N -DskipTests apache-rat:check`는 저장소 전체에서 **82,168개 미승인 라이선스**로 실패했다. `ui/node_modules` 81,107개, `ui/dist` 795개, coverage 등 나머지 266개가 포함됐다. Epic 변경 파일 58개만 WSL ext4의 독립 검사 디렉터리에 복사하고 동일 RAT 0.8로 재검사했다. 기존 정책처럼 JSON 2개를 제외했고, 검사 POM 포함 표준 텍스트 29개는 Apache Licensed, PNG 28개는 binary, 미승인 0으로 통과했다. 이는 변경 파일의 집중 라이선스 검사이며 저장소 전체 RAT 통과는 아니다.

## 배포

31 대표 환경에서 동작과 데이터 갱신·DB를 확인한 뒤 32에 확대했다. 서버별 **843개 정적 파일 전부**를 빌드 manifest와 대조했다. 활성 `/usr/share/cloudstack-management/webapp`의 정적 자산만 갱신하고 WEB-INF/config를 보존했다. 기존 FTCTL marker와 새 그룹 lookup marker를 확인했다.

| 항목 | 31 | 32 |
| --- | --- | --- |
| 종료 시 디스크 여유 | 약 328GiB | 약 32GiB |
| mold PID 전후 | 646030, 동일 | 1158028, 동일 |
| 서비스 / client | active / HTTP 200 | active / HTTP 200 |
| WEB-INF / META-INF | 존재 / 기존부터 없음 | 존재 / 기존부터 없음 |
| config / 관리 JAR | 전후 해시 동일 | 전후 해시 동일 |
| 최종 index SHA256 | 4748fe529f40851728dba56f770e67b8313e3b6ac3c015ef39ef476d293645c6 | 동일 |
| 배포 롤백 경로 | /root/epic1357-vg02-deploy-20261013-164854 | /root/epic1357-vg02-deploy-20261013-165508 |

31 JAR SHA256 `5d47fc82e18064a6570c92962eef0dc88495001b0979006dcd8eac451550caec`, 32 JAR SHA256 `4e2c97126c1865c7048d15b8d8f0833745f76ad20516a6cf4860c918d8c34863`를 유지했다. UI 배포 과정에서 관리 서비스 재시작과 호스트 패키지 변경은 수행하지 않았다. 로그인 화면의 기존 버전 표시는 config 보존에 따라 유지되며 배포 소스 증거는 정적 파일 해시다.

## 실제 UI·API·DB 검증

[역할·요청·화면 근거](evidence/VG-02/ui-evidence.md)를 함께 확인할 수 있다.

| 시나리오 | 31 | 32 |
| --- | --- | --- |
| admin 자기 계정 / 타계정 후보와 실제 연결 | PASS | PASS |
| 일반 사용자 후보 / 해제 / 연결 | PASS | PASS |
| 프로젝트 구성원 후보 / 실제 연결 | PASS | PASS |
| listInstanceGroups를 거부한 전용 제한 역할 | 입력 disabled, 조회 요청 0 | 동일 |
| 같은 이름의 관리자·개인·프로젝트 그룹 분리 | 서로 다른 UUID, 대상 소유 매핑 | 동일 |
| 정상 빈 후보의 안내와 새 그룹 생성 | 해당 개인 계정에 생성 | 동일 |
| 생성 계정 → 프로젝트 전환과 초안 제거 | PASS | PASS |
| 겹친 두 조회: 최신 실제 결과 후 이전 빈 응답 도착 | 최신 후보 유지 | 동일 |
| 최종 선택 프로젝트로 실제 VM 생성 | Stopped, 기존 프로젝트 그룹 UUID | 동일 |

배포 전 31에서 타계정 VM 편집이 `account=admin`을 조회함을 다시 기록했다. 배포 후에는 `vg02-31-user-20261013`을 조회했다. 32에서도 대상 계정을 조회했다. 프로젝트 조회는 projectid만 사용했다. 편집 연결/해제 요청은 id + groupid/cleargroup이며, 프로젝트 전송 문맥의 projectid는 공통 API 계층에서 추가된다. UI의 생성 요청은 **최종 projectid + group 이름 + startvm=false**였다.

양쪽 역할·ACL API 검사 각 8건, 총 **16건 PASS**. 일반 사용자의 타계정 후보 조회·타소유 그룹 연결·타계정 VM 변경은 서버에서 531로 거부됐다. 제한 역할의 실제 listInstanceGroups 요청은 432로 거부됐으며 UI API discovery에서 제외되어 폼은 요청을 보내지 않았다. UI 가용성으로 서버 ACL을 대체하지 않았다.

31 그룹 HTTP 503을 탭 XHR에 주입해 후보 제거·입력 차단·정상 재시도를 확인했다. 32에서는 편집 보조 템플릿/오퍼링 조회를 20초 timeout까지 지연시켜 동적 확장 설정 비활성화를 확인했다. 이어 HTTP 403/JSON errorcode 531을 주입해 권한 없음과 일반 실패를 구분했다. 그룹 조회만 복구한 후 메타데이터 실패 상태에서 id + cleargroup=true만 제출했으며 기존 동적 확장 값을 보존했다. 해당 32 구간의 처리되지 않은 Runtime 예외는 **0건**이다. 서버 운영 장애를 판정하는 시험과 탭 응답 주입 시험을 구분한다.

전용 검증 VM 8대의 DB 상태·계정/도메인·호스트·템플릿·오퍼링·OS·HA·동적 확장과 루트 볼륨 상태·크기·pool/path가 전후 동일했다. UI로 추가 생성한 2대도 Stopped/host 없음이었다. 모든 시험 VM을 시작하지 않았으며 기존 사용자 VM은 변경하지 않았다.

## 정리와 경계

시험 VM **10대**, 그룹 **10개**(빈 후보 시험에서 먼저 지운 2개 포함), L2 네트워크 **8개**, 프로젝트 **4개**, 일반/제한 계정 **4개**, 복제한 제한 역할 **2개**를 정확한 ID와 소유 정보로 확인해 정리했다. 전용 계정/프로젝트 및 그룹에 예상 밖의 VM이 없는지 먼저 확인했다.

최종 DB의 시험 대상 활성 VM/볼륨/그룹/매핑/네트워크/프로젝트/계정/역할은 양쪽 모두 **0**이다. 서버별 5개 never-started 볼륨은 removed가 기록되고 pool/path는 NULL이다. 브라우저 XHR 주입과 캐시 override를 해제하고 프로젝트 문맥을 기본 보기로 복원했다. 최종 배포 843개 파일·config/JAR/PID·WEB-INF·HTTP 200을 다시 대조했다.

이번 역할 행렬은 Stopped 전용 VM을 사용했다. Running VM의 전원/PID 보존은 VG-01 근거를 유지하며, 전체 역할·접근성·좁은 폭·멤버 관리 회귀는 VG-06 범위다. 단일 제한 역할 및 집중 조회 경합을 검증했으며 모든 가능한 역할 정책과 대규모 페이지 부하/응답 시간 SLA를 인증한 결과는 아니다. 기존 Epic의 Java 전체 테스트 실패 6건은 이번 UI 변경으로 수정하지 않았다. 전체 CI 상태는 PR의 실제 체크 상태와 별도로 확인한다.
