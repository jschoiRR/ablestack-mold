<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License for the specific language governing permissions and limitations. -->

UD-09 #1390 검증 중 기존 backend의 **BOM 본문 APPEND 형식 제한**을 확인했습니다. 후속 복제/버전/입력 정책에서 다룰 근거를 남깁니다.

- 전용 라이브러리의 UTF-8 BOM+`#cloud-config` 원본은 등록/단독 VM 저장/본문 조회/실제 복사·다운로드에서 바이트 보존됐습니다.
- 별도 템플릿을 APPEND로 연결하고 그 BOM 본문을 사용자 지정 본문으로 지정해 `startvm=false` 배포하면 MIME 형식 인식 실패로 API530입니다. VM은 생성되지 않았습니다. BOM 없는 cloud-config 대조는 배포·병합 저장본 조회에 성공했습니다.
- `engine/userdata/cloud-init/.../CloudInitUserDataProvider.java:112,271`의 기존 인식/병합 계약입니다. 이번 UI 변경과 backend JAR 배포 변경은 없습니다. 입력 허용 범위·병합 시 정규화와 원본 바이트 보존의 정책을 확정해야 합니다.
- 기존 VM 자동 전환은 하지 않는 복제/새 버전 정책과 함께 별도 검토하며, 이번 읽기 UI의 완료와 구분합니다. 재현에 사용한 자원은 정리했습니다.

[실제 응답과 VM0건](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/append-bom-policy.json) · [APPEND 대조](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/case-append.json) · [전체 보고](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/report.ko.md)
