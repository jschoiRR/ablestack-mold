<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License for the specific language governing permissions and limitations. -->

# UD-09 VM 저장 사용자 데이터 조회 설계

대상은 upstream #1390, Epic #1381 / Draft PR #1393이다. VM 상세의 기존 작업 메뉴에 `getVirtualMachineUserData` 기반 읽기 전용 작업을 추가한다. 라이브러리의 사용 VM 탭은 기존 VM 링크를 통해 이 작업에 도달한다.

## API와 표시 의미

- `GetVMUserDataCmd` → `UserVmManagerImpl.getVmUserData`는 VM account 접근 권한을 검사하고 `user_vm.user_data`를 반환한다. `virtualmachineuserdata.virtualmachineid`를 선택 UUID와 대조한다.
- 동적 `listApis`의 `getVirtualMachineUserData` 및 `virtualmachineid` 매개변수로 노출한다. 역할 이름, 정지/실행 상태, 본문 유무에 따른 불필요한 허용 목록을 만들지 않는다.
- 재사용하는 `UserDataContent`에 VM 소스 모드를 추가한다. 원문/정규 Base64, 엄격 UTF-8, 바이너리, 복사, 바이트 다운로드, 오류·빈 본문, 동일 범위 갱신·지연 응답 방어를 공유한다.
- `ViewVmUserData`는 `MoldDialog size=lg`와 32px 닫기 버튼을 사용한다. 읽기 전용이므로 요청 중에도 닫을 수 있다. 닫으면 컴포넌트를 해제하여 늦은 결과가 표시되지 않는다.
- VM 이름/UUID, 연결 라이브러리, API의 템플릿 정책·변수 정보를 본문과 구분한다. 정책/변수는 저장된 메타데이터이다. ConfigDrive/VR 전달 또는 게스트 실행 결과를 의미하지 않는다.

## 일관성과 원본 비교

- 고정 VM UUID로 조회한다. 가능한 경우 소유자·프로젝트와 본문·참조·정책·변수 revision을 전후로 확인한다. `listVirtualMachines`의 본문 옵션 이름은 `userdata=true`이다. 본문의 URL encoding을 해제하여 저장본 API와 비교한다.
- 라이브러리 원본은 조회 가능한 경우에만 UUID/소유자/이름을 재확인한다. 정규 Base64의 동일함은 원래 바이트의 동일함이다. 같음/다름/직접 참조 없음/비교 불가를 구분한다. 원본 실패를 빈 원본이나 동일함으로 해석하지 않는다.
- 원본 조회 후 VM revision을 다시 확인한다. VM 삭제·소유자나 본문 변경은 결과를 지운다. 일반 통신 오류에서는 이전 본문과 오류 안내를 보존하되 복사·다운로드를 비활성화한다.
- VM 메타데이터 API가 없거나 처음부터 권한이 없으면 독립적으로 허용된 저장본 API를 사용하고 비교 불가로 표시한다. 원본 읽기 권한은 VM 저장본 권한을 대신하지 않는다.
- VM/프로젝트/사용자/세션/경로/권한 변경과 해제 후 결과는 버린다. 동일 요청의 중복 실행을 방지한다.

## 검증 범위

102개 집중 Jest 테스트, 변경 파일 ESLint, UI production build를 구분한다. 전체 Cloud/Maven/GitHub Actions 빌드는 요청하지 않았다. 31번 전용 정지 VM의 API/DB 본문 바이트와 실제 UI 복사·다운로드를 대조한다. VM 표준 대화상자·일반/다크·1440×1000,1280×720,390×844,390×480 및 역할/프로젝트/API 거부·지연·변경/삭제·오류를 확인하고 전용 자원을 정리한다. 기존 운영 라이브러리는 변경하지 않는다.
