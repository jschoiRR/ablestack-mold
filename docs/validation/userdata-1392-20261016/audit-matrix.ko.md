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

# UD-11 최종 검토 목록

qualification.json의 최종 256개 화면/모달 검사와 별도 기능·상태 자료로 판정한다. initial/before 재현 자료는 합격 집계에서 제외한다.

| 화면 | 기준/규칙 | 기능·상태 | 최종 판정 |
|---|---|---|---|
| 사용자 데이터 목록 | VM ListView/도구 순서 | 검색·정확 UUID·필터·페이지20/50·현재 페이지 정렬·선택·일괄/빈/오류 | 적합 |
| 상세/상세 탭 | ResourceLayout/InfoCard/ResourceView | 소유자·UUID·긴 변수·직접 URL·새로고침·잘못된 tab·뒤로/앞으로 | 적합 |
| 본문 | 공통 detail-tab-toolbar | 복사→다운로드→표현→업데이트·일반/Base64·UTF8/긴행·실패/권한/지연 | 적합 |
| 버전 기록 | 공통 toolbar/섹션 간격 | 출처·바이트·독립 버전·빈/에러/삭제 가능한 참고 기록 | 적합 |
| 사용 가상머신 | VM ListView/toolbar/pagination | UUID필터·0/1/11·페이지10+1·검색·링크·참조변경/해제·갱신 보존 | 적합 |
| 연결 템플릿/ISO | 같은 참조 탭 | 각2개·정책·연결/변경/해제 후 UUID/건수·상세 링크 | 적합 |
| 코멘트 | VM 공통 AnnotationsTab | 보내기→업데이트·작성 내용 갱신 보존·해당 UUID·추가/삭제 | 적합 |
| 등록/복제 | MD-01~12 | 중앙·Header/Body/Footer·32px·20px 필드·popup·포커스/복귀/Escape·오류/중복/부분 성공/미리보기 | 적합 |
| 단건/일괄 삭제·참조 안내 | MD-01~12 | 안전 취소 포커스·대기 잠금·실제 사용중431/실패 복구·16px 섹션 | 적합 |
| VM 저장 본문/재설정 | MD-01~12 | 현재값/라이브러리/직접/없음·실패 유지·실제 저장본·공통 viewer | 적합 |
| VM 생성 wizard | 기존 전체 페이지 경계/선택기 | 기존 순서·소유 범위·페이지/검색/선택·정지 VM 실제 생성·본문/UUID | 적합 |
| 템플릿/ISO 편집 | MD-01~12/기존 그리드 유지 | 같은 소유자 선택기·정책·연결 완료 후 닫기·부분 실패 유지·정확 templateid | 적합 |
| 공통 경로 취소 | API rejection/조회 scope | 관리자·일반 사용자·도메인 관리자·프로젝트 진입/이탈·대시보드↔목록·뒤/앞/빠른 전환·지연 결과 | 적합 |

두 테마:1600×1000,1440×1000,1440×900,1280×720,1024×768,390×844,390×480,1440×900 물리 화면의200%(720×450 CSS/deviceScaleFactor2).

중앙/스크롤 고정≤1px, 텍스트/placeholder/활성 버튼≥4.5:1, 필수 경계/포커스≥3:1을 반올림 전 실제색으로 판정한다. 비활성도 읽을 수 있게 측정한다. 표/긴 행 스크롤만 내부 허용하며 페이지/모달 가로 overflow는 허용하지 않는다.

## MD 규칙별 판정

