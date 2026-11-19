<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License for the specific language governing permissions and limitations. -->

UD-10 설계·구현·31번 배포·기능 검증과 전용 자원 정리를 완료했습니다. 구현/검증 범위 기준으로 종료합니다. Epic PR은 Draft이며 병합을 의미하지 않습니다.

- 원본/편집 본문·변수 재사용, 같은 소유자의 변경 본문 새 UUID 등록, 출처 기록·버전 탭·적용/복원 안내를 구현했습니다. 기존 참조를 자동 변경하지 않습니다.
- 응답 유실 결과 조회, 출처만 재시도/수동 복구, 대기 링크 차단과 창 종료 후 이동, 상세 경로/CNI 분기·포커스를 검증했습니다. 출처 메모는 255자 제한 안의 UUID/바이트 수이며 불변 감사 이력이 아닙니다.
- Jest174/6 suites, ESLint/UI build, 실환경 정량90항목. UI 복제6건 API/DB 대조, VM5대/템플릿 보존과 명시적 전환/복원, 실제 APPEND 생성, 일반/다크4 viewport씩8조합·VM32px 기준을 확인했습니다.
- UI `ca99503ac2a511638baef74a60346ce4f0bb70f0` / `js/app.c9e3887d.js` 배포. 정적870개·JAR/12클래스·WEB-INF/config/PID 보존, mold active, HTTP200. API/DB11종 활성 잔여0, 운영 라이브러리 응답 동일입니다.
- BOM APPEND 제한은 원본 보존과 명시적인 BOM 없는 새 버전 정책으로 안내/검증했습니다. 전체 Cloud CI·게스트 실행·PR 병합과 구분합니다. 프로젝트 공통 ERR_CANCELED 2건과 최종 전체 UI 판정은 #1392에서 추적합니다.

[설계](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/design.ko.md) · [전체 보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/report.ko.md) · [정량](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/qualification.json) · [배포](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/runtime-final.json) · [정리](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/db-after.json)

Epic #1381은10/11, 다음은 #1392입니다.
