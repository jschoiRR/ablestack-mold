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

# AS-07 #1353 검증·배포 기록

상위 Epic #1346, 누적 Draft PR #1356. 2026-10-11~12 구현·변경 모듈 빌드·31/32 배포·해당 범위 실제 UI 검증 및 전용 자원 정리를 완료했다. Epic 전체 프로덕션 판정은 후속 #1355/#1354 완료 후 수행한다.

## 구현 계약

- 기존 `listAutoScaleVmGroups` 응답에 `memberstatecounts`, `statuscollectedat`, `lastscaledat`, `lastcheckedat`, `operationdeadline`, `evaluationerror`를 추가했다. 현재 ACL/프로젝트 필터를 사용하며 신규 API/DB 컬럼을 만들지 않았다.
- VM 집계는 바인딩된 그룹 ID로 단일 SQL을 실행한다. Running, Starting/Stopping/Migrating, Stopped, Error/Unknown, Destroyed/Expunging/Removed/Missing 및 나머지 상태를 구분한다. 기존 조정 대상 수와 애플리케이션 정상 수는 의미가 다르다.
- 최근 성공 시각은 성공한 자동 조정 완료 기록이다. 실패·재시도가 이를 덮어쓰지 않는다. 상태 조회/모니터 확인/메트릭 수집/평가/연결 유예 시각도 각각 표시한다.
- 현재 Mold ResourceLayout에 운영 상태 탭과 상세 운영 요약을 추가했다. 기존 Cloud API로 그룹·멤버·LB 매핑·최근 5개 이벤트를 별도 조회하며, 각 출처의 실패/권한 없음/조회 중/빈 결과를 구분한다. 기존 화면·조회 시각을 유지하고 늦은 응답을 무시한다.
- 검색/페이지는 서버 조회에 적용하며 운영 정보 새로고침과 공통 업데이트에서 유지한다. 새로운 자동 폴링은 추가하지 않는다.
- Cloud의 VM 프로세스 상태와 LB 등록 정보는 서비스 건강 판정으로 사용하지 않는다. LB 서비스 상태 필드가 없는 응답은 등록됨으로만 표시한다. 애플리케이션 정상은 별도 HTTP 검증 자료다.
- 최근 평가의 오래된 시각, 정책 변경 후 과거 기록, 손상/없음/미래 상태를 표시한다. 측정값 없음은 숫자 0으로 대체하지 않는다.
- 이벤트 UUID 및 상위 작업 이벤트 UUID 링크로 작업을 추적한다. 새로운 async job ID 매핑은 추가하지 않았으며, 이벤트 원문은 펼쳐서 볼 수 있다. 내부 작업 토큰·키·UserData 원문은 신규 운영 조회에 노출하지 않는다.

## 실화면에서 발견해 수정한 회귀

1. 유효 로그인 세션과 빈 API 캐시에서 GetInfo가 완료되지 않아 빈 화면이 남는 문제: API 발견을 재수행하고 실패도 완료 경로로 반환한다.
2. 날짜 포맷터 수신자 유실로 `$store`를 읽지 못하는 문제: 사용자 시간대 설정을 읽는 컴포넌트 호출로 수정한다.
3. flat locale의 `$te` 오판정으로 알려진 평가가 알 수 없음, LB 미등록/권한 없음이 등록됨으로 표시되는 문제: 실제 번역 결과로 판정한다.
4. 라이트 운영 링크 대비 3.24:1: Mold 본문 색상 토큰과 밑줄로 보완한다. 최초 다크 표본은 최저 4.58:1이었다.

## 빌드 및 검증 경계

Java/API 모듈 소스 `9b1363af4f2815e87a920f1191c3925f6c85c277`, 최종 UI 소스 `f8628299a6c0f5698bfca94416b5a8d70f633c6e`.

- 변경 api/schema/server Maven install 및 Java **299건** 통과(API12/schema18/server269), Checkstyle 통과, 모듈 RAT 통과.
- UI **10 suites/222건** 통과, 변경 파일 lint 통과. API/Cloud/생성 복구/정책·프로필·예약/리소스 화면의 집중 회귀를 포함한다.
- 초기 RAT는 기존 생성된 systemvm dist의 라이선스 미인식 파일 2개로 실패했다. 파일을 검증 폴더에 보존한 뒤 소스 검사를 재실행해 통과했다. 날짜/새로고침 회귀의 수정 전 실패 자료를 보존했다.
- 전체 Cloud/fat JAR/RPM/GitHub Actions 빌드와 전체 CI는 실행하지 않았다. 기존 배포 fat JAR에 변경 Maven 모듈의 63개 클래스(누적)를 검증 후 반영했다.

## 31·32 시험 자원과 보존 원칙

31 프로젝트 `AS-PROD-31-AS01-project`, 32 일반 사용자 `epic1346-as01-user32`의 AS04 그룹을 사용했다. API는 시험 VM 준비에 사용하고 운영 조회와 그룹 변경·VM 정지/시작·정리 상태는 실제 UI에서 검증한다.

