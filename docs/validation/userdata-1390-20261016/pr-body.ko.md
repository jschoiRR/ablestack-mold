<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License for the specific language governing permissions and limitations. -->

## 해결한 문제와 결과

사용자 데이터 라이브러리 Epic #1381의 등록·선택·재설정·참조/삭제·본문과 VM 저장본 조회를 개선한다. **UD-01 #1382부터 UD-09 #1390까지 구현·31번 배포·범위 내 검증을 완료**했다. 진행은 **9/11**이며 #1391·#1392와 Epic은 OPEN, PR은 Draft로 유지한다.

등록 정규화·소유 범위 중복·오류 복구와 MoldDialog, 서버 검색/페이지·선택 유지, 현재값/모드/템플릿 정책 재설정과 공급자 실패 정합성, UUID/소유 범위를 고정한 일괄 삭제와 부분 실패 복구를 제공한다. 사용 VM/템플릿/ISO 전용 탭은 서버 UUID 필터·ACL·count/page로 조회한다. 목록·상세·본문·코멘트와 일반/다크는 기존 ListView/ResourceLayout/ResourceView/InfoCard/MoldDialog와 공통 스타일을 재사용한다.

## UD-09 VM 저장본 조회

VM 상세 작업 메뉴에서 `getVirtualMachineUserData`로 읽기 전용 저장본을 조회한다. 라이브러리 사용 VM 탭의 기존 상세 링크를 통해 같은 작업으로 접근한다. API 발견/매개변수에 따라 노출하며 일반 사용자와 프로젝트도 서버 권한 범위로 사용한다.

기존 UserDataContent의 원문/Base64·엄격 UTF-8·복사/다운로드·빈값/바이너리·오류/재시도를 재사용한다. 원본과 저장본은 바이트 단위의 동일/차이/직접 참조 없음/비교 불가로 구분한다. VM 메타데이터의 정책·변수 값과 공급자 전달·게스트 실행의 의미를 구분한다. 전후 revision/UUID/소유 범위와 VM·프로젝트·세션·해제 뒤 늦은 응답을 검사한다. 원본 권한이 없어도 별도로 허용된 저장본을 표시한다.

## 검증과 배포

- 최종 소스 Jest **4 suites / 102 tests**, 변경 파일 ESLint와 production UI build 통과. 실환경 정량 **118항목** 통과.
- 전용 정지 VM10개 API/DB 본문 바이트·해시 대조. 실제 원문 복사/다운로드12,012바이트, Base64 16,016바이트 일치. 원본/APPEND/인라인/빈값/바이너리·권한/API 거부·503/403·손상·재시도·실제 VM 변경/삭제·지연 응답을 검증했다.
- 일반/다크 4 viewport씩8조합, 최종 화면38개. 32px 버튼, 중앙·고정 Header/Footer·Body/코드 스크롤, 포커스·키보드, 공유 라이브러리 본문 회귀를 확인했다.
- UI `8d79de0db24b0e432a381b8c532e4fb361ffaa12`, app `js/app.6abf6820.js`, 정적867개 해시 일치. 기존 JAR/12클래스·WEB-INF/config/META-INF 상태·PID1247065 보존, mold active, /client/200. Java 변경이 없어 이번 Maven 빌드는 없다.
- 전용 테스트 개체 전부 정리: API/DB 활성 잔여0, 운영 라이브러리 전체 API 응답 동일, 브라우저 상태 복원.
- 완전한 네트워크 구간21개 중19개 JS예외0. 프로젝트 진입/종료2개는 기존 공통 ERR_CANCELED 각1건이며 전체 스택을 #1392에서 추적한다. 전체 세션 예외0·전체 UI 최종 적합성으로 해석하지 않는다.
- 자산 크기 경고2/Browserslist/Jest forceExit 안내가 있다. 전체 Cloud 빌드·수동 Actions·qemu/호스트 배포·PR 병합은 수행하지 않았다. 집중 검증과 전체 CI를 구분한다.

[UD-09 설계](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/design.ko.md) · [전체 보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/report.ko.md) · [정량 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/qualification.json) · [배포 해시](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1390-20261016/runtime-final.json)

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

앞선 이슈의 테스트/게스트 실행은 해당 보고서의 검증이며 이번 UD-09 결과와 혼합하여 합산하지 않는다.

## 남은 이슈

1. #1391: 라이브러리 복제·새 버전·중복/변수·출처·전환/복원 및 BOM APPEND 형식 정책.
2. #1392: 전체 목록·상세·탭/콘텐츠·대화상자·일반/다크/모바일/확대의 최종 통합 표준 검토. 공통 경로 취소 예외와 기존 미검증 경로도 포함한다.
