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

# SSH 키 쌍 UUID 조회 조건 설계 — Epic #1369 / #1370

## 문제와 계약

`listSSHKeyPairs`는 UUID 형식의 `id`를 공개하지만 조회에 사용하지 않는다. UUID는 기존 API 프레임워크가 SSHKeyPair의 내부 Long ID로 변환한다. 서버는 그 내부 ID를 기존 ACL·필터 검색의 교집합 조건으로 적용해야 한다.

- `id` 지정: 접근 가능한 해당 키만 반환한다. 일치하면 count=1이며 페이지 범위에 따른 rows와 전체 count의 기존 의미를 보존한다.
- `id` 생략: 기존 전체 목록·이름·지문·키워드 조회 동작을 보존한다.
- 이름·지문·키워드·account/domain/project와 함께 지정: 기존 검색 조건에 AND로 결합한다.
- 존재하지 않는 UUID: 기존 UUID 변환 계층의 명시적 오류를 유지한다.
- 존재하지만 접근 불가능하거나 소유 범위가 불일치하는 UUID: 기존 ACL 결과에 따라 빈 결과 또는 명시적 접근 오류를 반환한다. 다른 키로 대체하지 않는다.

## 구현

`ListSSHKeyPairsCmd.getId()`를 추가한다. `ManagementServerImpl.listSSHKeyPairs()`에서 값을 읽고 기존 `buildACLSearchParameters` → `buildACLSearchBuilder` → `buildACLSearchCriteria` 경로로 만든 SearchCriteria에 `id EQ <internal ID>`를 추가한다.

별도 `findById()`로 검색 경로를 우회하지 않는다. DAO, DB schema, 공개 API 파라미터, 응답, 기본 정렬, count와 pagination의 계약을 변경하지 않는다. UI 경로 통일·대상 불일치 차단은 #1371의 후속 범위다.

## 검증

`ManagementServerImplSSHKeyPairTest`는 실제 DAO의 SearchBuilder/SearchCriteria를 사용해 SQL 조건과 bind 값을 확인하고 DAO 실행은 mock으로 분리한다. UUID 대응 ID와 기존 ACL·이름·지문·키워드 조건의 결합, ID 생략, 빈 결과, pagination/count, account/project ACL 인자 전달을 확인한다. 기존 ManagementServerImplTest도 함께 실행한다.

31번 클러스터에서는 관리자·일반 사용자, 같은 이름의 서로 다른 계정·도메인·프로젝트 키, 정상/불일치 필터, 미존재/접근 불가 UUID, 페이지 조회를 API로 검증한다. 실제 UI에서 UUID 상세와 코멘트가 있는 키의 진입 대상을 확인한다. 테스트 개체를 정리하고 기존 키 UUID·지문 보존을 확인한다.

## 배포

WSL ext4의 dhslove 작업 트리에서 api/server 변경 모듈만 빌드한다. 서버의 현재 클래스와 수정 전 빌드 클래스의 일치를 먼저 확인한다. 활성 관리 JAR 전체를 백업하고 두 변경 클래스만 교체한 뒤 Mold를 재시작한다. webapp·WEB-INF·UI 정적 파일은 보존한다.

배포 후 클래스 SHA-256, 서비스 상태, `/client/` HTTP 200, 관리 로그와 실제 API/UI 응답을 함께 확인한다. 실패 시 백업 JAR로 복구한다. 전체 Cloud 빌드·RPM 배포·다른 Epic 하위 이슈 구현은 이 변경의 범위가 아니다.
