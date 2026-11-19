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

# AS-06 (#1352) 구현·배포·UI 검증 기록

2026-10-11. Epic #1346 / 누적 Draft PR #1356. **정책·프로필·예약 계약 구현, 변경 모듈/UI 빌드, 31/32 배포, 해당 범위 실제 UI 검증과 전용 VM·볼륨 정리를 완료했다. #1352 완료, Epic 진행6/9.** 다음 #1353은 자동 착수하지 않는다.

## 구현된 계약

- GT/GE/LT/LE/EQ 및 0 임계값을 현재 정책 탭에서 보존한다. 공유 정책·조건의 영향받는 그룹을 소유자/프로젝트 범위에서 안내하고 마지막 정책/조건 제거를 막는다. 프로젝트에서 새 조건/정책의 소유권을 전달한다.
- 서비스 오퍼링 단독 변경도 모든 연결 그룹 Disabled/진행 작업 없음 조건으로 보호한다. 활성 고정 오퍼링, Ready 템플릿, ROOT/DATA 오퍼링·크기, 네트워크, SSH 키 및 affinity/security group의 소유권/Zone/형식을 검증한다. 잘못된 이름·중복 배포 파라미터·잘못된 조합을 거절한다. 실제 security group 배포 인수를 전달하며 비지원 네트워크는 거절한다.
- 누락 필드는 유지하고 마지막 파라미터 제거는 `clearotherdeployparams`/`clearcounterparams`로 DB 값까지 비운다. DATA 오퍼링 제거 시 크기도 함께 제거한다. UserData 원문/저장 데이터 및 세부 변수의 유지·변경 계약을 구분한다. Update Profile API의 요청/응답을 sensitive로 지정하고 내부 VM 생성은 POST로 전달한다.
- 편집은 작업 완료까지 저장 중 상태를 유지하고 완료 후 다시 읽는다. 15분 한도와 제한된 조회 재시도를 사용하며 POST를 자동 재전송하지 않는다. 새 조건/정책 연결의 확정 실패만 의존성 역순으로 보상하고 결과 불명은 자원 ID와 확인 안내를 남긴다. 편집 자체의 영속 저널/재접속 복구 기능을 구현한 것은 아니다.
- 예약 날짜를 선택한 시간대의 벽시각으로 다룬다. 알 수 없는 시간대, DST gap/overlap, 종료≤시작, 잘못된 min/max를 거절한다. 기존 시작일은 변경하지 않았으면 재전송하지 않고 종료일 제거는 `clearenddate`로 명시한다.
- 분을 버림 처리하고 시작 경계를 포함한다. 작업 잠금 획득·이미 제출한 async 작업·최신 예약 활성/자원/동작/기간을 다시 확인한다. 동일 그룹·동일 시각은 생성 ID가 작은 예약 하나만 실행한다. 수동 변경과 같은 그룹 API/경쟁 제어 경로를 사용한다. 비활성 그룹은 범위만 저장하며 이미 제출한 작업을 예약 비활성화로 취소하지 않는다.
- 실제 UI에서 발견한 Cron 편집의 일·월 조건 유실을 수정했다. 편집 시 Cron 모드로 원식을 유지하고 Mold `ko_KR`를 cronstrue `ko`로 매핑해 폼과 목록 모두 한글 설명을 표시한다. 예약 이벤트 4종과 파라미터 값 오타를 보완했다.

## 빌드 및 검증한 소스

| 항목 | 결과 / 근거 |
|---|---|
| Maven 소스 | `a423c1332aa8164b24d5389235057ddcba8866e3` |
| 최종 UI/locale 소스 | `fc50b9c765ac9676f7bf529e13594727b41d79e3` |
| Java | API 8 + server 246 = **254건**, 실패/오류/skip 0. AutoScale 생성/평가/프로필/예약 및 기존 VM 예약 포함 |
| 변경 모듈 | WSL ext4 `api,engine/schema,server` install 성공, Checkstyle 오류 0, 모듈 RAT 성공 |
| UI | **7 suites / 162건**, coverage=false 집중 실행, 변경 파일 lint 및 production build 성공 |
| UI 빌드 소스 결합 | tracked UI 774파일의 빌드 전 SHA256과 소스를 확인. postbuild의 config 문서 매핑 제거만 원본으로 복원하고 774파일 재대조 |

