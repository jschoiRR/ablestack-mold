<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->
## 목적과 검증 결론

사용자 데이터 라이브러리의 API↔UI 대응, 31번 클러스터의 실제 UI 기능, 가상머신 UI 표준 준수 여부를 검증하고 결함과 개선 방향을 독립 과제로 관리한다.

기본 등록·조회·상세·코멘트·프로젝트 CRUD, 템플릿/ISO 연결 정책과 사용 중 삭제 제한은 동작한다. 입력 검증, 등록 실패 복구, VM 선택기의 검색/페이지 처리, VM 재설정 입력 모드, 적용 실패의 상태 정합성, 목록 일괄 작업에는 재현 결함이 있다. 이를 **결함 수정 6개 + 기능/UX 보완 4개 + 최종 UI 표준 검토 1개**로 관리한다.

- 검증: 2026-10-15, 31번 Mold 실제 UI/API/관리 로그. 분석 소스는 upstream Europa `4bd1a2cff50c83089dc23ff1c83def631a343e3d`.
- [전체 검증 보고서와 API·DOM·화면·정리 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/report.ko.md)
- 아래 재현 결함은 최초 검증 시점의 기록이다. 현재 구현·배포·완료 범위는 하단 진행 항목을 따른다. 미완료 하위 이슈는 수정 완료를 뜻하지 않는다.

## 재현된 결함

| 영역 | 실제 확인 |
| --- | --- |
| 등록 검증 | 공백 이름·공백 본문 저장. eA==/eA가 같은 저장 본문으로 중복 등록됨 |
| 실패 복구/대화상자 | 잘못된 Base64의 431 오류 후 등록 창이 닫혀 입력 손실. 독립 Footer가 없어 390×480에서 확인 버튼이 본문 스크롤 밖에 위치 |
| VM 선택기 | 실제 30건 중 첫 서버 10건만 로컬 페이지 처리. page-01 검색 시 handleSearchFilter is not a function |
| VM 재설정 모드 | 라이브러리→수동 입력 전환 시 userdata/userdataid 동시 전송으로 431. 설정 안함도 잘못된 userdataid로 431 |
| 적용 상태 | UserData 미지원 L2 VM은 530 실패를 표시하지만 userdataid/변수값이 Cloud에 저장되고 사용 관계에도 남음 |
| 일괄 작업 | 2개 선택 표시 후 제거 실행 버튼 없음. groupAction 정의와 공통 툴바의 toolbarLabel 계약 불일치 |

## API와 UI의 차이

- register/list/delete/reset/link API는 UI 진입점이 있다. 템플릿/ISO 편집의 연결/해제·정책은 실제 검증했으므로 누락 기능으로 분류하지 않는다.
- 최초 검증 시 getVirtualMachineUserData의 UI 경로가 없었다. UD-09 #1390에서 조회/비교 경로를 구현했다.
- 라이브러리의 사용 VM·연결 템플릿/ISO 가시성, 삭제 차단 사유, 본문 copy/download, ID·소유 범위 검색을 보완한다.
- 라이브러리 수정 API는 현재 없다. UD-10 #1391에서 기존 등록/댓글 API를 이용한 독립 새 버전과 출처 기록을 제공하며 기존 참조 VM을 자동 변경하지 않는다.

## UI 표준과 스타일 재사용

사용자 요구를 모든 UI 하위 이슈의 완료 조건으로 적용한다.

