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

# SSH 키 등록 실패 후 폼 보존 설계 (#1373)

Epic #1369의 기존 PR #1378에 #1370~#1372 다음 변경으로 누적한다. 최신 upstream Europa는 a5a811387476ac87a8774fc729cc68021104684a이며 분리된 WSL ext4 작업 트리를 재사용한다.

## 원인과 변경

CreateSSHKeyPair의 등록 finally가 성공/실패 모두에서 목록 갱신과 closeAction을 실행해 입력값과 선택 범위를 잃는다. 성공 then에서만 성공 메시지·목록 갱신·닫기를 수행한다. catch에서는 오류를 표시하고 폼/도메인 선택/계정/전역 프로젝트를 재초기화하지 않는다. finally는 loading 해제만 담당한다.

loading을 비동기 폼 검증 전에 설정해 validate가 대기 중인 이중 클릭/키보드 제출도 차단한다. 검증 실패와 API 실패 모두 loading을 해제해 수정·재시도가 가능하다. 검증 오류는 해당 필드로 스크롤하고 기타 오류는 알림으로 전달한다. @finish의 폼 값, Ctrl+Enter 이벤트, 직접 호출을 모두 처리하도록 preventDefault는 가능한 경우에만 실행한다.

새 키 생성은 기존 API와 개인 키 결과 화면·copy/download/close를 유지한다. 생성 성공 때만 목록을 갱신하며, 생성 실패 시에도 원래 입력을 유지한다. 생성 결과 화면에 진입한 뒤 반복 제출을 차단한다. 생성/등록 모드 분리·권한 확장·대화상자 표준 전환은 #1375 등의 후속 범위이며 이번 변경에서 배치를 바꾸지 않는다.

실제 브라우저 연결 중단 시험에서는 공통 request 처리기가 로그아웃/로그인 이동을 실행해 폼을 잃는 추가 경로를 발견했다. 생성·등록의 두 postAPI 호출에 기존 preserveOnFailure 옵션을 명시한다. 이 옵션은 해당 폼이 통신/서비스 오류를 처리하게 하고 실제 현재 세션의 401 인증 만료는 기존 로그아웃 처리를 유지한다. 공통 요청 정책이나 다른 API는 변경하지 않는다. API 인자로 이 내부 옵션을 전송하지 않는 기존 규칙과 이전 세션의 응답 격리도 회귀 검증한다.

## 검증과 배포

단위 테스트는 invalid/duplicate/transport 등록 실패, 입력/도메인/계정/프로젝트 보존, 재시도 성공 순서, 비동기 검증 및 API 대기 중 중복 제출, 검증 실패, 새 키 생성의 실패/성공과 개인 키 결과 동작을 확인한다. 기존 SSH/공통 목록·상세 회귀를 함께 실행하고 변경 파일 lint·UI 생산 빌드·변경 텍스트 RAT를 수행한다. Java/DB 스키마는 변경하지 않으므로 Maven/전체 Cloud/RPM/전체 Actions 빌드는 실행하지 않는다.

31번 현재 배포 UI에서 invalid 공개 키의 431 오류 뒤 폼 소실을 먼저 재현한다. 수정 UI에서 실제 invalid/중복 실패→값 보존→수정→등록 성공, 선택 도메인/계정 및 프로젝트 범위, loading 해제와 중복 실행 방지, 새 키 생성·개인 키 결과를 검증한다. 통신 오류와 요청 대기 상태는 실제 브라우저 요청에 제한적으로 주입해 명확히 구분해 보고한다. 세션/비밀번호/개인 키는 파일이나 PR에 남기지 않는다.

UI 정적 파일만 assets 먼저/index 마지막 배포하고 WEB-INF/META-INF·config.json·#1370/#1372 활성 JAR 및 Mold PID를 보존한다. 임시 키·계정·도메인·프로젝트만 해당 UUID/범위로 정리하고 기존 전체 namespace 키와 서비스/DB/API 상태를 확인한다. 증거를 남긴 뒤 #1373만 completed로 닫고 Epic은 4/8 완료로 유지한다.
