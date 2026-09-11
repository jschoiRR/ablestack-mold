# Europa HA 변경 및 검증 기록

> 현재 구현 전체 흐름과 설정은 [Europa HA 현재 구현 구조와 설정](europa-ha-current-flow.md)에 통합했다. 이 문서는 각 변경의 검증 근거를 기록한다.

- 대상 브랜치: `codex/ha-safety` (운영 기준 브랜치: `europa-2026`)
- 검토 기준 HEAD: `1fcb1b0467`
- 구현계획: [europa-ha-safety-implementation-plan.md](europa-ha-safety-implementation-plan.md)
- 선택한 정책: 재부팅 유지, 원래 호스트 Maintenance 유지, 복구 가능한 HA VM은 다른 호스트에서 재시작

## 실제 변경 흐름

1. 각 Health 작업에서 관리 서버가 최신 BMC STATUS를 **한 번만** 조회한다. 서로 다른 작업의 OFF 응답이 기본 3회 연속 누적될 때 HB 만료 전 Fencing으로 진행할 수 있다. 조회당 timeout 1초이며 감지 중 내부 3초 대기는 없다. Ping 실패·BMC 무응답·PoweringOff는 OFF 증거가 아니다.
2. OFF 1~2회이면 agent Health를 생략하고 작업을 반환하며, 증거가 유효하고 Activity 재확인이 필요하지 않으면 다음 poll도 Health를 우선한다. OFF 증거가 만료되면 우선권을 해제하여 Activity로 복귀한다. ON·UNKNOWN·오류는 OFF 연속성을 초기화한 뒤 기존 Health/Activity 경로로 관찰한다. Activity는 초기 성공 표본 때문에 검사를 중단하지 않는다. `kvm.ha.activity.check.failure.threshold`로 연속 DEAD 횟수를 직접 지정하며 기본값은 4다. ALIVE 또는 UNKNOWN이 끼면 실패 연속성이 끊어진다.
3. Fencing 작업은 Maintenance를 DB에 확정하고 agent 명령 전송을 차단한 뒤, 복구할 VM ID·UUID를 `host_details`에 저장한다.
4. OFF 요청 → 조회 완료 후 3초 간격으로 실제 OFF 연속 5회 확인 → ON 요청 순으로 수행한다. 이 펜싱 검증 횟수는 감지용 3회와 별개다. 전원 변경 직전에 HA 활성화, 클러스터·존 설정, 관리 서버 소유권, Maintenance를 다시 확인한다.
5. 성공한 fencing을 DB에 Fenced로 기록한다. 저장한 VM 목록으로 `HostFenced` HA 작업을 DB에 등록한 뒤에만 목록을 제거하고 호스트 HA를 비활성화한다. 원래 호스트의 Maintenance는 유지한다.
6. VM 작업은 stop 상태 정리를 재개할 수 있는 단계로 먼저 저장된다. 복구 배치에서는 작업에 기록된 원래 호스트를 제외한다. 이미 다른 호스트로 이동했거나 제거된 VM에는 이전 복구 작업을 적용하지 않는다.

조기 OFF 확인은 Activity DEAD 판정을 기다리거나 Activity 결과를 DEAD로 변환하는 절차가 아니다. 별도 `PowerOffConfirmed` 이벤트로 Fencing에 진입하고, 늦게 도착한 Activity 결과는 반영하지 않는다. 실제 순서는 Maintenance 확정이 전원 조작보다 먼저이며, VM 복구는 다른 호스트에서의 재시작이다.

## 최신 변경: 연속 DEAD 임계값 설정 통합

`kvm.ha.activity.check.failure.threshold` 하나로 Recovery 진입에 필요한 연속 DEAD 횟수를 지정한다. 기본값은 4이며 검사 총횟수 제한은 없다. KVM provider와 공통 Activity task에서 기존 횟수·비율 조합을 제거했고, 시뮬레이터와 KVM smoke test도 단일 임계값을 사용한다. ALIVE/UNKNOWN 초기화, Degraded 지속 관찰, OFF 증거 만료 시 Activity 복귀 및 펜싱 검증은 유지한다.

