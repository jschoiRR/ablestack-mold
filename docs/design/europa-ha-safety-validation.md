# Europa HA 변경 및 검증 기록

- 대상 브랜치: `codex/ha-safety` (운영 기준 브랜치: `europa-2026`)
- 검토 기준 HEAD: `1fcb1b0467`
- 구현계획: [europa-ha-safety-implementation-plan.md](europa-ha-safety-implementation-plan.md)
- 선택한 정책: 재부팅 유지, 원래 호스트 Maintenance 유지, 복구 가능한 HA VM은 다른 호스트에서 재시작

## 실제 변경 흐름

1. Health 작업에서 최신 BMC STATUS를 반복 확인한다. 기본 3회 연속 OFF일 때만 HB 만료 전 Fencing으로 진행할 수 있다. Ping 실패·BMC 무응답·PoweringOff는 OFF 증거가 아니다.
2. 조기 OFF 증거가 없으면 기존 Health/Activity 경로로 관찰한다. 초기 성공 표본 때문에 검사를 중단하지 않는다. 7회·50%는 연속 DEAD 4회, 9회·50%는 5회이며, ALIVE 또는 UNKNOWN이 끼면 실패 연속성이 끊어진다.
3. Fencing 작업은 Maintenance를 DB에 확정하고 agent 명령 전송을 차단한 뒤, 복구할 VM ID·UUID를 `host_details`에 저장한다.
4. OFF 요청 → 시간 간격을 둔 실제 OFF 반복 확인 → ON 요청 순으로 수행한다. 전원 변경 직전에 HA 활성화, 클러스터·존 설정, 관리 서버 소유권, Maintenance를 다시 확인한다.
5. 성공한 fencing을 DB에 Fenced로 기록한다. 저장한 VM 목록으로 `HostFenced` HA 작업을 DB에 등록한 뒤에만 목록을 제거하고 호스트 HA를 비활성화한다. 원래 호스트의 Maintenance는 유지한다.
6. VM 작업은 stop 상태 정리를 재개할 수 있는 단계로 먼저 저장된다. 복구 배치에서는 작업에 기록된 원래 호스트를 제외한다. 이미 다른 호스트로 이동했거나 제거된 VM에는 이전 복구 작업을 적용하지 않는다.

## Degraded 지속 관찰 추가

사용자와 합의한 흐름에 따라 총 검사 횟수 제한 없이 Activity를 관찰하면서, 연속 ALIVE 기준으로 Degraded에 진입하도록 보완했다.

| 조건 | 동작 |
|---|---|
| Available | Health 및 전원 상태 확인만 주기적으로 수행; Activity 반복 검사 없음 |
| Health 비정상 + Activity ALIVE 3회 연속 | Degraded 진입 (새 설정으로 횟수 변경 가능) |
| Degraded의 후속 ALIVE 또는 실패 기준 미달 | Degraded 상태에서 Health/Activity 계속 수행 |
| Activity UNKNOWN·timeout·오류 | 성공/실패 연속성 모두 초기화, 경고 로그 출력, 관찰 지속 |
| Activity DEAD 연속 4회 (기존 7회·50% 설정) | Checking 또는 Degraded에서 Recovering으로 전환, 기존 fencing 절차 연결 |
| 관찰 중 대상 호스트 Health 정상 | Available 복귀, Activity 이력 초기화 |
| Fencing/Fenced | 늦은 Activity 결과로 상태를 되돌리지 않음 |

- 신규 클러스터 설정: `kvm.ha.activity.check.success.threshold`, 기본값 **3**, 양의 정수. 총 검사 횟수와 별개인 Degraded 진입 기준이다.
- Available 복귀 기준은 기존 Health 정상 1회다. 이번 변경에서 별도의 연속 Health 성공 횟수를 추가하지 않았다.
- Degraded에서 검사할 때도 DB HA 상태를 유지한다. 기존 VM HA에는 계속 Disconnected 및 VM 생존으로 전달되어, 새 검사마다 일시적으로 Up으로 보이지 않는다.
- UNKNOWN 이후 유지되는 Degraded는 마지막 확인 상태이며, 매 순간 최신 ALIVE가 확인됐다는 뜻은 아니다. UNKNOWN 자체로 새로운 Degraded 진입이나 fencing을 결정하지 않는다.
- 일반 Activity 결과는 DEBUG 로그로 남기고, 성공한 Degraded/Recovering 판정에만 상세 DB 이벤트를 기록한다. 지속 검사에 따른 동일 이벤트의 무제한 누적을 줄인다.
- 관리 서버 재시작으로 Degraded의 소유권이 비어 있으면 DB CAS로 소유권을 확보한 뒤 다음 주기에 관찰을 재개한다. 관찰 카운터는 재시작 후 새로 시작한다.

