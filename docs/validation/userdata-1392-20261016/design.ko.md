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

# UD-11 #1392 최종 통합 검토 설계

기준: #1201 상세 탭 표준, #1213 MD-01~MD-12, 현재 Epic PR #1393의 VM 화면. 기존 사용자 요청에 따라 Footer 버튼은 VM과 같은 32px를 유지한다.

- 목록/상세 공통 ListView·ResourceLayout·InfoCard·ResourceView를 유지하고 7개 탭을 URL·콘텐츠와 대조한다.
- 등록/복제 필드 하단 6px를 20px로 보완한다. 삭제 본문은 16px 독립 간격, 버전 기록은 16px 독립 간격을 둔다. 본문 도구 업데이트는 공통 reload+업데이트로 맞춘다.
- 두 테마의 실제 색상을 계산한다. 부족한 대비는 기존 의미 토큰을 해당 영역에 적용한다. 포커스/비활성/오류/popup도 포함한다.
- 대시보드의 종료되지 않은 조회 Promise와 해제되지 않는 프로젝트 watch를 수정한다. API 취소는 여전히 reject이며, 화면 어댑터가 정상 취소를 종료 처리한다. route/session/project 및 같은 인자 최신 요청만 반영한다. 실제 실패는 기존 결과와 오류/재시도를 함께 표시한다.
- 모든 지정 viewport와 200% 레이아웃을 검증하고 기본/다크·계정/도메인 관리자/프로젝트·실제 등록/참조/재설정/삭제·오류/지연을 독립 근거로 남긴다. 기존 운영 자원은 수정하지 않는다.
- UI 정적 파일만 31번 활성 webapp에 배포한다. JAR·12개 클래스·WEB-INF/config·관리 PID를 보존한다. 전체 Cloud 빌드·병합은 수행 범위에 없다.

연계 화면 추가 발견: UpdateTemplate/UpdateISO를 같은 MoldDialog에 연결한다. 기존 필드/그리드는 보존하고 사용자 데이터 선택만 기존 UserDataSelection으로 바꿔 소유 범위·서버 검색/페이지·선택 정합성을 재사용한다. 기존 ISO isoid 인자 대신 실제 API의 templateid를 사용한다. metadata update 뒤 linkUserDataToTemplate를 기다린 다음에만 성공/닫기하며, 부분 실패는 입력과 명시적 오류를 유지한다.

추가 재현과 보완: 프로젝트 API 목록 조회를 기다린 뒤 SetProject/테마/라우팅을 실행한다. 기본 보기 id0은 빈 프로젝트{}로 정규화하여 VM 생성의 projectid=0/431 오류를 방지한다. 코멘트는 저장 성공 전까지 초안을 유지하고 중복 쓰기·옛 로그인/프로젝트/자원 응답을 차단한다. 관리자 전용 checkbox의 상태도 제어한다. 코멘트 조회 권한이 없으면 탭을 숨기고 잘못된 직접 URL은 상세로 정상화한다. 이미지 연결 실패 안내는 Body 안으로 스크롤·포커스하여 보이게 하며, 처음/검증 오류 포커스를 명시한다. VM 재설정은 비동기 조회 완료 뒤 최초 변경 스위치 또는 안전 취소에 포커스한다.

## 사용자 지적에 따른 최종 정정

목록에 별도로 넣은 검색·정렬·프로젝트 설명은 VM 표준에 없는 형식이었다. 목록의 listDescription 경로를 제거하고 참조 탭의 검색/범위/해제 설명 및 상태 합계, 본문 탭의 계약/표현 설명, 버전·복제의 일반 정책 펼침, 삭제 후속 안내, 재설정의 조작 안내를 제거했다. 검색·필터·표·서버 페이지 및 실제 오류·작업 결과·영향 경고는 기존 공통 구조를 유지한다. 빈 변경 기록도 간단히 표시한다. 기능/API 계약 설명은 검증 문서로 옮기며 화면에 새 설명 블록을 추가하지 않는다.
