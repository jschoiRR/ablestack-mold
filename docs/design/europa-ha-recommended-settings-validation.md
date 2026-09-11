# Europa HA 권장 설정과 시나리오 검증

작성일: 2026-09-11. 대상: `codex/ha-safety` 현재 작업 트리. 수정 전 검증 기준은 `fc8891623a`다.

이 문서는 현재 코드로 테스트할 설정을 추천한다. 운영 서버의 설정을 변경한 결과가 아니다. **OFF 관찰이 계속 만료될 때 Health 우선 배정을 해제하여 Activity로 복귀하도록 수정했다.** 아래 10초/60초 조합은 빠른 OFF 3회 확인을 위한 권장값이다. 수정 코드도 실행 지연 자체를 제거하거나 OFF 증거의 유효 간격을 자동으로 늘리지는 않는다.

## 1. 권장값

현재 확인된 3호스트 테스트 환경에서 사용할 시작값이다. 표의 초 단위 설정에는 숫자만 입력한다. 클러스터 범위 설정은 **클러스터 저장값 → 전역 저장값 → 소스 기본값** 순으로 적용한다.

| 설정 | 권장값 | 의미 |
|---|---:|---|
| `ha.checking.interval` | 10 | HA 작업 배정 간격. 현재 스케줄러는 관리 서버 시작 시 값을 읽으므로 변경 후 관리 서버 재시작 필요 |
| `kvm.ha.power.off.check.enabled` | true | Health 작업에서 BMC 전원 조회 사용 |
| `kvm.ha.power.off.confirmations` | 3 | 서로 다른 Health 작업의 연속 OFF 3회로 조기 장애 확정 |
| `kvm.ha.power.off.max.interval` | 60 | OFF 응답 사이 최대 유효 간격. 60초를 먼저 기다리는 설정이 아님 |
| `kvm.ha.power.check.timeout` | 2 | STATUS 1회 제한. 소스 기본값 1초보다 응답 여유를 늘린 추천값 |
| `kvm.ha.health.check.timeout` | 20 | BMC 확인을 포함한 Health 작업 제한 |
| `kvm.ha.activity.check.interval` | 5 | Activity 배정 사이 최소 간격. 실제 간격은 poll/Health 교대/작업 지연에 따라 증가 |
| `kvm.ha.activity.check.timeout` | 60 | Activity 작업 제한. 스토리지 HB 만료 기준과 별개 |
| `kvm.ha.activity.check.max.attempts` | 7 | 전체 검사 제한이 아닌 DEAD 임계값 계산 기준 |
| `kvm.ha.activity.check.failure.ratio` | 0.5 | 7과 조합하여 연속 DEAD 4회 필요 |
| `kvm.ha.activity.check.success.threshold` | 3 | Health 비정상 중 연속 ALIVE 3회면 Degraded |
| `kvm.ha.fence.power.off.confirmations` | 5 | 실제 펜싱 OFF 요청 후 OFF 확인 횟수 |
| `kvm.ha.power.check.interval` | 3 | 펜싱의 반복 확인 사이 대기. 조기 감지에는 적용하지 않음 |
| `kvm.ha.fence.timeout` | 60 | 펜싱 전원 시퀀스 제한 |
| `kvm.ha.recover.failure.threshold` | 1 | KVM Recovery 실패 후 다음 poll에서 Fencing 전이 |
| `kvm.ha.recover.timeout` | 60 | Recovery 작업 제한 |
| `kvm.ha.recover.wait.period` | 600 | 기존 값 유지. 현재 KVM의 실패한 Recovery 경로에는 이 대기가 적용되지 않음 |
| `kvm.ha.degraded.max.period` | 60 | 기존 값 유지. 현재 지속 Activity 관찰에 별도 대기를 추가하지 않음 |

Health/Activity/Recovery/Fence의 `ha.max.concurrent.*.operations`는 각각 10, `ha.max.pending.*.operations`는 각각 100을 유지하는 것을 시작점으로 삼는다. 이 값은 관리 서버당 작업 풀의 설정이며, 실제 호스트 수와 지연을 측정하지 않고 늘릴 이유는 없다. 풀 크기 변경도 관리 서버 재시작이 필요하다.

