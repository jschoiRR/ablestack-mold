<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->

## UD-08 #1389 최종 번들에서 공통 취소 예외 추가 근거

31번 UI 소스 `8629c06d37a4f51c0acb7b9cb084e7bc744c37e1`, `app.50a3be37.js`에서 프로젝트 진입/이탈 중 `CanceledError`, `ERR_CANCELED`, `Uncaught (in promise)`가 각 1건 관찰됐다. 두 전체 스택 모두 `beforeRouteLeave` → `cancel`이며 위치는 app 1:550686, 1:682845와 vendors 155:308780이다(파일 표기는 1-based).

#1388에서 새 참조 컴포넌트 0인 목록/대시보드 경로로 분리한 [기존 근거](https://github.com/ablecloud-team/ablestack-cloud/issues/1392#issuecomment-6207345069)와 같은 공통 경로다. 이번 변경은 해당 beforeRouteLeave/cancel 코드를 수정하지 않았다. 중앙 취소 처리와 연결된 promise rejection을 #1392에서 보완하고, 라이브러리/프로젝트/역할/대시보드/빠른 경로 전환을 다시 검증해야 한다.

최종 기능 요청 27개 완전 구간 중 25개는 예외 0건이고 위 2개는 각 1건이다. 별도의 새로고침 1구간은 이벤트 버퍼 잘림으로 완전한 예외 집계에서 제외했다. 전체 세션 JS 예외 0건으로 계산하지 않는다. #1389 본문·내보내기·권한·8개 테마/화면 조합의 검증을 전체 UI 최종 통합 승인으로 해석하지 않는다.

- [진입 예외 전체 스택](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/network-final-role-project.json)
- [이탈 예외 전체 스택](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/network-final-role-domainadmin.json)
- [UD-08 최종 보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/report.ko.md)
