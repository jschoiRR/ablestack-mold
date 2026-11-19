<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->
Parent Epic: https://github.com/ablecloud-team/ablestack-cloud/issues/1381

## 문제 및 확인 범위

사용 VM 보기 링크는 있지만 라이브러리 상세 안의 참조 목록이 없고 연결된 템플릿/ISO를 찾기 어렵다. 사용 중 삭제는 거절되지만 어느 개체를 정리해야 하는지 UI가 안내하지 않는다.

2026-10-15, 31번 Mold 실환경과 최신 upstream Europa `4bd1a2cff50c83089dc23ff1c83def631a343e3d` 기준이다. 확인된 현상과 제안하는 확장 범위를 구분한다.

## 재현 및 근거

- VM 관련 링크는 실제 2건을 정확히 조회함.
- VM 사용 중 삭제와 템플릿 연결 중 삭제는 530 일반 오류로 거절됨. 데이터 보호 자체는 정상임.
- 현재 라이브러리 탭은 상세/코멘트뿐이며 템플릿·ISO 참조 목록이나 정책별 연결 정보가 없음.
- 템플릿/ISO 편집에는 기존 연결/해제와 정책 설정 UI가 있으므로 이를 누락된 API로 잘못 분류하지 않음.

## 보완 범위

- 선택한 라이브러리의 상세 화면에 **전용 `사용 가상머신` 탭**을 제공한다. 해당 라이브러리를 적용한 VM 목록을 탭 콘텐츠 안에서 보여주며, 기존 외부 VM 목록 이동 링크만으로 대체하지 않는다. 연결 템플릿/ISO는 별도 참조 탭/공통 목록으로 구분하고 실제 ID·소유 범위·정책·상세 링크를 보여준다.
- 템플릿/ISO 참조는 기존 API의 서버 필터 지원 여부를 확인하고 필요하면 ACL·총 건수·페이지 처리가 있는 참조 조회 API를 추가한다. 일부 페이지만 가져와 전체 참조가 없는 것처럼 표시하지 않는다.
- 삭제 전 조회한 참조와 서버 최종 거절을 모두 처리하고, 관련 개체로 이동해 해제한 후 되돌아올 수 있게 한다.
- VM 실제 실행 상태와 Cloud 연결 관계를 구분한다. 사용 중 강제 삭제를 임의로 추가하지 않는다.

## 완료 조건

- [x] VM/템플릿/ISO 참조가 API와 일치하고 권한 범위 밖 정보가 노출되지 않는다.
- [x] 사용 중 삭제 안내에서 관련 개체와 해제 경로를 알 수 있다.
- [x] 삭제 직전 참조 변경과 페이지/검색/갱신/오류 상태를 검증한다.
- [x] 기존 가상머신 보기 및 템플릿/ISO 편집의 연결/해제 흐름을 유지한다.

## 사용 가상머신 탭의 명시적 계약 — 2026-10-15 추가 요구

