<!-- Licensed to the Apache Software Foundation (ASF) under one
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
under the License. -->

# UD-11 사용자 데이터 라이브러리 최종 UI 통합 검증

대상: upstream [#1392](https://github.com/ablecloud-team/ablestack-cloud/issues/1392), 결함 [#1394](https://github.com/ablecloud-team/ablestack-cloud/issues/1394), Epic [#1381](https://github.com/ablecloud-team/ablestack-cloud/issues/1381), Epic PR [#1393](https://github.com/ablecloud-team/ablestack-cloud/pull/1393). 2026-10-16~17, 테스트 클러스터31.

## 변경과 판정 범위

기존 ListView/ResourceLayout/InfoCard/ResourceView/MoldDialog, UserDataSelection과 의미별 테마 토큰을 재사용한다. 최종 대상은 목록·상세 7개 탭, 등록·복제·단건/일괄 삭제·VM 저장 본문·재설정·템플릿/ISO 편집과 VM 생성 경로다. 전체 제품의 모든 메뉴나 운영 공급자/게스트 시험을 새로 수행했다는 의미는 아니다.

- 등록·복제 필드 하단을 6px에서20px, 삭제 본문과 버전 기록 사이를16px로 정리했다. 탭 도구의 업데이트 문구·아이콘·간격을 VM 표준에 맞췄다. Footer는 사용자의 명시 요청과 같은 배포본 VM 규격에 따라32px를 유지한다. MD-04 초안의42px와 다른 업무별 적용 기준이다.
- 마지막 양성 코멘트 측정에서 작성자/시각/공개 범위의 대비 부족도 확인해 기존 의미별 토큰으로 보완했다. 링크·활성 탭·placeholder·hover 버튼·검색 아이콘·비활성 값·체크박스/라디오/스위치의 실제 대비를 보완했다. 전용 영역에 기존 토큰을 적용하며 기존 다크 테마의 높은 specificity가 유휴 경계색을 덮는 것도 수정했다. 색상 세트나 광범위 전역 override를 새로 만들지 않았다.
- 템플릿/ISO 편집을 같은 MoldDialog로 전환하고 기존 필드/그리드는 유지했다. 첫 페이지만 가져오던 선택기를 소유 범위·서버 검색/페이지를 지원하는 공통 선택기로 바꿨다. ISO도 실제 API의 templateid를 사용한다. 메타데이터 저장 뒤 연결 완료를 기다리고, 부분 실패는 입력 보존·가시적인 오류·포커스·재시도로 처리한다.
- 프로젝트 API 목록을 기다린 뒤 프로젝트·테마·라우팅을 변경한다. 기본 보기 id0은{}로 정규화해 wizard의 projectid=0/431을 방지했다. 대시보드는 정상 취소와 실제 오류를 구분하고 이전 경로/세션/프로젝트·옛 요청 결과를 반영하지 않는다.
- 공통 코멘트는 실패 시 초안/관리자 전용 값을 유지한다. 성공한 제출 이후 입력한 새 초안도 지우지 않는다. API 권한이 없는 코멘트 탭을 숨기고 잘못된/숨겨진 탭 URL을 정상화한다. 떠나는 VM/이미지의 이전 탭 설정이 새 라이브러리 URL을 덮지 않도록 가드를 추가했다.

사용자 최종 지적에 따라 VM 표준에 없는 검색 설명과 탭/대화상자의 일반 안내 문구를 제거했다. 검색·필터·표·페이지 및 실제 오류·결과·작업 영향 정보만 남긴다. 단순히 검색 문구만 숨기지 않고 전용 메타데이터 경로와 사용되지 않는 정책 컴포넌트도 제거했다.

마지막 육안 대조에서 breadcrumb의 기존24px 음수 여백이 현재 workbench12px 패딩보다 커 main에12px 가로 스크롤을 만드는 것을 확인했다. 사용자 데이터 영역의 여백을12px로 맞추고 document뿐 아니라 실제 main 컨테이너의 overflow와 CSS 자산 식별값도 최종 행렬에 포함했다. before-header-overflow가 재현 근거다.

## 화면·테마·간격 검증

[최종 점검표](audit-matrix.ko.md), [정량 판정](qualification.json), [화면 좌표 인덱스](geometry-index.json)와 final-light/final-dark 파일이 근거다. 2개 테마×8개 화면 조건×16개 화면/모달을 측정한다. 목록과 상세7탭, 모달8종(단건/일괄 삭제 별도)을 포함한다.

화면 조건은1600×1000,1440×1000,1440×900,1280×720,1024×768,390×844,390×480,200%다. 200%는1440×900 물리 크기에 해당하는720×450 CSS viewport/deviceScaleFactor2의 CDP 에뮬레이션으로, 브라우저 메뉴의 네이티브 확대 조작과 구분한다. 전체 역할×테마×화면 크기의 모든 조합을 실행한 것은 아니며 역할은 별도 실제 대표 경로로 검증했다.

페이지/모달 가로 넘침, 모달 중심 오차≤1px, 텍스트/placeholder/활성 버튼 대비≥4.5, 필수 경계/포커스의 인접 표면 대비≥3을 반올림 전 값으로 판정한다. 장식선과 비활성 경계는 필수 식별 요소와 구분하되 비활성 텍스트는 읽을 수 있어야 한다. 투명한 native radio/checkbox input의 내부 값on은 표시 텍스트로 계산하지 않는다. 체크된 컨트롤은 외곽/채움과 바깥 표면, 선택 표시의 대비를 구분한다.

390×480의 두 테마7개 모달에서 실제 본문 스크롤을 끝까지 이동했고 Header/Footer 좌표 차이는 모두0px였다. 등록·복제20px 필드 여백, 삭제/버전16px 섹션, 탭 toolbar 간격과 긴 제목·한글·본문 내부 스크롤을 확인했다. 키보드 검사는14개 대표 경로에서 Tab이 모달 안에 머물고 배경 클릭이 창을 닫지 않으며 Escape 후 등록 버튼/작업 버튼으로 포커스가 복귀했다. popup·hover·오류·비활성·로딩은 별도 상태 자료와 함께 판정한다.

## 실제 기능과 API/DB 대조

- 일반 텍스트/필수값 오류/변수와 Base64 등록, 서버503 전 입력 보존·잠금·재시도를 확인했다. BOM·CRLF·한글·emoji54바이트의 원문 복사/다운로드와 Base64 복사가 저장 바이트와 정확히 일치했다.
- 새 VM을 UI wizard로 실제 생성했다. 선택한 전용 네트워크·Rocky 이미지·컴퓨트 오퍼링·startvm=false·라이브러리 UUID·TOKEN=UD1392-WIZARD를 요청에서 확인한 뒤 전송했다. 비동기 완료 상태Stopped,3075바이트와 해시를 API로 대조했고 라이브러리의 사용 VM 전용 탭에 정확히1개가 표시됐다.
- A 라이브러리11개 VM의10+1 서버 페이지, 0/1개 목록, VM 상세 링크를 확인했다. 새 VM의 라이브러리를 B로 변경하고 수동 본문·설정 안함으로 전환해 실제 저장본/메타데이터와 각 사용 목록의 이동을 대조했다. 최종 새 VM은 연결/본문0, 기존 전용 A VM11개는 A 참조다. 운영 VM은 변경하지 않았다.
- 목록 이름/정확UUID/빈 검색,20개 서버 페이지 경계와 총 건수,현재 페이지 선택·정렬·일괄 작업을 확인했다. 공통 목록은 페이지 이동 시 선택을 초기화한다. 실제 혼합 삭제에서는 미사용1개만 성공하고 사용 중 A는 서버431과 참조 사유를 표시했다. 단건503은 현재 존재 여부 확인 후 실패 대상만 재시도해 삭제됐다.
- 템플릿/ISO 각각2개 참조와 정책을 API와 대조했다. 실제 연결 변경·해제·복원과 메타데이터200 이후 연결503의 부분 실패를 구분했다. 최종 오류 안내의 top88/bottom150은720px 화면 안에 있고 실제 포커스가 이동했다.
- 새 버전 생성 후 출처 메모503, 메모만 재시도를 확인했다. API/DB 결과는 새 UUID1개·출처 메모1개다. 독립 버전이며 댓글은 삭제 가능한 참고 기록이다.
- 메모503 후 초안/관리자 전용 보존, 새로고침 보존, 실제 재등록 후 초기화, 삭제 popconfirm을 확인했다. 제출 중 새로 작성한 초안은 이전 성공 응답이 지우지 않았다. 같은 공통 컴포넌트의 VM·템플릿 추가를 두 테마에서 확인했고 공유 CNI 등록의 제목/필드/버튼도 보존됐다.
- 관리자·일반 사용자·도메인 관리자·프로젝트·제한 역할에서 실제 UI/API 범위를 확인했다. 일반 사용자/도메인 관리자 등록, 프로젝트 전용 목록/사용VM/복제 창, 제한 역할의 숨겨진 코멘트/복제 기능과 직접 URL fallback을 확인했다.
- 조회503 때 기존 행과2페이지를 보존하고 재시도로 복구했다. A 조회를 지연시킨 뒤 B로 이동해 옛 본문이 섞이지 않음을 확인했다. 프로젝트 목록 지연 중 중복 선택 잠금,503 롤백,실제 재시도와 기본 보기 복원,대시보드 왕복/뒤로/앞으로에서 JS 예외를 관측했다. 최종 구간과 초기 재현 구간은 network-index로 구분한다.

503은 CDP Request 단계에서 백엔드 전달 전에 주입한 통제 장애다. 실제 사용 중431·제한 역할432·성공200·비동기 완료와 구분한다. 초기(before/initial)와 최종(final) 증거를 혼합해 합격 수를 늘리지 않는다. 기능 회귀의 주 검증 이후 교차 메뉴 URL·표시/대비·경로 카드·문구·Affix 높이 갱신을 보완했으며 최종 배포본으로 전체 화면 행렬을 다시 수집했다. 최종 기능/API 대조는 그 이전 기능 수정 배포의 자료이며, 마지막 수정은 표시 문구 제거·빈 기록 번역·코멘트 작성자/시각/공개 범위 대비에 한정한다.

## 빌드·배포·정리

집중 Jest15 suites/317 tests, 변경 JS/Vue23개 ESLint와 WSL ext4 production UI build를 검증했다. 전체 Cloud/Maven/GitHub Actions 빌드, qemu/호스트 배포, 관리 서비스 재시작·PR 병합은 수행하지 않았다. Browserslist/번들 크기/Jest forceExit 안내는 전체 CI 성공을 뜻하지 않는다.

[배포 검증](runtime-final.json)의 최종 SHA와app 식별값, 정적870개·기존 JAR/12클래스 해시가 기준이다. WEB-INF/config·Mold PID1247065를 보존했고 mold active,/client/HTTP200이다. 루트 전체 교체나rsync--delete 없이 정적 파일만 변경했다. pre-UD11 원본 백업은/root/ud1392-ui-20261016-223230이며 이후 각 배포 백업은 deployment.json에 기록한다.

최종 표시 수정 뒤 2개 라이브러리·정지 VM/네트워크/템플릿/ISO 각1개와 공개/비공개/출처 메모로 전체 화면을 재수집했다. 추가 자원도 standard-cleanup/comment-cleanup과 최종 db-after로 정리 확인했다. 이전 기능 검증의 정리 전 전용 활성 데이터는 라이브러리25,VM/볼륨14,네트워크3,템플릿/ISO4,계정3,프로젝트/내부계정/도메인/역할 각1,USER_DATA/VM/TEMPLATE 메모6개였다. [정리 결과](cleanup.json)·[정리 후 DB](db-after.json)로 활성 잔여0과 기존 운영 라이브러리 전체 응답 동일함을 검증한다. 제거 이력 행은 남는다. 계측/viewport/클립보드를 복원했고 검증 탭은 작업 종료 시 닫는다. 원래 FT 작업 트리의3개 수정 파일과HEAD를 보존한다.

## 다음 단계

#1392 및 연결 결함#1394를 범위 내 통과 근거와 함께 종료하고 Epic 진행을11/11로 갱신한다. Epic PR#1393은 통합 검토를 위한 Draft를 유지한다. 다음 작업은 PR 최종 리뷰·병합과 Epic#1381 종료다. 사용자 데이터 구현 하위 이슈를 새로 남기지 않는다. 공급자/게스트 실행의 앞선#1386 근거는 해당 보고서 범위이며 이 UI 전용 정지 VM 시험에서 다시 실행하지 않았다.

## 최종 사용자 화면 대조 및 추가 보완

사용자 첨부 이미지의 메뉴/Breadcrumb 사이 세로선을 재현했다. 가상머신은 카드 왼쪽이 x244라 메뉴 뒤에 가려지지만 사용자 데이터는 가로 넘침 보완 후 x256에 1px 세로 테두리가 노출됐다. 여백을 다시 늘려 넘침을 만드는 대신 Breadcrumb의 좌우 테두리를 제거하고 위/아래 구분선은 유지했다. 일반/다크·작은 화면에서 실제 0px 테두리와 가로 넘침0을 확인했다.

최종 재검증 중 비활성 보내기 글자4.252:1과 모바일 일괄 작업 도구의129px 넘침도 발견했다. 기존 테마의 비활성 배경/글자 토큰과 기존 버튼·검색 컴포넌트를 사용하고 작은 화면에서만 줄바꿈했다. before-final-corrections의12개 실패 프레임은 수정 전 자료이며 합격 수에 넣지 않는다. 최종 배포 뒤256개 화면 전체를 다시 수집했고 모든 정량 기준을 통과했다.

모바일에서 선택 수가 바뀐 뒤 고정 영역229px와 실제 카드189px가40px 어긋나는 것도 확인했다. 필터 영역에서 이미 사용하는 Affix key 갱신 패턴을 라이브러리의 선택 수/기기 전환에만 재사용한다. 짧은 viewport에서 자동 locator의 중앙 스크롤은 고정 헤더 뒤로 대상을 옮기므로, 실제로 노출된 위치를 확인한 포인터 입력으로 선택/해제를 별도 검증한다.

## 최종 확정

배포 소스 beb090148d910db0f5940aaaad7f4078bc201257, app js/app.1c6ca5f1.js. 같은 소스에서 집중15 suites/317 tests와 production UI build를 통과했다. 최종256개 화면/모달은 모두 이 번들에서 재수집해 판정했으며 활성 탭 ID·URL·실제 활성 패널, 공개/비공개/출처 메모3개, 빈틈/넘침·32px Footer·대비·Breadcrumb 좌우0px를 확인했다. 모바일390×480/844의 두 테마에서 실제 포인터로 두 개 선택/모두 해제를 수행했고 선택 시229px, 해제 시189px로 카드/Affix/placeholder 높이가 모두 일치했다.

기능/API 대조와 별도 hover/focus/키보드/스크롤 상태의 앞선 단계는 qualification.json의 evidence_phases로 구분한다. 수정 전 실패 파일은 합격 집계에 사용하지 않는다. 기존 API 응답 보존·활성12종 잔여0·기존 FT 작업 diff/HEAD 동일, 기본 보기/다크·배율·요청 가로채기·빈 클립보드를 복원했다. 최종 실제 화면은 delivered-standard-dark-list.png다.
