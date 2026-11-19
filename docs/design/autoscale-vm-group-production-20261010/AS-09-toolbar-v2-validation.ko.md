> 최신 #1355 보완(행별 드롭다운·6개 칼럼·폼 잘림·갱신 유지)은 [2026-10-12 후속 검증](AS-09-row-actions-and-readability-validation.ko.md)을 기준으로 합니다. 아래는 이전 revision의 검증 기록입니다.

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

# AS-09 #1355 승인 목업 구현·배포·UI 검증 완료

2026-10-12. 사용자 승인 목업의 기존 액션6탭을 현재 Mold Vue3/AntD에 적용했다. 목업 단계와 달리 실제 Cloud API·저장 결과 및 대화상자를 검증했다. Epic #1346은8/9, PR #1356은 Draft/Open, #1354는 지시 대기다.

## 구현 동작

- 운영 상태: 기존 업데이트 하나만 콘텐츠 상단 왼쪽에 배치한다. 프로필·LB·정책·예약의 변경 주 액션은 파란 아이콘+텍스트 하나, 나머지는 일반 아이콘+텍스트, 업데이트는 마지막이다.
- 기존 행/본문의 편집·삭제·조건·VM/IP·매개변수 액션을 상단으로 옮기고 정확한 현재 선택을 요구한다. 마지막 정책/조건과 AS에 묶인 LB 삭제 보호, Disabled/진행 중 상태·기존 API 권한·공유 영향·비동기 영수증/보상을 유지한다.
- 삭제 확인창을 열 때 그룹/프로필/정책/조건/예약/VM-IP 대상을 고정한다. 조회 갱신으로 행 선택이 없어져도 그 대상을 사용하며, 부모/현재 목록/VM-IP가 바뀌면 변경을 거절하고 다시 선택하도록 안내한다.
- 정책 편집은 대화상자 초안으로 분리해 취소가 본문을 바꾸지 않는다. 매개변수/조건 추가, 태그 footer, 기존 프로필·UserData·LB·세션 고정·예약을 Mold 대화상자로 정리했다.
- 공통 DetailsTab/EventsTab/AnnotationsTab/InfoCard/ListView/ResourceView/ResourceLayout 및 VmSchedulesTab은 승인 목업 HEAD250fd2e9와 byte 동일이다. 액션 없는 공통 탭에 새 툴바를 넣지 않았다.
- 공용 ResourceSchedules의 배치·선택·한글 상태/Cron/달력·오류 처리는 AutoScaleVmGroup에 한정한다. ResetUserData의 한글·소유 프로젝트 이미지 조회·활성 모드 제출·설정 안함/탭 복귀·늦은 변수 응답 처리는 AutoScale 프로필에 한정한다. 비-AS 요청 계약 회귀를 검증했고 빈 변수 배열 방어는 공용 안전 처리다.
- 한글 용어/상태/달력과 라이트·다크의 입력/선택/버튼/비활성/오류/달력·확인 팝업 대비를 보완했다. 스코프는 AS 화면·대화상자·전용 portal 클래스이며 공통 자원 화면의 형식은 유지한다.

## 빌드·배포

- UI **13 suites / 298 tests**, 추가 툴바/초안/정확한 선택/UserData/갱신/다른 자원 방어47건, 변경 Vue/JS8파일 집중 ESLint 및 WSL ext4 production build 통과. 테스트 harness의 미등록 컴포넌트 경고·오래된 Browserslist 알림은 제품 UI 오류와 구분한다.
- 컴파일 소스 `3ce80fcdebb1d3122484915a599ab531d02e0d36`, UI 아카이브 SHA256 `5d476aa19aa771989a41c540e38d787d1f013f3044aa6e271f49128e65a1b7dd`. 최종 PR의 UI 입력 파일 byte/hash와 대조한다. 이번 최종 변경은 UI이며 API/server/engine/plugins/API client/router는250fd2e9와 동일이다.
-31/32 active webapp의 정적843파일 해시 검증, WEB-INF/META-INF(기존 존재 시)/config 보존·mold active·client HTTP200. UI 배포는 관리 PID를 보존한다. 배포 backup과 원 관리 JAR 해시는 공개 evidence에 기록했다.
- 전체 Cloud/fat JAR/RPM/GitHub Actions 빌드·전체 CI를 실행하거나 합격으로 주장하지 않는다. 검증 도중 시도한31 LB Java 진단 패치는 필요 없음을 확인해 source·runtime 모두 원본으로 되돌렸다. 그 진단 모듈 테스트는 최종 합격 수에 포함하지 않는다.

