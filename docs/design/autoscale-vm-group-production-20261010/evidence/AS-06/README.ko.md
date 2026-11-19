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

# AS-06 근거

검증 결과와 환경·판정 범위는 [AS-06 검증 기록](../../AS-06-validation.ko.md)을 참조한다. JSON은 소유자·VM/볼륨·예약·해시·오류의 제한된 근거이며 비공개 프로필 원문, 계정 비밀번호, 개인 키는 포함하지 않는다. 새 SSH 키의 이름만 공개한다.

- `test-summary.json`: 최종 집중 테스트/모듈/UI 빌드와 실제 빌드 소스.
- `as06-management-artifacts.json`: 서버별 기존 활성 JAR 기반 해시, 변경55클래스별 해시.
- `as06-ui-artifact-r3.json`: 최종 UI 소스·774파일 소스 해시 묶음·tgz SHA256.
- `schedule-db-audit-31.tsv`, `schedule-db-audit-32.tsv`: 동일 시각 예약 승자·충돌의 async ID, Started/Completed, 활성 그룹 예약 성공, 예약6개 Disabled.
- `host-storage-audit.json`: 대표31 GFS2 Sparse QCOW2·대표32 krbd block/raw의 실제 mount/libvirt/크기.
- `guest-key-userdata-long.json`: 새 VM SSH key 인증·UserData marker·120/10GiB 디스크 및 cloud-init 경고/exit 구분.
- `final-fixture-state.json`: Disabled/min1/max3/VM1/LB1 및 원본 프로필 완전 일치·Disabled 예약·HTTP20/20.
- `cleanup-db-before.json`/`cleanup-db-final.json`: 승인된4VM/8볼륨, 축소 전후 멤버·LB·2차 복사 참조 및 실제 삭제 상태.
- `cleanup-timeline.json`: 3→2→1, 대상476→475/152→151, DRAINING/DESTROYING과 최종 Disabled·범위 복원의 DB 전이.
- `cleanup-physical-final.json`: 6호스트의 삭제 VM 도메인·GFS2 파일·RBD 이미지·krbd 매핑 부재와 원본 도메인/ROOT 보존.
- `cleanup-baseline-resources-31.json`/`32.json`: 기존 그룹/VM·ROOT 자원 및 quota 사용량300/400GiB·고아 매핑0.
- `cleanup-management-health.json`: 최종 JAR SHA256/PID·mold active·WEB-INF·client HTTP200.
- `31/32-cleanup-*.png`/`.txt`: 축소 진행·최종 원본 VM1 및 ROOT100GiB의 실제 UI 증거.
- 기타 JSON: 서버 입력·권한 거절과 공유 정책 복원.
- UI PNG/텍스트: 실제 Cloud UI 입력·값·오류·한글·테마 증거. 최종 목록은 r3; 날짜/DST는 r2; 신규 VM 적용은 최초 r1.

전체 CI/장시간 운영/실제 다중 관리 노드/모든 provider 검증과 구분한다. 원본 로컬 로그는 `/home/ablecloud/work/epic1346-implementation/as06`에 보존한다.
