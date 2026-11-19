<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# AS-05 실제 검증 기록 (#1351)

Epic #1346 / Draft PR #1356의 다섯 번째 완료 범위다. 대상은 VM·LB 등록/제외·드레인·실제 삭제 확인과 실패 보상, 멤버 추적이다. 전체 Epic 완료 또는 서비스 무중단/성능 인증을 의미하지 않는다.

## 소스·빌드·배포

- 수명주기 구현 `3526e95e8ff0c114c3ebb416101700acac009776`, 한국어 오류 안내 UI `fd9087a1145f80eb3f1ee4634755473845b8661f`, 시작 상태 보완 `e943870b5a0fa76904df035b318cd05835e94c8c`, 최종 서버 `a5ea15c370cd1cf9ba0de92fef2fee9693123a62`.
- 최종 Java 고유 207건: schema DAO/VO 12, AutoScaleManager 157, LB 19, UserVmManager 19. 실패/오류/skip 0. 변경 `engine/schema,server` 모듈 install과 Checkstyle 오류0, 모듈 RAT 통과. RAT는 이전 빌드가 생성한 systemvm `metadata.ini/sha512sum.txt`만 작업 디렉터리 밖에 임시 보관하고 원위치로 복원했다. 소스에 라이선스 예외를 추가하지 않았다.
- UI 132건/4 suites, lint/production build 통과. 최종 서버 보완은 UI를 변경하지 않아 위 UI 번들을 유지했다.
- 변경 모듈의 47개 클래스 항목을 현재 설치 관리 JAR에 반영했다. 전체 Cloud/fat JAR/RPM/Actions 빌드를 실행하지 않았다. 두 관리 서버의 서비스 active, `/client/` 200, 클래스별 SHA256과 UI 정적840파일 해시를 확인했다. WEB-INF와 서버 config를 보존했다. META-INF는 배포 전부터 두 서버에 없었으며 존재하는 경우 보존하는 배포 절차를 적용했다.

| 대상 | 최종 관리 JAR SHA256 | 백업 |
|---|---|---|
| 31 GFS2 | `fb39babdfd017e5894a38d0c8361de1b5fd9b57fc3a48ad325020d641597f2df` | `/root/epic1346-as05-r4-20261011-163142` |
| 32 krbd | `f99880d0fad6519a5231e0c4b26b98aa599b419104261bd21d7edc8861d13309` | `/root/epic1346-as05-r4-20261011-163219` |

UI 압축물 `ui-as05-fd9087a1145.tgz` SHA256 `f1e0ce074e5ff955c6726cf200ea44c254296a560849834b28764574ccf49d35`. 31/32 UI 백업은 각각 `/root/epic1346-as05-ui-r2-20261011-154828`, `/root/epic1346-as05-ui-r2-20261011-154845`다.

## 실제 UI·스토리지와 검증 결과

31은 프로젝트 `AS-PROD-31-AS01-project`를 UI 헤더에서 선택했다. 32는 admin과 기존 일반 사용자 `AS01 검증`을 사용했다. 기존 `AS-PROD-31-AS04-CONCURRENCY` / `AS-PROD-32-AS04-CONCURRENCY` 그룹으로 시험하고 전용 AS05-CLEANUP 그룹만 삭제했다. 실제 UI의 버튼/폼/확인 모달을 사용했으며 API/DB/VR/게스트/호스트 조회는 원인 확인과 교차 검증이다.