`kvm.ha.on.storage.heartbeat`는 HA 대상으로 사용하는 지원 스토리지 풀에서 true여야 한다. 글로벌 설정만 바꾸는 대신 실제 풀별 적용값을 확인한다. `kvm.ha.fence.on.storage.heartbeat.failure`는 false를 유지한다. 이는 일부 스토리지의 HB 실패 처리 정책이며 HA 전체 활성 스위치가 아니다.

호스트·클러스터·존의 HA와 OOBM이 활성화되어 있어야 하며, 같은 클러스터의 정상 이웃 호스트가 해당 HB 스토리지에 접근할 수 있어야 한다. 관리 서버와 KVM agent/Activity 스크립트의 배포 버전도 일치해야 한다.

## 2. 시간과 실제 검사 주기

| 조건 | 지연이 작을 때 예상 |
|---|---|
| Available 정상 호스트 | Health 및 BMC 조회 약 10초마다. Activity 반복 없음 |
| Suspect/Degraded, OFF 누적 없음 | Health와 Activity 교대. Activity 설정 5초라도 실제 Activity 간격은 약 20초 |
| 유효한 OFF 누적 진행 | Activity 재확인 요청이 없으면 다음 poll도 Health. BMC STATUS는 작업당 1회 |
| OFF 증거 만료 | Health 우선권을 해제하고 기존 간격에 따라 Activity로 복귀. 만료 자체로 Activity DEAD 누적을 초기화하지 않음 |
| OFF 연속 3회 | 첫 조회 기준 약 0초/10초/20초에 관찰하여 Fencing 상태 진입. 실제 펜싱 실행·VM 복구 완료 시간은 별도 |

실제 시간에는 poll 실행 시간, 큐 대기, 이미 실행 중인 검사, BMC/agent 응답이 추가된다. 10초마다 실행 중인 작업을 중복 제출하는 구조는 아니지만, 지연되면 다음 검사도 늦어진다.

권장 전원 설정의 코드 예산은 다음과 같다.

```text
Health: STATUS timeout 2 < Health timeout 20 - 1
Fence OFF 검증: 5 × 2 + 4 × 3 = 22초
Fence 검증 조건: 60 > 22 + 21 = 43초
```

OFF/ON 호출 각각 10초를 모두 쓰고 STATUS도 각각 2초를 쓴 모의 테스트에서는 전원 시퀀스가 42초였다. 이 값은 Maintenance, poll 대기, VM 배치/기동까지 포함하는 복구 완료 시간 보장이 아니다. 2초가 실제 IPMI 장비에 충분한지는 측정해야 한다. 애플리케이션 STATUS 작업 1회 안에서 ipmitool 자체의 통신 재전송은 가능하다.

## 3. 시나리오별 확인 결과

아래 기존 확인 결과는 `fc8891623a`의 실제 Java 클래스와 모의 BMC/agent/DB 응답으로 검증했다. 마지막 행의 정체 결함은 작업 트리에서 수정했고, 수정 후 회귀 검증 결과는 6절에 별도로 기록했다. 실제 장비 전원 조작이나 스토리지 장애 시험 결과는 아니다.

