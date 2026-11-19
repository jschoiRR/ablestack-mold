# #1410 Kubernetes 노드 생성 예외 후 삭제 복구 검증

검증일: 2026-10-18 KST · 31번 Mold/호스트 · 기준 Europa `5b6751ff0f86919601c3b8036bf5fe430659b114` · 구현 `a6ee7252cda`.

## 결과

일반 런타임 예외와 실제 비동기 VM start 예외에 실패 job/단계 영수증이 자동 기록됐고, 영수증이 누락된 이전 버전의 Nodes 상태도 **운영 DB 수동 보정 없이** 정상 API/UI로 삭제됐다. 최신 실패 생성 job, 미부트스트랩, 네이티브 노드·버전·소유권·네트워크의 VM 집합을 재확인하며, API/Provider/CSI 기동 이후 상태는 기존 보호 경로를 사용한다.

[설계](../../design/kubernetes-delete-recovery-1410.ko.md) · [집중 테스트 집계](test-summary.json) · [실제 작업 영수증](lifecycle-job-receipts.json).

## 구현·빌드

- StartWorker 공통 실패 처리: Nodes 단계의 실제 Create job UUID/리소스 일치를 확인해 `lifecycle.creation.failed.job`과 `NodeProvisioningFailed`를 기록한다. 이미 Error로 전환된 체크 예외도 처리한다. Preflight/Bootstrap/Ready와 기동 표식은 덮어쓰지 않는다.
- DestroyWorker: 최신 FAILED 생성 job/진행 중 생성 없음/기록 UUID 일치와 CloudManaged·빈 endpoint·bootstrap/Provider 표식 없음, ISO 미부착·네이티브 VM 소유·버전·네트워크 집합을 확인한다. 기존 Nodes 실패는 오류 문자열에 의존하지 않고 복구한다. 다른 VM·소유 범위·부트스트랩 상태는 보존한다.
- 미부트스트랩 소유 자원 영수증이 있으면 기존 검증기·정지·네이티브 ACL/규칙/IP 정리 경로를 사용하며 실패 시 prepared 표식을 쓰지 않는다. 백업/Service/CSI/미확인 네트워크 자원 가드는 유지한다.
- WSL ext4에서 **Kubernetes Maven 모듈만 package**, **11개 suite / 165 tests, failures/errors/skipped 0**. 일반 런타임·실제 start 메서드의 비동기 예외 전파, 실패 UUID, legacy, 동시/최신 job, 외부/중복/다른 소유자/버전·ISO·bootstrap/endpoint/Provider, 제거 재시도·소유 영수증 오류, 정상 Service/CSI 차단 회귀를 포함한다.

```bash
source /home/ablecloud/work/dhslove/europa-build-env.sh
mvn -pl plugins/integrations/kubernetes-service \
 -Dtest=KubernetesNodeProvisioningFailureTest,KubernetesCreatedFailureCleanupTest,KubernetesPreflightCleanupTest,KubernetesCreationComponentFailureTest,KubernetesFailedBootstrapCleanupTest,KubernetesServiceCleanupGateTest,KubernetesCsiCleanupGateTest,KubernetesOwnedResourceReceiptTest,KubernetesNativeNodeRuleOwnershipTest,KubernetesScaleNetworkOwnershipTest,KubernetesClusterManagerImplTest \
 -DfailIfNoTests=false -Dcheckstyle.skip=true -Drat.skip=true package
```

전체 Cloud/Release CI·RPM·수동 Actions는 실행하지 않았다. UI 소스 변경이 없어 UI 빌드/정적 재배포는 하지 않았다. CSI PV/PVC의 실제 장애 재현은 추가하지 않았으며 CSI 보존 경계는 Java 회귀 테스트로 확인했다.

## 31번 실제 검증

| 사례 | 실제 증거와 결과 |
| --- | --- |
| 노드 생성 전 일반 런타임 예외 | cluster86 `k1410-before`, create `2f19a178-18ca-4362-9953-0e44d768e407`가 StringIndexOutOfBoundsException으로 실패. 노드0·빈 endpoint·미부트스트랩이며 phase/job 자동 기록. [DB 근거](before-proof.tsv) |
| 무관 VM 보호·재시도 | 같은 전용 네트워크의 추적되지 않은 Stopped VM632를 정상 deploy API로 생성. 삭제 job 실패 시 해당 VM 상태/UUID 보존 확인. VM을 정상 destroy/expunge API로 정리한 뒤 cluster86 삭제 재시도 성공. [근거](foreign-preserved.json) |
| 실제 비동기 VM start 예외 | cluster88 `k1410-async`, create `49e32c97-fab5-4cff-b1e2-536238488f61`. 가상 라우터 메타데이터 생성에서 주입한 예외가 **VmWorkStart→VmWorkJobHandlerProxy→Create job**으로 전파. VM635 Stopped/host 없음/ISO 없음, phase/job 자동 기록. **UI 상세→작업→삭제→확인→목록 복귀** 및 실제 delete job 성공. [DB](async-proof.tsv) · [서버 로그](fault-log-proof.txt) · [확인 화면](async-delete-confirm.png) · [삭제 후](async-delete-complete.png) |
| legacy 영수증 누락 | cluster89 `k1410-legacy`, create `a7dec980-8a81-42ab-943d-208e105672ec`. 전용 주입 계층에서 기록 메서드만 생략해 Nodes·failed job·Stopped VM636·실패 영수증 없음 상태 재현. 정상 delete API가 실제 job/네이티브 증거를 확인하고 복구·삭제 성공. DB UPDATE/수동 보정 없음. [삭제 전 DB](legacy-proof.tsv) |
| 최종 바이너리 정상 회귀 | 주입 코드를 모두 제거한 최종 JAR에서 새 cluster90 `k1410-success` 생성 job 성공. 노드637/638 **2개 Ready**, Calico **2/2**를 실제 게스트 kubectl로 확인. 이후 UI 정상 삭제·목록 복귀·API job 성공. [게스트](success-guest-proof.json) · [실행 화면](final-running.png) · [최종 정리 화면](final-cleanup.png) |

