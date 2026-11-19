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

# SSH 키 사용 가상머신 탭 설계 (#1377)

Epic #1369 / 누적 PR #1378. 최신 upstream Europa a5a811387476ac87a8774fc729cc68021104684a와 #1370~#1376 구현 위에 추가한다.

## 화면과 조회 계약

기존 ResourceView에 상세 → 사용 가상머신 → 코멘트 순서로 탭을 선언한다. VM 표준의 데스크톱/태블릿 좌측, 모바일 상단 탭을 유지한다. 코멘트는 listAnnotations 권한 조건을 따른다. SSH 탭 전환은 Vue router로 URL을 갱신하여 검색·페이지와 브라우저 뒤로/앞으로 이동을 복원한다. 다른 리소스의 탐색 동작은 유지한다.

VM 그룹의 공통 ListView/Status를 재사용하고 읽기 전용 업데이트 버튼·검색·현재 페이지 상태 요약·하단 우측 페이지를 배치한다. 툴바 높이 32px, 간격 8px, 본문 간격 20px. 좁은 화면은 검색을 다음 행에 배치하고 표 안에서만 가로 스크롤한다. 열은 원문 표시 이름/UUID 기반 VM 상세 링크, 상태, account 또는 project, domain이다. VM 연결·이동·해제·키 적용 작업은 추가하지 않는다.

서버 검색은 keypair 이름 및 정확한 account/domain 또는 project/domain 범위와 함께 listVirtualMachines로 실행한다. count와 page/pagesize는 서버 결과를 사용한다. 열 정렬은 현재 페이지이며 UI에 안내한다. 조회 가능한 ACL 범위의 결과와 Cloud 연결 관계라는 점을 표시하고 guest authorized_keys 설치/회수 확인과 구분한다. 기존 가상머신 보기와 삭제 전 사용 안내는 같은 sshKeyPairUsage/Identity 범위 계약을 재사용한다.

## 식별·오류·갱신

Autogen의 검증된 키 상세에서만 조회한다. 각 요청 전후 listSSHKeyPairs UUID/이름/지문/소유 범위를 대조하여 조회 도중 삭제/재등록된 이름을 이전 키로 표시하지 않는다. listVirtualMachines 권한이 없으면 API 요청 없이 명시적 권한 상태를 표시한다. 서버 오류, 권한 없음, 키 변경/삭제, 정상 빈 목록을 구분한다. 외부 소유 VM, 잘못된 UUID, 중복 행, 잘못된 count는 표시하지 않는다.

기존 listRefreshMixin의 10초 주기 및 가시성/활성 탭 조건을 사용한다. 동일 조건 갱신은 행을 유지하고 실패 시 마지막 성공 결과임을 표시한다. 권한 또는 키 범위 변경은 행을 제거한다. 키/지문/소유자/전역 프로젝트/사용자/세션/검색/페이지/활성 탭이 달라지거나 컴포넌트가 제거되면 늦은 응답을 버린다. 검색/페이지 크기 변경은 첫 페이지, 결과 축소로 범위를 벗어나면 마지막 실제 페이지로 이동한다.

## 검증과 배포

단위 테스트로 범위, 권한, 변경/지연 응답, 오류 후 보존, URL 복원과 페이지 계약을 검증한다. 31번 실제 API·DB와 브라우저에서 전용 정지 VM의 단일/다중 키, 무키, 다른 계정/프로젝트 및 일반 사용자/관리자, 검색/정렬/페이지/뒤로·앞으로, 실제 연결 변경 후 갱신, 실패·복구와 테마·390×844 반응형을 확인한다. 기존 VM은 변경하지 않고 등록 공개 키를 이용해 19개 사용 VM 목록의 여러 페이지를 검증한다. 게스트 SSH 실행은 이 읽기 전용 탭 검증과 별도로 보고한다.

UI 생산 빌드만 수행하고 정적 자산을 먼저/index를 마지막에 배포한다. 활성 WEB-INF/META-INF/config.json/관리 JAR/PID와 FTCTL 마커를 보존한다. 전체 Cloud/Maven/RPM/Actions 빌드는 수행하지 않는다. 전용 VM/볼륨/NIC/네트워크/키/계정/프로젝트/역할/도메인 정리와 기존 키/VM SSH 상세 해시 보존을 확인한 뒤 #1377을 종료한다. Epic의 공개 키 API/게스트 SSH 등 별도 미구현 항목은 완료로 표시하지 않는다.