| 시나리오 | 현재 코드의 동작 | 판정 |
|---|---|---|
| Health 정상, BMC ON | Available 유지, Activity 미실행 | 확인 |
| 10초 간격으로 OFF 3회 | Available → Suspect → Fencing. Activity를 기다리지 않음 | 확인 |
| OFF 응답 사이가 20초/30초로 늘어남 | 60초 이내이므로 OFF 1→2→3 유지 | 확인 |
| OFF 2회 후 ON 또는 UNKNOWN | OFF 누적 초기화. Health 비정상이면 일반 Activity 경로 가능 | 확인 |
| Health 비정상 + ALIVE 연속 3회 | Degraded 진입, 이후 관찰 지속. 정상 Health가 와야 Available | 확인 |
| Health 비정상 + DEAD 연속 4회 | Recovering → KVM Recovery 실패 누적 1회 → 다음 poll에서 Fencing 전이 조건 충족 | 확인 |
| Activity UNKNOWN | ALIVE/DEAD 연속성 해제, UNKNOWN을 DEAD로 누적하지 않음 | 확인 |
| BMC가 계속 불통 | 통신 불가만으로 OFF 판정 안 함. Activity로 DEAD를 확인해도 전원 펜싱을 검증하지 못하면 해당 경로의 VM 복구 완료로 진행하지 않음 | 모의 응답/태스크 테스트로 확인 |
| 정상 펜싱 | Maintenance → OFF 요청 → OFF 5회 확인 → ON 요청 → Fenced 기록 → VM 복구 작업 등록 | 단계별 테스트로 확인 |
| 펜싱 중 OFF 4회 후 UNKNOWN 또는 만료된 응답 | ON으로 넘어가지 않음 | 확인 |
| 장애 호스트가 재부팅 후 빨리 연결됨 | Maintenance 상태의 VM Start 차단, HA 배치에서 원래 호스트 제외 | 회귀 테스트로 확인 |
| OFF 관찰 간격이 반복해서 60.001초, 유효 간격 60초 | 수정 전에는 OFF 1회와 Health 우선이 반복되어 정체. 수정 후에는 만료된 증거의 우선권을 해제하고 Activity로 복귀 | **수정 후 회귀 테스트 통과** |

실제 Maintenance 및 다른 호스트에서의 VM 재시작까지 최종 검증하려면 현장 시험이 필요하다. PCS가 호스트를 다시 켜지 않는다는 전제를 유지한다. Mold 외부의 libvirt/PCS 자동 VM 시작은 Mold의 Start 차단 테스트가 보증하지 않는다. HA 대상 VM, 공유 볼륨, 대체 호스트 자원 등 기존 복구 자격 조건도 필요하다.

## 4. 60초 poll을 유지할 경우

| poll | OFF 최대 간격 | 판정 |
|---:|---:|---|
| 10 | 60 | 권장 테스트 조합. 정상적인 처리 지연에서 빠른 OFF 3회 관찰 가능 |
| 60 | 60 | 비권장. 작은 지연으로 OFF 누적이 만료되며, 수정 후에는 Activity로 복귀. 조기 OFF 확정은 보장되지 않음 |
| 60 | 180 | 느린 대안. 60.001초 간격의 3회 OFF가 누적됨을 모의 검증. 첫 관찰부터 약 120초 후 확정 |

180초는 대기시간이 아니라 OFF 증거 사이에 허용하는 최대 간격이다. 더 오래된 증거를 합칠 수 있게 범위를 넓히므로 단순히 크게 설정하는 방식으로 결함을 덮어서는 안 된다. **10초/60초도 실제 지연이 계속 60초를 넘으면 조기 OFF 3회는 누적되지 않을 수 있지만, 수정 후에는 Activity 경로로 관찰을 계속한다.**

현재 수정은 작업 선택 전 OFF 증거를 만료시키고, Health 응답 처리 중 만료된 경우에도 Activity 재확인 요청을 유지한다. 요청은 현재 작업으로 검증된 Activity 결과 처리 때 해제되며, 단순 예약·제출 실패·오래된 결과로 소모하지 않는다. UNKNOWN은 재확인 수행으로 처리하지만 DEAD로 세지 않는다. 정상 OFF 3회 확정과 기존 작업 중복 보호는 유지한다.

OFF 누적 처리에서 최대 간격이 실제 등록된 poll 이하이면 경고를 남긴다. provider·등록 poll·최대 간격 조합별로 관리 서버 실행 중 한 번 경고하며 설정을 자동 변경하지 않는다. poll의 실행 중 재등록은 구현하지 않았으므로 변경 후 재시작이 필요하다.

## 5. 적용 확인

