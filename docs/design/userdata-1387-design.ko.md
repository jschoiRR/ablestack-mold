<!-- Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements. See the NOTICE file distributed with this work
for additional information regarding copyright ownership. The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not use
this file except in compliance with the License. You may obtain a copy at
http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or
agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
or implied. See the License for the specific language governing permissions
and limitations under the License. -->
# UD-06 #1387 사용자 데이터 일괄 삭제 설계

## 문제와 범위

최신 upstream Europa `4bd1a2cff50c83089dc23ff1c83def631a343e3d`, Epic 누적 소스 `006db373775ccf3991fcd7a17796408b91a10333`에서 시작한다.
`deleteUserData`의 groupAction에 toolbarLabel이 없어 선택 후 명시적인 작업이 보이지 않는다.
기존 일괄 경로는 현재 목록으로 groupMap을 다시 만들며 사용자 데이터용 항목별 오류/재시도 계약이 없다.
UD-06은 삭제 노출, 대상 고정, 단건/일괄 확인·진행·결과·재시도를 다룬다. 사용 VM 탭과 참조 상세는 #1388이다.

## 동작 계약

- 기존 AutogenView/ActionButton의 toolbarLabel 권한 필터와 SSH 삭제의 selfManagedDialog 패턴을 따른다.
- 작업 시작 시 UUID·이름·account/domain 또는 project의 불변 스냅샷을 만든다. 삭제 API는 UUID와 배타적인 소유 범위만 보낸다.
- 매 항목 POST 전 소유 범위가 일치하는 UUID를 조회한다. 불완전 응답·조회 실패·신원 차이는 차단한다.
- 삭제된 UUID 변환이431이면 소유 범위/이름으로 완전한 목록을 조회하여 원래 UUID 부재를 확인한다. 같은 이름의 새 UUID를 삭제하지 않는다. 500건을 넘겨 완전성을 확인할 수 없으면 차단한다.
- 순차 요청으로 결과를 항목에 보존한다. 실제 deleteuserdataresponse.success=true 또는 재조회 부재만 완료다.
- 오류 본문의 command-response를 읽는다. 응답 유실·서버 오류는 재조회하고 조회 불가이면 결과 미확인을 유지한다. 미확인 항목은 자동 재제출하지 않는다.
- 실패만 재시도한다. 차단/미확인은 상태 확인 후 현재 동일 UUID 존재가 확인되어야 재시도 가능하다.
- 세션·프로젝트·전체 프로젝트 보기·route 변경/권한 소실 시 이후 요청을 중단한다. 이미 진행한 요청의 결과를 추측하지 않는다.
- 완료 창을 닫을 때 삭제 확인된 항목만 목록에서 제거하고, 현재 동일 화면 범위의 실패 항목 선택을 보존한다. 상세에서 삭제되면 라이브러리 목록으로 돌아간다.
- 서버의 기존 deleteUserData ACL 및 VM 사용 참조 차단을 유지한다. 서버 변경/전체 Cloud 빌드는 필요 없다.

## UI 기준과 검증 계획

공통 MoldDialog lg, AntD Table/Badge/Alert/Button, 기존 테마와 i18n을 재사용한다. Header/Footer 고정·Body 스크롤,
취소→위험 삭제 버튼32px, count/UUID/소유 범위·항목별 사유를 표시한다. 진행 중 닫기·Escape·중복 제출을 막고 beforeunload로 이탈을 경고한다.
일반/다크 × 1440×1000·1280×720·390×844·390×480을 확인한다. 좁은 화면 표는 가로 스크롤한다.
전용 클러스터31 시험 항목으로 미사용2개 이상·사용중 혼합·실패 재시도·권한 오류·일반/도메인 관리자·프로젝트·조회오류·응답 유실을 실제 UI/API/DB와 대조한다.
정적 UI만 배포하며 WEB-INF/config/JAR/9클래스/PID 보존, HTTP200·mold active·모든 배포 정적 파일 해시를 검증한다.
완료 후 시험 자원을 정리하고 기존 라이브러리 전체 API 응답이 같은지 확인한다. 검증 결과는 별도 보고서에 기록한다.
