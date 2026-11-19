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

# 사용자 데이터 라이브러리 등록 검증 설계 — UD-01 / #1382

상위 Epic: #1381. 기준 upstream Europa: `4bd1a2cff50c83089dc23ff1c83def631a343e3d`.

## 등록 계약

1. 기존 `getOwner`의 권한·계정/도메인/프로젝트 소유자 확정을 먼저 수행한다.
2. 이름 앞뒤 Unicode 공백을 제거하고 1~255자를 허용한다. 본문의 앞뒤 공백·줄바꿈은 제거하지 않으며, 디코딩한 본문 전체가 공백인 경우만 등록을 거절한다.
3. 일반 모드는 입력을 항상 UTF-8로 인코딩한다. Base64처럼 보이는 일반 문자열을 자동 판별하지 않는다. Base64 모드는 기존의 임의 바이트를 지원하고 UTF-8 텍스트로 제한하지 않는다.
4. 허용 Base64 표기는 패딩 생략, 표준/URL-safe alphabet, 공백/탭/CR/LF, 한 단계 percent encoding이다. 잘못된 percent escape, 중간 패딩, 3개 이상의 패딩, 불가능한 길이는 검증 오류로 반환한다. URL 표기의 literal `+`를 보존한다.
5. 디코딩 후 표준 alphabet과 padding을 가진 Base64로 재인코딩한다. **정규화한 Base64의 ASCII 바이트 수**에 GET 4096/POST 1048576 및 동적 `vm.userdata.max.length`를 적용한다. 이 구성값은 원문 UTF-8 바이트 제한이 아니다. 기본값 32768은 원문 24576바이트를 허용한다.
6. 변수 키는 대소문자를 구분하며 앞뒤 공백을 제거한다. `[A-Za-z_][A-Za-z0-9_.-]*`를 허용하고 빈 항목·중복·가상 라우터 메타데이터 예약어를 거절한다. 변수 선언은 게스트 실행 여부의 증거가 아니다.
7. 소유 계정/도메인별 DB 기반 `GlobalLock`으로 이름·정규화 본문 중복 검사와 저장을 직렬화한다. 같은 소유자의 같은 이름 또는 같은 최종 본문은 거절하며, 다른 계정/프로젝트에는 같은 이름·본문을 허용한다. 기존 레코드의 내용/소유자는 수정하지 않는다.
8. `user_data` 컬럼의 대소문자 비구분 collation을 고려해 SQL 후보의 최종 Base64 문자열을 Java에서 정확히 비교한다. `EA==`와 `eA==`처럼 바이트가 다른 데이터는 별개로 저장하고, 첫 후보가 달라도 뒤의 정확한 중복을 놓치지 않는다. DB 스키마 변경은 필요 없다.

## API 및 UI

- `registerUserData`의 등록 전용 blank/변수 검증을 분리해 VM 사용자 데이터의 `null`/blank 해제 계약과 CNI 등록 정책을 바꾸지 않는다.
- 공통 `validateUserData`에는 잘못된 URL 오류 변환, Base64 형식 및 최종 인코딩 길이 검사를 적용한다. VM·CNI의 기존 정상 표기와 바이트 보존 경로를 회귀 확인한다.
- `listCapabilities.userdatamaxlength`에 동적 한도만 노출한다. 관리자·일반 사용자 모두 등록 폼을 열 때 새 값을 읽는다. 구버전/권한/일시 조회 실패에는 POST의 1MiB 상한으로 검사하고 최종 정책은 서버가 검증한다.
- 기존 Ant Form·TooltipLabel·i18n·공통 오류/테마 스타일을 재사용한다. name/body/params 필드 검증을 수행하고 중복 submit을 막는다. 서버 오류 시 입력과 소유 범위를 유지하며 성공 시에만 목록을 갱신하고 닫는다. 민감한 본문을 console에 출력하지 않는다.
- 모달 쉘 전환과 전체 목록/탭 UI 표준화는 #1383/#1389의 별도 범위다. 본 변경은 기존 레이아웃을 보존하고 입력 검증·오류 상태를 양쪽 테마에서 확인하며, 전체 적합성은 #1392에서 검증한다.

## 검증과 배포

- Java: API 모듈 관련 테스트, 공통 Base64/URL/GET/POST 경계·바이너리 회귀, 등록 소유자/정규화 중복/잠금 성공·실패·예외 정리, 이름/본문/변수 검증.
- UI: UTF-8/일반 텍스트 오인식/허용 Base64/잘못된 입력/크기·한글 경계/변수/서버 오류 보존/중복 제출, 변경 파일 lint 및 production UI 빌드.
- 31번: UI 실제 등록/거절/오류 수정 재시도와 API·DB 상태, 32768 경계 및 한시적 구성 변경 후 복원, 같은 소유자 동시 요청·다른 계정/프로젝트·도메인 관리자, 전용 시험 자원 삭제 및 기존 라이브러리 동일성.
- 변경 Maven 모듈만 WSL ext4에서 빌드한다. 테스트 서버의 기존 통합 JAR을 백업하고 검증한 변경 클래스만 교체한다. UI는 정적 파일만 갱신하고 `WEB-INF`/`META-INF`/환경 설정을 보존한다. 전후 해시·서비스·HTTP·실제 API/화면을 확인한다. 전체 Cloud 빌드와 qemu 배포는 수행하지 않는다.