1. 글로벌 값과 클러스터/스토리지 풀별 재정의 값을 함께 확인한다.
2. `ha.checking.interval=10`을 저장한 후 관리 서버를 재시작하여 HA 스케줄을 다시 등록한다. 여러 관리 서버가 있다면 각 서버의 적용값과 담당 호스트를 확인한다. UI에 변경 이벤트가 있다고 기존 스케줄이 즉시 갱신된 것은 아니다.
3. HA DEBUG 로그에서 대상 호스트의 `Fresh BMC OFF observations across health tasks`가 `1/3 → 2/3 → 3/3`으로 증가하는지 확인한다. STATUS 시작/완료 이벤트만으로 OFF 누적 여부를 판단하지 않는다.
4. Activity는 `[VM Activity Check] Observations:` 로그의 ALIVE/DEAD 횟수로 확인한다. 각 관찰은 DEBUG 로그이며, 이벤트 목록에 매번 기록되지 않는다.
5. Fencing 이후 Maintenance 유지와 다른 호스트의 VM 기동을 별도로 확인한다. ON 요청 성공은 OS 부팅 완료를 의미하지 않는다.

## 6. 검증 기록

### 최신 OFF 증거 만료 보완

Checkstyle을 활성화한 **44개 reactor 모듈 BUILD SUCCESS, 19개 suite의 251개 회귀 테스트 통과**, 실패·오류·제외 0개다. Checkstyle 감사 37개도 오류 없이 완료했다. 새 회귀 테스트 10개(counter 4개, manager 4개, task 2개)를 포함한다. 작업 선택 전 만료, 지연된 Health 응답에서의 만료, 반복 만료 후 Activity DEAD 누적과 Recovering/Fencing 연결, UNKNOWN 처리, 정상 OFF 3회, 작업 제출 실패·중복·오래된 결과 보호를 확인했다.

실행 로그: `/private/tmp/europa-ha-off-expiry-fix-tests.log`. 완료: `2026-09-11T16:47:38+09:00`. 모의 BMC/agent/DB 결과이며 실제 장애·전원 조작이나 RPM 패키징은 실행하지 않았다.

### 수정 전 기준 검증

아래는 정체 결함을 발견한 당시의 기록이며 수정 후 검증으로 해석하지 않는다.

- 당시 커밋 `fc8891623a`의 관련 44개 reactor 모듈: **BUILD SUCCESS**.
- Checkstyle 활성화. 선택한 기존 회귀 테스트 **241개 통과**, 실패/오류/제외 0. 완료: 2026-09-11 16:29:56 KST.
- 서버 권장 설정 추가 시나리오 **9개** 실행. 이 중 1개는 알려진 정체 결함의 재현 확인이며 정상 동작 통과를 뜻하지 않는다.
- KVM 전원 프로파일: 기존 24개 + 추가 4개 = **28개 통과**. 기존 24개는 위 241개와 중복되므로 총 고유 테스트 수에 다시 더하지 않는다.
- 추가 시나리오는 `/private/tmp`에서 현재 컴파일된 클래스/테스트 fixture를 사용했고, BMC/DB/provider 응답과 시간을 모의 처리했다.
- 실제 BMC/PCS/스토리지/VM 복구의 현장 통합 시험과 RPM 패키징은 이번 검증에 포함되지 않는다.

로컬 검증 파일:

- 회귀 로그: `/private/tmp/europa-ha-recommended-profile-regression.log`
- 서버 시나리오: `/private/tmp/ha-recommended-scenarios/org/apache/cloudstack/ha/RecommendedProfileScenarioTest.java`
- 서버 결과: `/private/tmp/ha-recommended-scenarios/results.log`
- 전원 시나리오: `/private/tmp/ha-power-profile-validation/RecommendedPowerProfileTest.java`
- 전원 결과: `/private/tmp/ha-power-profile-validation/test-output.log`

주요 소스:

- [설정 선언](../../plugins/hypervisors/kvm/src/main/java/org/apache/cloudstack/kvm/ha/KVMHAConfig.java)
- [poll/작업 선택](../../server/src/main/java/org/apache/cloudstack/ha/HAManagerImpl.java)
- [OFF 유효 간격](../../server/src/main/java/org/apache/cloudstack/ha/HAResourceCounter.java)
- [스케줄 등록](../../server/src/main/java/org/apache/cloudstack/poll/BackgroundPollManagerImpl.java)
- [전원 확인 및 펜싱 예산](../../plugins/hypervisors/kvm/src/main/java/org/apache/cloudstack/kvm/ha/KVMHAProvider.java)
- [Maintenance 및 VM 복구 단계](../../server/src/main/java/org/apache/cloudstack/ha/task/FenceTask.java)