| 클러스터 | 보존 원본 | 이번 전용 VM | ROOT | 실제 연결 |
|---|---|---|---|---|
| 31 | i-35-397-VM | i-35-477/478-VM | 각 100GiB | GFS2 Sparse qcow2, Thin 없음 |
| 32 | i-6-73-VM | i-6-153/154-VM | 각 100GiB | krbd block/raw |

원본 및 최신 시험 VM에 임시 삭제 보호를 적용해 삭제 재시도를 검증했다. 새 VM 4대·ROOT 400GiB만 정리했고 원본 보호·그룹 범위를 복원했다. 기존 31 3그룹/3VM, 32 4그룹/4VM 및 기존 정책·프로필·6개 Disabled 예약은 보존한다.

## 검증 범위와 완료 판정

아래는 해당 이슈의 실제 UI 검증과 지원 증거를 구분한 결과다. MET-01~10은 AS03 수집/계산 회귀와 이번 운영 표시 범위로, ASY-08~10은 기존 생성 복구 회귀와 이번 관찰/새로고침 범위로 연결한다. 외부 LB·실제 다중 관리 노드·전체 UI 접근성/전체 성능은 이번 PASS 범위가 아니다. AS05 초기 확장의 timeout 각4건 원인 미확정 기록도 유지한다. 장시간 운영은 #1355 UI 표준 통합 후 #1354 마지막 단계에서 수행한다.


## 실제 UI 및 지원 증거

| 항목 | 실제 UI 결과 | 지원 증거 / 경계 |
|---|---|---|
| 그룹/멤버 집계 | 31 프로젝트·32 일반 사용자에서 Running3 → Running2/Stopped1 및 최종 배포본 Running1/Stopped1/기록2 → Running2 → 원본 Running1을 확인 | 그룹 API·VM API·DB, `final-stopped-api.json`; Error/Unknown/Removed/Missing의 전체 조합은 집중 단위 테스트 |
| LB 의미 | 등록됨·미등록·매핑 조회 중을 구분. 드레인 중 대상은 Running이지만 미등록, 원본은 등록됨 | LB API와 HTTP를 별도 확인. API에 service state가 없어 앱 정상 배지는 만들지 않음 |
| 삭제 실패/재시도 | 최종 배포에서 ‘축소 · VM 삭제 재시도’와 해결 안내, 기존 성공 시각 보존 확인 | 삭제 보호 적용 대상478/154, 실제 오류 이벤트. Disabled 후에도 수락한 처리만 계속, 보호 해제 후 같은 대상 정리 |
| 드레인/성공 시각 | 양쪽 최종 UI에 연결 유예 종료 예정 및 ‘축소 · 연결 해제 및 유예 대기’, 이후 VM 삭제/완료와 새 성공 시각 | 최종 유예 종료 KST 00:01:19(31)/00:01:21(32), 실제 API DRAINING. 마지막 정리 완료 KST 00:01:26/00:01:32 |
| 평가 손상/없음/미래 상태 | 최종 배포본 두 UI에서 해석 실패·평가 기록 없음·AS07_UNKNOWN과 잘못된 날짜의 기록 없음 표시, 집계/멤버는 유지 | Disabled/작업 없음의 전용 그룹 평가 필드만 주입하고 원문 복원. 그룹 조회 실패/숫자0으로 바꾸지 않음 |
| 평가 시각/정책 | 수집·평가·경과 시각 및 단위·표본 수·대상 수, 오래된 마지막 평가와 Disabled 안내 | 측정값 없는 실제 정책은 —와 수집 없음. 정책 변경 후 과거 기록/시간 경계/순서 역전은 집중 테스트. MET-01~10 수집·계산 전체는 AS03 기록과 구분 |
| 부분 조회 실패 | 32 최종 UI에서 LB/이벤트만 실패해도 그룹·멤버 재조회. 마지막 데이터/시각과 ‘마지막 매핑 · 재조회 필요’ 유지 | 전용 복제 역할에 두 API만 거절하는 장애 주입; 원 역할은 변경하지 않음 |
| 권한 없음/복구 | 새 API 발견 후 32 최종 UI는 LB·이벤트 조회 권한 없음, 이전 데이터 제거. 역할 원복·재로그인 후 정상 표시 | 전용 임시 역할을 최종 삭제. 다른 소유자 접근은 두 클러스터 API 거부/빈 결과, 31 제한 계정 UI 직접 접근 시 상세 노출 없음 |
| 검색/갱신/탭 | 멤버 서버 검색·빈 결과, 운영 새로고침 및 공통 업데이트에서 검색/선택 탭 유지 | 32 최종 UI의 없는 멤버 검색 유지, 두 UI 정상 재조회. 다중 페이지/늦은 응답/그룹 교체는 집중 테스트 |
| 이벤트 추적 | 최근 오류/단계/성공과 이벤트 ID·상위 작업 이벤트 ID, 실제 상세 링크의 그룹/프로젝트 연결 확인 | 이벤트 링크 화면은 날짜 수정 후 r4, 최종 r6의 링크 구조/한글 이벤트 표시도 확인. 신규 async job ID 매핑은 없음 |
| VM 메뉴 회귀 | 진행 작업의 수동 정지는 한국어로 거절. 작업 해소 후 최종 UI 정지/시작 성공 | 기존 AS05 보호 계약을 유지, 지원 API/DB에서도 상태 전이 확인 |
| 한국어/테마/로그인 | 31 라이트·32 다크, 동적 상태·LB 미등록·권한 없음·날짜/단위/실패 안내 확인. 유효 세션·빈 API 캐시 복구 | 최종 샘플 각60개에서 라이트 최소7.56:1/다크7.13:1. 14:50UTC 이후 수집된 콘솔 오류0; 전 단계 날짜 오류는 수정 전 증거로 보존 |

