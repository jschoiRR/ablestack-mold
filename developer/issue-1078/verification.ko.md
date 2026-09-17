<!--
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 -->

# #1078 검증 및 배포 기록

## 소스와 빌드 범위

- 기준: upstream/ablestack-europa b92b9b2cb43d8e8386ad29d8c8a65d312b693e24.
- 구현: eae9e4081671d9fc981409854d365a15d2ba5199.
- Docker Rocky Linux 9.8 / linux/amd64, Node 14.21.3 / npm 6.
- UI만 빌드한다. Java/API/DB 변경 없음.
- 배포 artifact에는 서버에 기존 적용된 #1077의 DeployVM 변경(cf5e779cb225eef1c263f6da9f63c95a72ed4ccf)을 임시 overlay로 포함한다. #1078 PR diff에는 #1077 변경을 포함하지 않는다.

## 자동 검증

- UI 전체 단위 테스트: 47 suites / 475 tests PASS (32.728초).
- 변경 파일 및 신규 helper/test ESLint --no-fix PASS.
- git diff --check PASS.
- 새 회귀 테스트: completion-driven 요청 중복/visibility/백오프, 안정 키, 요청 순서 역전/이탈 후 응답 폐기, 행 DOM 및 draft 유지, 실제 AutogenView의 loading/columns 보존, 오류 snapshot 유지와 정상 빈 응답 구분.
- 기준 테스트의 indexed key 호환성을 유지하면서 공통 rowKey는 상태/배열 index와 분리했다.

## 배포 전 관찰

- 13번 서버 기존 UI의 VM 목록에서 자동 listVirtualMachines 조회 4회와 동일한 ant-spin-blur 진입 4회 확인.
- 이는 문서 전체 reload가 아니라 목록 전체 로딩 overlay에 따른 흐림이었다.
- 사용자 VM 생성 탭은 보존하고 별도 검증 탭에서 확인했다.

## 검증 범위의 구분

- 181개 후보의 적용/제외 경로는 list-refresh-matrix.ko.md에 기록.
- 모든 운영 목록을 실제 장비에서 실행했다는 의미가 아니다. 배포 후 직접 확인한 화면/사이클만 아래에 기록한다.
- 운영 VM 생성·삭제·장애 유발 없이 검증한다. API 실패/역순 응답/빈 목록/외부 변경에 해당하는 응답 교체는 단위 테스트로 검증한다.
- 호스트 장치 실시간 재검색, 입력용 선택 snapshot, 기간 보고서의 명시 실행은 유지한다.

## 배포 후 결과

### 최종 배포

- Docker UI production build PASS. 소스맵을 제외하는 프로젝트 표준 ./dev ui-build 사용. 기존 Browserslist/번들 크기 안내는 빌드 실패가 아님.
- 최종 정적 파일 배포: 2026-09-14 09:46 UTC (18:46 KST).
- artifact SHA-256: 4afbc79dea501258a3e944b886b7f0c358465380e889aa02bdc9c26393f98802.
- 최종 배포 직전 백업: /var/tmp/issue1078-20260914-094634/ui-before.tar.gz.
- #1078 최초 배포 이전 백업: /var/tmp/issue1078-20260914-093607/ui-before.tar.gz.
- 대상: /usr/share/cloudstack-management/webapp. config.json 및 WEB-INF hash 동일 확인. management service/DB 재시작 없음.
- HTTP로 받은 index.html 및 실행 JS 3개가 Docker dist와 byte/hash 일치.

### 실제 브라우저 관찰

| 시나리오 | 관찰 결과 |
|---|---|
| 최종 VM 목록/다크 테마 | 62초 동안 listVirtualMachines 6회. ant-spin-blur 진입 0회, Document 요청 0회. 표/헤더/행 backend DOM ID 유지. |
| 최종 VM 선택 및 스크롤 | 체크박스 포커스 유지 상태에서 선택 1개, 20행, 스크롤 top=742 유지. |
| 최종 VM 모의 변경 응답 | 별도 검증 탭의 XHR 응답에서 1행의 표시명만 일시 교체. 새 표시명이 반영되면서 기존 DOM ID/선택 1개 유지. 서버 수정 API 사용 없음. 가로채기 패턴은 즉시 해제. |
| 상세 이벤트 목록 | 51초 반복 조회 중 행 DOM 유지, blur 진입 0회. |
| 상세 이벤트 2페이지/라이트 테마 | 74초 동안 page=2/pagesize=10 조회 10회. 행 DOM과 페이지 유지, blur 진입 0회. |
| 테마 가독성 | 실제 일반 셀 텍스트 대비: 다크 rgb(197,204,212)/rgb(38,38,38) 약 9.34:1, 라이트 rgb(75,85,99)/white 약 7.56:1. 스크린샷으로 표/텍스트 표시 확인. 전체 UI 색상에 대한 접근성 인증은 아님. |

상세 이벤트/라이트 관찰은 최초 배포에서 수행했으며 이후 수정은 체크박스 포커스 예외와 숨긴 VM 장치 탭의 polling 제한이다. 최종 배포에서는 VM 선택/스크롤/반복 조회/모의 응답 교체를 재검증했다.

실제 검증 중 체크박스 포커스를 편집으로 오인하는 문제를 발견해 수정하고 regression test를 추가했다. 장치 탭을 열지 않은 동안 listVmDeviceAssignments를 반복 호출하던 것도 활성 조건으로 제한했다.

단위 테스트는 느린 응답·역순·실패·복구·빈 결과·이탈을 포함한다. 실제 서버의 모든 역할/프로젝트/플러그인/스토리지 조합을 브라우저에서 실행한 것은 아니며, 호스트 스캔/실물 VM 생성·장애 유발 테스트는 수행하지 않았다. 원래 사용자 탭은 보존하고 테스트 테마는 다크로 복원했다.
