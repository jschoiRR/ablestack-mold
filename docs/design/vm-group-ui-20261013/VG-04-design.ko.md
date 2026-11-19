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

# VG-04 검색·갱신 정합성 설계

2026-10-13 / Epic #1357 / issue #1361 / 누적 Draft PR #1364.
기준은 최신 Upstream Europa 9fa65ce2a27 및 VG-03 완료 HEAD 4070362cda1이다.

## 조회 계약

listInstanceGroups의 실제 ListVMGroupsCmd 및 QueryManagerImpl을 대조했다. name은 DB 동일 이름 검색이고 keyword는 이름 또는 UUID의 부분 검색이다. 계정·도메인·프로젝트와 listall/isrecursive는 서버 ACL을 유지한다. 페이지 조회는 서버 id 오름차순이며 서버 정렬과 Zone 필터는 지원하지 않는다. 별도 Zone API 요구는 없으므로 그룹 화면에서 Zone을 제거한다. AutoScale 등 다른 자원의 Zone 필터는 유지한다.

그룹 경로의 URL만 정리한다. API 필드 name/keyword/account/domainid/projectid/id/isrecursive/listall/page/pagesize와 UI 상태 q/tab/action/dataView/sortkey/sortorder를 구분한다. 미지원/배열/잘못된 UUID·페이지·불리언은 제거하고 제거 알림을 표시한다. q와 keyword를 일치시켜 URL 재진입의 검색 입력을 복원한다. 기존 이름의 공백을 검색 단계에서 자동 정규화하지 않는다.

정렬은 기존 Mold 목록과 같이 현재 페이지에 적용한다. 정렬 필드/방향을 URL과 AntD column sortOrder에 유지하고 서버에 미지원 sortkey/sortorder를 보내지 않는다. 검색·페이지·탭·소유 범위의 지원된 상태는 갱신으로 소실되지 않는다.

## 조회 상태와 문맥

그룹 경로에서 getAPI preserveOnFailure 및 20초 timeout을 사용한다. 일반 네트워크/서버 실패와 권한 거부는 화면이 처리하며 현재 세션 401의 기존 인증 처리만 유지한다. 내부 조회 상태는 loading/refreshing/ready/empty/error/forbidden/missing으로 구분한다. 사용자 지시에 따라 정상 조회·갱신·0건에는 상단 리본이나 성공 확인 시각을 표시하지 않는다. 기존 목록과 갱신 버튼의 로딩 표시를 사용하고, 실패·권한 거부·없어진 대상에만 기존 공통 목록과 같은 문장형 안내·재시도를 표시한다. 이전 결과를 유지한 실패에서만 마지막 성공 확인 시각을 함께 표시한다. 같은 문맥의 실패는 이전 성공 데이터·시각을 유지하지만 작업·선택을 막는다. 권한 거부에서는 데이터와 선택을 제거한다. 최초 실패와 정상 0건을 같은 빈 목록으로 안내하지 않는다.

페이지의 필수 응답 구조·UUID/이름·중복 ID·총 개수를 확인한 뒤 성공 시각을 갱신한다. 불완전한 응답은 오류이며 이전 결과를 성공/0건으로 바꾸지 않는다. 상세 대상이 사라진 경우 자체 missing 상태로 표시하고 오래된 상세 작업을 차단한다.

문맥은 route 대상, 지원 검색/페이지, 사용자·로그인 토큰·선택 프로젝트·전체 프로젝트 조회 모드로 결정한다. 문맥 변경 시 이전 데이터·시각·선택을 제거하고 열려 있는 그룹 작업을 닫는다. 요청 generation 및 문맥 비교로 늦은 성공/오류/완료가 새 화면을 덮어쓰지 않게 한다. 탭과 페이지 내 정렬은 데이터 문맥을 바꾸지 않으므로 동일 데이터 갱신과 상세 탭을 보존한다.

## 영향 및 검증

AutogenView/공통 ListView·ResourceView를 재사용하고 그룹 경로의 분기와 전용 상태 컴포넌트를 추가한다. 다른 자원의 기존 callAPI/조회 동작과 VM 스냅샷 상태 처리를 유지한다. VM 멤버 UI와 집계 API는 VG-05 범위다. Java/DB 변경이 없으므로 UI 모듈만 WSL ext4에서 빌드한다. 전체 Cloud 빌드/Actions 수동 실행은 하지 않는다.

새 집중 검사는 URL/서버 필터 계약, 초기/갱신/정상 0/실패/권한/불완전 응답, 확인 시각과 작업 차단, 실제 AutogenView 요청 메서드의 페이지·소유·세션·대상 경쟁, 검색/정렬/탭 유지와 다른 자원 회귀를 포함한다. 기존 VG-01~03 UI 집중 검사도 수행한다.

31 대표 환경 배포·검증 후 32로 확대한다. 기존 배포/백업 데이터는 식별·정리하고 최신 롤백 백업을 확보한다. 활성 webapp의 WEB-INF/config/META-INF와 management JAR/PID를 보존해 정적 UI만 갱신한다. 산출물 해시·service active·HTTP 200·실제 API/브라우저를 확인한다. 전용 계정/프로젝트/빈 그룹만 사용하고 기존 VM·볼륨을 조작하지 않는다.

상세 자식 경로는 route.name이 없으므로 그룹 분기는 목록·상세 공통의 route.meta.name으로 판단한다. 상세 코멘트 탭의 UI query는 API에서 제외하며 누락/403/실패도 그룹 전용 상태로 처리한다. 실제 unnamed 상세 경로를 집중 테스트한다.
