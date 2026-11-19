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

# SSH 키 이름 원문 표시 설계 (#1374)

Epic #1369 및 기존 PR #1378에 #1370~#1373 다음 변경으로 누적한다. 최신 upstream Europa는 a5a811387476ac87a8774fc729cc68021104684a이며 WSL ext4 분리 작업 트리를 재사용한다.

## 원인과 변경

ListView의 SSH name cell은 코멘트 없는 이름을 소문자로 바꿔 $t에 전달한다. 혼합 대소문자가 변형되고 label.cancel 등은 번역 문구로 바뀐다. SSH 전용 cell에서 text를 보간해 원문으로 표시한다. 코멘트 링크 및 UUID/account/domain/project 파라미터는 그대로 유지하며 데이터·검색·정렬·삭제 매핑을 변경하지 않는다. 보간을 사용해 HTML 모양의 이름도 텍스트로 표시한다.

추가로 InfoCard 제목과 DetailsTab name/type 처리가 USER.LOGIN 등의 이벤트 이름을 모든 리소스에서 번역한다. SSH 제목과 SSH name 필드에 한정해 이 이벤트 번역을 제외한다. 다른 리소스 이벤트 이름/타입과 메뉴·컬럼/필드의 정적 번역은 유지한다.

단건 삭제 대화상자의 대상 요약 개선은 #1376 범위다. 이번 작업은 현재 제공하는 일괄 선택 대상 표의 원문 표시 및 실제 단건/일괄 삭제 요청 이름·소유 범위가 원문 데이터와 일치함을 검증한다. 추가 삭제 UX나 사용 VM 탭은 구현하지 않는다.

## 검증과 배포

테스트는 코멘트 유무, 혼합 대소문자/한글/기호/공백/번역 키/이벤트 이름/HTML 모양 이름, embedded SSH 목록, UUID 및 이름 경로, 원본 데이터/정렬 이벤트, 이벤트 리소스 번역을 확인한다. 이전 SSH/VM/생성 오류 복구/요청 정책 회귀와 lint·생산 UI 빌드·변경 텍스트 Apache RAT를 수행한다. UI만 변경하며 Java/Maven/전체 Cloud/RPM/전체 Actions 빌드는 실행하지 않는다.

31번 API로 새 키 7개와 코멘트 2개, 임시 계정/도메인/프로젝트를 생성한다. 같은 이름을 다른 namespace에 준비하고 기존 UI의 표시 변형을 먼저 재현한다. 수정 UI의 실제 목록·상세 제목/이름 필드·검색·코멘트 링크·정렬·단건/일괄 삭제 대상과 실제 API/DB를 대조한다. 일반 사용자 자기 키와 프로젝트 범위도 확인한다. 서버의 기존 검색/정렬 규칙을 바꾸지 않고 UI 표시로 원본이 변형되지 않는지 판단한다.

정적 파일만 assets 먼저/index 마지막으로 배포하고 WEB-INF/META-INF/config.json·활성 관리 JAR·Mold PID를 보존한다. 임시 개체는 UUID 및 소유 범위로 확인한 뒤 정리하고 기존 키와 VM SSH 상세 해시를 대조한다. 비밀번호·세션·개인 키는 보고서/PR에 기록하지 않는다. 검증 후 #1374만 completed로 닫고 Epic은 5/8 완료로 OPEN을 유지한다.
