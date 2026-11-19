<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License for the specific language governing permissions and limitations. -->

# UD-10 사용자 데이터 복제·버전·변경 정책 검증

대상: upstream [#1391](https://github.com/ablecloud-team/ablestack-cloud/issues/1391), Epic [#1381](https://github.com/ablecloud-team/ablestack-cloud/issues/1381), Draft PR [#1393](https://github.com/ablecloud-team/ablestack-cloud/pull/1393). 검증일 2026-10-16, 테스트 클러스터31.

## 구현 결과

상세의 **복제하여 새 버전 등록**은 기존 RegisterUserData/MoldDialog와 UserDataContent를 재사용한다. 원본 UUID·소유 account/domain 또는 project를 동결하고 이름·정규 Base64·변수 선언을 채운다. 원본/편집 본문 미리보기, 바이트 차이·변수 변경, 적용 대상 링크와 전환·복원 안내를 제공한다. 기존 VM·템플릿·ISO를 자동 변경하지 않는다.

동일 소유자의 동일 바이트 본문은 이름·변수만 달라도 중복이다. 새 UUID는 독립 버전이며 자동 버전 순서나 updateUserData API를 만들지 않는다. 초기 Base64는 BOM·CRLF·바이너리를 보존하고 명시적인 원문 편집만 LF로 정규화한다. 동적 API 발견의 listUserData/registerUserData/listAnnotations/addAnnotation과 실제 서버 ACL을 함께 따른다.

출처는 USER_DATA 댓글에 원본/대상 UUID·바이트 수만 기록한다. 기존 댓글 API의 255자 제한 안에 들어가며 이번 실제 메모는 최대 151자다. 이름·변수는 해당 버전의 상세·복제 미리보기에서 확인한다. 댓글은 삭제·가시성 변경 가능하므로 불변 감사 로그나 자동 적용 이력으로 취급하지 않는다. 부모나 순서를 추정하지 않는 빈 상태도 표시한다.

등록 응답 유실 시 입력을 잠그고 같은 소유자·정확한 이름·본문·변수의 결과부터 조회한다. 출처 저장만 실패하면 생성 성공과 구분하고 댓글만 재시도하거나 메모를 복사/다운로드해 수동 복구한다. 결과/참조 링크는 대기 중 이동을 막고 작업창을 닫은 다음 대상 탭으로 이동한다. 상세 경로의 메뉴 메타데이터를 사용해 CNI로 잘못 분기하지 않는다.

## 실행 검증

- **Jest 6 suites / 174 tests**, 변경 파일 ESLint, production UI build 통과. 실제 공통 컴포넌트 등록을 사용한 상세 경로/빈 상태 렌더링, 소유 범위·중복·응답 유실·메모 길이·지연/권한/해제·포커스·링크 순서와 공유 CNI 계약을 포함한다.
- **정량 90항목 PASS**, 최종 화면 53개. 일반/다크 각각 1440×1000,1280×720,390×844,390×480에서 복제 대화상자와 버전 탭을 확인했다. 32px 하단 버튼은 같은 배포본의 VM 대화상자와 일치한다. 중앙 오차와 스크롤 전후 Header/Footer 차이는 1px 이내, 스크롤은 Body 안에 한정된다.
- 대기 중 Escape/닫기·중복 제출·결과 링크를 차단했다. 오류 후 입력·소유 범위를 보존하고, 성공 영수증은 닫기 버튼에 포커스를 돌려준다. 결과 링크와 참조 링크 이동 후 남은 대화상자 수는 0이다. 공유 CNI 등록 화면도 별도로 열어 올바른 제목/필드/버튼을 확인했다.
- 측정한 유휴 본문/이름·본문 입력 텍스트·placeholder·활성 footer 대비는 4.5:1 이상, 이름·본문 입력 경계/포커스는 3:1 이상이다. 본문 대비는 일반 14.68:1, 다크 13.35:1이다. 모든 popup/hover/비활성·확대 상태의 최종 통합 판정은 UD-11 범위다.
- 원본 미리보기의 실제 복사·다운로드 **12,012바이트**가 BOM·CRLF·한글·emoji·긴 행을 포함해 원본 해시와 정확히 일치했다. 본문은 실행되지 않는다. 바이너리는 원문 편집을 막고 Base64 바이트를 보존했다.
- UI 복제 **6건**: 관리자 v2/v3·바이너리, 일반 사용자, 도메인 관리자, 프로젝트. 각 UUID·소유자·본문 SHA256/크기·변수와 출처 메모를 API/DB로 대조했다. 일반 사용자 원본 SOURCE_VAR가 유지되고 NEW_VAR가 추가됐다. 프로젝트 응답의 account 생략을 임의로 다른 소유자로 해석하지 않는다.
- 실제 동시 이름 경쟁으로 서버의 431 거절과 충돌 확인 후 입력 잠금 해제를 확인했다. 실제 생성의 200 응답을 503으로 대체한 유실 시험에서도 객체 수는 1이며 조회 복구 후 재등록하지 않았다. 출처 요청 503에서는 생성1/메모0을 API로 확인하고 댓글만 재시도했다. 출처 응답만 유실된 바이너리 시험은 이미 저장된 동일 메모를 찾아 중복 기록하지 않았다. **503/원본403은 CDP로 통제한 장애 주입**, 중복431·실제 권한432·원본 삭제는 실제 서버 결과다.
- 원본을 삭제한 후 제출한 복제는 생성되지 않았다. 원본 403 조회는 빈 데이터와 구분되며 제출이 비활성화되고 재시도로 회복했다. API 권한 제한 역할에서는 복제 작업이 노출되지 않고 실제 addAnnotation도 432로 거절됐다.

## 기존 참조와 적용·복원

복제 직후 기존 정지 VM5대의 참조·변수·저장 본문과 전용 템플릿의 UUID/정책이 검증 전과 동일했다. 이후 전용 관리자 VM과 템플릿에서 새 UUID로 명시적으로 전환하고 이전 UUID/정책으로 복원했다. 원본 VM은 API 저장 본문까지 정확히 복원됐으며 다른 VM은 변경하지 않았다.

BOM 없는 새 v2/v3를 사용해 APPEND VM1대를 실제 생성했다. 병합 저장본은 단일 라이브러리와 다르며 VM UI도 차이로 표시한다. BOM 시작 원본의 APPEND는 기존 MIME 인식 제한으로 530을 반환하고 VM을 만들지 않았다. 이는 원본을 몰래 정규화하는 대신 UI/설계에서 안내한 지원 형식 정책이다. standalone 원본의 BOM 바이트는 유지된다.

적용은 기존 VM 사용자 데이터 재설정과 템플릿/ISO 연결 작업에서 별도로 수행한다. 되돌릴 UUID·당시 변수·템플릿 정책·실제 저장 본문을 기록하고 이전 버전을 보존해야 한다. 참조 ID만으로 본문 동일함을 판단하지 않으며 게스트에서 이미 실행된 스크립트의 효과나 삭제된 UUID를 복구한다고 주장하지 않는다. 이번 VM은 전용 정지 VM으로 공급자 전달/게스트 실행 검증과 구분한다.

## 배포와 정리

- 최종 UI 소스 `ca99503ac2a511638baef74a60346ce4f0bb70f0`, `js/app.c9e3887d.js`, SHA256 `7c3d44887b1960b193d88fa0a9ecc94cba5fa9996101c4d609bd703ec042bf24`. 정적 870개 해시와 실제 브라우저 app 로드를 확인했다.
- 기존 JAR `bf53fc37aee0431dd53377526523fcfa2b650e1a32ce9524e243438bab3be7c1`와 12클래스, WEB-INF inode67111992·config·META-INF 상태, Mold PID1247065를 보존했다. mold active, /client/ HTTP200이다. 웹앱 전체 교체/삭제 없이 정적 파일만 갱신했다.
- 최종 직전 백업 `/root/ud1391-ui-20261016-213653`, UD-09 원본 정적 백업 `/root/ud1391-ui-20261016-210017`. 빌드/백업은 되돌리기 자료로 남겼다.
- 정리 직전 활성 전용 데이터: 라이브러리14, VM/볼륨6, 네트워크5, 템플릿1, 계정3·프로젝트/내부계정/도메인/역할 각1, 출처 메모6. 모두 정리했고 **API VM/네트워크0, DB11종 활성 잔여0**, 운영 라이브러리 전체 API 응답은 검증 전과 동일하다. 제거 이력 행은 정상적으로 남는다.
- 관리자/기본 보기/다크를 복원하고 클립보드·다운로드2개·캐시·포커스 에뮬레이션·viewport·Fetch 계측을 정리했다. 에이전트 탭만 닫았다. 기존 FT 작업 트리의 3개 수정 파일과 HEAD는 보존했다.

## 한계와 다음 이슈

완전한 네트워크 관측 구간 **14개 중 12개 JS 예외0**이다. 프로젝트 진입/종료 각1건은 기존 공통 CanceledError/ERR_CANCELED이며, app.c9e3887d.js의 cancel(1:687871) → beforeRouteLeave(1:555712) 전체 스택을 남겼다. 새 등록/출처/조회 동작은 통과했지만 전체 세션 예외0이나 전체 UI 최종 적합성으로 표현하지 않는다.

Java 변경이 없어 이번 Maven 빌드는 없으며 전체 Cloud/GitHub Actions 빌드·qemu/호스트 배포·PR 병합을 실행하지 않았다. 번들 크기 경고2/Browserslist/Jest forceExit 안내는 집중 통과와 별개다. CI 전체 성공으로 해석하지 않는다.

다음은 **#1392(UD-11/P1)**: 모든 목록·상세·7개 탭/콘텐츠·등록/삭제/선택/재설정/복제 대화상자, 일반/다크·1600/1440/1280/1024/390·200% 확대·popup/hover/focus/비활성·역할/프로젝트를 최종 통합 점검한다. 공통 취소 예외를 함께 검토한다. #1391 완료 시 Epic은10/11이며 #1392·Epic·PR Draft는 유지한다.

## 근거

[설계](design.ko.md) · [정량 결과](qualification.json) · [API/DB 복제](clones-final.json) · [DB 가동 상태](db-runtime.json) · [기존 참조](references-after-clone.json) · [전환/복원](switch-rollback.json) · [APPEND](append-policy.json) · [배포](runtime-final.json) · [해시 목록](ui-manifest.json) · [정리](cleanup.json) · [정리 DB](db-after.json) · [대비](contrast.json) · [복사/다운로드](exports.json)

초기 발견·수정 근거는 initial/의 상세 경로/CNI 분기, 댓글 길이, 영수증 링크 이동 자료다. 최종 정량 결과는 루트의 최종 배포본 자료만 사용한다. 자격 증명·운영 본문 baseline·런타임 제어 상태는 게시하지 않는다.