- [x] 상세 메타데이터/본문/참조/코멘트와 구분되는 전용 `사용 가상머신` 탭이 있고 탭 이름·URL·활성 상태·실제 VM 목록이 일치한다. 목록 행에서 올바른 VM 상세로 이동하고 뒤로 가면 라이브러리/탭 상태를 복원한다.
- [x] 선택한 라이브러리 UUID를 `listVirtualMachines`의 `userdataid`에 전달하고 호출자의 계정·도메인·프로젝트 ACL을 적용한다. 표시명/UUID·상태·소유 범위·상세 링크를 제공하며 VM 표준 목록/Status/Tooltip/도구·페이지 스타일을 재사용한다.
- [x] 서버가 제공한 총 건수와 page/pagesize를 기준으로 검색·페이지·업데이트를 처리한다. 첫 페이지 10건만 받아 전체로 표시하거나 클라이언트의 전체 VM 목록 필터로 권한/페이지를 대신하지 않는다. 검색/정렬은 실제 API 지원 범위와 의미를 명시한다.
- [x] 0건·1건·복수·페이지 경계, 로딩·권한 제한·오류·재시도 및 갱신 중 기존 데이터/조회 조건 보존을 일반/다크·데스크톱/모바일에서 검증한다.
- [x] 전용 VM의 UI 생성/재설정으로 라이브러리를 연결·다른 라이브러리로 변경·해제한 뒤 각 라이브러리의 목록/총 건수가 최신 API와 일치하는지 확인한다. 다른 사용자/프로젝트의 VM은 노출하지 않는다.
- [x] 목록의 의미는 Cloud에 저장된 라이브러리 연결 관계임을 명확히 한다. 템플릿/ISO 상속 또는 복사된 본문의 포함 여부는 서버 관계 계약과 검증 결과로 확정한다. UUID 관계가 없는 VM을 본문 일치만으로 포함하거나 이 목록을 게스트 cloud-init 실행 성공의 증거로 취급하지 않는다.
- [x] UI 배치는 [UD-08 #1389](https://github.com/ablecloud-team/ablestack-cloud/issues/1389)과 함께 구현하고 최종 표준 검토에서 재검증한다. 기존 VM을 변경하지 않는 전용 자원으로 검증하고 잔여 자원을 정리한다.

## 공통 구현 및 검증 조건

- 사용자 요구: 가상머신 메뉴의 목록·상세·탭·탭과 콘텐츠 매칭·대화상자 표준을 따른다. 일반/다크 모드를 모두 지원하고 기존 공통 컴포넌트와 스타일을 최대한 재사용한다.
- `ListView`, `ResourceLayout`, `ResourceView`, `InfoCard`, `MoldDialog`, 기존 Status·Tooltip·테마 토큰을 우선 재사용한다. 메뉴 전용 레이아웃 프레임워크나 별도 테마 색상 세트를 만들지 않는다.
- 대화상자는 [MOLD-DIALOG-1.0 #1213](https://github.com/ablecloud-team/ablestack-cloud/issues/1213)과 실제 VM 제목/Header·Footer 계약을 따른다. 본문 필드의 업무상 순서는 보존하며, Header/Footer 고정·Body만 스크롤·중앙 정렬·포커스·오류 후 입력 유지·중복 제출 방지를 검증한다.
- 테마/상태 검증: 일반·다크, 1440×1000·1280×720·390×844·390×480, 제목·레이블·본문·placeholder·도움말·경계·선택·오류·비활성·로딩·popup. 색상/간격은 공통 토큰을 사용하고 실제 computed style과 화면 이미지로 확인한다.
- 관리자/일반 사용자/도메인 관리자/프로젝트와 API 권한별 노출·제출을 검증한다. 서버 오류·권한 없음·빈 목록·삭제된 대상·지연 응답을 구분하며, 갱신 중 기존 데이터와 사용자 선택을 안전하게 보존한다.
- 31번 전용 테스트 데이터로 UI 동작 → API 결과 → 실제 연결/정리 상태를 대조한다. 소스·테스트·UI 빌드 결과와 클러스터 기능 검증을 구분한다. 전체 Cloud 빌드는 별도 사용자 요청 없이 실행하지 않는다.

## 소스 및 검증 자료

- [ui/src/config/section/compute.js#L1453](https://github.com/ablecloud-team/ablestack-cloud/blob/4bd1a2cff50c83089dc23ff1c83def631a343e3d/ui/src/config/section/compute.js#L1453)
- [server/src/main/java/com/cloud/server/ManagementServerImpl.java#L8198](https://github.com/ablecloud-team/ablestack-cloud/blob/4bd1a2cff50c83089dc23ff1c83def631a343e3d/server/src/main/java/com/cloud/server/ManagementServerImpl.java#L8198)
- [server/src/main/java/com/cloud/template/TemplateManagerImpl.java#L2976](https://github.com/ablecloud-team/ablestack-cloud/blob/4bd1a2cff50c83089dc23ff1c83def631a343e3d/server/src/main/java/com/cloud/template/TemplateManagerImpl.java#L2976)

- [전체 검증 보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/report.ko.md)
- [related-vms-dom.txt](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/related-vms-dom.txt)
- [delete-in-use-dom.txt](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/delete-in-use-dom.txt)
- [template-api-tests.json](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/template-api-tests.json)
- [template-link-ui-dom.txt](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/template-link-ui-dom.txt)
- [iso-link-ui-dom.txt](https://github.com/dhslove/ablestack-cloud/blob/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/iso-link-ui-dom.txt)

![31번 실제 검증 delete-in-use.png](https://raw.githubusercontent.com/dhslove/ablestack-cloud/refs/heads/dhslove/userdata-library-validation-20261015/docs/validation/userdata-20261015/delete-in-use.png)

## 최종 통합 검토

- [UD-11 #1392](https://github.com/ablecloud-team/ablestack-cloud/issues/1392)에서 사용 가상머신 전용 탭과 전체 UI 표준 준수 여부를 최종 재검증한다.


## 구현·31번 배포·범위 내 검증 완료 — 2026-10-16

전용 사용 가상머신/연결 템플릿/연결 ISO 탭, 템플릿·ISO 서버 UUID 필터와 SQL count/page, 삭제 전 참조 건수·해제 경로·최종 거절 처리를 구현했다. VM 표준 공통 컴포넌트·일반/다크·32px 버튼을 재사용한다.

- Java 집중3건·UI2 suites/44건·ESLint·변경 api/server 모듈 및 production UI build 통과. API 행렬11개와 실제 UI 생성/변경/해제·ACL·검색/페이지·실제 삭제 경합 검증. 증거 정합성46항목 통과.
- UI `f6396f8cf4360e899677f21b7be64381d09447b3`, 정적861개/최종12클래스 해시, WEB-INF/config 보존, UI 배포 PID1247065 보존, mold active,/client/200.
- 전용 VM/볼륨16개씩과 이미지4개·라이브러리5개·네트워크3개·계정/프로젝트/도메인/역할 및 추가 분리 시험 프로젝트 정리. DB10종 활성잔여0, 기존 라이브러리1개 전체응답 동일.
- 공통 경로 전환의 CancelToken 미처리 Promise 예외를 새 참조 컴포넌트0 상태에서 분리 재현했다. 이번에 변경하지 않은 공통 AutogenView 경로이며 #1392에 후속 검토를 남긴다. 전체 UI 최종 적합성을 이 이슈 종료로 계산하지 않는다.
- 전체 Cloud 빌드·수동 Actions·PR 병합은 실행하지 않았다. Epic #1381은 7/11, #1389~#1392와 Epic은 OPEN, Epic PR #1393은 Draft 유지.

[설계](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/design.md) · [상세 보고서·화면](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/report.ko.md) · [정량 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/qualification.json) · [최종 배포](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/runtime-final.json)
