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

# UD-05 #1386 사용자 데이터 재설정 실패 정합성

## 저장과 공급자 계약

`resetUserDataForVirtualMachine`은 정지된 VM만 지원하는 동기 API다.
이 API의 성공은 공급자 수락과 Cloud 저장을 의미한다. ConfigDrive는 다음 시작에서
재생성하고, 게스트의 cloud-init 소비/실행은 별도로 확인해야 한다.
재설정만으로 이미 실행한 cloud-init이 자동 재실행된다고 안내하지 않는다.

1. 기존 ACL/SharedFS/External 검사를 유지하고 VM lock을 획득한 뒤 최신 상태를 확인한다.
   동일 VM의 다른 재설정은 대기 중 후보를 덮어쓰지 않고 재시도 오류를 받는다.
2. 모든 NIC의 UserData 서비스/공급자를 저장 전에 검증한다. 미지원 NIC는 제외하고,
   최소 한 경로가 필요하다. 서비스가 선언됐지만 공급자가 없으면 적용 전 실패한다.
   ConfigDrive는 실제 생성 경로와 같이 기본 NIC만 인정한다.
3. 후보 본문/ID/변수는 메모리의 VM profile로 공급자에 전달한다. VR rule은 로드한
   VM의 SSH 등 기존 세부 정보는 유지하면서 profile의 후보 userdata 필드를 사용한다.
   이전 DB 본문을 다시 읽어 전달하는 오류를 방지한다.
   이 동작은 내부 profile의 명시적 RESET_CANDIDATE 표식이 있을 때만 적용한다.
   기존 updateVirtualMachine은 DB를 먼저 저장하고 오래된 profile을 전달할 수 있으므로
   표식 없는 기존 경로는 새 DB 본문을 읽는 동작을 유지한다.
4. 지원하는 모든 NIC의 수락이 필요하다. 한 NIC 성공으로 다른 실패를 가리지 않는다.
   전부 수락 후 VM이 여전히 Stopped인지 재확인하고 userdata 세 필드만 저장한다.
   실패/지연 중 API·사용 관계 조회는 이전에 수락한 Cloud 값을 반환한다.
5. 공급자 false/예외/타임아웃, 상태 변경 또는 저장 실패 때 이전 본문/ID/변수를
   각 대상 NIC에 다시 전달한다. 실패한 NIC도 원격 부분 적용 가능성이 있어 복원 대상이다.
   복원은 한 NIC의 실패로 중단하지 않는다. Cloud 값/참조는 변경하지 않는다.
   외부 공급자는 DB 트랜잭션과 원자화할 수 없다. 복원 확인이 불가능하면 오류에
   명시하고 VM 시작 전에 재시도를 요구한다. 공급자까지 복원됐다고 잘못 보고하지 않는다.
6. UI는 실제 현재 VM/NIC와 각 네트워크를 조회해 지원 조건을 안내한다.
   미지원/비정지 VM은 읽기와 유지가 가능하되 변경을 차단한다. 네트워크 조회 실패도
   적용을 차단하고 재시도한다. 서버는 같은 조건을 다시 검사한다.
7. 실제 command-response 형태의 오류에서도 공급자 복원 상태를 Body에 표시한다.
   네트워크 단절/시간 초과로 응답을 받지 못하면 서버 실패로 확정하지 않고 결과 미확인으로
   안내한다. 입력은 보존하며 작업 종료 후 저장값을 다시 확인하도록 한다.

기존 `updateVirtualMachine`의 다른 설정 변경을 묶는 원자성이나 VM 수명 주기 전체의
직렬화 프로토콜을 새로 정의하지 않는다. 이번 변경은 명시적인 사용자 데이터 reset API다.
프로세스 강제 종료 등 외부 명령의 결과 자체를 알 수 없는 분산 실패까지 원자적이라고
주장하지 않는다. 재시도·다음 시작의 재생성과 실제 게스트 검증을 별도 근거로 기록한다.

## 검증과 UI 재사용

새 DB 스키마/API는 만들지 않는다. 공통 MoldDialog·AntD alert·번역·테마·32px 버튼을
재사용한다. 관리자/권한, 미지원 L2, ConfigDrive 기본/보조 NIC, VR·다중 NIC,
공급자 false/예외/지연·복원 실패·재시도·저장 실패·동시 재설정을 집중 검사한다.
31번 전용 자원에서 UI/API/DB·실제 전달 매체·게스트 수신/실행 결과를 구분하고 정리한다.
일반/다크·네 viewport와 오류 입력 보존은 최종 배포 화면에서 확인한다.
전체 Cloud 빌드는 수행하지 않고 WSL ext4에서 변경 server 모듈과 UI만 검사·빌드한다.
