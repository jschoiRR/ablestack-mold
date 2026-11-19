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

# VG 02 대상 소유 범위의 그룹 후보 및 편집 보조 조회

Epic #1357 / issue #1359 / 누적 Draft PR #1364. Upstream Europa `9fa65ce2a27b977e848440cb7c787411c11d585b`가 Epic HEAD `aa6febc74609c8acac59eb94245afbab4cd48d3f`에 포함됨을 작업 시작 시 재확인했다. VG-01 계약과 서버 ACL을 유지하며 이번 변경은 UI 모듈에 제한한다.

## 소유 범위와 역할 행렬

| 화면/역할 | 조회 기준 | 저장 기준 |
| --- | --- | --- |
| 관리자 자기 VM | VM account + domainid | 선택 후보의 groupid |
| 관리자 타계정 VM | 대상 VM account + domainid | 타계정 소유 후보 groupid, admin 그룹 제외 |
| 일반 사용자 | 대상 VM account + domainid 및 기존 서버 ACL | 자기 그룹 ID, 타소유 요청은 서버에서 거부 |
| 프로젝트 VM | 대상 VM projectid | 프로젝트 그룹 ID, 동일 이름 개인 그룹 제외 |
| 제한 역할 | 위 대상 소유 범위 + listInstanceGroups API 권한 | 후보 API가 없거나 401/403/432/531이면 그룹 변경 차단 |
| VM 생성 | 최종 선택 owner(projectid 또는 account + domainid) | 기존 deploy API의 group 이름과 같은 owner |

로그인 사용자/상단 프로젝트를 VM 편집 대상의 소유 범위로 대체하지 않는다. 소유 정보가 없으면 넓은 listall 조회로 폴백하지 않는다. 프로젝트 ID가 있으면 계정 파라미터를 함께 보내지 않는다. 생성 owner가 불완전한 동안 로그인 계정으로 폴백하지 않는다.

## 후보 조회와 저장

공통 vmGroupLookup은 각 조회·문맥 변경·재시도에 세대 번호를 부여한다. 시작 즉시 이전 후보를 비우고 새 범위의 loading 상태를 게시한다. 이전 세대의 성공/실패/finally와 화면 종료 후 응답은 화면을 갱신하지 않는다. VM ID/소유 범위/템플릿/오퍼링/Zone 변경은 편집 폼과 보조 조회도 초기화한다. 생성 소유 문맥 변경은 입력한 그룹 이름을 비운다.

상태는 loading / ready / empty / forbidden / error / unavailable로 분리하고 한글·영어 안내와 오류/빈 결과의 새로고침을 제공한다. ready/empty이면서 현재 소유 범위가 일치하는 경우에만 그룹 변경을 제출한다. 실패·권한 상실에서 타계정 후보나 이름 기반 자동 생성을 허용하지 않는다. 그룹 미변경 시 다른 독립 설정의 저장은 유지한다.

조회는 500개 페이지를 끝까지 수집하고 각 후보의 ID/이름/실소유를 검증한 뒤 한 번에 게시한다. 비정상 응답·타소유 항목·중복 페이지·부분 페이지 실패는 error가 된다. 정상 빈 응답만 새 이름 입력을 허용한다. 최대 100페이지와 페이지별 20초 timeout으로 조회를 제한한다. 실패 처리에는 기존 preserveOnFailure API 경로를 사용한다.

편집은 VG-01 groupid/cleargroup 계약을 유지한다. 생성 API는 groupid를 지원하지 않으므로 검증한 소유 범위와 정확한 이름을 함께 보낸다. 권한 확인의 최종 주체는 기존 서버 ACL이며 UI 검증으로 ACL을 대신하지 않는다.

## 편집 보조 조회

템플릿과 오퍼링은 빈 결과를 객체 기본값과 empty 상태로, 실패를 error로 처리한다. 관련 정보가 없으면 동적 확장 설정을 비활성화하고 제출 단계에서도 변경을 거부한다. 오퍼링을 확인하지 못했으면 HA 변경을 제출하지 않는다. 그룹만 바꿀 때는 VG-01의 metadata-only 요청을 사용해 하드웨어·전원·볼륨 속성을 변경하지 않는다.

Zone/보안 그룹/OS 유형/네트워크/UserData 조회는 빈 결과·실패와 VM 문맥 변경을 방어한다. UserData 입력은 실제 VM의 데이터 조회와 디코딩을 완료한 뒤 열어 조회 실패를 빈 데이터로 덮어쓰지 않는다. 화면 종료 시 보조 조회의 세대를 무효화한다. 폼 초기화 이전의 VM 문맥 변경과 생성 보조 검증 대기 중의 소유 문맥 변경은 제출 직전에도 재검증한다. 빈 SYSTEM Blank Template은 정상적으로 편집할 수 있으며 그룹 작업의 실패로 해석하지 않는다.

## 검증·배포 계획

소유 범위·권한·빈/실패·다중 페이지·늦은 응답·재시도·dispose·설정 변경 차단을 집중 Jest 테스트로 검증하고 변경 파일 lint/UI production build를 WSL ext4에서 실행한다. Java/API/DB 스키마 변경은 없으므로 Maven 모듈 재빌드 대상이 없다. 전체 Cloud/Actions 빌드는 실행하지 않는다.

31/32에서 전용 계정/프로젝트/역할/VM/그룹을 만들고 실제 UI 후보와 제출 요청, API 응답, DB 매핑을 비교한다. 기존 사용자 VM은 변경하지 않는다. 정상 소유 후보·동일 이름 타소유·일반 사용자·프로젝트·제한 역할을 양쪽에서 확인한다. 지연/실패/빈 메타데이터는 집중 UI 시험에서 주입하고 실제 서버 동작과 구분해 기록한다.

배포 전 공간·현재 bundle·서비스 PID·WEB-INF/config/JAR 해시를 확인한다. UI 정적 자산만 교체하고 서버 JAR/PID와 WEB-INF/config를 보존한다. 31 대표 환경 검증 후 32에 확대하고 파일 해시·HTTP 200·콘솔·UI·소유 계약을 다시 확인한다. 전용 시험 자원은 정확한 ID로 정리하고 완료 근거를 이슈와 PR에 기록한다.
