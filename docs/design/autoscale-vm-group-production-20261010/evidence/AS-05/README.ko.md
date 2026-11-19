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

# AS-05 공개 검증 근거

[검증 기록](../../AS-05-validation.ko.md)의 수치와 판정은 아래 파일 및 실제 UI DOM/화면으로 대조한다. API·DB는 보조 근거이며 사용자 동작과 최종 상태는 현재 Mold UI에서 확인했다.

| 근거 | 읽는 방법 |
|---|---|
| repeat-ui-results.json / 31·32-round-*.dom.txt | 각10회 왕복의40개 UI 수렴 기록. 1~5회3526, 6~8회e943, 9~10회a5ea 서버. 일부 초기 회차 라벨은 실제 실행 기록으로 복구하고 JSON에 표시했다. |
| cpu-ui-results.json / 31·32-cpu-*.dom.txt·png | 최종a5ea CPU 1→2→3→2→1, max hold와 min floor. 초기 enable-job 대기 중 OTHER 및 두번째 축소 중 SCALING을 정상 수렴 기록과 구분한다. |
| as05-cpu-metrics.jsonl / as05-cpu-boundary-check.json | 전체 VM/API 인원110샘플씩. max3/min1. 최근5VM만 있는 DB 타임라인을 전체 인원 근거로 사용하지 않는다. |
| as05-cpu-settled.jsonl / as05-regression-counts.jsonl | 인원·Running·활성 LB·작업 필드를 교차 확인한다. |
| as05-storage-max.json | 최대3×2대의 실제 독립 Sparse qcow2 및 raw krbd. raw block의 qemu-img allocatedBytes0을 실제 Ceph 할당0으로 해석하지 않는다. |
| as05-resource-audit-31·32-final.json / as05-shared-resources-final.json | 기존 그룹·VM·ROOT·카운터·공유 프로필/정책/조건 보존, 정리 그룹 삭제, 고아0. 정상 정책/오퍼링/UserData 복원. |
| as05-physical-resource-final.json | 6호스트에서 삭제 VM 도메인·파일/RBD·krbd 매핑 없음, 원본 이미지 존재. |
| 31·32-cleanup-*, lb-*, start/quota/storage-failure* | 부분 실패·동일 대상 유지·보상 UI. 일부 초기 영문 알림은 UI 번역 보완 전 역사적 증거이며 최종 한글 검증은 아래 수동 작업 화면이다. |
| 31-project/32-user-manual-*-blocked.* | 최종서버의 활성 그룹 수동 정지/재시작/일반 삭제 거절·한글 안내·VM 유지. |
| 31·32-disabled-normal-reboot.* / as05-disabled-reboot-jobs.json | 진행 작업 없는 비활성 그룹 정상 재시작 INFO·완료 + status1/resultCode0. |
| 31·32-final-preserved-group.* | DISABLED/멤버1, 31 프로젝트/32 일반 사용자 최종 보존 화면. |
| 31-lifecycle-light.png / 31·32-cpu-max-hold.png | 기존 Mold 라이트/다크 읽기 가능성. 전체 UI 통합/반응형 최종 인증은 #1355/#1354 범위다. |
| as05-http-*.json | 240초 초기 확장: 각4 timeout을 보존. 150초 드레인: 5658/5712 HTTP200 실패0. 마지막30초 재시작 이후: 1143/1064 HTTP200 실패0. 각각 시험 구간·목적을 구분한다. |
| as05-slow-within-grace-*.jsonl | 60초 grace에 30/45초 연결 총8개가 LB 제외 이후 기한 전에 본문까지 완료. grace보다 긴 연결의 종료를 무손실로 표현하지 않는다. |
| as05-java-test-final.json / as05-final-health.json | Java207 집중 시험 및 클래스47/UI840 해시·활성 관리 경로·WEB-INF·HTTP200. 전체 Cloud CI 결과가 아니다. |
| as05-r3-errors-31·32.json | 시작 상태 보완 이후 두 AS04 그룹 ERROR0. 의도적 VM 수동 거절 오류와 구분. |

비밀번호·API/secret key·전체 UserData/profile·VR 통계 인증 값·게스트 SSH 키는 공개하지 않는다. 장시간 운영은 최종 #1354에서 사용자 절차대로 수행한다.
