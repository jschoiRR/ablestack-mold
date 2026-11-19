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

## 해결한 문제와 결과

사용자 데이터 라이브러리 Epic #1381의 등록·선택·재설정·참조/삭제·본문·VM 저장본·복제 버전과 최종 UI 표준을 개선한다. UD-01 #1382~UD-11 #1392와 최종 감사 결함 #1394의 구현·31번 배포·범위 내 검증을 완료했다. **11/11**, Epic은 최종 리뷰/병합을 위해 OPEN, PR은 Draft를 유지한다.

기존 ListView/ResourceLayout/InfoCard/ResourceView/MoldDialog와 UserDataSelection·테마 토큰을 재사용한다. 서버 검색/페이지·소유 범위, 실패 정합성·입력 보존·부분 복구, 사용VM/템플릿/ISO 전용 탭, 원문/Base64 복사·다운로드, VM 저장본 비교와 독립 새 UUID 버전을 제공한다. 기존 참조 VM을 자동 변경하지 않으며 출처 댓글은 변경 가능한 참고 기록이다.

## UD-11 최종 개선

등록/복제20px 필드·삭제/버전16px 섹션·VM32px Footer, 본문 업데이트 도구, 일반/다크 링크·선택/경계·placeholder/hover/focus 대비를 맞췄다. 템플릿/ISO의 기존 본문 그리드를 유지한 공통 모달·서버 선택기·정확한 templateid·연결 완료 대기/부분 실패 복구를 적용했다.

VM 표준에 없는 목록의 검색/정렬 설명과 참조·본문·버전 탭 및 관련 대화상자의 일반 안내 블록은 제거했다. 검색·필터·표·페이지와 실제 작업 상태/오류/영향을 남겼다. 마지막 표시 수정 이후 최종 정적 번들에서 전체256개 화면을 다시 수집했다. 실제 CRUD·권한/장애·VM 생성 대조는 이전 기능 수정 단계의 증거로 명시한다.

프로젝트 API→프로젝트→테마→라우팅을 기다리고 기본id0을 정규화했다. 대시보드는 정상 취소를 처리하고 옛 범위 응답을 차단한다. 코멘트의 실패 초안과 제출 이후 새 초안, 제한 API 탭과 잘못된 URL을 처리하며 교차 메뉴 이동의 이전 탭 설정이 새 URL을 덮지 않도록 수정했다.

## 최종 검증과 배포

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



초기 재현과 최종 통과 자료를 분리했다. 전체 Cloud/GitHub Actions CI 결과가 없으며 단위 검사·UI 빌드·실환경 범위 내 통과를 전체 CI 성공으로 확대하지 않는다. 앞선 공급자/게스트 검증은 #1386 보고서의 범위이며 이번에는 정지 VM의 생성/저장 경로를 검증했다.

## 완료 범위별 근거

| 이슈 | 완료 범위 | 보고서 |
| --- | --- | --- |
| #1382 | 등록 정규화·중복 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1382-20261015/report.ko.md) |
| #1383 | 등록 공통 대화상자·VM 버튼 규격 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1383-20261015/report.ko.md) |
| #1384 | 선택기 검색·서버 페이지 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1384-20261016/report.ko.md) |
| #1385 | 현재값·모드·템플릿 정책 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1385-20261016/report.ko.md) |
| #1386 | 적용 실패 정합성·공급자/게스트 검증 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1386-20261016/report.ko.md) |
| #1387 | 일괄 삭제·부분 실패·미확인 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1387-20261016/report.ko.md) |
| #1388 | 사용 VM/템플릿/ISO 전용 참조 탭 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/report.ko.md) |
| #1389 | 목록·메타데이터·본문·VM UI 표준 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/report.ko.md) |
| #1390 | VM 저장본·원본 비교·권한·지연 응답 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/report.ko.md) |
| #1391 | 복제·독립 버전·출처 기록/복구·전환/복원 정책 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/report.ko.md) |
| #1392 / #1394 | 전체 UI 표준·간격/대비·공통 경로 회귀 및 최종 통합 검증 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/report.ko.md) |


## 다음 단계

추가 구현 하위 이슈는 없다. PR 최종 리뷰·명시적인 병합 요청 후 Epic #1381을 마무리한다. 이 PR에서 병합을 수행하지 않았다.
