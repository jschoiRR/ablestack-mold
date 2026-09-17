<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# #1080 VM 목록 IP 간략화 검증

## 변경
목록 셀에는 대표 IP와 추가 주소 수(+N)만 표시한다. 출처, 수집 상태, 관측 시각은 주소 툴팁과 상세 팝오버로 이동했다. 대표 주소 선택 및 중복 제거/추가 개수 계산은 유지했다. 대표 주소는 최대 180px에서 말줄임하며 전체 주소 복사 값은 유지한다. 주소가 없으면 대시, 추가 주소가 없으면 버튼을 표시하지 않는다.

## Docker 검증
- Rocky Linux 9.8 linux/amd64, 프로젝트 Node 14/npm 6.
- 변경 컴포넌트 테스트 7/7 PASS; 변경 파일 lint --no-fix PASS.
- 기존 배포 변경을 포함한 UI 전체 테스트 48 suites / 482 tests PASS.
- ./dev ui-build PASS (UI만 빌드, 소스맵 제외).
- 테스트 범위: IPv4/IPv6, CIDR/대소문자 중복, 대표 주소가 목록에 없는 경우, 단일 주소, 주소 없음, 클라우드 기본 NIC fallback, 상태 및 관측 시각, 복사 값, 갱신으로 주소/개수가 변경되는 경우.

## 13번 배포 및 실제 화면
- 2026-09-15, http://10.10.13.10:8080/client/#/vm
- 코드: a11aea60eb9b71eed77634ac7dd52bf2b517e908
- 기존 배포의 #1077(cf5e779cb225eef1c263f6da9f63c95a72ed4ccf), #1078(PR #1079, 8370d9362c047d73226979338b0fc7d36c443eee) UI 변경을 빌드 시 함께 적용했다. 이 PR에는 #1080 변경만 포함한다.
- 산출물 SHA256: 4e80980a8b0430ceb6b2ab8a4a424d7f4360ac193c2c9b96c4333ea5bcf4aad3
- UI 파일 561개 해시 일치, HTTP 200, index 및 진입 스크립트 3개 해시 일치.
- 서버 config.json 및 WEB-INF 배포 전후 해시 동일. 관리 서비스 재시작 없음.
- 백업: /var/tmp/issue1080-20260915-013416/ui-before.tar.gz
- 동일 브라우저 화면에서 IP 셀 폭 308.90625px → 193.25px (약 37% 감소).
- 실제 목록: 대표 IP +4/+5/+1 및 추가 주소 없는 VM 표시 확인.
- +4 팝오버에서 주소 5개, IPv4/IPv6, 대표 표시, 출처, 상태, 관측 시각 확인.
- 라이트/다크 테마 육안 확인. 다크 테마 계산 대비: IP 링크 4.58:1, +N 8.47:1, 상태 설명 7.13:1. 라이트 테마 상태 설명 6.98:1. 전체 UI 접근성 인증을 의미하지 않는다.
- 대표 IP와 전체 IP 복사 클릭 시 실제 document.execCommand('copy')에 각각 전체 값/개행 구분 주소가 전달되고 true 반환 확인. 검증용 임시 계측은 제거했다.
- 화면 확인 중 팝오버의 관측 시각이 새 값으로 갱신되는 것 확인. props 변경에 따른 대표 IP/개수 갱신은 단위 테스트로 검증.
- 검증 후 사용자 테마를 다크로 복원했다.