## 실제 UI 검증 범위

-31 관리자(프로젝트 소유 자원)·32 일반 소유 사용자/관리자에서 상단 선택·편집·추가·삭제·업데이트, 프로필 저장/복원, 임시 조건·정책 연결/분리와 마지막 항목 보호, LB 알고리즘 변경/복원과 태그·세션 고정 생성/해제를 확인했다.
-31/32 비활성 스케줄 생성·선택 편집·삭제,31 미래2030년 달력 선택, 필수 오류의 필드 표시와 요청 실패 알림 없음, 등록/수동 UserData의 전환·설정 안함 저장 및 원 프로필 복원을 확인했다.
-6탭×라이트/다크, 프로필·매개변수·UserData·LB·태그·세션 고정·정책 추가/편집·조건 추가/편집·예약11종 대화상자, 실제1024/390px 줄바꿈·문서 넘침·모달 footer/키보드·달력/확인 popup를 확인했다. 테마와 실제 viewport를 DOM으로 대조하고 native 화면을 저장했다.
- 안정화된 화면의 표본 대비/실행 경로/이미지는 `ui-final-verification.json`, `ui-audits.json`, [화면 갤러리](evidence/AS-09-toolbar-v2/README.ko.md)에 기록한다. 표본 측정이며 전체 WCAG 인증이 아니다. 공통 상세/이벤트/코멘트는 실제 AS와 VM에서 기존 형식을 확인했다. VM 스케줄은 소스 byte 격리·집중 테스트 범위다.

## 자원 보존·검증 과정의 원복

- 기존31 3그룹/3VM/ROOT300GiB,32 4그룹/4VM/ROOT400GiB를 보존한다. AS04는 Disabled/진행없음/min1 max3/원본 Running VM1/LB1, 원 프로필8항목·CPU 정책/조건·예약3개씩 Disabled이며 HTTP 각각20/20이다.
- 이번 툴바 작업에서 VM/디스크는 새로 생성하거나 삭제하지 않았다.31은 기존 GFS2 Sparse 시험 VM,32는 기존 krbd 시험 VM을 사용했다. GFS2 Thin 이미지 생성은 없었다. 임시 정책·조건·예약·태그·고정 세션과 프로필 변경을 정리/복원했다.
-31 기존 VM 연결 해제1회는 LB와 AS 멤버십을 함께 제거하고 Running VM/ROOT를 보존했다. 원 상태 복구에는 지정 멤버십/LB 메타데이터 원복을 사용했으며 자동 재가입/자체 복구 PASS로 기록하지 않는다. 같은 알고리즘 값의 LB update는 장비 재적용이 없는 계약이다. 기존 관리 JAR에서 실제 알고리즘 변경/복원과 HTTP20/20을 확인했다. 확인 문구에 멤버십 제거·VM/볼륨 보존·재활성화 시 새 최소 VM 생성 가능성을 명시했다.

## 다음 단계

#1355 완료 처리 후 #1354 GFS2·krbd 최종 UI E2E·장애·마지막 장시간 운영/프로덕션 전환 판정을 안내하고 기다린다. 외부 LB·실제 다중 관리 노드·전체 접근성/CI 및 AS05 최초 timeout 원인 미확정 기록은 기존 경계를 유지한다. 이번 UI 완료를 Epic 전체 프로덕션 전환 완료로 확대하지 않는다.
