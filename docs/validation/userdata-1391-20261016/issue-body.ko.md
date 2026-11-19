<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License for the specific language governing permissions and limitations. -->

Parent Epic: https://github.com/ablecloud-team/ablestack-cloud/issues/1381

## 문제 및 확인 범위

라이브러리에는 기존 항목의 이름·본문·변수를 직접 수정하는 API와 복제/버전 관리 전용 UI 흐름이 없다. 사용 중 데이터는 삭제가 제한되므로 변경용 새 데이터를 만드는 흐름이 필요하다.

2026-10-15, 31번 Mold 실환경과 최신 upstream Europa `4bd1a2cff50c83089dc23ff1c83def631a343e3d` 기준이다. 확인된 현상과 제안하는 확장 범위를 구분한다.

## 재현 및 근거

- 31번 API와 최신 소스에서 updateUserData API 없음. UI 수정 버튼만 추가해서 해결할 수 있는 누락이 아님.
- 라이브러리 본문 copy/download/등록값을 재사용한 복제 흐름도 없음.
- 사용 중 데이터는 VM/템플릿 참조가 해제되기 전까지 삭제가 거절됨.

## 보완 범위

- 기본 개선안은 기존 참조를 보존하는 복제 후 새 버전 등록으로 설계한다. 기존 사용 VM을 자동 변경하지 않는다.
- 이름/본문/변수 재사용, 이전 버전/출처 표시, 차이 확인과 참조 전환 계획을 정의한다. 직접 수정이 필요하다면 별도 API의 권한·경쟁 제어·영향 범위를 먼저 확정한다.
- 동일 본문 중복 금지 정책과 내용이 같고 변수만 다른 버전의 의미를 UD-01과 합의한다.
- 기존 공통 본문 표시/등록 대화상자를 재사용하고 새 데이터 생성과 기존 VM 적용을 별도 작업으로 안내한다.

## 완료 조건

- [x] 수정/복제/버전 정책과 API 계약이 문서화되고 소유 범위·중복 정책과 일치한다.
- [x] 전용 데이터 복제로 새 버전을 만들고 기존 참조가 유지되는 것을 확인한다.
- [x] 사용자가 관련 VM/템플릿의 적용 범위와 되돌리는 경로를 알 수 있다.
- [x] 일반·다크·모바일 및 생성 실패/중복/재시도 상태를 검증한다.

## 공통 구현 및 검증 조건

- 사용자 요구: 가상머신 메뉴의 목록·상세·탭·탭과 콘텐츠 매칭·대화상자 표준을 따른다. 일반/다크 모드를 모두 지원하고 기존 공통 컴포넌트와 스타일을 최대한 재사용한다.
- `ListView`, `ResourceLayout`, `ResourceView`, `InfoCard`, `MoldDialog`, 기존 Status·Tooltip·테마 토큰을 우선 재사용한다. 메뉴 전용 레이아웃 프레임워크나 별도 테마 색상 세트를 만들지 않는다.
- 대화상자는 [MOLD-DIALOG-1.0 #1213](https://github.com/ablecloud-team/ablestack-cloud/issues/1213)과 실제 VM 제목/Header·Footer 계약을 따른다. 본문 필드의 업무상 순서는 보존하며, Header/Footer 고정·Body만 스크롤·중앙 정렬·포커스·오류 후 입력 유지·중복 제출 방지를 검증한다.
- 테마/상태 검증: 일반·다크, 1440×1000·1280×720·390×844·390×480, 제목·레이블·본문·placeholder·도움말·경계·선택·오류·비활성·로딩·popup. 색상/간격은 공통 토큰을 사용하고 실제 computed style과 화면 이미지로 확인한다.
- 관리자/일반 사용자/도메인 관리자/프로젝트와 API 권한별 노출·제출을 검증한다. 서버 오류·권한 없음·빈 목록·삭제된 대상·지연 응답을 구분하며, 갱신 중 기존 데이터와 사용자 선택을 안전하게 보존한다.
- 31번 전용 테스트 데이터로 UI 동작 → API 결과 → 실제 연결/정리 상태를 대조한다. 소스·테스트·UI 빌드 결과와 클러스터 기능 검증을 구분한다. 전체 Cloud 빌드는 별도 사용자 요청 없이 실행하지 않는다.

## 소스 및 검증 자료

- [api/src/main/java/org/apache/cloudstack/api/command/user/userdata](https://github.com/ablecloud-team/ablestack-cloud/tree/4bd1a2cff50c83089dc23ff1c83def631a343e3d/api/src/main/java/org/apache/cloudstack/api/command/user/userdata)
- [ui/src/views/compute/RegisterUserData.vue](https://github.com/ablecloud-team/ablestack-cloud/blob/4bd1a2cff50c83089dc23ff1c83def631a343e3d/ui/src/views/compute/RegisterUserData.vue)

- [전체 검증 보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/report.ko.md)
- [api-tests.json](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/api-tests.json)

## 연계 과제

- UD-01: https://github.com/ablecloud-team/ablestack-cloud/issues/1382
- UD-07: https://github.com/ablecloud-team/ablestack-cloud/issues/1388
- UD-08: https://github.com/ablecloud-team/ablestack-cloud/issues/1389