전체 Cloud/fat JAR/RPM 빌드, GitHub Actions 전체 CI, qemu/Agent 빌드는 실행하지 않았다. 현재 설치 관리 JAR에 변경 모듈의 55클래스를 반영한 방식이며 신규 전체 패키지 빌드로 표현하지 않는다. 모듈 RAT 첫 시도의 ignored systemvm-template 생성 메타데이터 2파일은 작업 폴더로 보관한 후 최종 모듈 RAT를 통과했다. 이전 실패/중단 로그를 최종 통과 건수에 합산하지 않았다.

## 31/32 실제 배포

| 대상 | 활성 파일 / SHA256 | 백업 / 상태 |
|---|---|---|
| 31 관리 | `cloudstack-4.23.0.0-Mold.Europa-202609222315.jar` / `fb8a982dabec39fbf300b235a4bf5ea5e4ecb91bb372544c9b578de8e7d9714e` | `/root/epic1346-as06-r1-20261011-175317`, PID 428950 |
| 32 관리 | `cloudstack-4.23.0.0.jar` / `c20c8d80b6f2e5f8a4ee4ad104e85737e2a2aebc2821033cca754cec6f28ab58` | `/root/epic1346-as06-r1-20261011-175355`, PID 885151 |
| 최종 UI 공통 | `ui-as06-fc50b9c765a.tgz` / `df6e686eb613f6b8b95517e0b8333c2e20aa5bee9bed1379d888d4a883339935` | 31 `/root/epic1346-as06-ui-r3-20261011-184055`; 32 `/root/epic1346-as06-ui-r3-20261011-184116` |

활성 `/usr/share/cloudstack-management/webapp`의 **정적 840파일** 개별 해시를 확인했다. WEB-INF·config와 존재하는 서버 디렉터리를 보존했고 UI만 배포할 때 관리 PID가 유지됐다. 두 서버 mold active와 `/client/` HTTP200, AS02/AS04/AS05 및 AS06 런타임 마커를 확인했다. 최초 UI는 a423, Cron 편집 보완은 34cb, 최종 목록 한글 보완은 fc50이다. 기능별 검증 시점을 아래와 같이 구분한다.

## 실제 UI 검증과 보조 증거