| 규칙 | 판정 | 근거 |
|---|---|---|
| MD-01 | 적합 | 공통 Header/Body/Footer, final-modal-scroll의 양쪽 테마14건 실제 Body 스크롤에서 Header/Footer 좌표차0px |
| MD-02 | 적합 | 전체 viewport 중심 오차≤1px, 배경 클릭 차단·배경 잠금·닫기 포커스 복귀. sidebar 시작 상태 대조 |
| MD-03 | 적합 | 8개 화면 조건×두 테마. 페이지/모달 가로 넘침 없음. 필요한 표/코드만 내부 스크롤 |
| MD-04 | 업무별 적용 적합 | 사용자 명시 요청에 따라 VM과 같은 Footer32px. 초안42px와 다른 적용 기준을 명시. 필드20px·섹션16px, 기존 글꼴/그리드/쉘 유지 |
| MD-05 | 적합 | 텍스트/placeholder≥4.5, 필수 경계/포커스≥3. 유휴/hover/focus/선택/오류/비활성/로딩/popup 실제 합성색 |
| MD-06 | 적합 | 필드/그리드·조건부 구조 유지. 첫 오류 이동과 API 실패 입력 보존. final-image-error-focus, final-registration-* |
| MD-07 | 적합 | 취소→주 작업, danger/영향 설명·하나의 제출·대기 잠금·연결 완료 대기. 단위 및 실제 요청 자료 |
| MD-08 | 적합 | aria-modal/제목 연결·초기/안전 포커스·Tab 순환·Escape/복귀. final-keyboard 14건 |
| MD-09 | 적합/일부 비대상 | popup/tooltip 위치·대비, 모달1개·mask. 업무상 자식 모달이 없어 중첩 생성은 비대상. 댓글 inline popconfirm 유지 |
| MD-10 | 적합 | 조회/제출/실패/빈값 구분·기존 결과/초안 유지·부분 성공 재시도. 실제200/431과 통제503 구분 |
| MD-11 | 적합/일부 비대상 | Form/Confirm/Detail 유형을 검증. 전체 페이지 VM wizard와 inline popconfirm은 쉘 비대상. 새 단계/진행 모달 없음 |
| MD-12 | 적합 | 공통 컴포넌트·의미 토큰·opt-in CSS, 설계/소스·집중 검사·실제 화면/API/DB/정리 근거 게시 |

## 기능·상태 근거와 적용 경계

final-wizard-request/api-final-after-wizard는 UI 생성·Stopped·UUID/변수/3075바이트, final-list-*는 이름/정확UUID/빈 목록·20개 서버 페이지, final-usage-*는0/1/11·10+1·조회 오류 때2페이지 보존을 기록한다. final-bulk-partial-actual-431과 final-delete-*는 사용중 차단·부분 성공·실패 대상 재시도, final-content-byte-check는 BOM/CRLF/한글/emoji 원문 및Base64 일치를 기록한다. final-clone-*·api-final-assertions, final-comment-*·final-common-*는 출처만 재시도·공통 메모 초안 보존을 검증한다.

final-role-*·api-final-roles·final-denied-*는 관리자/일반/도메인 관리자/프로젝트/제한 역할의 대표 실제 경로다. 관리자 양쪽 테마 전체 행렬과 역할 경로는 별도이며 전체 역할×테마×화면 조합을 모두 실행하지 않았다. final-cross-menu-route는 VM/이미지→라이브러리 특정 탭과 뒤/앞·잘못된 URL을 재검증한다.

#1392에 지정된 라이브러리와 VM/템플릿/ISO 연계 화면을 검증했다. Kubernetes/VNF/AutoScale/백업 메뉴 전체의 재설계·실제 서비스 배포는 대상이 아니다. 공유 선택기/범위 유틸은 앞선 집중 회귀와 이번 대상 소비 화면으로 대조한다. 공급자/게스트 실행은#1386의 기존 근거로 연결하며 이번 정지 VM 검증과 합산하지 않는다. 전체 Cloud CI/PR 병합은 이번에 실행하지 않았다.

최종 정정: VM에 없는 목록 검색 설명과 탭/대화상자의 일반 설명 블록은 제거했다. before-standard-removal은 정정 이전 재현 자료이며 최종 합격 집계에 쓰지 않는다. final 자료는 최종 정적 번들에서 다시 수집하고 standardProseAbsent를 함께 검사한다. before-comment-contrast는 양성 코멘트의 수정 전 대비 실패이며 최종 공개/비공개/출처 메모가 있는 화면을 두 모드로 재측정한다.

추가 대조: 사용자 첨부의 Breadcrumb 세로선은 VM에서는 메뉴 뒤로 가려진1px 테두리가 라이브러리에서는 노출된 것이었다. 라이브러리 영역만 좌우 테두리를 제거한다. 모바일 일괄 작업 줄바꿈·비활성 보내기 대비도 재현 후 수정했다. 기존 UUID의 이름/본문 수정 API가 없어 이름 변경은 비대상이며 새 UUID 등록/이름 충돌·기존 대상 식별과 출처 기록을 확인한다.
