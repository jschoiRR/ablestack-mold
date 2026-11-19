<!-- Licensed to the Apache Software Foundation (ASF) under one
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
under the License. -->

Parent Epic: #1381 · 최종 검토 #1392 · 구현 PR #1393

31번 기존 UI `ca99503a`, `app.c9e3887d.js`에서 최종 검토 중 다음 불일치를 확인했다.

1. 등록·복제 필드의 margin-bottom 6px로 다음 레이블이 붙어 보이고 버전/삭제의 안내·결과·작업 사이 간격이 부족하다. VM32px 버튼은 유지하면서 20px 필드/16px 섹션 간격으로 보완한다.
2. 프로젝트 전환 시 Runtime.exceptionThrown CanceledError가 실제1건 재현된다. 대시보드의 종료되지 않는 조회 Promise와 프로젝트 store.watch 수명주기를 보완하고 정상 취소·실패·늦은 결과를 구분한다.
3. 템플릿/ISO 수정은 legacy 모달 안에 본문 버튼이 들어 있고, 사용자 데이터는 첫 페이지 목록만 조회한다. 메타데이터 update 뒤 연결을 기다리지 않고 닫는다. ISO 연결은 templateid 대신 isoid를 전달한다. 기존 필드/그리드와 API를 보존하면서 공통 모달·선택기·연결 완료/부분실패 계약을 적용한다.
4. 라이브러리의 작업 버튼 hover에서 흰 글자 대비2.5188:1을 관측했다. 일반/다크의 실제 렌더링 상태를 측정하고 기존 의미별 테마 토큰을 재사용한다.

- [ ] 설계/구현, 집중 단위 검사와 production UI build
- [ ] 31번 정적 배포·해시/WEB-INF/config/JAR/PID 보존 검증
- [ ] 실환경 역할/프로젝트/지연/실패·뷰포트/두 테마 재검증과 정리

범위 내 수정·재검증은 #1392 통합 검토와 함께 진행한다. 이 이슈를 완료하기 전 #1392를 최종 통과로 표시하지 않는다.
