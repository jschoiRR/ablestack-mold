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

# AS-03 카운터·기간·정책 평가 검증 기록 (#1349)

Epic #1346 / 누적 Draft PR #1356. 2026-10-11에 변경 모듈을 31 GFS2 Sparse와 32 Ceph krbd에 배포하고 기존 AS04 전용 그룹에서 실제 Mold UI로 정책·운영 결과를 대조했다. AS01/AS02 그룹과 기존 자원을 보존한다. 이 기록은 AS03 범위이며 Epic 전체 프로덕션 판정은 후속 이슈 완료 후 수행한다.

## 구현과 실환경 발견 사항

- 명시적인 1000ms/초 및 long 범위 검증, 현재 멤버 전원의 전체 연속 관측 기간·신선도·수집 공백·유효 표본을 검사한다. UI의 관측 평균은 설명 값이며 판정은 관측 구간의 모든 시점이 조건을 만족해야 한다.
- 조건 AND, 동일 방향 정책 OR, 확장 우선, 불확실한 확장 판단에서 축소 보류, 최신 min/max 및 AS04 작업 소유권 검사와 연동한다. 성공한 완료 시각을 그룹별로 저장하고 관련 양 방향 정책의 가장 긴 quietTime을 적용한다. 공유 정책의 다른 그룹을 함께 제한하지 않는다.
- CPU는 Host/Agent의 VM 평균 %, 메모리는 balloon의 unused/last-update 기반 VM 평균 %다. 호스트 메모리 수집 주기의 두 배를 넘긴 값은 미수집으로 처리한다. 네트워크는 공유 VR 공인 인터페이스 누적 차분의 Mbps/현재 VM 수, LB는 해당 규칙의 실제 연결 합계/현재 VM 수다. 누락을 0으로 대체하지 않는다.
- 실제 배포 후 GenericDao의 기본 문자열 길이 255로 평가 JSON이 잘리고 Date 저장이 초 단위로 절삭되는 문제를 발견했다. MEDIUMTEXT 최대 길이를 명시하고 평가/완료 시각을 BIGINT 밀리초로 저장한다. 실제 GenericDao 바인딩 집중 테스트와 2KB 이상의 API JSON·DB 시각을 검증했다. 기존 시간값의 마이그레이션은 최대 999ms 보수적으로 올림하여 쿨다운을 짧게 만들지 않으며 무효 평가 JSON만 초기화한다.
- 기존 Mold 상세 탭에 최근 평가 시각·수집 시각·판정/보류·정책·카운터별 값/단위/표본/대상 수를 표시한다. 조건별 사유와 정책 전체의 보류 사유를 구분한다. 생성·상세 편집 모두 GT/GE/LT/LE/EQ와 임계값 0을 지원하고 표준 카운터 이름을 한글로 표시한다. 전체 VM 메뉴 표준 통합은 #1355에서 수행한다.

## MET 검증 결과와 증거 수준

| 항목 | 실제 UI·클러스터 증거 | 집중 테스트에서 보강한 경계 |
|---|---|---|
| MET-01 CPU | 31 두 CPU 부하에서 약 99.734%, 32 HTTP 부하에서 약 1.508% 관측. 부하 종료 후 감소 및 각 2→1→2 자동 조정의 UI·DB·VM/LB 대조 | 다중 VM 평균, 현재 멤버만 포함, invalid/미래 값 |
| MET-02 메모리 | 두 클러스터의 정상 % 표시 후 한 VM의 live balloon 통계 주기를 60→0으로 중지. 오래된/부분 누락 시 —·보류를 표시하고 축소 조건이 맞아도 2대 유지. 수집 설정 복원 | stale/partial/비활성 표본을 0으로 평가하지 않음 |
| MET-03 네트워크 | 32 실제 HTTP/장기 연결 부하의 RX/TX 양수 확인. 별도 AS01 그룹의 같은 VR·다른 LB 포트 부하에서 대상 AS04 RX/TX 양수, 대상 LB 연결 0 및 공유 트래픽 안내 일치. 31도 실제 VR 값/단위/현재 멤버 분모 표시 | Mbps 1,000,000bit/s, 누적 차분/초/VM, 정렬·중복·분모 |
| MET-04 LB·VR | 32 실제 LB 평균 6.857 연결/VM 관측. 양쪽 라우터를 UI로 정지/시작. 정지 시 NO_RESOURCES/—로 2대 보존, 복구 후 새 전체 기간 대기 | 누적 카운터 감소·라우터/유형 변경·부분 실패 |
| MET-05 연산자 | 두 UI의 다섯 한글 연산자 선택, GE 0·LE 100 편집 저장. 32 EQ 0 정책 생성과 양쪽 생성 폼 EQ/GE/LE 0 조건·한글 표 표시 | GT/GE/LT/LE/EQ의 같은 값/앞/뒤 정확한 경계 |
| MET-06 논리 | AND 다섯 조건 중 CPU 미충족이면 확장하지 않고 축소. OR의 다른 CPU 정책이 충족하면 확장 우선, 최대 2대에서 축소로 넘어가지 않음 | 여러 정책 OR, 불확실 정책과 유효 정책, 최신 min/max |
| MET-07 기간 | 재활성화/멤버 변경/VR 복구 후 60초 전체 구간 대기 및 이후 판단을 UI 확인 | 가짜 시계 59.999/60.000/60.001초, interval 초/ms 경계 |
| MET-08 쿨다운 | 각 축소 완료 뒤 Up/Down 모두 맞아도 300초 동안 1대 유지, 이후 확장 우선 1→2. 실제 완료 시각 밀리초 및 cooldownUntil 대조 | 반대 방향·복수 정책·공유 정책의 그룹 간 분리 |
| MET-09 결측 | 라우터 중지/메모리 한 VM 누락 때 값 —, 구체적 보류 사유·축소 보호 확인 | 빈/더미/INACTIVE/오래된·누락·큰 공백·중복 표본 |
| MET-10 입력 | 32 정책 기간 1초(interval10) 및 2147483648 입력을 한글 오류로 거부, 자원 생성 없이 편집 유지 | 정수/양수/상한 및 overflow, duration>=interval |

