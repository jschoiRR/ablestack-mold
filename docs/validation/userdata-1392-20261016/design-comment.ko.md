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

## UD-11 설계·구현 진행

목록/상세/7개 탭·VM 생성/재설정/저장 본문·템플릿/ISO 선택/연결·등록/복제/삭제를 최종 점검 대상으로 확정했습니다. #1201과 MD-01~MD-12를 기준으로 두 테마, 지정7 viewport 및200% CSS 레이아웃을 검증합니다. 기존 사용자 요청에 따른 VM32px Footer 버튼을 유지합니다.

실제 기존31번 UI에서 프로젝트 전환 CanceledError1건과 등록 폼 하단6px, 작업 hover 글자대비2.5188:1, 템플릿/ISO의 legacy 모달·첫 페이지 선택기·연결 완료 전 닫기/ISO 인자 차이를 확인했습니다. 재현과 수정 범위는 하위 결함 #1394에 연결했습니다.

구현 `d46d9959e64888f846b10abd00be06ddc59d8410`을 Epic PR #1393 브랜치에 올렸습니다. 공통 테마 토큰·MoldDialog·UserDataSelection·상세 탭 툴바를 재사용하고, 대시보드 조회에는 수명주기/최신 요청/정상 취소 처리를 적용합니다. 기존 API의 취소 rejection 계약은 유지합니다. 이미지 연결은 실제 templateid로 처리하고 완료를 기다린 다음 닫으며 부분 실패는 폼을 보존합니다.

기존273검사와 새20검사가 통과했습니다. 현재 production UI 빌드 중이며 이후31번 정적 배포와 실환경 검증을 수행합니다. 아직 최종 적합/완료 판정이 아닙니다. 전용 정지VM13대·템플릿/ISO와 계정/프로젝트를 준비했고 운영 자원은 변경하지 않았습니다.
