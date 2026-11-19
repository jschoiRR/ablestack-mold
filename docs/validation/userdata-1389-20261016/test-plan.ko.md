<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->

# UD-08 실제 검증 행렬

- 공통 VM 기준: 상세/모바일 레이아웃과 32px·padding4px15px 대화상자 버튼.
- API: UUID, 정확한 이름, 이름 일부, 계정·도메인, 미검색 0건, 10/2 서버 페이지, 일반·도메인 관리자·별도 계정, 프로젝트 멤버·관리자.
- UI 목록: 동일 검색 경로, 고급 필터 popup, 현재 페이지 정렬, 선택 보존/갱신, 페이지 이동, 0건.
- 상세: 메타데이터·본문·VM·템플릿·ISO·코멘트 탭 순서, 활성·URL·콘텐츠, 뒤/앞/새로고침, 프로젝트 메타데이터.
- 본문: 12,012바이트 UTF-8 BOM·CRLF·한글·emoji·긴 행·끝 줄바꿈·script 문자열 이스케이프. 원문/Base64 DOM·실제 클립보드·실제 다운로드 SHA256 대조. 바이너리는 Base64 안내.
- UI 상태: 실제 API 응답에 제어한 XHR 지연/503/403/431/빈 본문/잘못된 Base64/식별 불일치로 상태 구분과 재시도. 제어한 실패를 실서버 자연 장애로 보고하지 않는다.
- 일반·다크 각각 1440×1000,1280×720,390×844,390×480에서 목록·본문·참조·메타데이터·오류 실측. 등록 대화상자 버튼은 VM 기준과 최종 번들 다크 데스크톱·390×480에서 별도 회귀 확인하며, 모든 대화상자의 최종 통합 감사는 #1392에서 수행한다. 코드와 표만 내부 스크롤.
- 역할: 관리자, 일반, 도메인 관리자, 프로젝트, listUserData 거절 역할. API 발견 권한에 따른 메뉴 노출.
- 전용 참조 VM·템플릿·ISO UUID를 UI/API/DB 대조하고 정리 후 활성 자원 10종=0, 기존 라이브러리 전체 응답 동일 확인.
- 전체 Cloud CI 또는 전체 UI 최종 감사로 확대하여 보고하지 않는다. 공통 CancelToken 취소 오류는 #1392 후속에 남아 있다.