| 항목 | 31 / 32 증거와 판정 |
|---|---|
| LIF-01 | **PASS: UI 1↔3 왕복 각10회.** 두 환경의 40개 UI 수렴 기록과 API Running VM/LB3·1을 대조했다. 준비 중 멤버도 max3에 포함한다. 1~5회는 최초 서버3526, 6~8회는 시작 상태 보완e943, 9~10회는 최종 서버a5ea에서 수행했다. 32의 2~10회는 일반 사용자 UI다. |
| LIF-02/03 | **PASS: 최종 서버a5ea의 실제 CPU 부하로 1→2→3→2→1.** min1/max3을 변경하지 않고 Up GT20/5, Down LT10/1로 자동 조정했다. 최대3을 유지하는 추가 부하·최소1 이하로 줄이지 않는 저부하 평가를 UI와 전체 API 인원으로 확인했다. 110개 샘플/환경에서 관측 min1/max3이었다. |
| LIF-04 | 불일치 host tag로 시작 실패, 사용자 VM quota 소진, 스토리지 tag 불일치로 적격 primary pool 없음. 두 UI에 오류·재시도 안내를 확인하고 할당 실패/시작 실패 VM·ROOT를 보상했다. pool 없음 시험은 실제 allocator `Found 0 pools`와 capacity 예외를 대조했다. 실제 물리 디스크 전체 소진 시험은 수행하지 않았다. |
| LIF-05 | 실제 VR의 CsLoadBalancer consumer에 해당 AS04 pool만 TTL 제한 실패 주입. 신규 VM은 Running/ASSIGNING_RETRY, 멤버2/LB1을 유지하고 중복 VM을 만들지 않았다. 원복하면 동일 VM452/128이 LB에 등록됐다. |
| LIF-06 | LB 제외 실패 복구→제외 확인→저장된 DRAINING 기한→VM 삭제. 60초 유예에 30초/45초 HTTP 연결 각2개씩, 총8개가 제외 이후 기한 전에 본문까지 완료했다. 20초 유예보다 긴 45초 연결은 기한 후 삭제로 종료되며 이 제한을 UI에 설명했다. |
| LIF-07 | 실제 LB 제외 실패 시 EXCLUDING_RETRY에 같은 VM·멤버/LB를 보존했다. 삭제 보호로 destroy 실패 시 미삭제 VM을 DESTROYING_RETRY에 추적했다. 유예 중 확인된 LB 제외를 반복하지 않는다. |
| LIF-08 | cleanup=false는 멤버2 보존/삭제 거절. 승인받은 전용 그룹에 cleanup=true UI 실행: 한 VM 삭제/한 VM 보호로 부분 실패를 보존. 보호 해제 후 자동 정리해 Disabled/빈 그룹을 유지하고 명시적 UI 재시도로 그룹 삭제. VM426/427/102/103 및 각 ROOT100GiB 제거 확인. |
| LIF-09 | AS01/AS02/AS04, 공유 프로필/정책/조건, 기존 VM/LB 보존. 스케줄0 유지. 고아 매핑·삭제 VM의 활성 ROOT0 및 실제 파일/RBD·libvirt 부재 최종 교차 검증. |
| LIF-10 | **PASS: 최종 활성 그룹의 중지·재시작·일반 삭제를 31 프로젝트/32 일반 사용자 UI에서 거절하고 VM·ROOT를 보존했다.** 비활성 CLEANUP 작업 중 수동 중지도 거절했다. 작업 없는 비활성 그룹에서는 두 UI의 정상 재시작 완료 이벤트와 async job 성공(status1/resultCode0)을 확인했다. |

최대3대씩 확장 상태의 실제 ROOT6개를 호스트에서 확인했다. 31은 Sparse 오퍼링·100GiB 독립 qcow2·backing 없음·실제 할당 약3.6GB로 Thin을 사용하지 않았다. 32는100GiB raw `/dev/rbd/rbd/<UUID>`의 실제 krbd 디스크다. 할당 실패 후 이미지가 생성되지 않은 ROOT는 `Destroy+removed/path=null`일 수 있으므로 생성된 이미지의 `Expunged+removed`와 구분했다.

## 정상 시작 오류 기록 보완

반복 시험에서 VM 시작 API가 시작 전 Stopped VO를 반환하여 성공한 VM에도 STARTING_RETRY 오류 이벤트가 생기는 것을 발견했다. 최종 서버는 시작 호출 후 DB의 현재 VM 상태를 다시 확인한다. Running만 LB에 연결하며 Starting/Migrating 등 과도 상태는 실패로 기록하지 않고 기존 작업을 기다린다. 저장된 Stopped/null은 실제 실패로 처리한다. 오래된 반환 VO와 실제 Running/Starting/Stopped 조합3개를 집중 테스트했고 시작 상태 보완 배포 이후 07:18:18 UTC부터 최종 CPU/반복 시험까지 두 그룹의 AutoScaleVmGroup ERROR 이벤트0을 확인했다. 의도적으로 거절한 VM 수동 작업의 ERROR 이벤트와 구분한다.

## 수동 재시작 보호 보완

실제 UI에서 활성 그룹 VM 재시작이 허용되는 경로를 발견했다. 중지/삭제에는 기존 상태 보호가 있지만 공개 reboot API에는 빠져 있었다. 최종 서버는 권한 검사 뒤, volatile ROOT 교체나 게스트 명령 전에 같은 보호 검사를 적용한다. 일반/강제 재시작 거절 및 허용 이후 경로를 집중 테스트했다. 최종 31 프로젝트/32 일반 사용자 UI의 재시작 거절과 한글 안내·Running VM 보존을 확인했다. 비활성·작업 없음의 실제 UI 재시작은 31 07:53:05 UTC/32 07:53:06 UTC에 INFO·완료로 기록됐고 각 VM은 Running을 유지했다.

## HTTP 판정과 실제 한계

