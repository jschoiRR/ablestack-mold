<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License for the specific language governing permissions and limitations. -->

#1390 구현·31번 배포·기능 검증을 완료했습니다.

- VM 상세 작업 메뉴와 라이브러리 사용 VM 탭의 상세 링크에서 저장 본문에 접근합니다. UserDataContent/MoldDialog·32px 버튼·공통 테마를 재사용했습니다.
- 원본 동일/차이/참조 없음/비교 불가, 정책·변수 메타데이터와 게스트 실행 의미를 구분합니다. API 발견 기반 노출과 일반 사용자/도메인 관리자/프로젝트/거부 역할을 실제 검증했습니다.
- 최종 소스 102 tests·ESLint·UI production build와 실환경 118항목 통과. 실제 복사/다운로드·VM 변경/삭제·오류/지연·일반/다크 8조합과 포커스/키보드/공유 본문 회귀를 확인했습니다.
- UI `8d79de0db24b0e432a381b8c532e4fb361ffaa12`, app `js/app.6abf6820.js`, 867정적/12기존클래스 해시·WEB-INF/config/PID 보존. API/DB 시험 활성 잔여0, 운영 라이브러리 전체 응답 동일입니다.
- 공통 프로젝트 전환 ERR_CANCELED 2건은 #1392, BOM APPEND 형식 정책은 #1391에 전달합니다. 전체 Cloud 빌드/CI/전체 UI 최종 판정은 이번 완료 근거가 아닙니다.

[설계](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/design.ko.md) · [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/report.ko.md) · [검증 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/qualification.json) · [런타임](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/runtime-final.json)

Epic PR #1393을 9/11 완료 범위로 갱신했습니다. 다음은 #1391, 이후 #1392입니다.
