<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an AS IS BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. -->
# #1388 사용자 데이터 참조 설계

- 상세의 `사용 가상머신`, `연결 템플릿`, `연결 ISO` 탭은 ResourceView/ResourceLayout/ListView/Status와 공통 테마를 재사용한다. 기존 외부 가상머신 링크와 템플릿·ISO 편집 액션은 유지한다.
- 목록은 서버에 저장된 userdataid UUID 관계다. 본문 일치나 게스트 cloud-init 실행 성공으로 관계를 추정하지 않는다. 템플릿 상속 본문은 실제 VM의 userdataid가 있는 경우만 포함한다.
- VM은 기존 listVirtualMachines(userdataid), 템플릿·ISO는 listTemplates/listIsos의 새 UUID 필터를 사용한다. SQL 조건은 ACL·기존 검색 조건과 AND로 결합하며 count/page 전에 적용한다. showunique=true로 다중 존에 중복 집계하지 않는다. 필터 생략 시 기존 동작을 유지한다.
- 서버 ACL이 접근 가능한 관계를 결정한다. 관리자에게 허용된 다른 소유자 참조를 라이브러리 소유자로 재필터링하지 않는다. 라이브러리 프로젝트가 있으면 그 프로젝트, 아니면 현재 선택 프로젝트 또는 기본 보기다. 다른 프로젝트는 보기 전환이 필요하다는 범위를 명시한다.
- 일반 사용자는 executable, 관리자는 all 템플릿/ISO 필터를 기존 ACL 그대로 사용한다. 목록은 해당 필터로 접근 가능한 리소스다. 미준비·비공개 리소스의 추가 접근권을 부여하지 않는다.
- 검색/총 건수/페이지는 서버 기준이며 정렬과 상태 요약은 현재 페이지 기준이다. 각 탭 query 상태를 분리하고 router history로 복원한다.
- 조회 전후 원본 UUID/이름/소유 범위를 재확인하고 세션·프로젝트·탭·조회 조건 변경 시 지연 응답을 폐기한다. 동일 범위 새로고침은 성공했던 빈 결과를 포함하여 데이터를 유지하고 실패를 0건으로 간주하지 않는다.
- 삭제창은 현재 ACL/보기의 참조 건수와 탭 링크, VM 재설정/템플릿·ISO 연결 해제 경로를 먼저 제공한다. 일부 조회 실패는 미확인으로 표시한다. 실제 삭제의 서버 최종 거절·경합 처리와 #1387 재확인/재시도 계약은 유지한다. 강제 삭제는 추가하지 않는다.
- 전용 테스트 데이터로 UI 생성/재설정·API·DB, 0/1/복수/페이지경계, 권한/프로젝트, 일반/다크 및 네 화면 크기를 확인하고 모두 정리한다. 전체 Cloud 빌드는 실행하지 않는다. #1389 상세/본문 정비와 #1392 최종 통합 표준 검토는 별도다.
