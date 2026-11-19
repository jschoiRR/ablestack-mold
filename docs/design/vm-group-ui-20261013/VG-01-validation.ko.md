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

# VG-01 설계·구현·배포·검증 결과

2026-10-13 기준 [#1358](https://github.com/ablecloud-team/ablestack-cloud/issues/1358)의 구현, 변경 모듈 빌드, 31·32 배포 및 기능 검증을 완료했다. 상위 [Epic #1357](https://github.com/ablecloud-team/ablestack-cloud/issues/1357)의 첫 구현이며 VG-02~06은 미완료다. Epic PR은 누적 Draft로 운영하고 #1358은 병합 전까지 Open으로 유지한다.

## 소스와 설계

- 기준 Upstream Europa: `9fa65ce2a27b977e848440cb7c787411c11d585b`. 작업 시작 전 origin/upstream/local 일치를 확인했고 구현 중에도 upstream HEAD가 같은지 재확인했다.
- 브랜치: `codex/epic-1357-vm-group-ui`. 배포 소스: `6e633381107820b7851fb341242ee3fe73155e45`.
- Windows 작업 트리: `C:\Users\ablecloud\.codex\worktrees\vm-group-ui-analysis\ablestack-cloud`. WSL ext4 빌드 트리: `/home/ablecloud/work/dhslove/cloud-epic-1357-vm-group-ui`.
- 기존 Windows FTCTL 작업 트리의 3개 추적 변경과 WSL FTCTL 작업 트리·기존 분석 자료를 보존했다. local ablecloud-team 저장소는 변경하지 않았다.
- [설계 문서](VG-01-design.ko.md): 미전달/false는 유지, `groupid`는 같은 소유자의 활성 그룹 연결·이동, `cleargroup=true`는 매핑만 해제, 유효한 `group` 이름은 기존 자동 생성 계약 유지.
- 상충·빈/공백·삭제/없는 대상·계정/프로젝트 불일치는 변경 전 거부한다. VM/그룹 행 잠금과 트랜잭션으로 0~1 매핑을 유지하고, 그룹 삭제는 groupId 조건으로 해당 매핑만 제거한다.
- 그룹만 변경하면 기존 VM 설정·UserData·템플릿·전원·호스트·볼륨 경로를 실행하지 않는다. 혼합 요청의 다른 설정은 기존 경로를 유지한다. 혼합 요청 전체의 외부 효과를 되돌리는 계약은 제공하지 않는다.
- 서버 Spring 컨텍스트에 서비스를 등록했고, 한국어/영어 편집 안내와 API 설명에서 그룹 변경의 재시작 예외를 명시했다. 스키마 변경은 없다.

## 빌드와 자동 테스트

[빌드 근거](evidence/VG-01/build-evidence.md)를 참조한다. JDK 17, Maven 3.9.10, Node 14.21.3/npm 6.14.18을 사용했다.

| 검증 | 결과 |
|---|---|
| api 변경 모듈 install / UpdateVMGroupParametersTest | 9/9 PASS |
| server 변경 모듈 install / VmInstanceGroupAssignmentTest | 14/14 PASS |
| UserVmManagerImpl의 업데이트·ACL·OS 검증 및 새 그룹 전용 경로 | 18/18 PASS |
| VM 그룹 UI 집중 테스트 | 9/9 PASS |
| 변경 UI 소스·테스트 lint | PASS |
| UI production build | PASS, 기존 Browserslist/번들 크기 경고 |
| api/server Checkstyle 및 RAT | 위반 0, Unknown Licenses 0 |
| git diff --check | PASS |

집중 자동 테스트는 총 50건이다. 전체 Cloud 빌드/Actions 수동 실행은 수행하지 않았다. 기존 UserVmManagerImplTest 전체 실행은 VM 생성 소스 서비스 초기화가 누락된 6건에서 실패했다(2 Failures/4 Errors). 변경 전 기준 소스에서도 같은 6건을 재현했으며, 이를 전체 서버 테스트 통과로 보고하지 않는다. 그룹 저장 실패/잠금 실패는 unit에서 검사했으며 실제 DB에 고장이나 스키마를 주입하지 않았다.

UI production build 후 한국어/영어의 상단 재시작 경고만 추가 보완했다. 이 파일들은 런타임에 fetch하는 정적 locale JSON이다. JSON을 파싱 검증하고 dist에 반영했으며 최종 배포의 843개 파일 해시를 다시 대조했다.

## 배포와 디스크 공간

[배포 manifest](evidence/VG-01/deployment-manifest.md), [31 근거](evidence/VG-01/runtime-31.md), [32 근거](evidence/VG-01/runtime-32.md).

| 항목 | 31 | 32 |
|---|---|---|
| 기존 배포/백업 삭제 대상 | 113개 | 89개 |
| 확보한 공간 | 150,580,244,480 bytes | 32,901,324,800 bytes |
| 정리 전 여유 | 약 188 GiB | 약 1.1 GiB |
| 작업 종료 여유(df) | 약 329 GiB | 약 32 GiB |
| 현재 관리 PID | 646030 | 1158028 |
| 현재 관리 JAR SHA256 | `5d47fc82e18064a6570c92962eef0dc88495001b0979006dcd8eac451550caec` | `4e2c97126c1865c7048d15b8d8f0833745f76ad20516a6cf4860c918d8c34863` |
| 검증된 변경 클래스/정적 파일 | 39 / 843 | 39 / 843 |
| mold / client | active / HTTP 200 | active / HTTP 200 |

삭제 전 경로를 정규화하고 과거 배포/백업·staging·패키지임을 확인했다. 활성 관리 JAR/webapp, DB, VM·스토리지 데이터는 디스크 정리 대상에서 제외했다. 정리 직후에는 활성 파일 해시와 서비스 PID가 유지됐다.

전체 RPM/fat JAR 빌드 대신 api/server의 변경 클래스 39개와 Spring bean 등록을 기존 설치 JAR에 반영했다. 다른 JAR 엔트리와 서버 컨텍스트 등록은 보존했다. 실제 활성 `/usr/share/cloudstack-management/webapp`의 정적 자산만 갱신했으며 루트 교체나 rsync --delete는 사용하지 않았다. WEB-INF와 config.json 해시를 보존했다. META-INF 디렉터리는 최종 활성 webapp에 없다. 그룹 및 기존 FTCTL 번들 marker(`cleargroup`, `blockingLoadingState`, `fetchSyncProgress`, `extractJobId`)가 모두 존재한다. 새 서비스의 class loading/의존성 주입 오류는 관찰되지 않았다.

새 롤백 사본은 각각 `/root/epic1357-vg01-deploy-20261013-144755`, `/root/epic1357-vg01-deploy-20261013-145714`에 유지했다. 31을 먼저 배포·UI 검증한 뒤 32로 확대했다.

## 실제 UI와 API·DB·호스트 검증

[UI 요청·화면 근거](evidence/VG-01/ui-evidence.md), [API 계약 근거](evidence/VG-01/api-contract-results.md).

| 항목 | 31 | 32 |
|---|---|---|
| 전용 Running VM | VG01-31-20261013 / i-2-493-VM | VG01-32-20261013 / i-2-165-VM |
| VM UUID | 88c44092-351d-43d1-96e4-9d40d76295b7 | 3766cfb9-8ecd-47b6-b093-5114dc2fb5e7 |
| 호스트·스토리지 | ablecube31-1 / GFS2 Sparse qcow2 | ablecube32-3 / RBD krbd |
| UI 소속 흐름 / DB 매핑 수 | 무소속→A→B→무소속 / 0→1→1→0 | 무소속→A→B→무소속 / 0→1→1→0 |
| API 계약 검사 | 19 + 추가 6 PASS | 19 + 추가 6 PASS |
| QEMU PID(검증 전후) | 2017184 유지 | 3167801 유지 |

실제 브라우저에서 VM 편집 폼을 열어 기존 후보를 선택하고 입력을 비워 저장했다. POST 요청을 수집하되 인증 값은 제외했다. 연결·이동은 id+groupid만, 해제는 id+cleargroup=true만 전송했다. UI의 그룹 링크 갱신과 API/DB 결과를 대조했다. 31에서는 공백 입력의 한국어 오류, 3칸 입력 보존과 API 미전송도 확인했다.

미전달/false 유지, ID 이동·연결, 해제 멱등성, 다른 계정·프로젝트, 삭제/없는 ID, 빈/공백/초과 길이 이름, 상충 요청, 잘못된 혼합 요청, 이름 자동 생성·그룹 이름 변경 후 조회를 검사했다. 별도 Stopped VM 생성 시 기존 group 이름 계약도 통과했으며 해당 VM의 ID 이동·해제 시 Stopped 상태와 설정이 유지됐다.

사이트별 동시 이동/해제 12회(36요청), 기존 그룹 삭제와 B 이동 8회, 대상 그룹 삭제와 연결 8회를 실행했다. DB 표본마다 매핑 수 0~1, 고아/다른 소유자 매핑 0을 확인했다. 별도 동일 이름 동시 생성 6요청은 그룹 하나를 공유했다.

그룹 검증 전후 VM 상태·호스트·오퍼링·CPU·메모리·OS·HA·삭제 보호·UserData 해시·루트 볼륨 UUID/경로/크기/풀을 대조했다. Running VM의 도메인 XML 해시와 QEMU PID·디스크 경로가 같았고 FTCTL 보호는 0건이었다. 기존 VM의 소속이나 전원은 테스트 대상으로 변경하지 않았다.

## 실패·제한과 남은 Epic 범위

- [동시 생성 진단](evidence/VG-01/name-race-diagnostic.md): 최초 31의 6개 동시 이름 요청에서 40초 client timeout이 발생했다. InnoDB의 redo log mutex 대기와 다른 VM 통계 COMMIT 대기가 함께 관찰됐다. 이후 COMMIT/LOCK WAIT 0을 확인하고 다른 새 이름으로 재검증했다. 31의 6요청은 39.316초, 32는 10.124초에 성공했다. 최초 실패를 없었던 것으로 보거나 응답 시간 SLA 통과로 해석하지 않는다.
- 31 재기동 직후 첫 로그인/기능 검색에서 zones.filter 오류 2건과 minified API 오류 5건이 관찰됐다. 그룹 UI 흐름 중 추가 console error는 없었고 32에서는 error 0건이었다. 보조 조회·초기화 안정성은 VG-02/VG-06의 후속 범위로 남긴다.
- 전체 서버 테스트, 전체 Cloud 빌드, 전체 CI, 일반 사용자/프로젝트 소유 VM의 전체 UI 행렬 및 모바일 검증은 완료로 주장하지 않는다.
- VG-02 후보 소유 범위·보조 조회, VG-03 그룹명/삭제 안내, VG-04 필터·갱신, VG-05 표준 멤버 UI, VG-06 최종 회귀는 이번 구현에 포함하지 않았다.

## 정리

[정리 근거](evidence/VG-01/cleanup-results.md). 전용 VM 4대와 활성 그룹 13개를 API로 삭제/expunge했다. 임시 삭제 경합 그룹들은 각 검사에서 삭제했다. 양쪽 DB에서 활성 전용 VM/볼륨/그룹/매핑이 모두 0이다. 생성된 Ready 루트 볼륨은 Expunged이며 한 번도 시작하지 않은 Allocated 루트 볼륨도 removed 상태다. 해당 실행 호스트의 VM 도메인, 31의 GFS2 파일, 32의 RBD 이미지·krbd 매핑이 없다. 양쪽 서비스와 /client/ HTTP 200을 정리 후 다시 확인했다.
