<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License for the specific language governing permissions and limitations. -->

## 해결한 문제와 결과

사용자 데이터 라이브러리 Epic #1381의 등록·선택·재설정·참조/삭제·본문·VM 저장본과 **복제 후 새 버전 등록**을 개선한다. UD-01 #1382부터 UD-10 #1391까지 구현·31번 배포·범위 내 검증을 완료했다. Epic 진행은 **10/11**, #1392와 Epic은 OPEN이며 PR은 Draft로 유지한다.

기존 ListView/ResourceLayout/ResourceView/InfoCard/MoldDialog와 테마 토큰을 재사용한다. 서버 검색/페이지, 현재값/모드·템플릿 정책, 실패 정합성, UUID/소유 범위를 고정한 일괄 삭제, 사용 VM·템플릿·ISO 전용 탭, 엄격 본문 표시/복사·다운로드와 VM 저장본 비교를 제공한다.

## UD-10 복제·버전 정책

원본 이름·본문·변수를 같은 소유 범위에 채우고 변경된 본문을 새 UUID로 등록한다. 기존 참조는 유지한다. 동일 소유자/동일 본문의 이름·변수만 다른 등록은 거절하며, 수정 API나 자동 버전 번호를 만들지 않는다. 원본/편집 미리보기·바이트 차이와 적용/되돌리기 안내를 제공한다.

등록 응답 유실은 정확한 결과 조회로 복구하고, 출처 저장만 실패하면 이미 생성된 객체를 다시 만들지 않는다. 댓글의 255자 API 제한에 맞춰 UUID·바이트 수만 기록하며 버전 탭에서 표시한다. 이는 삭제·가시성 변경 가능한 참고 기록이다. 결과/참조 링크는 대기 중 이동을 막고 완료 후 작업창을 닫은 다음 이동한다. 상세 경로와 공유 CNI 분기·포커스를 함께 보완했다.

## 검증과 배포

- 최종 **6 suites / 174 tests**, 변경 파일 ESLint, production UI build 통과. 실제 정량 **90항목** 통과.
- 관리자·일반 사용자·도메인 관리자·프로젝트 UI 복제6건의 소유자/UUID/변수/본문 해시와 출처 메모를 API/DB로 대조했다. 기존 VM5대·템플릿 참조 보존, 전용 VM/템플릿 전환 및 정확 복원, BOM 없는 APPEND VM 실제 생성과 UI 저장본 차이를 확인했다.
- 실제 이름 경쟁431·권한432·원본 삭제, 통제한503/403, 등록/출처 응답 유실, 출처만 재시도, 대기 링크/키보드/닫기 잠금, 복사/다운로드12,012바이트, 일반/다크4 viewport씩8조합과 VM32px 기준을 확인했다.
- UI `ca99503ac2a511638baef74a60346ce4f0bb70f0`, `js/app.c9e3887d.js`, 정적870개 해시 일치. 기존 JAR/12클래스·WEB-INF/config/META-INF·PID1247065 보존, mold active, /client/200. 전체 Cloud/Maven/GHA 빌드는 실행하지 않았다.
- 전용 자원/메모 정리: API/DB 활성 잔여0, 운영 라이브러리 전체 응답 동일, 브라우저 상태 복원.
- 완전한 관측14구간 중12개 JS예외0. 프로젝트 진입/종료의 기존 공통 ERR_CANCELED 각1건은 #1392 추적 대상이다. 모든 UI 상태의 최종 적합성이나 전체 CI 통과로 확대하지 않는다. PR 병합과 호스트 배포는 하지 않았다.

[UD-10 설계](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/design.ko.md) · [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/report.ko.md) · [정량 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/qualification.json) · [배포 해시](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1391-20261016/runtime-final.json)

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

앞선 이슈의 공급자/게스트 실행 검증은 해당 보고서의 범위이며 이번 정지 VM 검증과 합산하지 않는다.

## 남은 이슈

#1392: 목록·상세·7개 탭/콘텐츠와 모든 관련 모달의 최종 통합 표준 검토. 일반/다크·1600/1440/1280/1024/390·200% 확대, popup/hover/focus/오류/비활성·역할/프로젝트·공통 취소 예외를 확인한다. 완료 후 Epic 마무리와 PR 최종 검토를 진행한다.
