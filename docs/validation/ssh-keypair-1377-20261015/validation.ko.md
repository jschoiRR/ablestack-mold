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

# SSH 키 사용 가상머신 탭 검증 (#1377)

2026-10-15 KST, 31번 클러스터. 누적 Epic PR #1378 / Epic #1369. 배포 UI 소스 `bc2dfcb58b2897278b9b8c6dcb93bc3a4982a0df`. upstream Europa 기준 `a5a811387476ac87a8774fc729cc68021104684a`.

## 결과

상세 → 사용 가상머신 → 코멘트 탭을 기존 ResourceView에 추가했다. VM 표준의 데스크톱/태블릿 좌측·모바일 상단 탭, 32px 툴바/8px 간격/20px 본문 간격과 공통 ListView/Status를 사용한다. 읽기 전용 업데이트·검색·페이지·VM 상세 링크를 제공한다. 사용자 표시 이름을 원문으로 유지한다.

조회 전후 키 UUID·이름·지문·account/domain/project를 확인하고 #1372의 정확한 keypair/소유 범위로 조회한다. 전체 서버 검색·count/페이지와 현재 페이지 열 정렬의 범위를 안내한다. 자동/수동 갱신은 행·조건·페이지를 유지하고 서버 오류에는 마지막 성공 결과임을 표시한다. 권한 없음·키 변경/삭제·오류와 정상 빈 목록을 구분한다. 키/소유자/프로젝트/세션/검색/페이지/활성 탭이 바뀐 뒤 도착한 응답은 반영하지 않는다.

## 검증 근거

| 범위 | 결과 | 근거 |
| --- | --- | --- |
| UI 단위/회귀 | 24 suite / **469개 통과** | ui-tests-final.log |
| 신규 탭/공통 탭/사용량 집중 검증 | 3 suite / **73개 통과** | ui-tests-focused.log |
| lint / 생산 UI 빌드 / 변경 텍스트 RAT | 통과 / 16개 승인, Unknown·Unapproved 0 | ui-lint.log, ui-build.log, changed-text-license-result.json |
| 실제 브라우저 탭 검증 | **44개 통과** | ui-verification.json, 화면 JPEG |
| 기존 생성/등록·VM 스냅샷/그룹 대화상자 회귀 | **8개 통과** | ui-regression-verification.json |
| 정리 후 기존 UI/관련 VM 회귀 | **2개 통과** | ui-cleanup-verification.json |
| 실제 API/DB 생성·범위·역할·페이지 | **28개 통과** | api-verification.json |
| 키 변경·다중 키 복원·기존 데이터 보존 | **7개 통과** | lifecycle-verification.json, db-relationship-after-reset.json |
| 정적 배포 | **855개 해시 일치**, HTTP 200 | deployment-verification.txt, runtime-final.txt |

전용 정지 VM 6대를 실제 deployVirtualMachine API로 생성했다. root 단일 A/다중 A+B/단일 B/무키, 다른 계정 A, 프로젝트 A를 비교했고 실제 SSH.KeyPairNames/PublicKey와 API/UI가 일치했다. 같은 이름·공개 키의 다른 소유 범위를 제외한다. 기존 VM은 변경하지 않고 기존 공개 키를 같은 소유자에 임시 등록해 19대의 서버 count/10+9 페이지와 검색을 확인했다.

전용 다중 키 VM에서 resetSSHKeyForVirtualMachine 비동기 완료 후 A+B→B로 변경했다. A 탭이 2→1대로 줄고 API·DB의 실제 연결 관계와 일치했다. A+B로 복원 후 다시 2대가 보이는 것을 확인했다. 임시 VM 표시 이름 USER.LOGIN의 원문과 UUID 링크도 실제로 검증하고 원래 표시 이름으로 복원했다.

Root Admin/Domain Admin/User/프로젝트 구성원으로 로그인했다. VM 조회 및 코멘트 금지 역할은 VM 요청 없이 명시적 권한 상태를 표시하고 코멘트 탭을 숨긴다. 전역 프로젝트/전체 프로젝트 선택에도 명시적 키 소유 범위를 유지하며 기존 가상머신 보기와 삭제 대화상자가 같은 사용 관계를 보여준다.

