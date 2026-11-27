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

# MOLD 스키마 리페어

`--apply`는 기존 버전 기록 대신 실제 테이블, 컬럼, 인덱스와 데이터를 확인합니다.
확인된 누락만 보완하고, 적용할 항목이 없으면 DB를 변경하지 않습니다.

```bash
cd /usr/share/cloudstack-common/scripts/util/schema-repair
./mold-schema-repair.sh --list   # DB 접속 없이 SQL 읽기 검사
./mold-schema-repair.sh --plan   # DB 변경 없이 누락과 검토 항목 확인
```

| 결과 | 의미 |
|---|---|
| `ALREADY_PRESENT` | 보완 대상이 이미 있거나 변경할 행이 없음 |
| `MISSING` | 실제로 적용할 항목이 확인됨 |
| `REVIEW_REQUIRED` | 조건이나 대상의 상태를 확인해야 함. 자동으로 반영하지 않음 |

실제 보완은 관리 노드 전체에서 Mold·Usage를 정지한 뒤 한 CCVM에서 진행합니다.
변경이 필요한 경우에만 `cloud`, `cloud_usage`를 백업한 뒤 적용합니다.

```bash
systemctl stop mold mold-usage
./mold-schema-repair.sh --apply
# 정상 완료와 검토 항목을 확인한 뒤 시작
systemctl start mold mold-usage
```

- INSERT는 업무 키로 누락 행만 추가합니다. 기존 값은 덮어쓰지 않습니다.
- UPDATE·DELETE는 원본 SQL에 붙은 `@repair-` 주석의 조건을 확인해 대상 행만 변경합니다. 원본 구문이 바뀌면 조건을 검토할 때까지 적용을 보류합니다.
- 프로시저 재생성, 테이블·컬럼 삭제, 정의가 다른 인덱스 교체, 검증되지 않은 동적 SQL은 검토 대상으로 남깁니다.
- 결과와 백업은 `/root/mold-schema-replay-*`의 `summary.json`, `actions.jsonl`, `backup.sql.gz`에서 확인합니다.
- 기본 대상은 시작 버전 `41810` 이상인 `schema-숫자to숫자.sql`입니다. Java 마이그레이션은 Mold 시작 시 실행됩니다.

Mold 기동 시 DB 버전이 4.21.0.0 이상이면 원본 `schema-42040to42100.sql`을 실행해 누락된 변경과 설정 범위 변환을 보완합니다.
같은 내용이 완료된 상태면 건너뛰며, 실행이 중단되었거나 SQL 파일이 바뀌면 다음 기동에서 다시 실행합니다.

Bash, Python 3.8 이상, MySQL 8.0, `mysql`·`mysqldump`가 필요합니다.
`--plan`은 누락을 발견하면 코드 2, 오류는 코드 1을 반환합니다. 검토 항목은 실행 결과에서 확인합니다.