기존 설정은 `DatabaseUpgradeChecker`가 DB 업그레이드 잠금을 보유한 상태에서 `KvmHaActivityThresholdMigration`으로 이관한다. 같은 버전의 관리 서버를 재시작할 때도 실행하며, 신규 기본값 등록 전에 처리한다. 글로벌·클러스터의 기존 두 값을 각각 상속 규칙에 맞춰 읽고 `floor(max.attempts × failure.ratio) + 1`을 저장한다. 기존 7/0.5는 4, 9/0.5는 5가 된다. 이미 존재하는 새 설정은 보존한다. 잘못된 기존 값은 0으로 이관하고 경고하여 Activity 실패 결정을 보류한다.

모든 새 값을 기록한 뒤 기존 두 설정을 삭제하며, 실패하면 트랜잭션을 되돌리고 기동을 중단한다. 재실행 시 새 값을 덮어쓰지 않는다. 관리 서버가 여러 대이면 모두 중지한 상태에서 같은 새 빌드를 적용한 후 시작한다. 이전 빌드는 삭제된 설정을 계속 사용하므로 버전을 혼용하지 않는다. 배포 후 글로벌 및 클러스터의 신규 임계값을 확인한다.

**검증 완료: Checkstyle 활성화, 44개 reactor 모듈 BUILD SUCCESS, 23개 suite의 289개 테스트 통과.** 실패·오류·제외 0개, Checkstyle 감사 37개 오류 0개다. 새 테스트 19개는 설정 이관 14개, KVM 설정 2개, HA task 3개다. 기존 `DatabaseUpgradeCheckerTest`/`DatabaseUpgradeCheckerDoUpgradesTest` 19개도 포함했다. 기존 HA 회귀 251개를 함께 실행했으며 각 실행의 수치를 중복 합산하지 않는다. 신규 기본값 등록보다 이관이 먼저 실행되는 Spring 순서와 같은 버전 재시작 경로를 독립 검토했다.

실행 로그: `/private/tmp/europa-ha-activity-threshold-tests.log`. 완료: `2026-09-11T17:25:02+09:00`. KVM smoke test Python 구문 검사와 문서 링크 검사도 통과했다. 이관 테스트는 JDBC/Mockito를 사용하므로 실제 MySQL에서 SQL을 실행한 검증은 아니다. 실제 BMC/PCS/스토리지 장애 시험, RPM 패키징 및 운영 배포는 수행하지 않았다.

재현 명령은 아래와 같다(JDK 17 및 로컬 Maven 의존성 사용).

