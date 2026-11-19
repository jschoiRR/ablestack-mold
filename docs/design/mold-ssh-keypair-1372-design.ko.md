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

# SSH 키 사용 VM 조회 정합성 설계 (#1372)

Epic #1369와 기존 PR #1378에 #1370/#1371에 이어 누적한다. 기준 upstream Europa는 `a5a811387476ac87a8774fc729cc68021104684a`이며 API `keypair=<이름>` 계약을 유지한다.

## 문제와 판정 기준

기존 QueryManager는 키에 대응하는 SSH.PublicKey 상세와 실제 조회 VM의 상세를 서로 다른 join으로 연결하고 계정만 비교한다. 다른 VM이나 제거된 VM의 상세가 조건을 만족하면 같은 계정의 무관한 VM까지 반환한다. 31번 admin의 기존 키에서 85개, 검증 VM 준비 후 신규 A/B 키에서 각각 89개를 API/실제 UI로 재현했다.

등록 및 reset 경로는 VMInstanceDetails의 SSH.PublicKey에 공개 키들을 LF로 연결해 저장한다. SSH.KeyPairNames의 CSV나 같은 계정이라는 사실만으로 사용을 판단하지 않는다. 현재 등록 키와 같은 VM의 공개 키 및 소유 범위가 모두 일치해야 한다.

## DB 조회와 ACL

VMInstanceDetailsDao에 listVmIdsBySSHKeyPairName(name, accountIds)를 추가한다. 준비한 SELECT에서 같은 VM id의 SSH.PublicKey 상세를 연결하고 ssh_keypairs와 VM의 account_id/domain_id를 모두 비교한다. 공개 키는 LF로 경계를 두고 BINARY INSTR로 완전한 키 단위로 비교한다. CR은 양쪽에서 제거해 CRLF도 처리한다. 단일/다중 키, 기존 RSA의 줄바꿈 저장 표현을 지원하고 부분 문자열·대소문자 다른 key material·다른 상세·빈 공개 키를 제외한다. 제거된 VM과 User 외 VM은 제외하고 DISTINCT id를 반환한다.

이름과 ACL로 구한 계정 ID는 PreparedStatement 인자로 바인딩한다. 키 이름의 따옴표·%, _·콤마 등의 문자를 SQL/LIKE 패턴으로 해석하지 않는다. 기존 ACL 파라미터 구성은 먼저 실행하고 계정 ID가 있으면 조회 범위를 줄인다. 전체 ACL·domain/project·state/id/keyword/tag 등 나머지 조건은 기존 VM 검색에서 계속 적용한다.

QueryManager는 구한 ID를 기존 SearchBuilder에 AND/IN 조건으로 추가한다. 기존 id/ids 조건을 대체하지 않는다. 최종 searchAndDistinctCount와 page Filter를 그대로 사용하므로 조회한 페이지를 나중에 메모리에서 필터링하지 않는다. 연결 ID가 없으면 빈 결과/count 0을 반환하고, DB 오류는 예외로 전달한다. 오류 후 전체 VM 조회로 fallback하지 않는다.

## UI 소유 범위

SSH 상세의 관련 VM 링크는 계정 키에 name/account/domain, 프로젝트 키에 name/project/domain을 전달한다. 공통 helper를 재사용하며 VM으로 이동하는 기존 버튼 배치는 유지한다.

AutogenView에서 listVirtualMachines 및 Metrics의 keypair 필터를 명시적 소유 범위에 맞춘다. projectid query가 있으면 전역 all-projects 인자를 대체하고, account/domain query가 있으면 전역 projectid를 제거하고 API 내부 ignoreproject를 설정한다. API 전송 전에 interceptor가 이 플래그를 제거한다. 소유 범위 없는 검색과 SSH 필터 없는 VM 조회는 기존 프로젝트 처리를 유지한다.

## 검증

DAO의 인자 바인딩/소유 범위/리소스 닫기/오류 전달, QueryManager의 ACL·기존 ID 조건·count/page 보존·미사용/오류 fallback 차단을 테스트한다. 공통 UI 회귀와 계정/프로젝트 VM 경로를 테스트한다.

실제 MySQL 연결에서 connection-local 임시 테이블을 사용해 DB 조건 자체를 검증한다. 실제 Cloud 테이블은 수정하지 않는다. 31번 API로 새 키 5개, 같은 공개 키의 계정/프로젝트 범위, 단일/다중/키 없는 정지 VM 6개, 네트워크 3개와 테스트 계정/도메인/프로젝트를 만든다. 기존 VM/키를 변경하지 않는다. 실제 API/UI 결과를 실제 DB의 연결 관계와 대조하고 count·페이지·일반 사용자/Domain Admin/프로젝트 접근을 확인한다.

Windows 화면 도구의 데스크톱 접근이 불가능하여 작업 지침의 WSL Chromium 경로로 실제 UI를 검증한다. 고정 viewport와 fullPage=false를 사용하고 세션·비밀번호는 파일/PR에 기록하지 않는다.

## 배포와 정리

WSL ext4의 분리 작업 트리에서 변경 engine/schema·server 모듈과 UI만 빌드한다. 실행 전 3개 대상 클래스가 수정 전 빌드와 해시가 같은지 확인하고 활성 관리 JAR을 백업한다. QueryManagerImpl, VMInstanceDetailsDao, VMInstanceDetailsDaoImpl만 교체한 뒤 Mold를 재기동하고 클래스 해시·기동 로그·API 동작을 확인한다. #1370 클래스와 WEB-INF 및 기존 설정을 보존한다.

UI는 정적 파일만 백업·배포하고 자산을 먼저 복사한 뒤 index.html을 마지막에 교체한다. WEB-INF/META-INF와 config.json·기동한 PID·관리 JAR을 유지한다. 전체 Cloud/RPM 빌드는 실행하지 않는다.

검증 VM/볼륨/NIC/네트워크/키·계정·도메인·프로젝트를 해당 UUID와 소유 범위로 정리하고 API/DB 잔여 및 기존 키/VM SSH 상세 보존을 확인한다. 이슈 #1372만 검증 완료 후 닫으며 사용 VM 탭 #1377과 나머지 Epic 과제는 OPEN으로 유지한다. 삭제 정책·게스트 키 회수 및 기존 VM의 SSH 상태 보정은 범위에 포함하지 않는다.
