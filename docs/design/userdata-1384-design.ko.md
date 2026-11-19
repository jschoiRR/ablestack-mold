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

# UD-03 서버 검색·페이지·선택 계약 (#1384)

대상은 Epic #1381 / Draft PR #1393의 VM 사용자 데이터 선택기다.
최신 upstream Europa `4bd1a2cff50c83089dc23ff1c83def631a343e3d`와 기존 누적 변경을 기준으로 한다.

## API와 공통 선택기

`UserDataSelection`이 `listUserData`의 조회 수명과 서버 페이지를 소유한다.
VM 재설정·VM 생성·AutoScale 생성·AutoScale 프로필 재설정·VNF 생성이 같은 구현을
사용한다. 부모의 중복 목록 조회와 누락되거나 잘못 연결된 `handleSearchFilter` 경로는 제거한다.

- API에 `page`, 소문자 `pagesize`, `keyword`, 실제 소유 계정+도메인 또는 프로젝트를 전송한다.
  `ignoreproject`로 전역 프로젝트 자동 주입을 방지하고 명시한 소유 범위만 사용한다.
- 페이지 수와 total은 서버 count를 그대로 사용한다. 검색/페이지 크기 변경은 첫 페이지부터
  조회한다. 페이지가 삭제로 비면 유효한 마지막 페이지/첫 페이지로 최대 3번 안에 복구한다.
- 번역 문자열 대신 `'0'`을 내부 설정 안함 sentinel로 사용한다. 설정 안함은 페이지 밖의
  고정 radio이며 서버 행·건수·UUID API 조회에 포함하지 않는다.
- 부모에는 검증된 ID와 전체 행을 함께 전달한다. 변수 정의를 다시 비동기로 조회해 이전
  선택의 응답이 새 선택을 덮는 경로를 없앤다. 명시적 선택 변경 시 이전 변수값은 지운다.
  AutoScale의 기존 제출 전 검증은 유지한다.

## 선택과 비동기 응답

검색·페이지 이동은 선택 ID와 이름, 입력한 변수값을 유지한다. 페이지 밖 선택은 이름을
따로 표시하며 필요하면 동일 소유 범위에서 ID로 확인한다. 초기 prefill도 페이지와 독립적으로
확인한 뒤 한 번만 알린다. 조회할 수 없는 선택은 별도 오류로 표시하며 다른 행 또는 설정 안함을
명시적으로 선택해야 한다. 목록 갱신으로 첫 행을 자동 선택하지 않는다.

소유 계정·프로젝트·zone·조회 권한이 바뀌면 검색·페이지·선택·변수값을 초기화한다.
요청 generation과 컴포넌트 수명 검사를 사용해 이전 검색/소유 범위의 응답과 닫힌 화면의
응답을 버린다. 목록 조회 중에는 기존 행을 남기고 행 선택/적용을 잠근다. 검색 입력은 새
검색을 허용한다. 실패 시 마지막 성공 목록·total·page와 선택을 유지하고 재시도할 수 있다.
빈 성공·권한 없음·조회 실패·삭제된 선택을 구별한다. 현재 라이브러리 조회가 준비되지 않으면
부모의 버튼과 제출 함수 모두 적용을 차단한다. 수동 입력과 템플릿 고정 모드에는 이 차단을
적용하지 않는다.

## UI 재사용

기존 InputSearch·Table·Radio·Alert·Pagination·i18n과 테마 스타일을 재사용한다.
전용 색상 세트를 추가하지 않는다. 좁은 화면은 검색 도구 영역을 줄바꿈하고 표 안에서 가로
스크롤한다. VM/AutoScale 재설정 창은 공통 `MoldDialog size="md"`를 사용해 중첩 모달을
없애고 Header/Footer 고정, Body 스크롤, 32px 기본 버튼과 일반/다크 토큰을 유지한다.

## 검증과 후속 범위

단위 테스트는 0/10/11/30/100건, 서버 페이지와 크기 변경, 검색, 삭제된 마지막 페이지,
늦은 응답, 오류 후 보존/재시도, 권한과 소유 범위, prefill, 변수 잔류와 제출 차단을 확인한다.
31번에서는 실제 소유 범위별 전용 데이터와 VM을 사용해 UI 검색·선택 → API 요청 →
실제 연결 상태를 대조한다. 일반/다크 네 viewport와 작은 화면 오류/로딩/선택 상태도 확인한다.
생성한 데이터·계정·프로젝트·VM·역할은 정리하고 기존 리소스 보존을 대조한다.

현재 VM 입력 모드 복원과 라이브러리/수동 본문의 배타적 전송, 설정 안함의 유지/해제 의미는
#1385에서 완성한다. VR/ConfigDrive 전달 실패의 VM 상태 정합성은 #1386,
전체 메뉴 UI 표준 최종 통합 검토는 #1392에 남긴다. 이 이슈에서 그 범위까지 완료로 표시하지 않는다.
