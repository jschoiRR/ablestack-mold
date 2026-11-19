<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->
## 해결한 문제와 결과

사용자 데이터 라이브러리 Epic #1381의 등록·선택·재설정·삭제 흐름과 참조 정보 부족을 개선한다. UD-01 #1382부터 UD-07 #1388까지 구현·31번 배포·범위 내 검증을 완료했다. 현재 진행은 **7/11**이며 후속4개와 Epic은 OPEN, 이 PR은 Draft다.

등록은 Base64 정규화·바이트 한도·공백/BOM·변수·중복과 소유자 검증을 보완하고 공통 MoldDialog로 복구/중복 제출/이탈 잠금을 제공한다. 선택은 서버 검색/페이지와 UUID 보존을 적용한다. VM 현재값·설정 안함·템플릿 정책·지원 공급자의 재설정 저장/전달을 보완하고 삭제는 UUID/소유 범위를 고정하여 부분 실패·미확인·실패만 재시도한다.

## UD-07 #1388의 최종 변경

라이브러리 상세에 `사용 가상머신`, `연결 템플릿`, `연결 ISO` 탭을 추가했다. VM 표준 ListView/ResourceView/ResourceLayout/InfoCard/Status와 공통 테마·32px 버튼을 재사용한다. VM은 기존 userdataid 필터를 사용하고 템플릿/ISO에는 같은 UUID 필터를 추가하여 기존 ACL·검색과 결합한 SQL count/page를 반환한다. 관리자에게 허용된 교차 소유 참조를 소유자 클라이언트 필터로 누락하지 않는다. 현재 프로젝트/기본 보기 범위와 서버 검색·현재 페이지 정렬 의미를 표시한다.

목록은 저장된 UUID 관계다. 템플릿/ISO 본문만 상속한 VM은 포함하지 않으며 게스트 실행 성공으로 해석하지 않는다. 동일 범위 갱신에서 빈 결과를 포함하여 기존 데이터를 보존하고 오류·권한·미확인을 0건과 구분한다. 늦은 응답을 폐기하고 탭별 URL/뒤로 가기를 복원한다.

삭제창은 참조 건수·탭 링크·해제 경로를 먼저 보여준다. 조회 중 제출과 처리 중 닫기/참조 이동을 막고 최종 서버 거절·경합을 처리한다. 템플릿/ISO 편집의 빈 해제 선택지는 `설정 안함`으로 표시한다. 기존 외부 VM 보기·편집 경로와 #1387 부분 실패 계약을 유지한다.

## 검증·배포

- #1388: WSL ext4 변경 api/server 모듈 빌드, 서버 SQL 경계3건, UI2 suites/44건, 변경 파일 ESLint, production UI build 통과. API UUID/ACL/count/page/검색 등11개 행렬과 실제 UI 생성·연결 변경·해제·이미지 편집·삭제 경합을 확인했다. 증거 정합성46항목 통과.
- 일반/다크 × 네 화면 크기의 목록8개·삭제창8개, 문서 가로 넘침 없음·표 내부 스크롤·32px/padding4px15px 버튼·작은 화면 고정 Header/Footer와 Body 스크롤을 실측했다. 이 범위의 검증을 전체 메뉴 최종 적합성으로 계산하지 않는다.
- 31번 UI `f6396f8cf4360e899677f21b7be64381d09447b3`, app `325357a9`, 정적861개·최종12클래스 해시 일치. 기존9클래스·WEB-INF/config 보존, 백엔드 배포 후 PID1247065를 UI 배포에서 유지, mold active,/client/200.
- VM/볼륨16개씩·이미지4개·라이브러리5개·네트워크3개와 계정/프로젝트/도메인/역할 및 추가 빈 프로젝트를 정리했다. DB10종 활성잔여0, 기존 라이브러리1개 전체 응답 동일. 관리자·다크·기본 보기·브라우저 설정 복원.
- 완전한 요청6구간 중5구간은 JS 예외0, 프로젝트 전환 구간에는 공통 CancelToken 취소 예외1건이 있다. 새 참조 컴포넌트0인 목록 전환에서도 같은 공통 `AutogenView.beforeRouteLeave` 경로를 분리 재현했고 #1392에 후속 검토를 남긴다. 역할 전환1구간은 버퍼 과거 이력 잘림으로 완전한 요청 증거에서 제외했다.
- UI build 크기 경고2개·Browserslist 안내, Jest --forceExit 종료 대기 경고가 있다. 전체 Cloud 빌드·수동 Actions·qemu 배포·PR 병합은 실행하지 않았다. 집중 검사·실환경 검증을 전체 CI로 표시하지 않는다.

[UD-07 설계](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/design.md) · [보고서·화면](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/report.ko.md) · [정량 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/qualification.json) · [최종 런타임](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/runtime-final.json)

## 기존 완료 범위

| 이슈 | 범위 | 검증 보고서 |
| --- | --- | --- |
| #1382 | 등록 입력·정규화·중복 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1382-20261015/report.ko.md) |
| #1383 | 공통 등록 대화상자·VM 버튼 기준 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1383-20261015/report.ko.md) |
| #1384 | 서버 검색·페이지·선택 보존 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1384-20261016/report.ko.md) |
| #1385 | 현재값·활성 입력·템플릿 정책 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1385-20261016/report.ko.md) |
| #1386 | 지원 공급자 재설정·저장/복원·게스트 검증 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1386-20261016/report.ko.md) |
| #1387 | 일괄 삭제 노출·부분 실패·재확인 | [보고서](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1387-20261016/report.ko.md) |

기존 완료 이슈의 전체 테스트를 이번 #1388에서 모두 재실행한 것으로 계산하지 않는다.

## 후속 이슈

1. #1389: 목록·상세·본문 탭의 VM UI 표준과 일반/다크 테마 정비.
2. #1390: VM 저장 사용자 데이터 조회 UI 접근 경로.
3. #1391: 복제·버전·변경 정책.
4. #1392: 전체 UI 표준 최종 통합 검토와 공통 경로 전환 취소 예외·기존 미검증 범위의 회귀.
