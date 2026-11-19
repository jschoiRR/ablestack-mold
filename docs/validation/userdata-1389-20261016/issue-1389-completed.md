<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->

Parent Epic: https://github.com/ablecloud-team/ablestack-cloud/issues/1381

## 문제 및 확인 범위

공통 목록/상세 레이아웃은 이미 VM 표준을 재사용한다. 다만 긴 사용자 데이터가 일반 상세 필드에 그대로 들어가고 본문 도구·소유 범위 검색·의미가 분명한 탭 구성이 부족하다.

2026-10-15, 31번 Mold 실환경과 최신 upstream Europa `4bd1a2cff50c83089dc23ff1c83def631a343e3d` 기준이다. 확인된 현상과 제안하는 확장 범위를 구분한다.

## 재현 및 근거

- ListView·ResourceLayout·InfoCard·ResourceView를 사용하며 데스크톱 좌측/모바일 상단 탭 배치는 정상.
- 상세/코멘트 전환, 뒤로/앞으로, 새로고침 시 URL과 실제 콘텐츠 매칭은 정상 확인.
- 본문은 `DetailsTab`에서 `white-space: pre-wrap`으로 표시되며 Userdata/파라미터 같은 혼용 라벨이 있음. copy/download/본문 전용 도구가 없음.
- API id 검색은 있지만 UI 검색 keyword는 이름만 조회하여 UUID 입력은 0건. 프로젝트 정보는 InfoCard에는 나오지만 상세 필드 구성에는 없음.

## 보완 범위

- 기존 공통 레이아웃·컴포넌트와 스타일을 보존하고 해당 리소스의 구성/콘텐츠만 확장한다.
- 탭 책임을 상세 메타데이터 → 사용자 데이터 본문 → 사용 VM → 연결 템플릿/ISO → 코멘트로 정리하되 UD-07의 참조 계약과 권한 조건을 따른다. 탭 이름·URL·활성 상태·실제 콘텐츠를 1:1로 맞춘다.
- 본문은 읽기 전용 코드/텍스트 표시, 원문/Base64 구분, 줄바꿈·UTF-8·긴 행·빈/오류 상태 및 실제 동작하는 복사/다운로드를 제공한다. 페이지 전체 가로 overflow를 만들지 않는다.
- ID/이름/소유 계정·도메인/프로젝트 검색을 지원 범위에 맞게 노출하고 정렬이 현재 페이지인지 안내한다.
- 툴바의 텍스트 primary 버튼 앞/보조 아이콘 버튼 우측, 공통 높이·간격·페이지 배치·반응형·테마 토큰을 VM 기준으로 대조한다.

## 완료 조건

- [x] 이미 정상인 공통 레이아웃과 탭 매칭/이력 동작이 유지된다.
- [x] 메타데이터·본문·참조·코멘트가 의미에 맞는 탭 콘텐츠로 표시된다. **선택한 라이브러리를 적용한 VM 목록은 상세 화면의 전용 `사용 가상머신` 탭 안에 표시**하고 외부 VM 목록 링크만으로 대체하지 않는다. #1388의 UUID 필터·ACL·서버 페이지·연결/변경/해제 후 갱신 기준을 따른다.
- [x] 한글 긴 본문과 copy/download 결과 바이트가 원본과 일치한다.
- [x] 일반/다크·데스크톱/모바일의 표·상세·탭·툴바·코드 영역·오류 상태를 실제 화면과 computed style로 검증한다.

## 공통 구현 및 검증 조건