정각 ±1ms, 인위적인 누적 값 감소, 공유 정책의 실제 다른 관리 노드 경쟁을 실클러스터 UI PASS로 확대하지 않는다. 해당 항목은 가짜 시계/DAO/Agent 계약 테스트와 실제 관련 UI 경로를 구분하여 기록했다.

## 빌드·테스트

WSL ext4 `/home/ablecloud/work/autoscale-review-20261010/source`에서 변경 `api/engine/schema/server/plugins/hypervisors/kvm`만 빌드했다. 고유 Java **520건**(Server160, Schema29, KVM331), UI **118건**(새 평가 helper31 + 기존 AS04/생성87)이 통과했다. 모듈 RAT·Checkstyle 및 UI 집중 lint/production build를 통과했다. 재실행 횟수를 테스트 수에 중복 합산하지 않는다. UI 마지막 변경은 조건별 사유·생성 카운터 표기/열 매핑·생성 다섯 연산자이며 집중 lint와 최종 production build·실제 화면으로 확인한다.

전체 Cloud/RPM 빌드·GitHub Actions 전체 CI는 실행하지 않았다. Maven은 `/mnt/c`에서 실행하지 않았다. Node20/OpenSSL legacy provider를 사용했다. 첫 UI 재빌드의 OpenSSL 환경 오류는 옵션을 지정해 재실행한 빌드와 구분한다.

## 배포·스토리지

배포는 변경 모듈의 클래스/리소스를 기존 관리/Agent JAR에 반영했다. 기존 FTCTL 기능과 설치 패키지 차이를 보존한다. 활성 UI `/usr/share/cloudstack-management/webapp`의 정적 파일만 갱신하여 WEB-INF/META-INF/config를 보존했다. 관리 서비스·각 Agent 활성/호스트 Up, `/client/` HTTP200 및 배포 해시를 대조했다.

- 관리 31 SHA256 `4668dbbafac60adf398625226b7b6d0108f746934c04e59fc700804753443f4b`, 백업 `/root/epic1346-as03-precision-20261011-134156`.
- 관리 32 SHA256 `43d10df9fda3d374ded20339ef79fbfd38eb6252160e83d3ee849e5b12335298`, 백업 `/root/epic1346-as03-precision-20261011-134233`.
- KVM 변경 모듈 SHA256 `6639dfccc7bd8daa60d6941f189002a9cb49a7f7f8a25275223e0272c52cd983`, 31/32 각 3대의 활성 JAR별 설치 해시는 `as03-agent-artifacts.json`과 배포 결과에 기록했다.
- 최종 UI: 소스 `f166e2b779b74e0912bd55b7ef85595002308c6e`, SHA256 `cdf2fafaab386d8e0f6526c6da206ab23980cae8b0f7f94602670dce5784ee9c`, 정적840파일 검증. 31 백업 `/root/epic1346-as03-ui-20261011-142812`, 32 백업 `/root/epic1346-as03-ui-20261011-142824`..

실제 이번 확장 VM 31 `i-35-425-VM` ROOT는 `/mnt/glue-gfs/3ab6a2bd-b06f-4eb4-a09a-a980a75cbeb9`의 독립 Sparse QCOW2, virtual100GiB/actual약3.57GB/backing 없음이다. 기준397도 같은 방식이다. GFS2 Thin을 사용하지 않았다. 32 `i-6-101-VM` ROOT는 `/dev/rbd/rbd/355f7c0b-8561-4d91-bdca-238daef451f2` raw krbd이며 기준73도 krbd다.

