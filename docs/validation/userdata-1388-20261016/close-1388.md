<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->
## 구현·31번 배포·범위 내 검증 완료 — 2026-10-16

전용 사용 가상머신/연결 템플릿/연결 ISO 탭, 템플릿·ISO 서버 UUID 필터와 SQL count/page, 삭제 전 참조 건수·해제 경로·최종 거절 처리를 구현했다. VM 표준 공통 컴포넌트·일반/다크·32px 버튼을 재사용한다.

- Java 집중3건·UI2 suites/44건·ESLint·변경 api/server 모듈 및 production UI build 통과. API 행렬11개와 실제 UI 생성/변경/해제·ACL·검색/페이지·실제 삭제 경합 검증. 증거 정합성46항목 통과.
- UI `f6396f8cf4360e899677f21b7be64381d09447b3`, 정적861개/최종12클래스 해시, WEB-INF/config 보존, UI 배포 PID1247065 보존, mold active,/client/200.
- 전용 VM/볼륨16개씩과 이미지4개·라이브러리5개·네트워크3개·계정/프로젝트/도메인/역할 및 추가 분리 시험 프로젝트 정리. DB10종 활성잔여0, 기존 라이브러리1개 전체응답 동일.
- 공통 경로 전환의 CancelToken 미처리 Promise 예외를 새 참조 컴포넌트0 상태에서 분리 재현했다. 이번에 변경하지 않은 공통 AutogenView 경로이며 #1392에 후속 검토를 남긴다. 전체 UI 최종 적합성을 이 이슈 종료로 계산하지 않는다.
- 전체 Cloud 빌드·수동 Actions·PR 병합은 실행하지 않았다. Epic #1381은 7/11, #1389~#1392와 Epic은 OPEN, Epic PR #1393은 Draft 유지.

[설계](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/design.md) · [상세 보고서·화면](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/report.ko.md) · [정량 근거](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/qualification.json) · [최종 배포](https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1388-20261016/runtime-final.json)