### 주입 범위와 실패 시도 구분

[검증 전용 주입기](FaultInjector.java)는 오프라인에서 별도 시험 JAR에만 적용했다. StartWorker의 `k1410-before*` 이름, `k1410-legacy*` 이름과 VM 메타데이터의 고유 `k1410-fault` 토큰에만 반응한다. 제품 소스에 장애 주입 옵션을 추가하지 않았다. 최종 JAR의 StartWorker/NetworkModelImpl/CommandSetupHelper 세 class에서 임시 표식 부재와 최종 class 해시를 확인했다.

첫 async 주입은 NetworkModel 경로에만 적용해 실제 가상 라우터의 CommandSetupHelper 경로에 도달하지 않았다. 해당 cluster87은 **비동기 예외 검증 PASS로 계산하지 않는다**. 다음 시험 JAR 배포에 필요한 관리 서버 재시작 전 활성 API 작업이 이 전용 create 한 건뿐임을 확인했고, 자동 취소된 작업을 기존 정상 delete API로 정리했다. 이후 실제 CommandSetupHelper 경로의 cluster88에서 예외와 복구를 다시 확인했다. 수정 전 원본 #1410 재현 기록과도 구분한다.

최종 배포의 원격 적용·해시·HTTP 확인 뒤, 실행 중 검증 스크립트 파일 수정으로 로컬 셸 종료 구문 오류가 있었다. 서버 JAR의 해시/세 임시 주입 class 부재/Mold PID/WEB-INF/HTTP를 [별도 점검](final-artifact-runtime.txt)으로 재확인했으며, 그 최종 바이너리로 정상 생성·노드 Ready·UI 삭제까지 수행했다. 이 로컬 오케스트레이션 오류를 원격 배포 실패나 전체 검증 성공 로그로 바꾸어 기록하지 않는다.

## 배포와 보존

- 설치 JAR `/usr/share/cloudstack-management/lib/cloudstack-4.23.0.0-ABLESTACK.Mold.jar`의 **StartWorker/DestroyWorker class family 3개 엔트리만 교체**. 나머지 **204,497개 엔트리의 압축 데이터 동일**을 비교했다. [아티팩트 영수증](backend-artifact.json)
- 기존 JAR SHA256 `3b9bc6d9e6b766938e46ecde613ea00d073a08a435fc864c89e9827f560a36eb` → 최종 `9d3108fcee6c38a69a0714b7d7f21cf5dbd4e0758717945097b1740fce9abdf3`.
- 최종 백업 `/root/k1410-final-20261018-230859`와 최초 원본 백업 `/root/k1410-fault-20261018-225918`. [최종 배포 영수증](deploy-final.log)
- 활성 webapp **12,717개 파일 전체 해시 동일**, WEB-INF/config/기존 UI 보존. 최종 Mold active, PID1757143, `/client/` HTTP200. index SHA256 `96034ffd66057b3411783b5091a6e808abb5c4ac0505aecdb4605ef3803612e6`. [최종 점검](final-smoke.txt)

## 정리·기존 데이터 보존

시험 클러스터5건(주입 미적용 시도1건 포함), VM7개·라우터1개·네트워크1개·CNI1개·계정/도메인을 정상 Cloud API로 정리했다. 활성 클러스터/맵/상세/VM/볼륨/NIC/네트워크/IP/규칙/CNI/코멘트/계정/도메인의 **잔여13종 모두0**, 31.1/2/3 호스트의 전용 도메인 **0**이다.

기존 **CNI3개·Kubernetes7개/노드20개**의 API 식별/상태/호스트/연결 정보를 대조했고, 운영 소유 범위·본문·참조·코멘트 등 **DB36행 전후 동일**이다. DB SHA256 `2d61f159980ac4fa490f62c74e16ce6024ac1f8bb9210c2c6caaa366c74357f7`.

[API 보존](api-preservation.json) · [정리 요약](cleanup-summary.json) · [잔여 DB 집계](residual-counts.tsv) · [호스트 도메인](host-domain-cleanup.json).

## 다음 진행 후보

1. **#1261 NIC hotplug PCI 슬롯 부족의 실제 오류 전달·사전 검증**: 미구현 제품 결함으로 열려 있으며 다음 코드 개선 후보로 권장한다.
2. **#1236 VLAN44 게스트 통신 실패**: 물리 스위치 미설정 태그의 구성/시험 조건 확인이 선행한다. 지원 VLAN의 성공 결과와 구분한다.
3. **#1232 CLVM_NG 템플릿 캐시 잠금**: 기존 사용자 지시로 시험에서 제외한 저장소 범위다. 재개 범위를 정한 뒤 별도 진행하며 이번 이슈 완료에 포함하지 않는다.
