<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->

# UD-08 #1389 설계

기존 ResourceLayout·InfoCard·ResourceView·DetailsTab·ListView·SearchView 및 공통 테마 토큰을 유지한다. 사용자 데이터 리소스 설정에만 상세 → 본문 → 사용 VM → 연결 템플릿 → 연결 ISO → 코멘트를 구성한다. 메타데이터에는 ID·이름·소유 계정·도메인·프로젝트·동적 파라미터 키를 표시한다. CNI 상세 구성은 변경하지 않는다.

본문 탭은 읽기 전용 pre/code, 공통 AntD 버튼·라디오·알림을 사용한다. UTF-8 원문과 저장 Base64를 명시적으로 구분한다. 엄격한 Base64/UTF-8 해석으로 임의 추측·치환을 피하고 BOM·CRLF·긴 행·끝 줄바꿈을 보존한다. 선택한 표현을 복사/다운로드하며 원문 다운로드는 Uint8Array를 Blob에 넣어 인코딩 재변환을 피한다. 복사는 Clipboard API의 실제 쓰기 완료 후 성공을 알린다. HTTP·구형 환경은 임시 readonly textarea를 포커스·선택하고 copy 이벤트에 원문을 직접 전달하여 줄바꿈 정규화를 피한다. 임시 요소와 이벤트는 제거하고 기존 포커스를 복원한다. 빈 본문은 전역 등록된 공통 Alert로 표시하고, 로딩 watch의 동일 범위 중복 조회는 병합한다. 본문에만 가로·세로 스크롤을 허용한다.

본문 조회는 UUID와 소유 범위를 고정하고 응답 UUID·이름·소유 정보가 일치할 때만 게시한다. 계정·프로젝트·권한·대상·탭 전환과 언마운트는 이전 응답을 무효화한다. 같은 범위 재조회 중 본문과 표시 선택을 유지하며 내보내기는 완료까지 비활성화한다. 503은 이전 본문과 stale 안내, 403/432·삭제·식별 불일치는 본문 제거, 빈 본문과 잘못된 Base64는 별도 상태다.

검색은 기존 SearchView 필터에 ID·정확한 이름·도메인·계정을 노출한다. 일반 검색의 UUID는 API id로 보내고 일반 텍스트는 기존 이름 keyword 포함 검색을 유지한다. 프로젝트는 공통 상단 보기로 선택한다. 현재 페이지 정렬임을 안내한다. API·Java 변경이나 전체 Cloud 빌드는 필요하지 않다.

#1388의 전용 참조 탭·UUID 필터·ACL·count·서버 페이지·갱신 계약을 변경하지 않는다. 전체 메뉴·대화상자 표준의 최종 통합 감사와 공통 취소 오류는 #1392 범위로 남긴다.