## 최종 UI·보존 상태

한국어·라이트/다크의 정상/보류/비활성 상태, 일반 사용자32 및 일반 사용자31의 프로젝트 문맥에서 최근 평가·정책 조회/편집 권한을 확인했다. 최종 생성 모듈에서 두 환경의 확장/축소 선택에 다섯 한글 연산자가 표시되고 EQ/GE/LE 임계값0을 한글 조건 표에 추가했다. 생성 폼의 초안은 제출하지 않고 닫았다. 기존 그룹을 실제로 조정하는 정책 설정은 UI로 저장했다. 전체 VM UI 표준/레거시 레이아웃은 #1355 범위다.

기존 `AS-PROD-31-AS04-CONCURRENCY`와 `AS-PROD-32-AS04-CONCURRENCY`는 각각 DISABLED/진행 작업 없음/Running VM1/실제 LB 멤버1/HTTP200이다. 원래 min1/max3/interval10, duration60/quiet10, CPU Up(31 GT20/32 GT5), Down(31 LT10/32 LT1) 및 원래 정책만 연결하도록 복원했다. 임시 메모리/VR 조건과 OR 정책은 연결을 해제하여 동작에 영향을 주지 않는다. 미연결 검증 메타데이터는 추적용으로 남긴다.

31은 `AS-PROD-31-AS01-project` 선택 후 그룹이 보인다. 프로젝트 안에 AS01/AS02/AS04 그룹3개가 각 DISABLED/VM1로 보존되고, 32 AS01 USER/VPC/AS02/AS04 그룹4개도 각 DISABLED/VM1이다. 양쪽 라우터 Running, balloon 통계 주기 원래60초·새 last-update를 확인했다. 제한 HTTP 생성기는 종료됐다. 31 CPU 부하는 180초로 제한한 프로세스이며 종료 후 정상 저부하 값으로 돌아왔다. 추가 게스트 SSH/QGA 종료 조회는 접속/지원 문제로 완료하지 못했으므로 프로세스 조회 성공으로 표현하지 않는다.

이번 추가 VM은31 424/425,32 100/101이다. VM은 removed/Expunging 이력으로 남고 ROOT4개는 Expunged/removed다. 실제 GFS2 파일2개와 RBD 이미지2개가 없는 것을 별도로 확인했다. 오토스케일 관련 pending job0, 원래 VM/LB만 남아 있다. 이 확인은 정상 경로 결과이며 #1351의 삭제 실패 보상 검증을 대체하지 않는다.

## 제한과 후속

32 초기 180초 제한 HTTP 부하에는 **8/6,659 요청 실패**가 있었다. 무중단·성능 합격으로 표현하지 않는다. 별도 공유 VR 범위 확인 90초 부하는 3,337회 HTTP200/실패0이지만 서비스 수명주기 전체 합격을 뜻하지 않는다. 실패 원인별 드레인·LB/VM 보상은 기존 #1351, 전체 운영 안정성은 #1354에서 검증한다.

NetScaler/Netris/VMware/XenServer, 실제 다중 관리 노드 경쟁 및 장시간 운영은 수행하지 않았다. 기존 레이아웃의 좁은 탭/일부 레거시 영문 라벨은 #1355의 VM UI 표준 통합에서 처리한다. #1352의 편집/예약 전체 계약과 #1353 운영 가시성 전체 범위는 착수하지 않았다.

## 추적 가능한 자료

실행 자료는 WSL ext4 `/home/ablecloud/work/epic1346-implementation`의 `as03-` 파일에 보존한다. 화면 증거는 Windows `C:/Users/ablecloud/.codex/artifacts/epic1346-1349-20261011`에 보존한다. 인증 값 파일은 보고서/PR에 포함하지 않는다.

- `as03-db-runtime-timeline-31/32.jsonl`: actual 평가/멤버/작업 시각.
- `as03-*-proof-31/32.json`: 기간·쿨다운·확장/축소·최대 인원·메모리 보호·OR 증거.
- `as03-shared-vr-counter-proof.json`, `as03-current-disks-*.log`, `as03-memory-fault/ restored-*.json`: 수집 범위·이미지·복원 증거.
- `as03-server-tests-final.log`, `as03-schema-precision-final.log`, `as03-kvm-final.log`, `as03-ui-r3-tests.log`, `as03-module-rat-final.log`: 집중 검증.
- `as03-precision-management-*`, `as03-agent-*`, `as03-ui-operators-*`: 설치 파일·소스/해시·복원 경로.

다음 진행 대상은 **#1351 AS-05 VM/LB 수명주기·드레인·삭제 보상**이다. 이슈별 절차에 따라 사용자 지시까지 대기한다.
