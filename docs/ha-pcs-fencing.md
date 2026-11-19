<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# KVM Host HA의 PCS 대체 펜싱

Host HA가 호스트를 `Fencing`으로 전환하면 Mold는 유지보수 상태와 VM 복구 목록을 저장합니다. IPMI OFF를 확인할 수 없으면, PCS 설정이 있는 클러스터의 정상 KVM 호스트에서 Pacemaker에 새 전원 차단을 요청합니다. 현재 요청이 성공하면 `Fenced`를 저장하고 VM 복구 작업을 등록합니다. 원래 호스트의 전원은 운영자가 복구합니다.

클러스터 범위의 두 값을 설정하면 PCS 대체 경로가 활성화됩니다. 예시의 UUID는 Mold에서 조회한 호스트 ID로, 노드 이름은 Pacemaker에 등록된 이름으로 바꿉니다.

| 설정 | 예시 |
| --- | --- |
| `kvm.ha.pcs.fence.cluster` | `mold-ha` (`totem.cluster_name`) |
| `kvm.ha.pcs.fence.node.map` | `e0da975a-59bf-45f8-a8fb-4cbe1d3c63b1=node-a,3cd37a20-1a9e-4c07-ae77-f70b266b7260=node-b` |

일반 Host HA·OOBM 설정과 스토리지 활동 검사는 함께 사용합니다. 관리 서버와 요청을 전달받는 KVM 에이전트를 모두 업데이트합니다. 에이전트에 Corosync/Pacemaker 도구가 설치되어 있어야 하고, 로컬 클러스터 정보와 펜싱 서비스를 사용할 권한 및 관리 서버와 동기화된 시계가 필요합니다.

PCS에는 대상 BMC와 독립된 펜싱 장치가 필요합니다. 예를 들어 관리형 PDU의 모든 전원 공급 경로가 실제로 차단되고, 전원 차단 명령이 완료된 뒤에도 격리가 유지되는지 확인합니다. PCS 노드와 장치 매핑은 실제 장비에 맞게 검증합니다. PCS 역시 해당 IPMI에만 의존하는 경우 전원 단절 중에는 펜싱이 보류됩니다. 두 수단을 사용하면 `kvm.ha.fence.timeout`의 후반부가 PCS에 예약되므로 장치 응답 시간이 전체 제한 내에 들어오도록 설정합니다.

원래 호스트의 전원과 유지보수 상태는 VM 복구와 별도로 관리합니다. 원래 호스트의 전원을 복구하기 전에 로컬 VM 자동 시작이나 자동 복원이 비활성화되어 있는지 확인해야 합니다. 펜싱 요청과 VM 복구가 완료된 뒤 VM 배치 및 스토리지 상태를 확인하고 유지보수를 해제합니다.