- 사용자 요구: 가상머신 메뉴의 목록·상세·탭·탭과 콘텐츠 매칭·대화상자 표준을 따른다. 일반/다크 모드를 모두 지원하고 기존 공통 컴포넌트와 스타일을 최대한 재사용한다.
- `ListView`, `ResourceLayout`, `ResourceView`, `InfoCard`, `MoldDialog`, 기존 Status·Tooltip·테마 토큰을 우선 재사용한다. 메뉴 전용 레이아웃 프레임워크나 별도 테마 색상 세트를 만들지 않는다.
- 대화상자는 [MOLD-DIALOG-1.0 #1213](https://github.com/ablecloud-team/ablestack-cloud/issues/1213)과 실제 VM 제목/Header·Footer 계약을 따른다. 본문 필드의 업무상 순서는 보존하며, Header/Footer 고정·Body만 스크롤·중앙 정렬·포커스·오류 후 입력 유지·중복 제출 방지를 검증한다.
- 테마/상태 검증: 일반·다크, 1440×1000·1280×720·390×844·390×480, 제목·레이블·본문·placeholder·도움말·경계·선택·오류·비활성·로딩·popup. 색상/간격은 공통 토큰을 사용하고 실제 computed style과 화면 이미지로 확인한다.
- 관리자/일반 사용자/도메인 관리자/프로젝트와 API 권한별 노출·제출을 검증한다. 서버 오류·권한 없음·빈 목록·삭제된 대상·지연 응답을 구분하며, 갱신 중 기존 데이터와 사용자 선택을 안전하게 보존한다.
- 31번 전용 테스트 데이터로 UI 동작 → API 결과 → 실제 연결/정리 상태를 대조한다. 소스·테스트·UI 빌드 결과와 클러스터 기능 검증을 구분한다. 전체 Cloud 빌드는 별도 사용자 요청 없이 실행하지 않는다.

## 소스 및 검증 자료

- [ui/src/config/section/compute.js#L1434](https://github.com/ablecloud-team/ablestack-cloud/blob/4bd1a2cff50c83089dc23ff1c83def631a343e3d/ui/src/config/section/compute.js#L1434)
- [ui/src/components/view/DetailsTab.vue#L171](https://github.com/ablecloud-team/ablestack-cloud/blob/4bd1a2cff50c83089dc23ff1c83def631a343e3d/ui/src/components/view/DetailsTab.vue#L171)
- [ui/src/components/view/ResourceView.vue](https://github.com/ablecloud-team/ablestack-cloud/blob/4bd1a2cff50c83089dc23ff1c83def631a343e3d/ui/src/components/view/ResourceView.vue)
- [ui/src/views/compute/InstanceTab.vue](https://github.com/ablecloud-team/ablestack-cloud/blob/4bd1a2cff50c83089dc23ff1c83def631a343e3d/ui/src/views/compute/InstanceTab.vue)
- [ui/src/views/compute/SSHKeyPairUsage.vue](https://github.com/ablecloud-team/ablestack-cloud/blob/4bd1a2cff50c83089dc23ff1c83def631a343e3d/ui/src/views/compute/SSHKeyPairUsage.vue)

- [전체 검증 보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/report.ko.md)
- [tab-content-match.json](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/tab-content-match.json)
- [project-detail-light-dom.txt](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/project-detail-light-dom.txt)
- [library-search-dom.txt](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/library-search-dom.txt)

![31번 실제 검증 detail-light-desktop.png](https://raw.githubusercontent.com/dhslove/ablestack-cloud/refs/heads/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/detail-light-desktop.png)

![31번 실제 검증 detail-dark-desktop.png](https://raw.githubusercontent.com/dhslove/ablestack-cloud/refs/heads/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/detail-dark-desktop.png)

![31번 실제 검증 detail-dark-mobile.png](https://raw.githubusercontent.com/dhslove/ablestack-cloud/refs/heads/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/detail-dark-mobile.png)

## 연계 과제

- UD-07: https://github.com/ablecloud-team/ablestack-cloud/issues/1388

## 최종 통합 검토

- [UD-11 #1392](https://github.com/ablecloud-team/ablestack-cloud/issues/1392)에서 사용 가상머신 전용 탭과 전체 UI 표준 준수 여부를 최종 재검증한다.


## UD-08 구현 및 검증 완료 / 2026-10-16

31번 최종 UI `8629c06d37a4f51c0acb7b9cb084e7bc744c37e1` 배포 후 77개 UI 회귀, API 14항목, 8개 테마/화면 조합과 실제 복사·다운로드 바이트를 검증했다. 기존 자원 보존과 전용 자원 정리를 완료했다. 공통 CancelToken 예외 2건은 #1392에 유지하며 전체 UI 최종 적합성으로 확대하지 않는다.

[최종 보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/report.ko.md) · [181항목 정량 검증](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/qualification.json) · [Epic PR #1393](https://github.com/ablecloud-team/ablestack-cloud/pull/1393)