운영 패널의 실제 화면 확인은 기본 1280px 화면에서 수행했다. 전체 메뉴의 목록/생성 배치·모든 반응형 폭·키보드/접근성 표준 통합은 #1355에서 수행한다. 현재 이슈가 UI-01~06 전체 화면이나 ASY-08~10의 생성 장애 재시험 전체를 완료했다는 의미는 아니다. 기존 AS02/AS03 집중 회귀와 이번 운영 조회·상태/검색/새로고침 범위를 검증했다.

## 최종 배포 해시와 복원

| 대상 | 활성 JAR SHA256 | 관리 백업 / PID |
|---|---|---|
| 31 | `db71b5e055b60ff28b95bc620038cc6757432b66fe9f82a73bc0c850d7fb9b1d` | `/root/epic1346-as07-r1-20261011-225130` / 453558 |
| 32 | `ee0c69e97ce0d7918eaec03ed381703ff18a7974a50b2e242eac3ef71156bba5` | `/root/epic1346-as07-r1-20261011-225208` / 916408 |

최종 UI `ui-as07-f8628299a6c.tgz` SHA256 `f8e38eddc5049bbe112c8437e98cdbbfb2975aef3591235ba7f86d021c9e8135`, UI 소스 입력 manifest SHA256 `e14f7bcb7aedefb28d409e0d8ff538c22843a0b8b4161f73cb46ba3213f567ba`.

- 31 정적 백업 `/root/epic1346-as07-ui-r6-20261011-235007`, 32 `/root/epic1346-as07-ui-r6-20261011-235022`. 최종 정적843파일을 각각 재검증했다.
- active webapp의 WEB-INF/config, 관리 JAR/PID를 보존했다. META-INF는 원래 없었으며 존재할 경우 보존하는 배포 절차다. mold active·/client/ HTTP200. 운영 조회/생성 및 기존 FTCTL 3개 마커도 활성 번들에서 확인했다. FTCTL 기능 시험을 수행한 것은 아니다.
- r1/r3/r4 배포에서 발견한 실제 화면 문제를 수정했고 r6가 최종이다. r2/r5 빌드는 변경 반영을 위해 중단했으며 배포본으로 판정하지 않는다. 보고서 커밋은 빌드 소스를 변경하지 않는다.

## 전용 자원 정리와 기준 상태

31 **i-35-477/478-VM**, 32 **i-6-153/154-VM** 및 ROOT100GiB×4를 정리했다. DB는 VM Expunging/removed, ROOT Expunged/removed, 대상 활성 그룹·LB 매핑과 secondary store 참조0이다. 6호스트의 해당 VM 도메인·GFS2 파일·RBD 이미지·krbd 매핑은0이며 원본 도메인과 ROOT는 보존됐다.

양쪽 AS04는 **DISABLED / 진행 없음 / min1 max3 / 원본 Running VM1 / LB1**이다. 원본397/73 삭제 보호를 false로 복원하고 제한 역할 원복 및 임시 역할 삭제를 완료했다. 평가 장애 주입 원문을 복원한 뒤 정상 모니터의 후속 평가만 남았다. 원본 프로필8항목과 CPU 정책/조건/duration60/quiet10/grace60이 AS06 최종 기준과 일치한다. 예약 각3개는 Disabled다.

기존 31 **3그룹/3VM/ROOT100GiB×3**, 32 **4그룹/4VM/ROOT100GiB×4**, primary 사용량 **300/400GiB**가 일치한다. 고아 멤버 매핑 및 삭제 VM의 활성 볼륨은0이다. 최종 짧은 HTTP 요청은 각각 **20/20 HTTP200**, 실패0이며 장시간 운영/성능 판정으로 확대하지 않는다.

[공개 근거](evidence/AS-07/README.ko.md)에 실제 UI 텍스트/이미지·API/DB/호스트·아티팩트 해시·집중 빌드 요약을 제공한다. 계정 비밀번호, 개인 키, API/secret key, UserData 원문 및 비공개 역할/프로필 원문은 제외했다.

**완료:** #1353 구현·배포·해당 범위 UI 검증 완료. Epic 진행 **7/9** 및 누적 Draft PR #1356을 갱신한다. 다음 **#1355 현재 Mold에서 가상머신 메뉴의 목록·상세·생성 UI 표준 통합**은 사용자 지시까지 대기한다. #1354 최종 통합 검증/마지막 장시간 운영이 남아 있으므로 Epic과 PR은 Open/Draft로 유지한다.
