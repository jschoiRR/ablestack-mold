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

# SSH 생성·등록·개인 키 결과 대화상자 설계 (#1375)

Epic #1369 및 기존 PR #1378에 누적한다. 기준 upstream Europa와 기존 누적 HEAD는 검증 보고서에 기록한다. MOLD-DIALOG-1.0(#1213)의 MD-01~MD-09 및 기존 VM MoldDialog/selfManagedDialog 계약을 따른다. #1214 전체 공통 전환 완료를 의미하지 않는다.

## 프레임과 기존 본문

기존 MoldDialog에 선택적 sm/md/lg/xl preset을 추가하고 preset을 선택한 화면만 MD-03~MD-05의 viewport 상한·Body/Footer padding·Footer 42px 버튼·테마 토큰을 적용한다. 기존 width 760/화면별 width와 VM 대화상자 배치는 유지한다. preset wrapper/mask에만 width:100vw를 적용하고 wrapper에 scrollbar-gutter:auto와 overflow:hidden을 적용해 html의 stable gutter로 생기는 5px 중앙 편차를 없애고, 닫기 버튼 내부 48px을 외부와 맞춰 8px 넘침을 막는다. SSH는 md=720px를 사용한다. 사용자 요청에 따라 제목과 Header는 실제 VM 대화상자 기준으로 통일한다. 데스크톱/모바일 모두 제목 16px·굵기 500·줄 높이 22px·secondary 텍스트 색상·제목 padding 0, Header padding 16px 24px를 사용한다. 별도의 큰 제목(22px/모바일 18px)은 적용하지 않는다. 사용자 후속 요청에 따라 데스크톱/모바일 별도 모서리 반경(14px/10px)도 제거한다. content/Header/Footer의 반경은 VM과 같은 공통 AntD 스타일을 그대로 사용하며 현재 실제 VM content 반경은 네 모서리 모두 4px다. Header/Footer는 독립 영역, Body만 스크롤하며 별도 SSH 모달 프레임이나 중첩 모달을 만들지 않는다. preset 입력의 normal/hover/focus/error/disabled 스타일은 body 범위의 선택자로 기존 dark-mode 입력 규칙보다 우선하며 불투명한 테마 입력 표면과 대비 토큰을 사용한다. 저장 확인 체크박스의 미선택/선택 경계와 Footer/닫기/체크박스의 키보드 포커스에도 같은 토큰을 적용한다. AutogenView의 selfManagedDialog 분기에 refresh-data 연결만 추가해 성공 목록 갱신을 보존한다.

설명→이름→공개 키→도메인→계정 순서와 기존 input/select를 유지한다. 공개 키는 등록 모드에만 해당하는 조건부 필드로 처리한다. 계정 필드는 선택 도메인이 있을 때만 표시한다. 생성과 등록은 API별 독립 액션으로 노출하고 각 권한이 있어야 열고 제출할 수 있다. 목록의 생성은 기존 텍스트 primary 버튼을 유지하고 등록은 import 아이콘만 표시하며 기존 공통 ActionButton의 마우스 오버 툴팁과 aria-label로 작업명을 제공한다. 등록 빈 공개 키는 검증 오류이며 생성으로 전환되지 않는다. 생성 모드는 오래된 공개 키 값이 있어도 register API를 실행하지 않는다. 각 Footer 문구는 생성/등록이다.

2026-10-16 공통 스타일 보완(#1383): VM 대화상자 버튼 크기에 맞추라는 사용자 요청으로
preset의 42px 높이와 별도 여백 지정을 제거했다. SSH 생성/등록/삭제를 포함한 preset
Footer는 VM과 동일한 기본 `a-button` 크기를 상속한다(31번 실측 높이 32px,
padding 4px 15px). 위의 42px 설명은 최초 적용 이력이며 현재 버튼 크기 계약은 이 보완을 따른다.

## 요청·오류·포커스

Footer·Enter·기존 Ctrl+Enter는 같은 제출 경로다. Footer의 외부 submit 버튼을 form 속성으로 연결하고, Ctrl+Enter는 keydown에서 직접 처리한다. 기존 directive가 Footer로 포커스를 옮겨 form 밖의 keyup을 놓치는 문제를 피한다. 검증 시작부터 loading을 잠그며 요청 중 취소/닫기/Escape를 비활성화하고 Body 상태 문구를 표시한다. #1373 preserveOnFailure 정책과 값·소유 범위를 보존한다. API 오류를 Body에 표시하고 기존 포커스/Body 스크롤을 복원하며 필수값 검증은 loading 해제 후 nextTick에서 첫 오류로 스크롤하고 명시적 Input ref에 포커스를 적용한다. AFormItem의 fragment $el과 비활성 입력에 대한 이른 포커스 호출에 의존하지 않는다. 성공 등록은 갱신 후 닫고 성공 생성만 결과로 전환한다. 열 때 첫 입력, 결과 전환 시 복사, 닫기 후 기존 AutogenView 트리거에 포커스를 복원한다. 기본 maskClosable=false와 AntD focus trap을 재사용한다.

## 개인 키 결과·저장 정책

이름·지문·account/domain/project와 최초 저장/재조회 불가 안내를 Body에 표시한다. 원문 개인 키를 본문/DOM 속성/저장소/로그에 표시하지 않고 인메모리에서만 복사·다운로드에 사용하며 닫기/해제 시 지운다. Footer는 닫기→복사→다운로드(유일한 primary)다. 복사 성공 콜백 후에만 성공 피드백을 표시하고 실패는 Body에 표시한다. 다운로드는 Blob URL로 시작한 뒤 URL을 해제하며 시작을 완료로 표현하지 않는다.

복사/다운로드로 영구 저장 여부를 알 수 없으므로 자동 저장 완료 처리하지 않는다. 사용자가 안전한 곳에 저장했다는 확인을 선택하면 바로 닫는다. 미확인 닫기/닫기 아이콘/Escape는 같은 모달의 경고와 계속 저장/저장 없이 닫기 Footer로 전환해 모달 중첩을 피한다. 생성된 키 쌍이 남는 의미도 안내한다. 브라우저 새로고침/이탈은 미저장 결과 또는 제출 중일 때 beforeunload 기본 확인을 사용한다. 공개 키 재조회·알고리즘 선택·개인 키 재조회는 추가하지 않는다.

## 검증·배포·종료

모드/API 권한(관리자/도메인 관리자/일반 사용자/create-only/register-only), 실패 후 값·포커스·스크롤, 중복 제출, 원문 대상/범위, 개인 키 실제 복사/다운로드 및 닫기/새로고침, VM 스냅샷과 SSH의 제목/헤더 실제 computed style 일치 및 대표 공통 대화상자 Footer 회귀를 검증한다. 데스크톱·390×844·390×480·확대, 밝은/어두운 테마의 좌표·Body 스크롤과 가독성을 실제 31번 UI에서 확인한다. 생성한 키/역할/계정/도메인/프로젝트를 정리하고 기존 키/VM 상세 해시를 비교한다.

WSL ext4에서 대상 UI 테스트·lint·생산 빌드·변경 텍스트 RAT를 실행한다. 정적 파일만 배포하고 WEB-INF/META-INF/config/JAR/PID를 보존한다. 전체 Cloud/Maven/RPM/Actions 빌드는 실행하지 않는다. 검증 근거를 기존 PR에 추가하고 #1375만 completed로 닫으며 Epic은 6/8 완료 OPEN, #1376/#1377 OPEN으로 유지한다.
