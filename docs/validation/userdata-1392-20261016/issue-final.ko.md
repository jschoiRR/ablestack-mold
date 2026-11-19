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

Parent Epic: https://github.com/ablecloud-team/ablestack-cloud/issues/1381

## 목적과 수행 시점

UD-01~UD-10의 사용자 데이터 라이브러리 개선을 통합한 뒤, **가상머신 메뉴의 목록·상세·탭·탭 콘텐츠 매칭·대화상자 기준, 일반/다크 모드 및 공통 스타일 재사용을 모두 준수하는지 최종 검토**한다. 개별 이슈의 기능 통과만으로 UI 표준 준수를 완료 처리하지 않도록 별도 검증 결과를 남긴다.

등록 후 설계·구현·배포·최종 적합성 검증까지 진행했고, 완료 결과와 적용 범위를 아래에 연결했다. 사용자 데이터 라이브러리와 그 라이브러리를 선택·적용·조회하는 VM/템플릿/ISO 연계 화면이 대상이며, 제품 전체 메뉴의 재설계 작업으로 확대하지 않는다.

## 기준과 선행 관계

- 기준 화면: 검토 시점에 배포된 가상머신 메뉴의 실제 목록·상세·탭·대화상자. 비교 대상 경로·버전·화면을 함께 기록한다.
- [VM 상세 및 공통 자원 탭 표준 #1201](https://github.com/ablecloud-team/ablestack-cloud/issues/1201), [MOLD-DIALOG-1.0 / MD-01~MD-12 #1213](https://github.com/ablecloud-team/ablestack-cloud/issues/1213) 및 통합 시점의 공통 컴포넌트 계약을 적용한다. VM 화면에 남은 미전환 모달을 표준 미준수의 근거로 복제하지 않는다.
- UD-01~UD-10의 적용 범위와 구현 PR·배포 상태를 대조한 뒤 최종 검증한다. 특히 [UD-02 #1383](https://github.com/ablecloud-team/ablestack-cloud/issues/1383), [UD-07 #1388](https://github.com/ablecloud-team/ablestack-cloud/issues/1388), [UD-08 #1389](https://github.com/ablecloud-team/ablestack-cloud/issues/1389)의 완료 결과를 통합 확인한다.
- 이미 표준에 맞는 구조·동작은 재사용하고 회귀 확인한다. 라이브러리 전용 레이아웃 프레임워크, 별도 테마 세트, 광범위한 전역 CSS override를 도입하지 않는다.

## 화면 및 표준 점검표

각 항목을 **적합 / 미적합 / 미검증 / 비대상(근거 필수)**으로 판정하고 실제 화면·DOM/API 증거와 연결한다.

| 영역 | 최종 검토 내용 |
| --- | --- |
| 목록 | VM과 공통 ListView의 컬럼·행 상세 링크·선택·일괄 작업·검색/필터·서버 페이지/총 건수·정렬 의미·빈 목록·권한·로딩/실패/재시도·페이지 이동 위치를 대조한다. 첫 페이지만 받아 전체 결과로 표시하지 않는다. |
| 도구 영역 | 텍스트가 있는 주요 액션을 앞에, 보조 아이콘 액션을 오른쪽에 둔다. 탭 조회 액션은 VM 표준의 reload 아이콘 + 업데이트 표기와 접근성 이름을 따른다. 공통 크기·간격·상단/행 작업 구분을 재사용하고 조회 전용 탭에 불필요한 주요 액션을 만들지 않는다. |
| 상세 | ResourceLayout·InfoCard·ResourceView 등 기존 공통 구조, 소유자/도메인/프로젝트·메타데이터·액션·번역/레이블·설명/도움말·긴 값 표시를 확인한다. 이미 정상인 데스크톱 왼쪽/모바일 상단 탭과 이력 이동을 보존한다. |
| 탭과 콘텐츠 | 상세 메타데이터, 사용자 데이터 본문, **사용 가상머신**, 연결 템플릿/ISO, 코멘트의 책임을 구분한다. 탭 이름·URL·활성 상태·실제 콘텐츠·권한이 1:1로 일치하고 직접 URL·새로고침·뒤로/앞으로·잘못된/접근 불가 탭 처리가 일관돼야 한다. 라이브러리 전환이나 늦은 응답으로 다른 자원의 내용이 섞이지 않는다. |
| 사용 가상머신 | 선택한 라이브러리 상세의 **전용 탭 내부**에 해당 라이브러리와 연결된 VM 목록이 표시되는지 확인한다. 외부 VM 목록으로 이동하는 링크만으로 충족하지 않는다. #1388의 UUID 필터·ACL·서버 페이지·상세 링크·연결/변경/해제 후 갱신 기준을 대조한다. Cloud 연결 정보와 게스트 실행 결과를 구분한다. |
| 본문 및 참조 | 긴 한글/UTF-8·줄바꿈·긴 행·원문/Base64 표시·복사/다운로드 바이트와 오류/빈 상태를 검증한다. 템플릿/ISO 참조와 삭제 차단 이유가 API/실제 연결과 일치하고 코멘트가 해당 라이브러리 범위로 동작해야 한다. |
| 대화상자 | 등록·삭제/일괄 삭제·참조 안내·VM 라이브러리 선택 및 재설정·복제 등 구현된 모든 모달을 목록화한다. MD-01~MD-12별 Header/Body/Footer·viewport 중앙·폭/높이·본문 스크롤·제목/닫기·취소→주 작업·포커스/키보드·popup·중첩·오류 복구·비동기/중복 제출 방지를 확인한다. 콘텐츠 구조는 각 기능에서 승인한 범위로 보존한다. |
| 일반/다크 및 재사용 | 공통 ListView/ResourceLayout/ResourceView/InfoCard/MoldDialog/Status/Tooltip와 의미별 테마 토큰·공통 스타일을 실제로 사용하는지 소스로 확인한다. 개별 화면의 불필요한 중복 CSS/하드코딩을 점검하고 두 모드의 구조·작업 순서를 일치시킨다. |
| 상태 및 접근성 | 정상·hover·focus·선택·오류·비활성·로딩·placeholder·도움말·입력 경계·popup/tooltip·코드 뷰어까지 양쪽 테마에서 확인한다. 키보드 접근, 포커스 표시/복귀, 접근성 이름, 색상 이외의 상태 설명을 검증한다. |
| 반응형 | 1600×1000, 1440×1000, 1440×900, 1280×720, 1024×768, 390×844, 390×480 및 데스크톱 200% 확대에서 검증한다. 페이지/모달의 가로 overflow·가려진 작업이 없어야 하고 필요한 표/코드 스크롤은 콘텐츠 안에 한정한다. |

## 정량 및 기능 회귀 기준

- 모달 Body 스크롤 전후 Header/Footer bounding box 차이 각각 1px 이내, 모달 중심과 전체 viewport 중심 차이 수평·수직 각각 1px 이내를 확인한다. 짧고 긴 본문, sidebar 접힘/펼침, 긴 제목·오류/조건부 필드·popup을 포함한다.
- MD-05의 실제 computed color로 텍스트/placeholder/활성 버튼 대비 4.5:1 이상, 필수 UI 식별 요소·포커스 3:1 이상을 계산한다. 반올림 수치로 통과를 판정하지 않으며 비활성 값/사유도 읽을 수 있어야 한다.
- 관리자·일반 사용자·도메인 관리자·프로젝트 범위를 확인한다. 열 수 없는 조건은 사유와 대체 검증을 기록하며 실환경 통과로 계산하지 않는다.
- UI에서 등록(일반/Base64/변수)·오류 후 입력 보존·검색/페이지/선택·삭제/일괄 삭제·사용 중 삭제 차단을 확인한다. VM 생성 wizard 및 VM 재설정의 라이브러리/직접 입력/설정 안함·현재값 복원·실패 후 정합성을 실제 API/비동기 완료 및 생성된 개체 상태와 대조한다.
- 사용 가상머신 탭은 0/1/복수/페이지 경계, 연결·다른 라이브러리로 변경·해제 이후 목록/총 건수/상세 링크를 검증한다. 새로고침 중 기존 데이터·검색·페이지 및 코멘트 작성 내용을 보존한다.
- 변경한 공통 컴포넌트가 있으면 VM과 대표 공통 자원에서도 영향을 확인한다. 관계없는 전체 제품 기능/Release CI 결과를 이 검토의 통과 증거로 대체하지 않는다.

## 완료 조건과 산출물

- [x] 대상 화면·모달·권한/조건별 목록과 위 기준/MD 규칙 ID를 연결한 최종 점검표를 게시한다.
- [x] 31번 클러스터의 실제 배포 SHA·정적 번들 식별값·기준 VM 화면 버전, 테스트 날짜·역할·viewport·테마를 기록한다.
- [x] 일반/다크의 실제 렌더링 이미지, 좌표/계산 스타일·대비 측정, 주요 API 요청/응답 및 콘솔/네트워크 결과를 첨부한다. 비밀정보는 제외한다. 목업·소스 검사·단위 검사·실환경 검증을 구분한다.
- [x] 전용 사용 가상머신 탭을 포함한 전체 탭/대화상자와 위 기능 회귀가 범위 내에서 통과한다. 확인하지 못한 경로는 미검증으로 남긴다.
- [x] 불일치를 발견하면 재현·기대/실제·영향·증거를 갖춘 후속 결함 이슈를 생성/연결하고 수정 후 재검증한다. 범위 내 미준수/미검증이 남아 있으면 본 이슈를 완료 처리하지 않는다. 범위 변경은 이유와 사용자 합의를 명시한다.
- [x] 기존 VM/라이브러리를 변경하지 않는 전용 테스트 자원을 사용하고 종료 시 생성 자원·연결·코멘트·계정/프로젝트의 잔여가 없는지 확인한다.
- [x] Epic #1381에 최종 결과와 구현/검증 범위·잔여 제약을 보고하고, 본 이슈 통과를 Epic의 UI 표준 최종 완료 조건으로 연결한다.

등록 시점의 계획에서 후속 구현·배포·검증으로 진행했다. 이후 검증에 필요한 검사/빌드는 변경 범위에 맞춰 수행하며 전체 Cloud 빌드는 사용자의 별도 명시 요청 없이 시작하지 않는다.


## 최종 결과

#1392 설계·구현·31번 배포·기능 검증·정리를 완료했습니다. 연결 결함 #1394도 수정·재검증했습니다.

- 목록/상세7탭/8종 모달, VM 생성·재설정과 템플릿/ISO 연계를 검증했습니다. 20px 필드·16px 섹션 간격, VM과 같은32px Footer를 적용했습니다.
- 사용자 지적에 따라 VM 표준에 없는 목록 검색 설명, 참조/본문/버전 탭의 일반 설명·상태 합계와 대화상자의 정책/조작 안내 블록을 제거했습니다. 검색·필터·표·페이지·실제 오류/결과·작업 영향은 유지하고 최종 배포본의 전체 화면을 재수집했습니다. 코멘트 작성자·시각·공개 범위의 대비도 보완했습니다.
- 사용자 첨부 화면의 메뉴/Breadcrumb 사이 세로선도 VM과 대조했습니다. 가로 넘침을 막는 여백을 유지하면서 Breadcrumb 양옆 테두리를 제거했습니다. 모바일 일괄 작업 도구는 기존 버튼·검색창을 줄바꿈하고 비활성 버튼 대비를 기존 테마 토큰으로 보완했습니다.
- 일반/다크×8개 화면 조건의 **256개 화면/모달 검사 통과**. 텍스트≥4.5, 필수 경계/포커스≥3, 중앙 오차≤1px, Header/Footer 스크롤 좌표차0px입니다. 200%는720×450 CSS/dpr2 에뮬레이션입니다.
- 실제 wizard VM 생성·저장 UUID/변수/3075바이트·사용VM1개,11개 사용VM의10+1 페이지, 실제 사용중431, 일괄 부분 성공, 오류/출처 메모만 재시도·코멘트 보존을 UI/API/DB로 대조했습니다.
- 관리자/일반 사용자/도메인 관리자/프로젝트/제한 API 역할을 대표 실제 경로로 확인했습니다. 전체 역할×화면×테마 조합을 실행했다는 의미는 아닙니다.
- 공통 프로젝트 정상 취소·실패 롤백·재시도, 이전 조회 결과, 교차 메뉴 탭 URL 회귀를 수정·검증했습니다. 집중 Jest **15 suites/317 tests**, 변경 파일 ESLint, UI production build 통과입니다.
- 배포 UI `beb090148d910db0f5940aaaad7f4078bc201257`, app `js/app.1c6ca5f1.js`, 정적870개·기존JAR/12클래스/WEB-INF/config/PID 보존, mold active,/client/200입니다.
- API/DB 전용 활성 잔여0, 기존 운영 라이브러리 전체 응답 동일. 전체 Cloud CI/호스트 배포/게스트 실행 재시험/PR 병합은 이번 작업에 포함하지 않았습니다.

[최종 보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/report.ko.md) · [점검표](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/audit-matrix.ko.md) · [정량/화면](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/qualification.json) · [배포/해시](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/runtime-final.json) · [정리](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/db-after.json)

![불필요한 설명과 세로선을 제거한 최종 다크 모드 목록](https://raw.githubusercontent.com/dhslove/ablestack-cloud/refs/heads/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/delivered-standard-dark-list.png)

Epic 구현/최종 UI 검토는11/11입니다. 다음은 Draft PR #1393 최종 리뷰·병합과 Epic #1381 종료입니다.