| 계약 | 31 GFS2 프로젝트 / 32 krbd 일반 사용자 결과 |
|---|---|
| 정책 편집 | 두 UI에서 GT/GE/LT/LE/EQ 및 임계값0 저장·왕복. 공유 영향 그룹 이름 확인. 31 공유 정책 duration70/quiet20 저장 후60/10 복원 |
| 조건·정책 연결 | 31 프로젝트 조건 추가/해제 및 새 확장 정책 추가/해제, 32 일반 사용자 새 축소 정책 추가/해제. 마지막 정책/조건 제거 버튼 제한 확인. 해제된 테스트 정책/조건 객체는 보존 |
| 프로필 상태 제한 | Enabled 상태의 편집 버튼 제한. 오퍼링 단독 변경의 서버 거절도 같은 소유자 API/job 실패로 확인 |
| 명시적 파라미터 제거 | 두 UI의 마지막 배포 파라미터 제거 후 빈 값 확인. ROOT0 한글 오류, 정상 새 파라미터 저장. DATA 제거 시 disksize 동시 제거 후 다시 조회 |
| 최종 축소·정리 | 두 UI3→2→1, 승인된 신규4VM/ROOT와 분리 DATA4개 별도 UI 영구 삭제. 원본 VM·ROOT 및 다른 그룹 보존, 실제 파일/RBD/매핑 부재 |
| 향후 VM 적용 | 31 오퍼링, 32 템플릿 변경·복원 및 UserData/ROOT120/DATA10/SSH/affinity 저장. 기존 VM ROOT100/새 키·DATA 없음 보존. 이후 새 VM에 ROOT120+DATA10+전용 키+affinity 반영 |
| UserData / SSH | 사용자 승인 후 두 UI에서 전용 공인 키 등록. 새 VM의 31 ubuntu / 32 root에 동일 로컬 개인 키로 로그인 및 AS06-31-UI/AS06-32-UI 파일 확인. 개인 키는 로컬 검증 폴더에만 보관 |
| 예약 생성/날짜 | 두 UI UTC/KST 추가·편집·활성/비활성. 시작 UTC31 09:10:36/KST18:10:44, UTC32 09:10:54/KST18:10:56 보존. 31 종료일 추가·같은 값 재조회·명시적 해제 |
| 날짜 오류 | 31 UI min4/max3 거절. 32 UI New York 2027-03-14 02:36:57(gap), 2027-11-07 01:36:57(overlap), 종료<시작을 한글 오류로 거절하고 폼/값 유지. 알 수 없는 시간대는 API/집중 테스트로 검증 |
| 비활성 그룹 예약 | 두 UI에서 만든 동일 시각 UTC/KST 예약: 09:14UTC 실행 후 min3/max3로 변경했지만 VM2/LB2 유지. 작은 ID UTC 예약만 async 수락, KST 충돌 예약은 async 없음 |
| 활성 그룹 예약 | 두 UI에서 활성화한 그룹의 09:24UTC 예약으로 VM2→3/LB2→3. 실제 SCALING/STARTING/ASSIGNING/ENABLED 전이 및 VM 목록·이벤트 확인 |
| 종료/이벤트 | 제출 직후 Started와 실제 async 완료의 Completed를 DB/API 및 실제 이벤트 탭으로 대조. 예약 변경 이벤트 한글·시작/완료 상태 확인 |
| 권한 | 31 프로젝트 소유자·32 일반 사용자 UI 편집 성공. 별도 제한 역할의 직접 다른 소유자/프로젝트 그룹 주소 접근은 상세 비노출/대시보드로 복귀. 제한 역할 API 쓰기 거절; 누락 API 메타데이터의 버튼/폼 가드는 집중 UI 테스트 |
| 한글/테마 | 최종 fc50 UI의 목록/폼 Cron 설명, 안내·오류·이벤트 한글 확인. 두 UI 라이트/다크, 기본 VM 상세·볼륨 조회 확인. 31 다크 예약 본문9.18:1/라벨7.13:1/숫자13.35:1(해당 폼의 측정; 전체 접근성 감사 아님) |

예약 DB 근거: 31 resource schedule12/13, async **11222** 성공/충돌13 async없음; 32 schedule1/2, async **1233** 성공/충돌2 async없음. 활성 그룹 예약은 31 schedule14/async **11225**, 32 schedule3/async **1236** 성공이다. 비활성화 후 각 예약의 미래 작업 행이 제거되고 6예약 모두 enabled=0임을 확인했다. 일·월 Cron 보존과 DST 오류는 r2 UI, 최종 한글 목록/라이트·다크/한글 이벤트는 r3 UI에서 검증했다.

## 실제 스토리지 및 VM 보존

- 31 원본 `i-35-397-VM` ROOT100GiB와 신규 `i-35-475-VM`/`i-35-476-VM` ROOT120GiB+DATA10GiB. Cloud 볼륨/VM 상세에서 Sparse 확인. 대표475의 실제 GFS2 mount와 file/qcow2, backing 없음, ROOT 실제할당 약3.34GiB/DATA 약1.89MiB, 120/10GiB 가상 크기를 확인했다. **Thin은 사용하지 않았다.**
- 32 원본 `i-6-73-VM` ROOT100GiB와 신규 `i-6-151-VM`/`i-6-152-VM` ROOT120GiB+DATA10GiB. 대표151의 실제 libvirt block/raw `/dev/rbd/rbd/<UUID>` 및 blockdev 120/10GiB로 krbd를 확인했다.
- 게스트에서 디스크120/10GiB와 UserData marker·SSH를 확인했다. 게스트 기존 파일시스템은 자동으로120GiB까지 커진 결과가 아니며 Cloud 측 볼륨 크기와 구분한다.
- 두 cloud-init은 errors=[]이나 recoverable warning/degraded done이 있다. 31 Ubuntu의 dhcpcd DHCP lease 조회 경고로 status exit2, 32 Rocky의 empty network config 경고(status exit0)를 보존했다. 키 주입과 marker 실행 성공을 전체 cloud-init 무경고/모든 게스트 부팅 보장으로 확대하지 않는다.

