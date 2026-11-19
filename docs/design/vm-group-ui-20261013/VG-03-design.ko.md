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

# VG 03 이름·오류·삭제 안내 설계

2026-10-13 / Epic #1357 / issue #1360 / 누적 Draft PR #1364.
최신 Upstream Europa 9fa65ce2a27 및 VG-01·02 완료 HEAD 6428ddc8eb7을 기준으로 dhslove Epic 작업 트리에서 진행한다.

## 이름 계약

31/32 로컬 cloud DB의 실제 instance_group.name은 varchar(255), utf8mb4_0900_ai_ci다. 최초 생성 스키마의 varchar(255)와 길이를 대조했다. 스키마/기존 데이터 일괄 변경은 없다.

- 새 생성·새 이름·VM 이름 기반 자동 생성은 같은 경계 Unicode whitespace 집합을 제거한 뒤 1~255 UTF-16 단위를 허용한다. 한글/ASCII는 1, 이모지 등 보충 문자는 2단위다. 내부 일반 공백·한글·대소문자는 보존한다. UI와 Java는 동일 집합/길이 규칙을 사용한다.
- 실제 API의 기본 STRING 상한 255 UTF-16 단위가 정규화보다 먼저 실행됨을 31에서 확인했다. 그룹 이름에만 원본 입력 상한 1024 UTF-16 단위를 명시하고 UI/서버도 같은 상한을 적용한다. 다른 API 파라미터의 기본 길이 및 공통 ASCII 제어문자 거부는 유지한다.
- InstanceGroupVO의 기본 Column.length=255와 GenericDaoBase의 String.substring(0, length) 바인딩을 대조했다. 최초 code point 설계로 255 이모지를 제출하면 저장 이름이 잘리는 것을 31 실제 API/DB 경로에서 확인했다. 저장 계약을 바꾸는 대신 UI/서버를 기존 ORM과 같은 255 UTF-16 단위로 맞춰 잘림 전에 거부한다. 허용되는 보충 문자 이름은 요청·응답·DB의 문자열 동일성으로 검증한다.
- 중복은 최종 계정/프로젝트 accountId의 DB 비교 규칙을 사용한다. 서버가 대소문자·악센트·trim 후 충돌을 판정한다. 다른 소유 범위의 같은 이름은 허용한다.
- 이름 미전달 update는 유지한다. 정확히 같은 기존 이름의 update와 기존 후보 이름 선택은 과거 패딩을 보존한다. 기존 이름을 자동 일괄 정규화하지 않는다. 다른 새 이름을 입력할 때 새 규칙을 적용한다.
- VM 그룹 입력의 빈 값은 VG-01 명시적 해제 계약을 유지한다. 공백만 있는 새 이름은 해제로 취급하지 않는다. 그룹 UUID 선택은 기존 그룹의 이름을 다시 생성하지 않는다.
- create/rename/이름 기반 연결은 같은 owner lock을 획득한다. 이름 변경은 owner 행과 활성 그룹 행을 다시 확인하고 트랜잭션에서 중복을 확인한다. 삭제·소유 변경 경합이면 원 이름을 보존하고 실패한다.
- VM 생성의 그룹 이름은 VM·볼륨 자원 예약 전 검사하며 자동 연결에서도 동일 정책을 재검사한다.

## UI 오류와 삭제

기존 AutogenView의 그룹 작업에만 이름 validator·전송 정규화·한글/영어 오류 변환을 추가한다. API 실패에서 폼/선택 대상을 닫거나 입력을 지우지 않는다. 중복·길이·빈 값·없어진 대상·ACL·일반 실패를 구분하며 Account 직렬화 같은 서버 내부 내용을 사용자 안내에 넣지 않는다. 동일 이름의 편집은 POST 없이 미변경으로 안내한다.

삭제는 상세 작업 메뉴·행 우클릭 일괄 동작을 유지한다. 공통 그룹 삭제 요약에서 선택 개수와 이름/UUID/계정 또는 프로젝트/도메인을 표시한다. 선택 대상을 snapshot으로 보존하고 일괄 요청도 그 UUID를 사용한다. 이름은 텍스트로 escape해 표시한다.

그룹 소속 연결 및 코멘트 제거, VM/볼륨 보존과 VM 전원/배치 불변, 되돌릴 수 없는 그룹 삭제를 명시한다. VM 수를 추가 조회하지 않으므로 N+1 및 권한 밖 집계가 없다. 멤버 집계와 탭은 VG-05 범위다.

## 검증 및 배포

Java 이름·생성/변경 잠금·중복·실패 보존·기존 소속 계약, API 그룹 이름 파라미터 입력 상한, UI 이름 경계·요청/오류·입력 보존·선택 snapshot/escape·기존 작업 회귀를 집중 검사한다. 변경 api/server Maven 모듈과 UI 모듈만 WSL ext4에서 빌드한다. 전체 Cloud 빌드를 실행하지 않는다.

31 대표 환경 확인 후 32로 확대한다. 기존 배포/백업 정리는 식별된 이전 작업 데이터로 한정한다. 활성 webapp의 WEB-INF/config/META-INF를 보존하고 변경 관리 JAR 클래스와 정적 UI 파일을 해시로 검사한다.

양쪽 실제 UI에서 공백·trim·한글·255/256·중복 생성/변경·입력 보존·미변경·실패·단건/일괄 삭제 취소와 완료를 확인한다. 전용 멤버 포함 그룹의 매핑/코멘트 삭제와 전용 VM의 상태/전원/호스트/볼륨 속성 전후 동일성을 API/DB로 대조한다. 시험 자원은 정확한 ID로 정리하고 실제 범위와 전체 CI 제한을 별도 보고한다.