URL 직접 진입, 탭/검색/페이지의 뒤로·앞으로 이동, 페이지 크기 변경, 현재 페이지 정렬, 업데이트 중 행 유지와 실제 10초 주기 갱신, 비활성 탭의 조회 중단을 확인했다. 지연 응답 뒤 다른 키로 이동해도 이전 행이 표시되지 않는다. 조회 도중 삭제·같은 이름 재등록은 원래 UUID 없음으로 처리하고 새 키 사용량을 이전 키에 표시하지 않는다.

두 테마에서 1280×720/900×844/390×844/390×480 배치와 툴바 크기/탭 방향/페이지/문서 가로 넘침 없음, 모바일 검색 다음 행 배치·표 내부 가로 스크롤을 검사했다. 모바일 본문과 도메인 열까지 실제 스크롤한 화면을 포함한다.

503/432와 지연은 검증 브라우저의 조회 요청에 한해 주입하고 해제했다. 실제 역할 제한은 별도 API/로그인으로 확인했다. 초기 검증 도구의 한글 열 선택자, 중복 상태 텍스트, 요청 해제 대기 및 내부 스크롤 선택을 조정해 미완료 항목을 재개했다. 제품 코드 변경이나 실패를 성공으로 간주하지 않았으며 JSON/통과 로그는 완료된 검증만 포함한다.

## 배포와 정리

정적 파일 855개 SHA256 일치. 자산 우선/index 마지막 배포, 백업 `/root/ssh-keypair-1377-ui-20261015-143710/static-before.tgz`. WEB-INF 유지, META-INF는 배포 전후 없음, config/JAR/PID **941912**와 FTCTL blockingLoadingState/fetchSyncProgress/extractJobId 보존, Mold active·/client/ HTTP 200을 최종 재확인했다. Java/DB 스키마/호스트 패키지는 변경하지 않았다.

키 9회 등록(최종 8개 정리 + 재등록 검증 중 원래 키 삭제), 전용 VM 6대와 볼륨/NIC·네트워크 3개·계정 4개·역할 1개·도메인/프로젝트 각 1개를 정리했다. API VM 및 활성 DB 자원·테스트 키 잔여 0. 기본 API/UI 키 3개, 전체 namespace 기존 키 **5개**, 기존 VM SSH 상세 **350행** 해시 보존. Cloud가 expunged VM에 보관하는 비활성 SSH 이력 **10행**은 정상 삭제 이력으로 별도 기록하고 DB를 직접 삭제하지 않았다.

전체 Cloud/Maven/RPM/Actions 빌드와 전체 CI는 실행하지 않았다. 정지 VM의 생성/키 재설정 비동기 성공과 Cloud 연결 관계를 검증했으며 게스트 OS의 실제 SSH 접속·authorized_keys 설치/회수는 검증하지 않았다. #1377의 구현 범위를 완료했다. Epic의 등록 하위 이슈 8개 완료와 공개 키 API/게스트 SSH 등 별도 후속 항목의 완료를 구분한다.

## 화면

- [기본 목록·다중 키](ui-root-multikey.jpg) · [검색 결과의 서버 페이지](ui-pagination.jpg) · [프로젝트](ui-project.jpg) · [VM 이름 원문](ui-vm-raw-name.jpg)
- [갱신 실패와 기존 결과](ui-refresh-error.jpg) · [권한 없음](ui-no-permission.jpg)
- [밝은 데스크톱](ui-layout-light-desktop.jpg) · [어두운 데스크톱](ui-layout-dark-desktop.jpg)
- [모바일 밝은 본문](ui-mobile-content-light.jpg) · [모바일 어두운 본문](ui-mobile-content-dark.jpg) · [모바일 도메인 열](ui-mobile-table-domain-dark.jpg)

비밀번호·세션·API/secret key·개인 키와 실행용 인증정보가 포함된 helper는 공개 근거에서 제외했다.
