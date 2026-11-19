<!--
Licensed to the Apache Software Foundation (ASF) under one
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
under the License.
-->

# SSH 키 쌍 단건·일괄 삭제 설계 (#1376)

Epic #1369, 기존 누적 PR #1378에 반영한다. #1370/#1371의 UUID/소유 범위 검증과 #1372의 실제 공개 키 기반 사용 VM 조회 계약을 재사용한다.

## 공통 화면과 대상 고정

기존 MoldDialog/selfManagedDialog를 재사용한다. 단건 md=720, 일괄 lg=960, 제목 16px/500/22px와 모서리 반경 4px 등 VM 공통 스타일을 따른다. Body만 스크롤하고 Header/Footer를 유지한다. 단건은 이름·UUID·지문·account/domain/project 요약, 일괄은 선택 건수와 같은 식별 정보 및 결과 표를 사용한다. 긴 값은 줄바꿈하고 일괄 표는 가로 스크롤/10개 페이지로 읽는다.

AutogenView에서 열 때 선택 행 또는 단건 resource의 식별 필드를 복사해 동결한다. 뒤의 선택·목록 갱신·전역 프로젝트가 요청 대상을 바꾸지 않는다. 이름과 소유 범위가 필요한 기존 delete API에 고정 값을 전달하고, 계정 대상에는 ignoreproject로 전역 프로젝트 자동 주입을 막는다. 각 요청 직전에 listSSHKeyPairs의 UUID·이름·지문·소유 범위를 대조하며 실패·불일치에는 POST를 보내지 않는다. 원래 UUID가 없으면 등록 없음으로 종료해 같은 이름의 재등록 키를 삭제하지 않는다. 현재 이름/소유자 기반 삭제 API에 대한 UI 사전 검증이며 서버의 원자적 UUID 조건 삭제 API를 새로 추가하는 범위는 아니다.

## 위험 작업과 결과

Footer는 취소→삭제(danger primary)다. 처리 중 제출 버튼 문구는 삭제로 유지하고 접근 이름도 명시한다. 초기 포커스는 취소에 두고 form/전역 Ctrl+Enter 제출을 사용하지 않는다. 사용자가 삭제 버튼을 선택해 실행한다. 제출 중에는 중복 실행·닫기·Escape를 막고 beforeunload 기본 확인을 적용한다. 대상 표와 Footer, 행별 진행 상태를 유지한다.

행 상태는 삭제 전→대상 확인→삭제 중→삭제 완료/등록 없음/실패/결과 불명/삭제 차단이다. 실패 재시도는 실패 행만 대상으로 하며 성공/등록 없음 행을 재삭제하지 않는다. 명확한 4xx 거부는 실패 사유를 표시한다. 통신·5xx·비정상 응답은 결과 불명으로 보고 UUID를 조회한다. 원래 등록이 없으면 완료, 동일 등록이 남아 있으면 실패로 바꿔 수동 재시도를 허용한다. 재조회도 실패하면 결과 불명을 유지하고 결과 확인 버튼만 제공한다. 사전 확인 차단 행에도 결과 확인을 제공해 조회 복구 뒤에만 재시도하도록 한다. 실제 서버가 삭제된 UUID 조회에 431을 반환하는 경우도 등록 없음으로 처리한다. 이 확인은 GET만 실행하며 맹목적으로 POST를 반복하지 않는다.

결과를 읽는 동안 부모 목록을 갱신해 모달이 사라지지 않게 한다. 닫을 때 목록을 갱신하고 처리한 일괄 선택을 해제한다. 상세에서 원래 키 삭제/등록 없음을 확인했다면 목록으로 돌아간다. 취소는 기존 선택을 보존한다. 세션이 변경되거나 해제되면 후속 POST를 중단한다.

## VM 사용 안내

사용 수는 #1372의 listVirtualMachines(keypair + account/domain 또는 project) 및 현재 ACL 범위다. 조회 전후 UUID·지문을 확인해 이름이 같은 재등록 대상의 수를 표시하지 않는다. 유효한 빈 응답만 조회 가능한 범위에서 사용 VM 없음으로 표현하고 조회 중/실패/권한 없음/원래 등록 없음을 구분한다. 조회를 마친 뒤 삭제를 실행하며 조회 실패가 등록 삭제 권한 자체를 없애지는 않는다. 결과 단계의 사용 수는 삭제 전 조회라고 명시한다.

공통 안내는 Mold의 키 쌍 등록 삭제이며 게스트에 설치된 키를 자동 회수하지 않는다는 의미다. 사용 VM 탭 #1377이 재사용할 수 있도록 범위/응답 검증 helper를 분리한다.

## 검증과 배포

대상 고정·권한·중복·부분 실패·재시도·불명 결과 재조회에 대한 단위 검증, 기존 Autogen/VM 스냅샷/생성·등록 회귀를 실행한다. 31번 실제 UI에서 단건/일괄과 같은 이름의 다른 소유자, 프로젝트, 실제 사용/미사용/조회 실패/권한 없음, 실패 복구 및 결과 불명을 검증한다. 390×480/390×844/데스크톱·확대 조건과 두 테마에서 Body/고정 Footer/긴 값/키보드를 확인한다. 테스트 키·역할·계정·도메인·프로젝트를 정리하고 기존 키와 VM SSH 상세 해시를 비교한다.

WSL ext4에서 UI 생산 빌드를 실행해 정적 파일만 배포하며 WEB-INF/config/JAR/PID를 보존한다. 전체 Cloud/Maven/RPM/Actions 빌드는 실행하지 않는다. 기존 PR에 근거를 추가하고 #1376을 completed로 닫으며 Epic은 7/8 완료 OPEN, #1377 OPEN을 유지한다.