```bash
JAVA_TOOL_OPTIONS='-javaagent:/Users/js/.m2/repository/net/bytebuddy/byte-buddy-agent/1.15.11/byte-buddy-agent-1.15.11.jar' \
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
mvn -o -Psimulator \
  -pl engine/orchestration,plugins/hypervisors/kvm,plugins/hypervisors/simulator,plugins/outofbandmanagement-drivers/ipmitool,plugins/outofbandmanagement-drivers/redfish -am \
  -Ddownload.plugin.skip=true -Drat.skip=true -Dspotbugs.skip=true -Dpmd.skip=true -DskipITs \
  -Dtest='KvmHaActivityThresholdMigrationTest,DatabaseUpgradeChecker*Test,KVMHAActivityConfigTest,CheckOnHostAnswerTest,HAResourceCounterTest,HAManagerImplTest,HATaskTest,FenceTaskTest,HAAbstractHostProviderTest,HostFencedRecoveryTest,HaSourceHostExclusionTest,HighAvailabilityManagerImplTest,HighAvailabilityDaoImplTest,DeploymentPlanningManagerImplTest,HostMaintenanceDispatchTest,KVMHAPowerSafetyTest,KVMHostHATest,KVMHostActivityCheckerTest,KVMHACheckerTest,KVMHAVMActivityCheckerTest,*SimulatorHA*Test,*OutOfBandManagement*Test' \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

## 이전 단계: OFF 증거 만료 시 Activity 정체 해소

poll 60초 / OFF 최대 간격 60초에서 OFF 관찰 간격을 60.001초로 모의 처리했을 때, 수정 전에는 OFF 횟수가 매번 1로 초기화되지만 Health 우선 조건은 유지되어 Activity와 Fencing이 정체됐다. 이 결함을 다음과 같이 수정했다.

| 지점 | 수정 후 동작 |
|---|---|
| Suspect/Degraded 작업 선택 | OFF 증거의 현재 유효성을 먼저 확인. 만료된 증거는 Health 우선권을 잃고 일반 Activity 일정으로 복귀 |
| 지연된 Health 응답에서 만료 | 새 OFF 1회가 기록돼도 Activity 재확인 요청 유지. 다음 poll에서 새 증거만 보고 다시 Health를 우선하지 않음 |
| Activity 재확인 요청 해제 | 현재 작업으로 검증된 결과 처리 때 해제. 단순 예약·제출 실패·오래된 결과로 소모하지 않음 |
| Activity 결과 | 만료 자체로 DEAD 이력을 초기화하지 않으므로 후속 DEAD가 임계값에 이르면 Recovering/Fencing 진행. UNKNOWN은 재확인 수행으로 처리하되 ALIVE/DEAD 연속성을 끊음 |
| 정상 OFF 관찰 | 유효 간격 안의 연속 OFF 3회 확정 경로 유지. 오래된 증거를 합쳐 확정하지 않음 |
| 설정 진단 | 최대 간격이 실제 등록 poll 이하이면 provider·등록 poll·최대 간격 조합별로 한 번 경고 |

실행 중인 작업, Activity 최소 간격, 기존 소유권·늦은 결과 보호를 유지한다. 최대 간격을 자동으로 늘리거나 poll을 실행 중 재등록하는 기능은 추가하지 않았다. `ha.checking.interval` 변경 후 관리 서버 재시작이 필요하다. 기본값은 poll 10초 / 최대 간격 60초 / STATUS timeout 1초로 그대로이며, [권장 설정](europa-ha-recommended-settings-validation.md)의 timeout 2초는 운영 권장값이다.

**검증 완료: Checkstyle 활성화, 44개 reactor 모듈 BUILD SUCCESS, 19개 suite의 251개 회귀 테스트 통과.** 실패·오류·제외 0개이며 Checkstyle 감사 37개도 오류 없이 완료했다. 새 회귀 테스트 10개(counter 4개, manager 4개, task 2개)는 만료·지연 응답·Activity 복귀·DEAD 누적과 작업 제출 실패·중복·오래된 결과 보호를 다룬다. 독립 코드 검토에서도 필수 수정 사항은 발견되지 않았다.

실행 로그: `/private/tmp/europa-ha-off-expiry-fix-tests.log`. 완료: `2026-09-11T16:47:38+09:00`. 실제 BMC/PCS/스토리지 장애 시험, RPM 패키징 및 운영 배포는 수행하지 않았다. 아래 검증 수치는 모두 이전 단계의 별도 결과이며 합산하지 않는다.

## 이전 단계: Health 작업당 BMC 1회 조회 및 poll 간 OFF 3회 누적

사용자가 한 Health 작업 안에서 5회 조회와 3초 대기를 반복하는 부담을 지적하여 감지 구조를 변경했다.

| 항목 | 변경 후 동작 |
|---|---|
| 감지 BMC 요청 | 관리 서버가 각 Health 작업에서 STATUS를 1회만 실행. agent에 BMC 조회를 요청하지 않음 |
| OFF 확정 | `kvm.ha.power.off.confirmations=3`, 서로 다른 작업의 성공한 OFF 응답만 연속 누적 |
| OFF 1~2회 | 일반 agent Health 생략 후 작업 반환, 다음 poll에서 Health 우선 배정. 최초 OFF에서 Available은 Suspect로 전환하여 소유권 확보; 기존 Suspect/Degraded는 유지 |
| ON·UNKNOWN·오류 | OFF 연속성을 초기화하고 일반 Health 검사 진행 |
| 오래된 증거 | `kvm.ha.power.off.max.interval=60`초를 넘는 OFF 응답 간격이면 현재 OFF부터 1회로 다시 계산 |
| 주기·소유권 유효성 | 새 HA 주기, 소유권·provider 변경·상실, 자격 변경 및 재시작 뒤 이전 OFF 이력을 재사용하지 않음 |
| HA poll | `ha.checking.interval` 소스 기본값 10초. 기존 DB override 자동 변경 없음 |
| 실제 펜싱 검증 | 새 `kvm.ha.fence.power.off.confirmations=5`와 기존 `kvm.ha.power.check.interval=3`초 사용. OFF → 5회 검증 → ON 유지 |
| timeout | STATUS 1초, Health 20초, Fence 60초 유지. Health 감지 예산은 STATUS 1회 기준 |

한 호스트의 작업이 대기·실행 중이면 다음 poll에서 중복 제출하지 않는다. OFF 감지의 대기 반복을 없앴지만 정상 ON 뒤 일반 Health, 다른 호스트의 작업, 이미 실행 중인 Activity 때문에 큐 대기는 생길 수 있다. 단일 STATUS의 1초 timeout은 전체 Health 작업의 완료 시간 보장이 아니다.

검증(2026-09-11): **43개 reactor 모듈 BUILD SUCCESS, 선택한 Java 회귀 테스트 236개 통과**, 실패·오류·제외 0개. 17개 suite에서 server 183개, engine/orchestration 5개, KVM 48개를 실행했다. 그중 `KVMHAPowerSafetyTest`는 24개이며, counter·manager·HA task는 각각 12·32·31개로 기존 포함 75개다. IPMI driver와 simulator 모듈도 빌드했다.

작업당 STATUS 정확히 1회, OFF 3개의 서로 다른 작업 누적, OFF 누적 중 agent Health 생략 및 Health 우선 배정, ON·UNKNOWN·오류/timeout에 의한 초기화, 오래된 증거와 소유권·provider 변경 차단, 감지와 펜싱 횟수 분리, 기존 Activity 및 유지보수·VM 복구 회귀를 확인했다. 실제 BMC·PCS·스토리지 인수시험이나 운영 배포는 수행하지 않았다.

실행 로그: `/private/tmp/europa-ha-single-power-tests.log`. 완료 시각 `2026-09-11T14:49:34+09:00`, Maven 실행시간 28.626초. JDK 및 Mockito agent 환경은 아래 최초 검증과 동일하며, 실행 명령은 다음과 같다.

```sh
mvn -o -Psimulator \
  -pl engine/orchestration,plugins/hypervisors/kvm,plugins/hypervisors/simulator,plugins/outofbandmanagement-drivers/ipmitool -am \
  -Ddownload.plugin.skip=true -Dcheckstyle.skip=true -Drat.skip=true \
  -Dspotbugs.skip=true -Dpmd.skip=true -DskipITs \
  -Dtest='HAResourceCounterTest,HAManagerImplTest,HATaskTest,FenceTaskTest,HAAbstractHostProviderTest,HostFencedRecoveryTest,HaSourceHostExclusionTest,HighAvailabilityManagerImplTest,HighAvailabilityDaoImplTest,DeploymentPlanningManagerImplTest,HostMaintenanceDispatchTest,KVMHAPowerSafetyTest,KVMHostHATest,KVMHostActivityCheckerTest,KVMHACheckerTest,*SimulatorHA*Test,*OutOfBandManagement*Test' \
  -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test