1. 가상머신 메뉴의 **목록·상세·탭·탭과 실제 콘텐츠의 매칭·대화상자 표준**을 준수한다.
2. 현재 ListView/ResourceLayout/InfoCard/ResourceView와 데스크톱 좌측·모바일 상단 탭, 정상 이력 복원은 재사용한다. 별도 메뉴용 레이아웃 프레임워크를 만들지 않는다.
3. 상세 메타데이터·사용자 데이터 본문·버전/변경 정책·사용 VM·연결 템플릿/ISO·코멘트의 책임을 분리하고 이름/URL/콘텐츠/권한을 일치시킨다.
4. [공통 대화상자 표준 #1213](https://github.com/ablecloud-team/ablestack-cloud/issues/1213)과 실제 VM 제목/Header·Footer, 기존 MoldDialog를 재사용한다. Header/Footer는 고정하고 Body만 스크롤한다.
5. **일반·다크 모드** 모두 기존 공통 테마 토큰·상태 스타일을 우선 사용한다. 새 코드 영역·popup·오류·선택·비활성·로딩까지 실제 화면과 computed style로 검증한다.
6. 텍스트 primary 버튼을 앞에, 보조 아이콘 버튼을 우측에 두며 VM 공통 툴바·표·페이지·간격·반응형을 따른다. copy/download는 실제 동작과 원본 바이트를 확인한다.

## 하위 이슈와 우선순위

| ID | 우선순위 | 분류 | 이슈 |
| --- | --- | --- | --- |
| UD-01 | P1 | 재현 결함 | [[UD-01][Europa][P1][사용자 데이터] 등록 입력 검증과 Base64 정규화 정합성 개선](https://github.com/ablecloud-team/ablestack-cloud/issues/1382) |
| UD-02 | P1 | 재현 결함 | [[UD-02][Europa][P1][사용자 데이터] 등록 대화상자 오류 복구와 공통 모달 표준 적용](https://github.com/ablecloud-team/ablestack-cloud/issues/1383) |
| UD-03 | P1 | 재현 결함 | [[UD-03][Europa][P1][사용자 데이터] VM 사용자 데이터 선택기 검색과 서버 페이지 처리 복구](https://github.com/ablecloud-team/ablestack-cloud/issues/1384) |
| UD-04 | P1 | 재현 결함 | [[UD-04][Europa][P1][사용자 데이터] VM 재설정 입력 모드와 설정 안함 및 현재값 복원 정합성 개선](https://github.com/ablecloud-team/ablestack-cloud/issues/1385) |
| UD-05 | P1 | 재현 결함 | [[UD-05][Europa][P1][사용자 데이터] 사용자 데이터 적용 실패의 VM 상태 정합성과 지원 조건 검증](https://github.com/ablecloud-team/ablestack-cloud/issues/1386) |
| UD-06 | P1 | 재현 결함 | [[UD-06][Europa][P1][사용자 데이터] 목록 선택 후 일괄 삭제 작업 노출과 결과 처리 복구](https://github.com/ablecloud-team/ablestack-cloud/issues/1387) |
| UD-07 | P1 | 개선 | [[UD-07][Europa][P1][사용자 데이터] 사용 VM 및 템플릿 ISO 참조와 삭제 차단 사유 가시화](https://github.com/ablecloud-team/ablestack-cloud/issues/1388) |
| UD-08 | P1 | 개선 | [[UD-08][Europa][P1][사용자 데이터] 목록 상세 본문 탭의 VM UI 표준과 일반 다크 테마 일치](https://github.com/ablecloud-team/ablestack-cloud/issues/1389) |
| UD-09 | P2 | 개선 | [[UD-09][Europa][P2][사용자 데이터] VM 저장 사용자 데이터 조회 API의 UI 접근 경로 제공](https://github.com/ablecloud-team/ablestack-cloud/issues/1390) |
| UD-10 | P2 | 개선 | [[UD-10][Europa][P2][사용자 데이터] 라이브러리 복제와 버전 관리 및 변경 정책 수립](https://github.com/ablecloud-team/ablestack-cloud/issues/1391) |
| UD-11 | P1 | 최종 검토 | [[UD-11][Europa][P1][사용자 데이터] 전체 UI 표준 준수 최종 통합 검토 및 회귀 검증](https://github.com/ablecloud-team/ablestack-cloud/issues/1392) |


## 진행 순서와 의존성

- 1단계: UD-01~UD-06의 재현 결함을 각각 수정·검증한다. UD-02 입력 복구는 UD-01의 오류 계약, UD-04 모드 복원은 UD-03 선택기 계약과 함께 검증한다. 백엔드 실패 정합성 UD-05는 독립적으로 먼저 진행할 수 있다.
- 2단계: UD-07 참조 조회 계약을 확정하고 UD-08 목록·상세·본문·사용 관계 탭 표준에 연결한다.
- 3단계: UD-09 VM 저장본 읽기, UD-10 복제/버전 관리 정책을 확장한다. 두 과제는 기존 사용자 데이터 수정/적용과 별도 의미로 안내한다.
- 4단계: UD-11 #1392에서 통합된 실제 배포 화면을 VM 표준·일반/다크·반응형·공통 스타일/컴포넌트 재사용 기준으로 최종 검토한다. 사용 가상머신 전용 탭과 모든 탭/대화상자 매칭을 포함하며, 범위 내 미준수/미검증이 남으면 완료 처리하지 않는다.

## 완료 검증과 현재 범위

- [x] [UD-11 #1392](https://github.com/ablecloud-team/ablestack-cloud/issues/1392)의 전체 UI 표준 최종 검토를 통과하고 실제 화면·측정·기능 회귀 결과를 연결한다.
- [x] 하위 이슈의 구현 PR/변경 범위/검증 근거를 연결하고 해결된 항목만 종료한다.
- [x] 관리자/일반 사용자/도메인 관리자/프로젝트 및 API 권한별 기능을 검증한다.
- [x] 정상/오류/빈값/지연 응답/중복 제출/재시도/삭제·이름 변경/페이지 경계에서 UI와 API가 일치한다.
- [x] 일반/다크, 1440×1000·1280×720·390×844·390×480 및 확대에서 목록·상세·탭·모달을 실제 검증한다.
- [x] VM 신규 생성 wizard의 라이브러리 선택과 실제 생성, 지원 공급자의 사용자 데이터 전달 및 게스트 cloud-init 실행을 검증한다. 신규 생성/저장 경로는 #1392, 지원 공급자/게스트 실행은 UD-05 #1386의 실제 검증 보고서로 확인했다. #1392에서 게스트 실행을 다시 수행했다는 의미는 아니다.
- [x] 사용 중 삭제 보호와 부분 실패 복구를 검증하고 전용 자원의 활성 잔여가 없음을 확인한다.

이번 기능 검증에 사용한 데이터 31건과 스타일 촬영용 1건, 전용 정지 VM 2개/볼륨, 템플릿·ISO 각 1개, 계정·프로젝트 각 1개 및 코멘트는 정리했다. 기존 라이브러리 전체 응답이 시작 시점과 정확히 일치했고 활성 테스트 VM/볼륨/템플릿/ISO/프로젝트/계정은 0건이다. 전체 Cloud 빌드는 실행하지 않았다.

## 실제 화면 예시

![일반 모드 상세](https://raw.githubusercontent.com/dhslove/ablestack-cloud/refs/heads/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/detail-light-desktop.png)

![다크 모드 상세](https://raw.githubusercontent.com/dhslove/ablestack-cloud/refs/heads/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/detail-dark-desktop.png)

![VM 선택기 페이지 결함](https://raw.githubusercontent.com/dhslove/ablestack-cloud/refs/heads/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/selector-page2.png)

![목록 일괄 작업 누락](https://raw.githubusercontent.com/dhslove/ablestack-cloud/refs/heads/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/bulk-selected.png)

## 구현 진행 — UD-01~UD-09 완료 / 2026-10-16

- 누적 Epic Draft PR: https://github.com/ablecloud-team/ablestack-cloud/pull/1393
- [x] UD-01 #1382: 입력·정규화·중복 검증, 31번 서버/UI 배포와 범위 내 검증 완료. [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1382-20261015/report.ko.md).
- [x] UD-02 #1383: 공통 등록 모달·고정 Header/Footer·오류 복구·제출/이탈 잠금, 31번 UI 배포와 범위 내 검증 완료. [최종 보고서](https://github.com/dhslove/ablestack-cloud/blob/1687d18f5226b6f723ee0bf113c36a7e775973ac/docs/validation/userdata-1383-20261015/report.ko.md).
- UD-02 검증: 등록/SSH 회귀 56건, 실제 UI 42항목, 일반/다크 네 viewport, 활성 대비 408개 샘플, 7개 생성 결과 API 원문/소유 범위 대조, 정적 파일 855개 해시와 기존 JAR·설정·PID 보존.
- 시험 자원 API/DB 활성 잔여 0건, 기존 사용자 데이터 1개와 CNI 3개 전체 API 응답 동일.
- [x] UD-03 #1384: 서버 검색·페이지·안전한 선택과 재사용 경로, 31번 UI 배포와 범위 내 검증 완료. [설계](https://github.com/dhslove/ablestack-cloud/blob/366d77cacc29563cc54791065dcec42b824dc9d0/docs/design/userdata-1384-design.ko.md) · [검증 보고서](https://github.com/dhslove/ablestack-cloud/blob/366d77cacc29563cc54791065dcec42b824dc9d0/docs/validation/userdata-1384-20261016/report.ko.md).
- UD-03: 단위128건, 최종10페이지/100개 ID API 일치, 실제 VM 생성·재설정과 한글 변수 API/DB 일치, 일반/다크8개 화면·32px 버튼, 855개 정적 해시·기존 서버 보존, API/DB 시험 자원 활성 잔여0건.
- [x] UD-04 #1385: 현재값/활성 모드/설정 안함·템플릿 정책, 31번 서버/UI 배포와 범위 내 검증 완료. [설계](https://github.com/dhslove/ablestack-cloud/blob/e1c9310c8fcfc6cfb6b135481e1270e954ceff9f/docs/design/userdata-1385-design.ko.md) · [검증 보고서](https://github.com/dhslove/ablestack-cloud/blob/e1c9310c8fcfc6cfb6b135481e1270e954ceff9f/docs/validation/userdata-1385-20261016/report.ko.md) · [정량 증거](https://github.com/dhslove/ablestack-cloud/blob/e1c9310c8fcfc6cfb6b135481e1270e954ceff9f/docs/validation/userdata-1385-20261016/qualification-summary.json).
- UD-04: UI152건·변경 server13건, 실제 권한/프로젝트/정책 UI→API/DB 대조, 최종 일반/다크8개 화면·32px 버튼, 855정적/7클래스 해시, API/DB 시험 활성 잔여0건.
- [x] UD-05 #1386: 실패/지연 중 기존 Cloud 값·참조 보존, 지원 조건·모든 NIC 수락 후 저장과 복원, 실제 VR/ConfigDrive 게스트 검증 완료. [설계](https://github.com/dhslove/ablestack-cloud/blob/006db373775ccf3991fcd7a17796408b91a10333/docs/design/userdata-1386-design.ko.md) · [보고서](https://github.com/dhslove/ablestack-cloud/blob/006db373775ccf3991fcd7a17796408b91a10333/docs/validation/userdata-1386-20261016/report.ko.md) · [정량 증거](https://github.com/dhslove/ablestack-cloud/blob/006db373775ccf3991fcd7a17796408b91a10333/docs/validation/userdata-1386-20261016/qualification-summary.json).
- UD-05: UI160·server46건, 실제 공급자 장애/동시 요청/권한·프로젝트, 최종 서버의 기존 updateVM 전달 회귀, 일반/다크8개 화면·32px 버튼, 855정적/9클래스 해시, 시험 활성 잔여0건.
- [x] UD-06 #1387: 일괄 삭제 노출·UUID/소유 범위 고정·부분 실패·현재 확인·실패만 재시도, 역할/프로젝트 실환경 검증 완료. [설계](https://github.com/dhslove/ablestack-cloud/blob/990940f79ce149cb7e01960cd1d8844cae6a12dd/docs/design/userdata-1387-design.ko.md) · [보고서](https://github.com/dhslove/ablestack-cloud/blob/990940f79ce149cb7e01960cd1d8844cae6a12dd/docs/validation/userdata-1387-20261016/report.ko.md) · [정량 근거](https://github.com/dhslove/ablestack-cloud/blob/990940f79ce149cb7e01960cd1d8844cae6a12dd/docs/validation/userdata-1387-20261016/qualification-summary.json).

- [x] UD-07 #1388: 사용 VM/템플릿/ISO 전용 참조 탭·서버 UUID 필터/ACL/count/page·삭제 전 안내와 실제 경합 검증 완료. [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/report.ko.md) · [정량 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/qualification.json).

- [x] UD-08 #1389: 메타데이터/본문 분리·실제 복사/다운로드·UUID/소유 범위 검색·VM 공통 UI 및 8개 테마/화면 조합 검증 완료. [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/report.ko.md) · [181항목 정량 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/qualification.json).
- [x] UD-09 #1390: VM 저장본 API UI 경로·원본 비교·권한·실제 변경/삭제·지연 및 8개 테마/화면 조합 검증 완료. [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/report.ko.md) · [118항목 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/qualification.json).
- [x] UD-10 #1391: 복제·독립 새 버전·소유 범위/중복·출처 기록/부분 복구·전환/복원 정책과 실제 생성 검증 완료. [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/report.ko.md) · [90항목 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/qualification.json).
- 현재 구현 진행은 **11/11**이며 #1392 최종 UI 검토와 #1394 수정·검증을 완료했다. 이 Epic은 PR #1393 최종 리뷰/병합을 위해 OPEN이다.
- 전체 Cloud 빌드·수동 Actions·PR 병합은 수행하지 않았다. PR은 후속 개선을 누적하는 Draft다.


#1391 변경 정책 구현·검증을 완료했고 다음은 #1392 최종 UI 통합 검토이다. UD-07에서 분리 재현한 공통 경로 전환 CancelToken 예외도 #1392에 전달했다. VR/ConfigDrive 시험 게스트 실행은 UD-05에서 확인했고 전체 UI 최종 적합성은 #1392에 남는다. 역할 전환/wizard 공통 메타데이터 예외와 AutoScale 프로필/VNF 실환경 미검증은 #1392에 전달했다.


UD-08 최종 UI에서도 프로젝트 전환의 공통 ERR_CANCELED 2건을 확인해 #1392에 추가 스택을 전달했다. 전체 UI 최종 감사와 Epic PR #1393의 후속 구현·통합 검증은 계속 진행 대상이다.


## UD-09 최신 배포와 검증 / 2026-10-16

#1390 구현·31번 배포·기능 검증을 완료했습니다.

- VM 상세 작업 메뉴와 라이브러리 사용 VM 탭의 상세 링크에서 저장 본문에 접근합니다. UserDataContent/MoldDialog·32px 버튼·공통 테마를 재사용했습니다.
- 원본 동일/차이/참조 없음/비교 불가, 정책·변수 메타데이터와 게스트 실행 의미를 구분합니다. API 발견 기반 노출과 일반 사용자/도메인 관리자/프로젝트/거부 역할을 실제 검증했습니다.
- 최종 소스 102 tests·ESLint·UI production build와 실환경 118항목 통과. 실제 복사/다운로드·VM 변경/삭제·오류/지연·일반/다크 8조합과 포커스/키보드/공유 본문 회귀를 확인했습니다.
- UI `8d79de0db24b0e432a381b8c532e4fb361ffaa12`, app `js/app.6abf6820.js`, 867정적/12기존클래스 해시·WEB-INF/config/PID 보존. API/DB 시험 활성 잔여0, 운영 라이브러리 전체 응답 동일입니다.
- 공통 프로젝트 전환 ERR_CANCELED 2건은 #1392, BOM APPEND 형식 정책은 #1391에 전달합니다. 전체 Cloud 빌드/CI/전체 UI 최종 판정은 이번 완료 근거가 아닙니다.

[설계](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/design.ko.md) · [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/report.ko.md) · [검증 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/qualification.json) · [런타임](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/runtime-final.json)

Epic PR #1393을 9/11 완료 범위로 갱신했습니다. 다음은 #1391, 이후 #1392입니다.


## UD-10 최신 배포와 검증 / 2026-10-16

UD-10 #1391 설계·구현·31번 배포·검증·정리를 완료했습니다. Epic 진행 **10/11**, 남은 하위 이슈는 #1392(최종 UI 통합 표준 검토)입니다. Draft PR #1393의 제목/설명과 근거를 갱신했습니다.

복제6건·출처 기록, 기존 VM/템플릿 참조 보존, 전환/복원과 실제 APPEND 생성, 일반/다크8조합을 검증했고 Jest174·실환경90항목이 통과했습니다. API/DB11종 활성 잔여0, 운영 라이브러리 응답 동일입니다. 전체 Cloud CI/게스트 실행/PR 병합과는 구분합니다.

[UD-10 보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/report.ko.md) · [배포/해시](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/runtime-final.json) · [정량](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/qualification.json)

#1392에서는 새 버전 탭/복제 흐름을 포함한 전체 목록·상세·모달·테마·반응형/200% 확대와 공통 ERR_CANCELED를 최종 점검합니다. Epic은 OPEN으로 유지합니다.


## UD-11 최종 결과 / 2026-10-16~17

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

공급자·게스트 실행 기준은 UD-05 #1386의 실제 VR/ConfigDrive·게스트 파일/실행 마커 보고서와 이번 wizard 실제 생성/저장 검증을 연결했다. 현재 작업에서 게스트 실행을 재시험하지 않았다. 앞선 UD-09/10의10/11·다음#1392 표기는 당시 진행 이력이다.
