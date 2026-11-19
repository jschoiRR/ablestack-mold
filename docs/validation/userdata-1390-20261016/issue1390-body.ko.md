<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License for the specific language governing permissions and limitations. -->

Parent Epic: https://github.com/ablecloud-team/ablestack-cloud/issues/1381

## 문제 및 확인 범위

getVirtualMachineUserData API는 존재하고 실제 전용 VM의 저장 본문을 반환하지만 UI에는 이 API를 호출하는 경로가 없다. 라이브러리 원본과 VM에 저장된 사용자 데이터를 구분하여 확인하기 어렵다.

2026-10-15, 31번 Mold 실환경과 최신 upstream Europa `4bd1a2cff50c83089dc23ff1c83def631a343e3d` 기준이다. 확인된 현상과 제안하는 확장 범위를 구분한다.

## 재현 및 근거

- 실제 31번 listApis와 getVirtualMachineUserData(전용 VM UUID) 응답 확인.
- 최신 upstream Europa ui/src 전체 검색에서 getVirtualMachineUserData 호출 없음.
- VM 상세에는 라이브러리 ID/이름/변수값이 표시되지만 본문 조회 경로가 없음.

## 보완 범위

- VM 또는 라이브러리의 사용 VM 목록에서 권한이 있는 경우 저장된 본문을 조회할 수 있게 한다.
- 라이브러리 원본, VM 저장본, 템플릿 정책/변수, 공급자 전달 상태와 게스트 실행 결과의 의미를 구분한다. API 응답만으로 게스트 실행 성공을 표시하지 않는다.
- UD-08의 본문 표시 컴포넌트와 MoldDialog를 재사용하고 복사/다운로드·UTF-8·오류·권한·삭제된 VM을 처리한다.
- 일반 사용자 포함 실제 API 발견/권한에 따라 노출한다. 역할 이름만으로 허용 여부를 고정하지 않는다.

## 완료 조건

- [x] API가 허용된 사용자는 VM 저장 본문을 UI에서 조회할 수 있다.
- [x] 원본과 저장본의 차이/동일함을 정확하게 표시하고 게스트 실행 완료를 오인하지 않는다.
- [x] 권한 없음/빈 본문/변경된 VM/지연 응답과 일반·다크·모바일을 검증한다.

## 공통 구현 및 검증 조건

- 사용자 요구: 가상머신 메뉴의 목록·상세·탭·탭과 콘텐츠 매칭·대화상자 표준을 따른다. 일반/다크 모드를 모두 지원하고 기존 공통 컴포넌트와 스타일을 최대한 재사용한다.
- `ListView`, `ResourceLayout`, `ResourceView`, `InfoCard`, `MoldDialog`, 기존 Status·Tooltip·테마 토큰을 우선 재사용한다. 메뉴 전용 레이아웃 프레임워크나 별도 테마 색상 세트를 만들지 않는다.
- 대화상자는 [MOLD-DIALOG-1.0 #1213](https://github.com/ablecloud-team/ablestack-cloud/issues/1213)과 실제 VM 제목/Header·Footer 계약을 따른다. 본문 필드의 업무상 순서는 보존하며, Header/Footer 고정·Body만 스크롤·중앙 정렬·포커스·오류 후 입력 유지·중복 제출 방지를 검증한다.
- 테마/상태 검증: 일반·다크, 1440×1000·1280×720·390×844·390×480, 제목·레이블·본문·placeholder·도움말·경계·선택·오류·비활성·로딩·popup. 색상/간격은 공통 토큰을 사용하고 실제 computed style과 화면 이미지로 확인한다.
- 관리자/일반 사용자/도메인 관리자/프로젝트와 API 권한별 노출·제출을 검증한다. 서버 오류·권한 없음·빈 목록·삭제된 대상·지연 응답을 구분하며, 갱신 중 기존 데이터와 사용자 선택을 안전하게 보존한다.
- 31번 전용 테스트 데이터로 UI 동작 → API 결과 → 실제 연결/정리 상태를 대조한다. 소스·테스트·UI 빌드 결과와 클러스터 기능 검증을 구분한다. 전체 Cloud 빌드는 별도 사용자 요청 없이 실행하지 않는다.

## 소스 및 검증 자료

- [api/src/main/java/org/apache/cloudstack/api/command/admin/vm/GetVMUserDataCmd.java#L30](https://github.com/ablecloud-team/ablestack-cloud/blob/4bd1a2cff50c83089dc23ff1c83def631a343e3d/api/src/main/java/org/apache/cloudstack/api/command/admin/vm/GetVMUserDataCmd.java#L30)
- [ui/src/views/compute/InstanceTab.vue](https://github.com/ablecloud-team/ablestack-cloud/blob/4bd1a2cff50c83089dc23ff1c83def631a343e3d/ui/src/views/compute/InstanceTab.vue)

- [전체 검증 보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/report.ko.md)
- [vm-vr-effective-userdata.json](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/vm-vr-effective-userdata.json)

## 연계 과제

- UD-07: https://github.com/ablecloud-team/ablestack-cloud/issues/1388
- UD-08: https://github.com/ablecloud-team/ablestack-cloud/issues/1389


## 완료 결과 / 2026-10-16

#1390 구현·31번 배포·기능 검증을 완료했습니다.

- VM 상세 작업 메뉴와 라이브러리 사용 VM 탭의 상세 링크에서 저장 본문에 접근합니다. UserDataContent/MoldDialog·32px 버튼·공통 테마를 재사용했습니다.
- 원본 동일/차이/참조 없음/비교 불가, 정책·변수 메타데이터와 게스트 실행 의미를 구분합니다. API 발견 기반 노출과 일반 사용자/도메인 관리자/프로젝트/거부 역할을 실제 검증했습니다.
- 최종 소스 102 tests·ESLint·UI production build와 실환경 118항목 통과. 실제 복사/다운로드·VM 변경/삭제·오류/지연·일반/다크 8조합과 포커스/키보드/공유 본문 회귀를 확인했습니다.
- UI `8d79de0db24b0e432a381b8c532e4fb361ffaa12`, app `js/app.6abf6820.js`, 867정적/12기존클래스 해시·WEB-INF/config/PID 보존. API/DB 시험 활성 잔여0, 운영 라이브러리 전체 응답 동일입니다.
- 공통 프로젝트 전환 ERR_CANCELED 2건은 #1392, BOM APPEND 형식 정책은 #1391에 전달합니다. 전체 Cloud 빌드/CI/전체 UI 최종 판정은 이번 완료 근거가 아닙니다.

[설계](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/design.ko.md) · [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/report.ko.md) · [검증 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/qualification.json) · [런타임](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/runtime-final.json)

Epic PR #1393을 9/11 완료 범위로 갱신했습니다. 다음은 #1391, 이후 #1392입니다.
