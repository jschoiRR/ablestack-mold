<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->
# #1388 검증에서 확인한 공통 경로 전환 회귀 항목

31번에서 일반 사용자 프로젝트 전환 후 빠르게 다른 화면으로 이동한 구간에 `Uncaught (in promise)` 1건이 포착됐다. 참조 목록은 해당 프로젝트 VM 1건을 정상 표시했고 다른 소유자의 VM은 표시하지 않았다. 전체 세션 무예외로 보고하지 않는다.

별도 빈 프로젝트에서 **사용자 데이터 목록 → 프로젝트/대시보드 전환 → 사용자 데이터 목록**을 재현했다. 대시보드 VM 건수 GET 응답 한 건을 잠시 보류하여 취소 상황을 고정했으며, 새 `.userdata-usage` 컴포넌트가 **0개**인 상태에서도 같은 예외를 수집했다. 스택은 `app.325357a9.js`의 `beforeRouteLeave`와 `cancel`을 가리킨다. 번들 위치를 대조하면 현재 #1388에서 변경하지 않은 `AutogenView.beforeRouteLeave`의 `sourceToken.cancel()`과 `utils/request`의 Axios CancelToken 취소 경로다. 참조 탭 기능과 구분한 공통 경로 전환 오류로 추적한다. 별도 프로젝트는 제거했고 기본 보기·관리자·다크 모드를 복원했다.

- [ ] 공통 경로 전환의 취소 Promise가 처리되지 않는 원인을 수정한다. 정상 취소를 서버 조회 실패·빈 결과와 혼동하지 않는다.
- [ ] 일반 사용자/도메인 관리자/관리자·프로젝트·대시보드↔목록·빠른 이동/뒤로 가기에서 미처리 예외와 오래된 결과 노출을 회귀 검증한다.

[분리 재현 스택](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/dashboard-cancellation-isolation.json) · [원래 프로젝트 구간](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/user-project-acl-requests.json) · [분리 시험 정리](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/navigation-probe-cleanup.json)

#1388은 VM/템플릿/ISO 참조 조회와 삭제 차단 사유 가시화의 구현·배포·범위 내 검증으로 종료하고, 이 공통 예외와 전체 UI 표준 최종 판정은 #1392에 남긴다.
