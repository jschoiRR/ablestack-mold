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

# #1082 다중 작업 대화상자 검증

## 수정
- BulkActionProgress의 밝은 고정 배경을 테마 토큰으로 교체했다. 작업 상태는 VM 상태와 구분해 진행 중/성공/실패로 표시한다.
- AutogenView와 EventDownload가 원본 chosenColumns 배열을 변경하지 않도록 분리했다. 공용 진행 표에서도 작업 상태 컬럼의 중복을 방어한다.
- 목록 갱신으로 선택 행이 재연결되어도 진행 대화상자의 작업 snapshot/status/jobid를 보존한다.
- 성공/실패 이후 늦은 진행 이벤트를 무시하고, 이전 실행의 비동기 콜백이 새 실행 결과를 덮어쓰지 않도록 실행 당시 선택 배열을 캡처한다.
- 작업 상태 변경 시 목록 조회를 모아서 실행한다. 필터는 현재 작업 항목에서 계산하고, 종료 시 대기 타이머를 정리한다.
- 다중 API 요청 실패도 Promise를 완료해 전체 작업 대기가 끝나도록 했다.

## 관련 대화상자 점검
BulkActionProgress/BulkActionView를 직접 또는 간접 사용하는 16개 파일을 확인했다: AutogenView, EventDownload, BulkActionView, ApiKeyPairsTab, DestroyVM, IsoZones, TemplateZones, IntegrityVerificationTab, EgressRulesTab, FirewallRules, IpAddressesTab, Ipv6FirewallRulesTab, LoadBalancing, PortForwarding, RoutingFirewallRulesTab, SnapshotZones. 공용 컴포넌트 수정으로 같은 진행 요약 패널에 적용된다.

추가로 고정 밝은 배경/문자색을 사용하는 ImportNetworkACL, DisasterRecoveryClusterAdd, DisasterRecoveryClusterDisable, RuleSilenceModal을 테마 토큰으로 교체했다. StorageServiceRuntimeUpgrade는 자체 다크 테마 오버라이드가 있어 유지했다. 추가 4개 화면은 소스/린트/빌드로 확인했으며 실제 ACL 가져오기·DR 변경·무음 설정 작업은 실행하지 않았다.

## Docker 검증
- Rocky Linux 9.8 linux/amd64, 프로젝트 Node 14/npm 6.
- 신규 테스트 10개 PASS: 상태 회귀, 다른 job 이벤트, 이전 실행 완료, 선택 snapshot 보존, 컬럼 원본 불변 및 중복 방어, 필터 갱신/해제, 조회 병합과 타이머 정리.
- UI 전체 50 suites / 492 tests PASS.
- 변경 파일 lint --no-fix PASS.
- 최종 ./dev ui-build PASS. UI 모듈만 빌드했고 소스맵은 제외했다.

## 13번 배포
- 소스: bde0e5fb56e02c417e25b64f52819c9d10d37eec (기존 #1077/#1079/#1081 병합 포함).
- 산출물 SHA256: f1d11c1d9bca36aabab3befb8393dad56d1fbd3d5b3334ba51c48b0c280cbc94
- 백업: /var/tmp/issue1082-20260915-020217/ui-before.tar.gz
- /usr/share/cloudstack-management/webapp의 UI 파일 561개 해시 일치.
- HTTP 200, index 및 진입 스크립트 3개 해시 일치.
- config.json/WEB-INF 배포 전후 해시 동일, 관리 서비스 재시작 없음.

## 실제 다중 정지/시작
사용자의 실물 테스트 승인 후 2026-09-15 13번 UI에서 실행했다.
- 대상: rocky9-vm (48bdce4a-8bba-4984-80f1-46b1c92042cd), iso-rocky-vm (73eb5275-1188-4992-9f8c-33b654a19a8f).
- 테스트 전 두 VM 모두 실행 중이었다.
- 다중 선택 후 정지 실행: 약 6.1초 내 두 작업 성공. 진행 중 2 → 성공 1/진행 중 1 → 성공 2/진행 중 0.
- 대화상자를 닫거나 수동 새로고침하지 않은 상태에서 배경 VM 목록의 정지 상태를 확인했다.
- 대화상자를 닫고 같은 두 VM을 다시 선택해 시작 실행: 약 6.1초 내 두 작업 성공. 진행 중 2 → 성공 1/진행 중 1 → 성공 2/진행 중 0.
- 시작 완료 대화상자를 열어 둔 채 90초 동안 성공 상태가 유지되었다. 진행 상태 회귀가 없었고 각 단계에서 작업 상태 컬럼은 정확히 1개였다.
- 시작에서도 대화상자를 닫기 전 배경 목록의 실행 중 상태를 확인했다.
- 테스트 후 두 VM 모두 기존 실행 중 상태/ablecube3 호스트로 복원됨을 확인했다.
- 다크/라이트 진행 대화상자를 육안 확인했다. 다크 요약은 배경 rgb(34,40,47), 글자 rgba(255,255,255,0.65); 라이트는 흰 배경과 rgb(31,41,55) 글자였다. 고정 밝은 패널이 사라지고 글자가 읽히는 것을 확인했다.
- 브라우저 console error 없음. 검증용 상태 관찰자를 제거하고 다크 모드를 복원했다.
