<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this work
for additional information regarding copyright ownership. The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy at
http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or
agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
or implied. See the License for the specific language governing permissions
and limitations under the License. -->

# UD-02 등록 대화상자 설계

대상: Epic #1381 / #1383, Europa. #1382의 서버·정규화 계약은 유지한다.

## 공통 쉘과 콘텐츠

`MoldDialog size="md"`와 기존 VM/SSH 등록 제목·도움말 패턴을 재사용한다.
`selfManagedDialog`로 AutogenView의 외부 모달을 제거한다. 기존 설명 → 이름 →
본문 → Base64 → 변수 → 도메인 → 계정 순서를 유지한다. Header/Footer와 Body
스크롤, 반응형 크기, 일반/다크 표면·입력·오류·선택 토큰은 공통 스타일이 소유한다.
화면 전용 색상·폭 프레임을 추가하지 않는다. Footer는 취소 → 등록이다.

Footer 버튼 크기는 VM 편집 대화상자와 같은 기본 `a-button` 스타일을 상속한다.
공통 preset이 높이·여백을 별도로 지정하지 않는다. 2026-10-16 실측 기준은 높이
32px, padding 4px 15px, 글자 14px, 모서리 4px다. 이전 preset의 42px 강제 높이는
VM 표준을 따르라는 사용자 보완 요청에 따라 제거한다. Footer의 gap 8px와 정렬은 유지한다.

## 제출과 오류 복구

- 버튼·폼 Enter·Ctrl+Enter는 한 경로를 사용하며 검증 시작 전에 잠근다.
  textarea Enter는 줄바꿈, 태그 선택 Enter는 태그 확정 동작을 유지한다.
- 필수값/형식 오류는 기존 Form 오류와 첫 오류 필드 포커스를 사용한다.
- 서버·통신 오류는 Body의 접근 가능한 Alert에 문자열로 표시한다. 폼과 소유 범위를
  변경하지 않으며 요청 전 포커스와 Body 스크롤을 복원한다. 별도 알림이 폼을 가리지 않는다.
- 요청 중 필드/취소/X/Escape를 잠그고 진행 문구를 표시한다. beforeunload는 브라우저
  이탈 경고를 요청한다. AutogenView는 자식의 `blocksNavigation` 동안 route update/leave를
  취소하므로 요청 취소 토큰을 먼저 해제하지 않는다. 닫기는 서버 요청 취소를 의미하지 않는다.
- 성공만 목록 갱신 후 닫는다. 취소/성공 후 다시 열면 새 컴포넌트의 빈 폼과 기본 소유 범위로
  시작한다. 임시 저장은 제공하지 않는다. 해제된 컴포넌트의 늦은 응답은 UI를 바꾸지 않는다.

## 공유 경로와 검증

CNI 등록도 같은 컴포넌트를 사용하므로 같은 쉘로 전환한다. CNI API·인코딩·변수 처리
계약을 유지하고 스모크 검증한다. 도메인 조회 실패는 빈 목록 성공과 구별해 Body에 표시한다.
사용자 데이터의 크기 상한은 #1382의 listCapabilities/서버 검증을 그대로 따른다.

MOLD-DIALOG-1.0 MD-01~12와 실제 VM 표준을 기준으로 일반/다크 네 viewport,
필수값·중복명·잘못된 Base64·크기·서버/통신·지연/키보드·취소/재열기,
관리자/사용자/도메인관리자/프로젝트와 권한 차단을 확인한다. 전용 리소스를 정리하고
기존 라이브러리와 런타임 보존을 대조한다. 전체 메뉴 표준 최종 감사는 #1392에 유지한다.
