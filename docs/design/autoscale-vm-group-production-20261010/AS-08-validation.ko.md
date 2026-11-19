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

# AS-08 최종 UI 통합·운영 관찰 진행 기록 (#1354)

**현재: 최종 대표 UI/기능 게이트 PASS, 24시간 관찰 진행 중.** 2026-10-13 사용자 승인으로 PR #1356을 병합하고 Epic #1346을 구현 완료로 닫는다. #1354만 Open으로 유지한다. 병합은 운영 관찰 결과를 대신하지 않으며 24시간 PASS 또는 장기 운영 완료는 아직 선언하지 않는다.

## 동일 구현 기준 및 배포 복원

리뷰 소스 `d7a99d2b3fc899015c706a6b86554ab422eefb27`, UI 컴파일 `6790065c011555b7bb9c1950cadcde2cba2b6194`. UI SHA256 `05684413d62b183f5c7332028b98143da3e89c16deef0707d79012b1ca8d4303`. 31/32 정적843개 파일과 실제 응답 script 목록이 일치한다. 검증된 서버별 관리 JAR의 AS 패키지/API/응답90개 class byte가 동일하다. 전체 JAR은 서버별 기존 설치 클래스/설정 차이 때문에 동일 hash를 요구하지 않는다.

| 환경 | 관리 JAR SHA256 | 관리 PID |
|---|---|---|
| 31 | `036f448a93ff0c6c156a3dafebd3b26af6621203c60a6caf98b43ff10ef179ac` | 628863 |
| 32 | `dce0e50d4b0193202ee7c6522434814065d8f4c2ebd3fe74fd42d84373818e19` | 938882 |

시작 전 31은 이전 검증 기록과 달리 JAR6191c250…/UI839개만 일치하고 이전 app8d699183을 제공했다. 검증된31 JAR와 비교하면 AS/예약 범위14개 클래스가 없고113개가 달랐다. 사용자의 “31도32와 동일” 지시에 따라 기존31 검증 JAR와 정적4파일(color.less/index.html/en/ko_KR locale)을 복원했다. 변경 전 백업은 `/root/epic1346-as08-baseline-restore-20261013-112544`. WEB-INF/config 보존·mold active·client200 및 최신app9cf4d6dc를 확인했다. 원래 GFS2를 krbd로 바꾸지 않았다. 이번에는 Cloud/UI 소스 변경이 없어 새 Maven/UI 빌드를 하지 않고 앞서 빌드·검증한 동일 산출물을 사용했다. 전체 Cloud/Actions/전체 CI를 실행하지 않았다.

## 이번 최종 대표 실검증

- UI에서 양쪽 AS04의 min3/max3·활성화로1→3 확장, 다시 min1/max3으로 변경 후 CPU 저부하 정책의3→2→1 수렴. 32 축소 범위 변경은 기존 일반 사용자 UI에서 실행했다.
- 확장 상태에서 각 Running3/LB3, 실제 ROOT6개100GiB. 31 독립 qcow2/무backing/Sparse(실제 할당약3.6GB), 32 raw `/dev/rbd/rbd/<UUID>`·krbd. GFS2 Thin 없음.
- 원본397/73을 유지하고 신규491/492/157/158 및 ROOT4개 자동 정리. 전체6호스트의 해당 도메인·GFS2 파일·RBD 이미지/krbd 부재를 확인했다. AS04 원 프로필8항목 동일·예약3개씩 Disabled, AS01/AS02 및 VPC 보존. 최종31 그룹3/VM3/ROOT300GiB,32 그룹4/VM4/ROOT400GiB.
- 양 AS04 마지막HTTP20/20, VPC HTTP200. Cloud Running/LB 등록과 별도로 애플리케이션 본문13byte SHA256을 검증했다. 이번 확장 전체를 대상으로 고빈도 무손실 벤치마크를 수행한 것으로 표현하지 않는다.
- 최종 실제 UI14관측: 31 복원 탭·프로젝트3그룹 목록/문맥·라이트/다크 운영 상태·활성 프로필 변경 차단·LB·다크 업데이트 모달, 32 일반 사용자 AS04 및 VPC 정상 상태/평가/멤버. 다크 표본 대비5.19:1 이상, 1280×720 업데이트 모달은 x255~1015/y180~540으로 화면 안이다. AS-09의 전체 대화상자/반응형 검증과 구분한다.
- 수집기 실제 사전 실행: 양 서버 API/DB/JVM 지표 수집 성공, HTTP 세 대상 각7/7, anomaly0. 초기 수집 도구의 pagesize에 page누락을 수정했고 이는 Cloud 기능 오류가 아니다. 해당 실패 실행은 관찰 시간에 포함하지 않았다.

## 선행 검증과 60개 계획의 증거 연결

