<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->

## 해결한 문제와 결과

사용자 데이터 라이브러리 Epic #1381의 등록·선택·재설정·삭제·참조·본문 조회와 VM UI 일관성을 개선한다. UD-01 #1382부터 UD-08 #1389까지 구현·31번 배포·범위 내 검증을 완료했다. 진행은 **8/11**이며 #1390·#1391·#1392와 Epic은 OPEN, 이 PR은 Draft다.

등록은 입력 정규화·바이트 한도·변수·중복·소유자 검증과 공통 MoldDialog를 제공한다. 선택은 서버 검색/페이지와 UUID 보존을 적용한다. VM 현재값·설정 안함·템플릿 정책·지원 공급자 재설정 저장/전달을 보완하고, 삭제는 UUID/소유 범위를 고정하여 부분 실패·미확인·실패만 재시도한다. 사용 VM/연결 템플릿/ISO는 서버 UUID 필터·ACL·count/page와 전용 참조 탭으로 표시한다.

## UD-08 #1389 최종 변경

공통 ResourceLayout·InfoCard·ResourceView·DetailsTab·ListView·SearchView를 유지하고 상세 → 본문 → 사용 VM → 연결 템플릿 → 연결 ISO → 코멘트로 구성한다. 메타데이터에는 프로젝트와 동적 파라미터 키를 표시한다. UUID·정확한 이름·계정/도메인 검색, 상단 프로젝트 선택, 현재 페이지 정렬 안내를 연결한다.

본문은 읽기 전용 원문 UTF-8/Base64와 실제 복사·다운로드를 제공한다. 엄격한 해석으로 BOM·CRLF·한글·emoji·긴 행·끝 줄바꿈을 보존한다. 조회 식별·권한·삭제·빈 본문·손상 인코딩·일시 오류를 구분하고 갱신 중 기존 본문/선택을 유지한다. 늦은 응답 폐기·중복 조회 병합을 적용했다. 기존 참조 탭 계약과 CNI 상세는 보존한다.

## 검증과 31번 배포

- #1389: UI 3 suites/77 tests, 변경 파일 ESLint, production UI build, API 14항목과 증거 정합성 181항목 통과. 원문 복사/다운로드 12,012바이트, Base64 복사/다운로드 16,016바이트와 디코딩 원문이 일치한다.
- 일반·다크 × 네 화면 크기에서 목록·메타데이터·본문·사용 VM·오류 40개 화면을 실측했다. 문서 가로 넘침 없음·코드/표 내부 스크롤, 본문 32px 버튼·16px 새로고침 SVG, 공통 등록창 취소/등록의 32px/padding4px15px 회귀를 확인했다.
- UI 소스 `8629c06d37a4f51c0acb7b9cb084e7bc744c37e1`, app `50a3be37`, 정적 864개와 기존 백엔드 12클래스 해시 일치. WEB-INF/config/META-INF 상태·PID1247065 보존, mold active, /client/200. #1389에는 Java 변경이 없어 새 Maven 빌드를 수행하지 않았다.
- 라이브러리 21개와 전용 VM/볼륨/네트워크·이미지·계정/프로젝트/도메인/역할을 정리했다. DB10종 활성잔여0, 기존 라이브러리 전체 API 응답 동일. 관리자·기본 보기·다크·브라우저 시험 설정 복원.
- 완전한 최종 요청 27구간 중25구간은 JS 예외0, 프로젝트 진입/이탈 2구간은 공통 ERR_CANCELED 각1건이다. 전체 beforeRouteLeave/cancel 스택을 #1392에 추가했다. 새로고침 1구간은 이벤트 잘림으로 예외 집계에서 제외했다. 전체 세션 예외0 또는 전체 UI 최종 적합성을 주장하지 않는다.
- 자산 크기 경고2개·Browserslist 안내·Jest forceExit 안내가 있다. 전체 Cloud 빌드·수동 Actions·qemu/호스트 배포·PR 병합은 수행하지 않았다. 집중 검증은 전체 CI와 구분한다.

[UD-08 설계](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/design.ko.md) · [보고서·화면](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/report.ko.md) · [정량 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/qualification.json) · [런타임](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/runtime-final.json)

## 완료한 하위 범위

| 이슈 | 범위 | 보고서 |
| --- | --- | --- |
| #1382 | 등록 정규화·중복 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1382-20261015/report.ko.md) |
| #1383 | 공통 등록 대화상자·VM 버튼 규격 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1383-20261015/report.ko.md) |
| #1384 | 서버 검색·페이지·선택 보존 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1384-20261016/report.ko.md) |
| #1385 | 현재값·모드·템플릿 정책 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1385-20261016/report.ko.md) |
| #1386 | 지원 공급자 저장/복원·게스트 검증 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1386-20261016/report.ko.md) |
| #1387 | 일괄 삭제·부분 실패·재확인 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1387-20261016/report.ko.md) |
| #1388 | 사용 VM/템플릿/ISO 참조·서버 UUID/ACL/count/page | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/report.ko.md) |
| #1389 | 목록·메타데이터·본문·테마·실제 내보내기 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1389-20261016/report.ko.md) |

이전 완료 범위의 전체 테스트를 #1389에서 모두 재실행한 것으로 계산하지 않는다. #1388의 API/server 변경 모듈 빌드·SQL 경계3건과 12클래스 런타임 검증은 해당 보고서를 따른다.

## 후속 이슈

1. #1390: VM 저장 사용자 데이터 조회 API의 UI 경로와 원본/저장본 구분.
2. #1391: 라이브러리 복제·버전·변경 정책.
3. #1392: 모든 목록·상세·탭·대화상자·일반/다크·반응형 최종 통합 감사, 공통 취소 예외와 기존 미검증 범위 회귀.
