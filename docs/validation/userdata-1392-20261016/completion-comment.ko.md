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

#1392 설계·구현·31번 배포·기능 검증·정리를 완료했습니다. 연결 결함 #1394도 수정·재검증했습니다.

- 목록/상세7탭/8종 모달, VM 생성·재설정과 템플릿/ISO 연계를 검증했습니다. 20px 필드·16px 섹션 간격, VM과 같은32px Footer를 적용했습니다.
- 사용자 지적에 따라 VM 표준에 없는 목록 검색 설명, 참조/본문/버전 탭의 일반 설명·상태 합계와 대화상자의 정책/조작 안내 블록을 제거했습니다. 검색·필터·표·페이지·실제 오류/결과·작업 영향은 유지하고 최종 배포본의 전체 화면을 재수집했습니다. 코멘트 작성자·시각·공개 범위의 대비도 보완했습니다.
- 사용자 첨부 화면의 메뉴/Breadcrumb 사이 세로선도 VM과 대조했습니다. 가로 넘침을 막는 여백을 유지하면서 Breadcrumb 양옆 테두리를 제거했습니다. 모바일 일괄 작업 도구는 기존 버튼·검색창을 줄바꿈하고 비활성 버튼 대비를 기존 테마 토큰으로 보완했습니다.
- 일반/다크×8개 화면 조건의 **256개 화면/모달 검사 통과**. 텍스트≥4.5, 필수 경계/포커스≥3, 중앙 오차≤1px, Header/Footer 스크롤 좌표차0px입니다. 200%는720×450 CSS/dpr2 에뮬레이션입니다.
- 실제 wizard VM 생성·저장 UUID/변수/3075바이트·사용VM1개,11개 사용VM의10+1 페이지, 실제 사용중431, 일괄 부분 성공, 오류/출처 메모만 재시도·코멘트 보존을 UI/API/DB로 대조했습니다.
- 관리자/일반 사용자/도메인 관리자/프로젝트/제한 API 역할을 대표 실제 경로로 확인했습니다. 전체 역할×화면×테마 조합을 실행했다는 의미는 아닙니다.
- 공통 프로젝트 정상 취소·실패 롤백·재시도, 이전 조회 결과, 교차 메뉴 탭 URL 회귀를 수정·검증했습니다. 집중 Jest **15 suites/317 tests**, 변경 파일 ESLint, UI production build 통과입니다.
- 배포 UI `beb090148d910db0f5940aaaad7f4078bc201257`, app `js/app.1c6ca5f1.js`, 정적870개·기존JAR/12클래스/WEB-INF/config/PID 보존, mold active,/client/200입니다.
- API/DB 전용 활성 잔여0, 기존 운영 라이브러리 전체 응답 동일. 전체 Cloud CI/호스트 배포/게스트 실행 재시험/PR 병합은 이번 작업에 포함하지 않았습니다.

[최종 보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/report.ko.md) · [점검표](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/audit-matrix.ko.md) · [정량/화면](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/qualification.json) · [배포/해시](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/runtime-final.json) · [정리](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/db-after.json)

![불필요한 설명과 세로선을 제거한 최종 다크 모드 목록](https://raw.githubusercontent.com/dhslove/ablestack-cloud/refs/heads/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/delivered-standard-dark-list.png)

Epic 구현/최종 UI 검토는11/11입니다. 다음은 Draft PR #1393 최종 리뷰·병합과 Epic #1381 종료입니다.
