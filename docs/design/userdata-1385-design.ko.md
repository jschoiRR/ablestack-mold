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

# UD-04 #1385 현재값·입력 모드·설정 안함

Epic #1381 / Draft PR #1393에 UD-03 선택기 위의 재설정 계약을 누적한다.
현재 `resetUserDataForVirtualMachine`은 매번 전체 사용자 데이터 설정을 교체한다.
빈 요청을 현재값 유지로 해석하면 연결과 변수가 지워지므로 UI에서 유지와 변경을 구분한다.

- 열 때 실제 VM 또는 AutoScale 프로필을 다시 조회한다. VM은 기존 ACL을 적용하는
  `listVirtualMachines(id, userdata=true)`로 본문을 읽는다. 일반 조회에는 본문을 추가하지 않는다.
  템플릿 기본 데이터는 VM library ID가 없어도 변수값이 필요하므로 명시적 본문 조회의
  응답에 기존 userdatadetails 필드를 함께 넣는다. 새 API/DB 스키마는 추가하지 않는다.
- 기본은 **현재 설정 유지**이며 확인을 눌러도 변경 API를 보내지 않는다. 변경 스위치를 켠
  경우에만 편집 폼을 적용한다. 기존 본문·라이브러리 이름·변수값을 표시하며 현재 내용은 읽기 전용이다.
- 라이브러리와 수동 입력은 각각 초안을 유지하되 제출할 때 활성 모드의 `userdataid` 또는
  인코딩 본문 `userdata` 하나만 보낸다. 숨겨진 초안·이전 라이브러리 변수는 전송하지 않는다.
  같은 선택기의 재마운트는 변수값을 보존하고 다른 행/설정 안함은 사용자 지정 변수를 초기화한다.
- 변경을 켜고 **설정 안함**을 적용하면 사용자 지정 연결/본문/변수를 지운다. 템플릿 연결이
  있으면 해당 기본 데이터로 돌아가며 템플릿 변수·정책은 적용된다. 안내 문구를 각각 제공한다.
  VM과 AutoScale 모두 빈 `userdata=''`로 의도를 나타내며 sentinel `0`을 API에 보내지 않는다.
- ALLOWOVERRIDE는 override 여부에 따라 템플릿 또는 사용자 지정 변수만, APPEND는 양쪽의
  유효 변수 합집합을 전송한다. 중복 키는 한 번만 보내며 사용자 지정 입력을 우선한다.
  DENYOVERRIDE는 사용자 지정 입력을 노출하지 않는다.
- APPEND의 기존 API는 원래 사용자 지정 본문을 별도로 저장하지 않고 합성 결과만 저장한다.
  그 결과를 다시 추가 입력으로 사용하면 템플릿이 중복된다. 현재 합성 본문은 정확히 표시하고,
  유지 모드에서는 바이트를 보존한다. 새 재설정에서는 **새로 추가할 본문**을 명시적으로 입력한다.
  원본 초안을 복원했다고 주장하거나 합성 결과를 임의로 분해하지 않는다.
- legacy 변수 응답은 Java Map 문자열이다. 선언된 변수 경계만 사용해 한글·쉼표·등호를 보존한다.
  반복 키 등 명백한 모호성은 오류로 차단한다. 이 직렬화 형식 자체의 완전한 역변환 한계는 남으므로
  현재 원문과 유지 경로를 제공하고 새로운 구조화 API로 변환한 것으로 표시하지 않는다.
- 현재값/정책 조회 실패는 적용 차단과 재시도, 제출 실패는 폼·선택·초안을 유지한다.
  제출 오류는 모달 Body 한 곳에 표시하여 전역 알림이 후속 작업 버튼을 가리지 않도록 한다.
  조회 세대와 컴포넌트 수명을 검사하여 이전 자원 응답이나 닫힌 창의 응답을 적용하지 않는다.
- 공통 MoldDialog·AntD·i18n·테마 토큰·32px VM 버튼을 재사용한다. 일반/다크와 네 viewport,
  권한·프로젝트·지연·오류/재시도, 실제 전용 VM의 UI 요청/저장값과 정리를 확인한다.

공급자 실패 시 이미 저장된 VM 값의 롤백·게스트 실행은 #1386, 전체 메뉴 UI 표준 판정은
#1392다. 전체 Cloud 빌드는 실행하지 않고 변경 server 모듈과 UI만 검사·빌드한다.