| 계획 ID | 선행 기록/증거 수준 |
|---|---|
| CRE-01~10 | [AS-01](AS-01-validation.ko.md): 두 실제 UI·권한/프로젝트·VPC·지원 provider와 입력 검증. 미보유 외부 provider의 실운영 PASS로 확대하지 않음 |
| ASY-01~10 | [AS-02](AS-02-validation.ko.md): 실제 UI 생성/응답 유실/복구/정리 및 계약 테스트 경계 |
| MET-01~10 | [AS-03](AS-03-validation.ko.md): 실제 CPU/메모리/RX·TX/LB/VR/60초/쿨다운/AND·OR/결측; 정각±1ms 등은 집중 테스트 |
| LIF-01~10 | [AS-05](AS-05-validation.ko.md): 양쪽10왕복·CPU1→2→3→2→1·quota/pool·LB/드레인·삭제 실패/재시도·VM 보호 및 이번 대표 최종1↔3 |
| OPS-01~04 | [AS-04](AS-04-validation.ko.md): UI 경쟁/disable/영속 작업/관리 재시작 복구; 실제 다중 관리 노드 경쟁은 미검증 |
| OPS-05~07 | [AS-06](AS-06-validation.ko.md): UI 예약/프로필/정책·향후 VM 반영, DST 등 계약 테스트와 구분 |
| OPS-08~09 | AS-01/06 및 [AS-07](AS-07-validation.ko.md): 권한/문맥·운영 조회/실패·출처 시각; 이번 프로젝트/일반 사용자/LB 대조 |
| UI-01~10 | [AS-09 최종](AS-09-row-actions-and-readability-validation.ko.md): 321tests/168관측/41대표이미지 및 이번 복원 UI 확인 |
| OPS-10 | **진행 중**: 아래24시간 기록 후 판정. 이전 짧은 HTTP 확인이나 현재 초기 수집을24시간 PASS로 처리하지 않음 |

AS-05 첫 확장 고빈도 HTTP 각4timeout의 원인은 미확정이며 무손실/전체성능 인증으로 확대하지 않는다. VM 연결 해제 후 메타데이터 수동 복원은 자동 재가입 PASS가 아니다. 외부 LB/실제 다중 관리 노드·전체 CI 한계는 선행 기록에 유지한다.

## 관찰 시작과 예약

**2026-10-13 11:44:15~2026-10-14 11:44:15 KST**. 읽기 전용 서버 수집기 PID31=631379,32=1137049, 전용 경로 `/root/epic1346-as08-soak-r5-20261013`. HTTP5초/API1분/DB·JVM·통계5분. 별도 채팅 heartbeat `1354-31-32`는30분 간격으로 기존 채팅에서 확인한다. 1/6/12/24시간 진행은 기존 #1354에 갱신한다. 초기 양쪽 HTTP/API/DB 오류0·anomaly0이며 장기 최종 결과는 미완료다. 다음 보고는 체크포인트 및 종료 판정으로 이어진다.

[배포·스토리지·정리·UI·게이트 증거](evidence/AS-08/README.ko.md)를 참조한다.


## 1시간 체크포인트 (2026-10-13)

24시간 완료 판정이 아니라1시간 이상 실제 기록의 중간 확인이다. 시작11:44:15 KST, 이번 서버 기록 확인은13:03경(약79분), UI는13:01~13:02, 원본 전체 인벤토리를 다시 대조했다.

| HTTP 대상 | 요청 | 본문 일치200 | 실패 |
|---|---|---|---|
| 31-AS04 | 948 | 948 | 0 |
| 32-AS04 | 948 | 948 | 0 |
| 32-VPC | 948 | 948 | 0 |

- 두 서버 각각 API79회, DB16회. API/DB/그룹 ERROR·수집 anomaly0, HTTP최대수집간격5초/API약60.05초. 세 그룹은모두Enabled/원본Running1/LB1, 범위내 유지 및 배포/PID/설정fingerprint 유지.
- 원본7VM·7그룹 및 각 서비스 응답을 보존했다. 고아 VM 매핑/삭제 VM 활성볼륨0. 실제 한글·다크 UI에서31프로젝트/32일반 사용자·VPC의 최신 조회·평가·멤버를 확인했다.32의 브라우저 유휴 세션은 기존 계정으로 재로그인했고 서버 연속 수집은중단되지 않았다.
- DB통계보존7200초(2시간). 현재 통계 행 증가는 해당 보존 구간 안이므로,2시간 이후 정리/범위 유지로 최종판정한다.
- 프로세스RSS/실제JVMheap/스레드/fd를 시계열로 보존했다.32의 스레드 증가를추가 확인했다. 양쪽db.cloud.maxActive=250, 소스의fixed API/Work풀상한은125/166.12:55경32 OS스레드416중 API풀73/Work풀32를 관측했다. 동시에32에서이번관찰그룹밖의VM/볼륨/템플릿작업이수행돼, 전체프로세스증감을오토스케일누수로단정하지않는다. 제한된풀증가와지속추이를함께확인한다.
- 관찰 중 배포/재시작/장애/VM시험/그룹설정 변경은하지 않았다. 이번32의다른대상작업은이관찰에서 실행한시험이아니다. 대상그룹의24시간기록과프로세스전체공유부하의판정범위를구분한다.


[중간 증거](evidence/AS-08/checkpoint-1h/README.ko.md).24시간 최종 판정은미완료이며관찰을계속한다.