추가 검증(2026-09-11): **42개 모듈 BUILD SUCCESS, 선택한 Java 회귀 테스트 195개 통과**, 실패·오류·제외 0개. 서버 146개, engine/orchestration 5개, KVM 44개이며, 새 연속 ALIVE/Degraded 동작을 다루는 counter·manager·task 테스트는 기존 포함 52개다. 수정한 KVM·시뮬레이터 smoke test 두 파일은 Python 구문 검사를 통과했다. 실제 smoke 환경·실장비 인수시험은 실행하지 않았다.

시뮬레이터는 별도 Maven profile로 등록되므로 다음 명령으로 빌드·회귀 검증했다. JDK 및 Mockito agent 설정은 아래 최초 검증 환경과 같다.

```sh
mvn -o -Psimulator \
  -pl engine/orchestration,plugins/hypervisors/kvm,plugins/hypervisors/simulator -am \
  -Ddownload.plugin.skip=true -Dcheckstyle.skip=true -Drat.skip=true \
  -Dspotbugs.skip=true -Dpmd.skip=true -DskipITs \
  -Dtest='HAResourceCounterTest,HAManagerImplTest,HATaskTest,FenceTaskTest,HAAbstractHostProviderTest,HostFencedRecoveryTest,HaSourceHostExclusionTest,HighAvailabilityManagerImplTest,HighAvailabilityDaoImplTest,DeploymentPlanningManagerImplTest,HostMaintenanceDispatchTest,KVMHAPowerSafetyTest,KVMHostHATest,KVMHostActivityCheckerTest,KVMHACheckerTest,*SimulatorHA*Test' \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

## 관련 보완

- HA 작업 중복 예약과 오래된 결과 반영을 막고, timeout 후 실제 작업이 종료될 때까지 해당 호스트의 예약을 유지한다.
- 상태 전이가 DB에 성공하기 전에 Recovery/Fence 작업을 실행하던 경로를 제거한다.
- 재부팅·재접속·전송 대기열·다른 관리 서버를 통한 명령 전송에서도 Start 직전에 DB Maintenance를 확인한다.
- OOBM STATUS는 한 번의 실시간 driver 응답을 반환하며, 전원 요청 전 조회와 실제 명령은 전체 제한 시간을 공유한다.
- Redfish 전환 중 상태, 인증 오류, IPMI·스토리지 스크립트 오류는 UNKNOWN으로 구분한다. Redfish OFF는 ForceOff, SOFT는 GracefulShutdown으로 매핑한다.
- KVM Health와 Activity는 명시적 결과를 사용하고, 모든 적용 가능한 스토리지에서 확인된 결과를 모은다. Activity는 한 곳이라도 ALIVE면 활동이 있는 것으로 처리하고, 전부 DEAD일 때만 실패 표본을 만든다.
- HA 작업 조회에서 VM 종류와 작업 종류를 혼동하던 조건을 수정한다. 실행 중인 자신의 작업을 대기 대상으로 삼지 않으며, 확인된 fencing 작업을 뒤늦은 일반 조사 작업이 취소하지 않도록 한다.
- `HostDown` 사유의 VM 복구도 원래 호스트를 배치에서 제외한다. PCS가 먼저 재부팅한 경우의 일반 HA 복구에도 이 제외 조건이 적용된다.

## 감지 시간 해석

스토리지 HB의 60초 보호 시간을 삭제한 것이 아니다. 물리적으로 전원이 꺼졌다는 별도 증거가 확보되는 경우 그 만료를 기다리지 않는 경로를 추가했다.

신규 poll 기본값은 5초다. 기존 설치의 저장된 `ha.checking.interval` 값은 자동 변경하지 않는다. 검사 시작 전 poll·대기열·현재 실행 중인 작업의 잔여 시간, BMC 응답 시간은 여전히 영향을 준다. Activity timeout 기본값은 60초이므로 이미 실행 중인 긴 검사까지 포함하여 항상 60초 미만에 감지한다고 보장하지 않는다.

BMC까지 전원이 끊겼거나 관리망이 단절되면 무응답을 완전 다운으로 바꾸지 않는다. 커널 정지처럼 전원은 ON인 장애도 조기 OFF 경로의 대상이 아니다. 기존 관찰과 검증된 fencing이 필요하다.

## 배포 및 현장 검증 조건

- 관리 서버, KVM agent, 변경한 HB/Activity 스크립트를 함께 갱신한다. 구형 일반 Answer는 UNKNOWN으로 처리되므로 혼용 기간의 HA 판단이 지연될 수 있다.
- 실제 저장된 poll·Health/Activity/Fence timeout과 새 BMC 설정 조합을 확인한다. 기본 OFF 확인은 3회, 간격 1초, 조회당 timeout 2초다. 짧은 poll은 BMC와 agent 부하를 증가시키므로 호스트 수에 맞춰 현장에서 측정한다.
- CLVM의 이웃 호스트 로컬 프로세스 검사는 장애 호스트 VM의 종료를 입증하지 못한다. HB 만료 후 이 근거만 있을 때 UNKNOWN을 반환한다.
- 로컬 root volume, HA 비활성 VM, 이미 이동·제거된 VM 등 기존 복구 제외 조건을 유지한다. 모든 VM이 무조건 다른 호스트에서 시작된다는 의미는 아니다.
- PCS 또는 libvirt의 외부 자동 시작은 Mold의 Start 차단만으로 통제되지 않는다. PCS가 Maintenance 확정보다 먼저 재부팅하는 시험과 libvirt 자동 시작 정책을 별도 확인해야 한다.
- 외부 전원 명령과 DB 기록은 원자적으로 묶이지 않는다. ON 성공 직후 Fenced 기록 전 관리 서버가 종료되면 재시도에서 전원 시퀀스가 반복될 수 있다. 관리 서버 장애를 포함한 전원 명령의 정확히 한 번 실행을 보장하지 않는다.

## 최초 HA 변경 검증

검증일: 2026-09-11. 최종 Maven 실행은 **BUILD SUCCESS**, 42개 reactor 모듈 모두 성공했다.

| 범위 | 실행·통과 | 제외 | 주요 검증 |
|---|---:|---:|---|
| utils | 34 | 11 | Redfish 응답·timeout, 프로세스 제한 시간·취소 |
| core | 4 | 0 | KVM heartbeat 답변의 삼상태 및 기존 result 의미 호환 |
| server | 137 | 0 | HA 상태·중복·늦은 결과·VM 복구 목록·배치·DB 조회·OOBM |
| engine/orchestration | 24 | 0 | 재접속 및 실제 Start 전송의 Maintenance 차단 |
| KVM | 49 | 0 | OFF 3회·순서·취소·소유권·설정, Health/Activity 집계 |
| Redfish driver | 2 | 0 | PoweringOff 구분 및 OFF/SOFT 매핑 |
| 합계 | **250** | **11** | 실패 0, 오류 0 |

utils의 제외 11개는 기존 `com.cloud.utils.ScriptTest`에 지정된 비활성 테스트다. 별도의 `com.cloud.utils.script.ScriptTest` 8개는 실행·통과했으며, 새 취소 검증을 포함한다.

- `python3 scripts/test_ha_activity.py`: **14개 통과**.
- 변경 HB/Activity shell script 8개: `bash -n` 통과.
- 최종 `git diff --check`: 통과.
- IPMI driver는 컴파일 검증에 포함된다. 실제 IPMI/Redfish 장비를 대상으로 명령을 실행한 결과는 아니다.

빌드 환경은 Zulu JDK 17이며 프로젝트 Java target은 11이다. Maven offline 모드와 기존 캐시를 사용했다. 테스트용 Mockito agent는 캐시의 Byte Buddy 1.15.11을 JVM 시작 시 명시해 로컬 실행 환경의 동적 attach 제약을 피했다.

```sh
JAVA_TOOL_OPTIONS='-javaagent:/Users/js/.m2/repository/net/bytebuddy/byte-buddy-agent/1.15.11/byte-buddy-agent-1.15.11.jar' \
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
mvn -o \
  -pl engine/orchestration,plugins/hypervisors/kvm,plugins/outofbandmanagement-drivers/ipmitool,plugins/outofbandmanagement-drivers/redfish -am \
  -Ddownload.plugin.skip=true -Dcheckstyle.skip=true -Drat.skip=true \
  -Dspotbugs.skip=true -Dpmd.skip=true -DskipITs \
  -Dtest='*HAPowerSafetyTest,HAResourceCounterTest,*HATaskTest,ActivityCheckTaskTest,HealthCheckTaskTest,RecoveryTaskTest,FenceTaskTest,HAManagerImplTest,HAAbstractHostProviderTest,HostFencedRecoveryTest,HaSourceHostExclusionTest,HostMaintenanceDispatchTest,HighAvailabilityManagerImplTest,HighAvailabilityDaoImplTest,DeploymentPlanningManagerImplTest,*OutOfBandManagement*Test,Redfish*Test,LibvirtStoragePoolTest,*Activity*Test,KVMHostHATest,KVMHACheckerTest,LibvirtCheckOnHostCommandWrapperTest,AgentAttacheTest,AgentManagerImplTest,ConnectedAgentAttacheTest,DirectAgentAttacheTest,ClusteredAgentManagerImplTest,ScriptTest,ProcessRunnerTest,CheckOnHostAnswerTest' \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

선택한 회귀 테스트와 모듈 컴파일 결과이며 전체 저장소 테스트, 정적 분석, 패키지 생성 또는 운영 인수시험을 통과했다는 의미는 아니다. 외부 checksum 다운로드는 offline 환경 때문에 생략했고 기존 캐시를 사용했다.

실제 BMC·PCS·스토리지 및 다중 관리 서버 장애 시험은 이 로컬 검증에서 실행하지 않았다. 배포나 실제 호스트 전원 조작도 실행하지 않았다.
