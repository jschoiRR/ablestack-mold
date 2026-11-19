<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License for the specific language governing permissions and limitations. -->

UD-09 #1390의 최종 배포에서도 **프로젝트 전환의 기존 공통 취소 예외**를 재확인했습니다.

- 최종 UI 소스 `8d79de0db24b0e432a381b8c532e4fb361ffaa12`, `js/app.6abf6820.js`. 완전한 수집 구간21개 중19개 JS예외0, 프로젝트 진입/종료2개에 `CanceledError / ERR_CANCELED` 각1건입니다. 초기 기능 번들에서도 같은2건이 있었으며 초기/최종 자료를 구분했습니다.
- 전체 스택은 `AutogenView.beforeRouteLeave → request.cancel` 경로입니다. #1388/#1389에서 전달한 공통 경로 이탈 취소와 같은 분류입니다. 본문 조회/오류/권한/실제 VM 변경·삭제·지연·사용 VM 링크 구간에서는 새 예외가 없었습니다. 전체 세션 예외0으로 처리하지 않습니다.
- UserDataContent/MoldDialog·32px 버튼·8개 테마/viewport·포커스/키보드의 UD-09 범위는 통과했지만, 목록/상세/전체 탭·모달과 확대·대비 등 **최종 전체 UI 표준 판정은 이 이슈에 남습니다**.
- 전용 자원 API/DB 활성 잔여0, 기존 라이브러리 전체 응답 동일, 관리자/기본 프로젝트/다크 및 브라우저 상태를 복원했습니다.

[진입 전체 스택](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/network-role-project.json) · [종료 전체 스택](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/network-project-leave.json) · [최종 118항목](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/qualification.json) · [최종 38화면/보고](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/report.ko.md)