## 최종 UI 축소·정리 및 보존 상태

사용자 승인 후 두 UI에서 min1/max1을 저장하고 활성화하여 **3→2→1**로 축소했다. Disabled 상태에서는 범위만 저장하며 VM3을 유지했고, 활성화 후 실제 기록 대상31 **476→475**, 32 **152→151**의 LB 제외·60초 유예·destroy/expunge·멤버 제거를 확인했다. 원본31 **397**, 32 **73**은 임시 삭제 보호로 보존하고 종료 후 보호를 원래 false로 복원했다.

VM 축소는 ROOT를 영구 삭제하고 DATA는 제품의 기존 분리·보존 계약에 따라 Ready/미부착으로 남겼다. **DATA가 자동 삭제된 것으로 보고하지 않는다.** 승인된 DATA475/476/151/152 각10GiB는 실제 볼륨 UI의 ‘볼륨 영구 삭제’로 별도 정리했다. 결과적으로 이번 시험의 **4VM, ROOT120GiB×4+DATA10GiB×4=520GiB** 정리를 완료했다. DB의4VM은 removed/Expunging,8볼륨은 removed/Expunged이며 대상 활성 멤버/LB/2차 복사 참조는 없다. 6개 호스트 모두 삭제 VM 도메인·GFS2 파일·RBD 이미지·krbd 매핑이 없고, 원본 ROOT 이미지 및 실행 도메인은 보존됐다.

최종 AS04는 두 곳 모두 **DISABLED / 진행 작업 없음 / min1 max3 / Running VM1 / LB1**, 각3개 예약은 Disabled다. 원래 CPU GT20/LT10(31), GT5/LT1(32), duration60/quiet10/grace60과 공유 AS01 연결 및 프로필8항목의 원본 완전 일치를 확인했다. AS06 시험 정책 연결은 UI에서 해제했고 정책·조건 객체, 전용 SSH 키/affinity, Disabled 예약은 후속 조회용 검증 자료로 보존한다. 기존31 **3그룹/3VM/ROOT100GiB×3**, 32 **4그룹/4VM/ROOT100GiB×4**를 보존하고 primary 사용량은300/400GiB로 복귀했다. 고아 VM 매핑 및 삭제 VM에 남은 활성 볼륨은0이다. 두 최종 HTTP는 각각 **20/20 HTTP200**, 실패0의 짧은 연결 확인이다.

최종 관리 JAR 해시·PID(31 428950/32 885151), mold active, WEB-INF와 /client/ HTTP200도 재확인했다. 축소·DATA 정리 뒤 VM 메뉴에서 원본 한 대와 ROOT100GiB(Sparse/krbd)를 실제 UI로 확인했다. 이번 마지막 작업은 검증·기록 변경이며 소스/배포 바이너리를 다시 변경하지 않았다.

## 남은 검증 경계

- 삭제된 자원의 예약·stale job·lock 획득 실패·정각±경계·실제 DST 전환 시계는 집중 계약 테스트와 UI 입력/서버 validation으로 구분한다. 시스템 시계 조작과 실제 다중 관리 노드 시험은 하지 않았다.
- security-group 네트워크와 외부 NetScaler/Basic provider는 환경 미보유로 실배포 UI 성공 판정 범위가 아니다. 서버 인수 전달/검증 테스트로만 확인했다.
- 수정한 UI는 현재 Mold 탭/폼/표/Alert를 재사용한다. 메뉴 전체의 최종 VM UI 표준 통합은 #1355, 관측·진단은 다음 **#1353 그룹·멤버·최근 평가·장애 상태의 운영 가시성 보완**이다.
- AS05 최초 확장 HTTP timeout 각4건의 원인 미확정 기록을 유지한다. 이번 짧은 HTTP 성공을 무손실 확장·전체 성능·프로덕션 합격으로 확대하지 않는다. 장시간 OPS-10은 모든 기능/최종 UI 통합 후 #1354 마지막 단계에서만 수행한다.

[근거 묶음](evidence/AS-06/README.ko.md) · [설계](AS-06.ko.md)