```

문서의 상대 링크 존재 여부와 코드 블록 구분을 확인했고 `git diff --check`도 통과했다. 아래 91개·195개·250개 통과 기록은 이전 단계의 별도 실행이며 현재 실행 수에 합산하지 않는다.

## 이전 단계: 전원 확인 기본값 조정

사용자 요청에 따라 `kvm.ha.power.check.interval=3`, `kvm.ha.power.off.confirmations=5`, `kvm.ha.power.check.timeout=1`로 변경했다. 단위는 간격·timeout 모두 초다. 필요한 관찰 예산 17초와 작업 종료 여유를 확보하기 위해 `kvm.ha.health.check.timeout` 기본값도 10초에서 20초로 변경했다. Fence timeout 기본값 60초는 유지한다.

이 항목은 **한 Health 작업 안에서 반복 확인하던 이전 구현**의 기록이다. 현재 감지 횟수는 3회이며 반복 대기는 제거했다. 따라서 아래 Health 10초와의 비호환 결과는 현재 단일 조회 감지에는 적용하지 않는다.

검증(2026-09-11): **38개 모듈 BUILD SUCCESS, 선택한 Java 테스트 91개 통과**, 실패·오류·제외 0개. `KVMHAPowerSafetyTest` 24개에는 기본값으로 조회당 1초가 걸려도 17초 안에 OFF 5회 확인, OFF 4회 후 UNKNOWN이면 확정 거부, 기존 Health 10초 override와의 비호환, OFF/ON 소요시간을 포함한 fencing 예산 검증을 추가했다. HA task·Fence task·OOBM service/status·KVM Health/Activity 회귀 테스트도 함께 통과했다. IPMI driver는 컴파일했으며 실제 BMC 응답시간은 측정하지 않았다.

## 이전 단계: Degraded 지속 관찰 추가

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
- HB가 유효시간 이내면 같은 타임스탬프를 다시 읽어도 ALIVE가 나올 수 있다. 따라서 ALIVE 3회는 새로운 HB 갱신 3회를 뜻하지 않으며, 실제 다운 직후에도 최근 HB로 Degraded에 진입할 수 있다.
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

현재 poll 기본값은 10초다. 기존 설치의 저장된 `ha.checking.interval` 값은 자동 변경하지 않는다. 유효한 OFF 응답이 이어지고 Activity 재확인 요청이 없는 동안 각 Health 작업은 한 번 조회하고 끝내며, 다음 poll에 Health를 우선 배정한다. 만료가 반복되면 Activity 관찰로 복귀한다. 지연 없이 각 poll에 배정된다면 첫 조회부터 세 번째 조회까지 약 두 poll 간격이지만 고정 완료 시간은 아니다. 검사 시작 전 poll·대기열·현재 실행 중인 작업의 잔여 시간, BMC 응답 시간은 여전히 영향을 준다. Activity timeout 기본값은 60초이므로 이미 실행 중인 긴 검사까지 포함하여 항상 60초 미만에 감지한다고 보장하지 않는다. OFF 관찰 사이의 최대 허용 간격 60초는 오래된 증거를 배제하는 값이며, HB의 60초를 기다리는 조건이 아니다.

BMC까지 전원이 끊겼거나 관리망이 단절되면 무응답을 완전 다운으로 바꾸지 않는다. 커널 정지처럼 전원은 ON인 장애도 조기 OFF 경로의 대상이 아니다. 기존 관찰과 검증된 fencing이 필요하다.

## 배포 및 현장 검증 조건

- 관리 서버, KVM agent, 변경한 HB/Activity 스크립트를 함께 갱신한다. 구형 일반 Answer는 UNKNOWN으로 처리되므로 혼용 기간의 HA 판단이 지연될 수 있다.
- 실제 저장된 poll·Health/Activity/Fence timeout과 BMC 설정을 확인한다. 감지는 poll당 STATUS 1회와 연속 OFF 3회이며, 펜싱 검증은 별도로 5회·3초다. 조회당 timeout은 공통 1초다. 기존 DB에 감지 횟수 5회·poll 5초가 저장돼 있으면 자동 변경되지 않는다. 펜싱 관찰 예산 17초는 Health에 적용하지 않고 Fence timeout 60초 안에서 OFF·ON 여유와 함께 검증한다. BMC 응답이 1초를 넘으면 UNKNOWN으로 처리되어 조기 감지나 fencing 완료가 지연될 수 있다. 실제 호스트 수에 맞춰 조회·agent 부하와 대기열 지연을 현장에서 측정한다.
- OFF 관찰 최대 간격은 실제 poll과 대기열 지연보다 충분히 길게 설정한다. 기존 DB의 poll 60초 이상과 최대 간격 60초 조합은 응답·스케줄링 지연 때문에 연속성이 반복 초기화될 수 있다. 수정 후에는 Activity 관찰로 복귀하지만 빠른 OFF 3회 확정을 보장하지 않는다. 소스 기본 조합은 poll 10초 / 최대 간격 60초다.
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