- LB 제외 실패·복구·드레인·삭제를 포함한 150초 일반 HTTP 요청: 31 **5,658/5,658**, 32 **5,712/5,712** HTTP200/실패0. 32 별도90초 기준은3,332회/실패0.
- 초기 정상 확장240초: 31 **9,142 HTTP200 +4 시간 초과**, 32 **9,109 HTTP200 +4 시간 초과**. 4개 요청자의 3초 제한이 신규 LB 등록 시점에 발생했다. 애플리케이션 초기 준비/HAProxy 검사/설정 적용 중 어느 것이 원인인지는 확정하지 않았다. 이 시험을 무손실 확장 또는 전체 성능 PASS로 적지 않는다.
- Cloud의 Running 상태와 애플리케이션 HTTP 준비 완료는 별개다. 관리자/게스트 서비스와 LB health check 준비가 필요하다. 사용자 지시와 같이 장시간 운영은 모든 구현·최종 UI 통합 후 #1354 마지막에 수행한다.
- 비활성 그룹 정상 재시작 이후 마지막30초 HTTP: 31 **1,143/1,143**, 32 **1,064/1,064** HTTP200/실패0.
- 이전 AS03/AS04 문서의 미분류 HTTP 실패를 이번 일부 성공 결과로 덮어쓰지 않는다. 마지막 제한 HTTP 확인도 장시간 운영 시험이 아니다.

## UI·보존·증거 읽기

현재 Mold ResourceLayout 상세 Alert에 재시도/드레인/부분 정리/사용자 그룹 삭제 재시도 안내를 반영했다. 삭제 확인과 grace tooltip, 수동 VM 작업 거절을 한글로 표시한다. 라이트/다크 모드의 배경·텍스트 대비, 31 프로젝트/32 일반 사용자 화면과 VM 메뉴를 검증했다. 프로필 저장 성공 후 모달이 자동으로 닫히지 않는 기존 UX 항목은 #1355에 이미 기록돼 있으며 이번 범위에 새 이슈를 만들지 않았다.

기존 AS01/AS02와 대표 AS04 그룹·VM은 유지한다. 31은 프로젝트 선택 후 그룹이 표시된다. 시험용 VM quota는20으로 복원했다. 시험 전용 NO-HOST/NO-POOL 오퍼링은 검증 카탈로그에 남기고 대표 그룹에서는 해제했다. 32 검증 계정의 primary storage quota는 기존200GiB가 이미 사용하는400GiB보다 작아 풀 부족 시험에 앞서700GiB로 준비했으며 후속 max3 시험의600GiB를 수용하도록 유지했다. 31 프로젝트는 기존600GiB를 유지한다. 서비스 오퍼링/ROOT 오퍼링/32 원래 UserData를 복원했다. 정책 duration/grace60초와 quietTime10초도 원복했다. 두 대표 그룹은 DISABLED/작업없음/Running VM1/LB1이다. 임시 CPU 부하를07:46:18 UTC에 명시 종료했고 VR 실패 주입 파일·consumer 변경을 원본 해시로 복원했다.

원시 DB 타임라인의 과거 `lbActive` 필드는 removed 된 historical LB row를 포함했으므로 서비스 인원 근거로 사용하지 않는다. 최종 audit는 `revoke=0 AND removed IS NULL`을 적용하며 실제 API/VR 대상과 대조한다. v2 DB 타임라인의 최근5개 VM 목록은 오래된 원본 VM을 생략할 수 있으므로 전체 인원 계산에는 사용하지 않는다. 전체 인원 근거는 CPU/API 스트림이다. DB grace DATETIME은 UTC 문자열로 해석한다. 과거 `deadlineEpoch`은 MySQL 시스템 시간대 변환 영향이 있어 사용하지 않는다.

최종 인원·보존·공유 자원·배포와 UI 증거는 [evidence/AS-05](evidence/AS-05/README.ko.md)에 기록했다. 31 기존3그룹/VM3·ROOT3·300GiB, 32 기존4그룹/VM4·ROOT4·400GiB와 사용량 카운터가 일치한다. 두 환경 고아 VM 매핑/삭제 VM의 활성 ROOT는0이고, 총6호스트에서 삭제 대상 libvirt 도메인·GFS2 파일·RBD 이미지·krbd 매핑 부재 및 원본 ROOT 존재를 확인했다. 기존 그룹의 스케줄은0으로 유지됐으며 별도 스케줄 생성/삭제의 실검증은 #1352 범위다. 초기 부분 삭제 화면의 일부 영문 오류는 UI 보완 전 증거다. 최종fd908 UI의 수동 VM 작업 오류는 한국어로 확인했다. 비밀번호·API 키·전체 profile/UserData·HAProxy 통계 인증 값은 공개 증거에 포함하지 않는다.

## 다음 순서

#1351만 완료하고 다음 **#1352 AS-06 정책·VM 프로필·예약 변경·권한 계약 정합성 개선**을 안내한 뒤 사용자 지시를 기다린다. Epic/PR Draft를 유지하며 #1353·#1355·#1354를 순서에 맞게 별도 처리한다.