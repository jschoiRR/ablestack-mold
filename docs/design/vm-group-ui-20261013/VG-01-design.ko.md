<!--
Licensed to the Apache Software Foundation (ASF) under one
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
under the License.
-->


# VG 01 가상머신 그룹 소속 변경과 해제 계약

Epic #1357 / issue #1358. 설계 기준은 Upstream Europa `9fa65ce2a27b977e848440cb7c787411c11d585b`이며 구현은 `codex/epic-1357-vm-group-ui` 브랜치에 누적한다.

## API 계약

| updateVirtualMachine 요청 | 결과 |
| --- | --- |
| group/groupid 미전달, cleargroup 미전달 또는 false | 기존 그룹 유지 |
| groupid UUID | 대상 VM과 같은 accountId(프로젝트 계정 포함)의 활성 그룹 연결 또는 이동 |
| cleargroup=true | VM의 모든 그룹 매핑 제거, 그룹 자체 보존 |
| 유효한 group 이름 | 기존 이름 기반 동작 유지. 대상 VM 소유 범위에 그룹이 없으면 생성 후 연결 |
| group/groupid/cleargroup=true 중 둘 이상 | 변경 전 거부 |
| 빈/공백만 있는 group | 변경 전 거부. 해제는 cleargroup=true 사용 |
| 없는/삭제된/다른 소유 그룹 또는 변경 중 소유자가 바뀐 VM | 매핑 변경 전 거부 |

기존 유효 이름의 앞뒤 공백과 대소문자를 자동 정규화하지 않는다. 이름 규칙 전체 변경·후보 조회 소유 범위 개선·멤버 탭은 VG-02/03/05에서 다룬다. groupid와 cleargroup은 기존 API에 추가하므로 기존 클라이언트의 미전달/유효 이름 요청을 유지한다.

## 서버 처리

UpdateVMCmd와 서비스가 상충/공백 요청을 검증한다. VM 접근 권한을 기존 경로에서 검사하고 groupid는 API ACL과 대상 VM의 정확한 accountId 검증을 모두 수행한다.

실제 파라미터에 그룹 선택자와 id·인증/프로젝트 문맥만 있는 요청은 매핑만 처리한다. 템플릿/오퍼링 조회, VM updateVM·UserData·DNS·DHCP·전원·호스트·볼륨 경로를 실행하지 않는다. 다른 명시적 파라미터 또는 파라미터 메타데이터가 없는 내부 호출은 기존 전체 업데이트 경로를 사용한다. 혼합 요청은 미리 그룹을 검증하고 기존 업데이트가 성공한 뒤 그룹 변경을 적용한다. 혼합 업데이트 전체의 외부 효과를 원자적으로 되돌린다는 계약은 제공하지 않는다.

VmInstanceGroupAssignment가 그룹 변경을 한 트랜잭션으로 처리한다. VM 행을 FOR UPDATE로 잠그고 소유자를 재검증하며 대상 그룹도 잠근 뒤 기존 매핑을 제거하고 최대 한 매핑을 생성한다. 이름 기반 생성은 소유 계정의 기존 잠금과 DB 행 잠금을 사용한다. 계정 행 잠금은 외부 트랜잭션이 있는 VM 생성 경로에서도 커밋까지 유지한다. 그룹 생성 API도 같은 계정 행을 잠근다. DB 오류/잠금 실패/대상 삭제는 트랜잭션을 실패시켜 기존 매핑을 보존한다.

그룹 삭제는 그룹 행 잠금·코멘트 제거·해당 groupId 매핑 제거·그룹 삭제를 한 트랜잭션으로 처리한다. VM id 기준으로 다른 그룹의 매핑까지 삭제하지 않으므로 동시 이동 뒤 새 매핑을 지우지 않는다. 스키마 변경은 없다. 변경 Java 모듈은 api와 server다.

## UI 처리

기존 VM 편집 폼을 유지한다. 기존 그룹 후보를 선택하면 groupid, 새로운 이름을 입력하면 group, 기존 소속의 입력을 비우면 cleargroup=true를 전송한다. 변경 없는 그룹은 파라미터를 보내지 않는다. 공백 입력은 오류로 안내하고 입력을 유지한다. 이전 서버가 해제 파라미터를 지원하지 않으면 무시된 성공을 만들지 않고 안내한다.

초기 폼과 UserData 조회 후 값을 기록한다. 다른 필드가 바뀌지 않은 그룹 변경은 id와 그룹 선택자만 제출한다. 다른 필드가 바뀌면 기존 폼 제출을 유지한다. 복제 중 편집의 메타데이터 허용 목록에 groupid/cleargroup을 포함한다. 그룹 입력에 재시작 없이 적용됨과 입력을 지워 해제하는 안내를 제공한다.

## 검증과 배포

- API: 미전달/false 유지, 각 유효 선택자, 공백/상충, 그룹 전용 요청 판별, 알 수 없는 또는 다른 명시적 파라미터의 전체 경로 유지.
- 서비스: 소유 범위/삭제/잠금/소유 변경 거부, 기존 ID 이동, 해제 멱등성, 이름 생성/기존 이름, 생성 잠금 실패와 매핑 저장 실패.
- UI: 미변경 유지, 기존 후보 ID, 신규 이름, 명시적 해제, 공백/구 서버 거부, 다른 필드 변경 보존, 그룹만 변경하는 실제 요청.
- 기존 UserVmManagerImplTest 회귀와 그룹 삭제 대상/트랜잭션 회귀를 수행한다.
- 31/32 전용 대상에서 UI 무소속→A→B→무소속, API 미전달/이름/false/상충/없는 ID/다른 소유 그룹, 동시 이동/해제 및 삭제 경합을 검증한다. VM 상태·호스트·도메인·볼륨과 DB 매핑을 대조한다.
- Maven 빌드는 WSL ext4에서 api/server만 수행하고 UI 집중 테스트/린트/프로덕션 번들을 검증한다. 전체 Cloud/Actions 빌드를 별도로 시작하지 않는다.
- 관리 서버의 기존 배포/백업 데이터를 정리한 후 공간을 재확인한다. 활성 서버·DB·VM 데이터는 정리 대상에 포함하지 않는다. 현재 설치 관리 JAR의 변경 모듈 클래스와 활성 webapp 정적 자산을 배포하고 신규 rollback 사본을 유지한다. WEB-INF/META-INF/config를 보존한다.

검증 결과·배포 해시·공간·전용 대상과 정리 근거는 VG-01-validation.ko.md 및 evidence/VG-01에 기록한다. Epic PR은 누적 Draft로 만들며 나머지 VG-02~06은 완료로 표시하지 않는다.
