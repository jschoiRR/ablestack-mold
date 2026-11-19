# #1410 노드 생성 예외 후 미부트스트랩 삭제 복구

## 문제와 범위

기준 Europa `5b6751ff0f86919601c3b8036bf5fe430659b114`. 생성의 일반 RuntimeException과 VM start 비동기 작업 예외는 Manager의 공통 catch까지 전달되지만, StartWorker는 상태만 Error로 바꾸고 단계·실패 job을 남기지 않는다. DestroyWorker의 legacy 복구는 정확한 오류 문구에 의존해 일반 예외를 복구하지 못한다.

## 설계

- 공통 생성 실패 처리에서 현재 실제 CreateKubernetesCluster job의 UUID/리소스 일치를 확인한다. Nodes 단계이며 endpoint가 없고 bootstrap 시작과 Provider 소유권 표식이 없는 CloudManaged 생성만 NodeProvisioningFailed 및 실패 job UUID를 기록한다. 기존 Error 상태로 먼저 전환한 checked 예외도 같은 영수증을 남긴다. Preflight/Bootstrap/Ready 단계는 덮어쓰지 않는다.
- 삭제 시 기록만 믿지 않고 soft-removed 포함 최신 실제 생성 job이 FAILED이고 진행 중인 생성이 없는지 다시 확인한다. 기록된 UUID도 최신 job과 일치해야 한다.
- native 노드·중복 없는 VM ID·계정·Kubernetes 버전·ISO 미부착과 네트워크의 정확한 VM 집합을 확인한다. 외부/다른 소유자/추적 안 된 VM이나 bootstrap/endpoint/Provider 표식이 있으면 기존 보호 경로로 보존한다.
- 이전 코드의 Nodes 단계 일반 예외도 위 증거를 모두 만족하면 삭제 시 실제 failed job으로 영수증을 복원한다. 오류 문자열이나 운영 DB 수동 보정에 의존하지 않는다. 0개/부분 노드 및 Alert/Starting 상태와 삭제 재시도를 포함한다.
- 우회하는 것은 존재하지 않는 Kubernetes API의 Service/CSI 정리다. 네이티브 네트워크 영수증이 있으면 모든 소유권을 먼저 확인하고 노드 정지→확인된 ACL/규칙/IP 정리 순서로 처리하며, 오류 시 prepared 표식을 쓰지 않아 재시도한다. 기존 백업 가드, 노드·네트워크 멤버 검증, 소유 자원 영수증/미확인 네트워크 자원 보존, 정상 bootstrap 이후 cleanup은 유지한다.

## 검증과 배포

일반 런타임/체크 예외·비동기 start 예외의 실패 전파와 영수증, legacy 복구, 최신 job/권한/부트스트랩/소유자 경계·재시도를 Java 집중 테스트한다. WSL ext4에서 Kubernetes 변경 모듈만 빌드한다. 31번 설치 JAR의 변경 class family만 교체하고 나머지 엔트리·UI·설정·WEB-INF를 보존한다. 전용 fixture에서 실패→정상 API/UI 삭제→잔여 정리 및 기존 데이터 대조를 수행한다. 실패 주입은 전용 이름에 한정된 별도 검증 계층으로 기록하고 최종 배포물에 포함하지 않는다. 전체 Cloud 빌드/수동 Actions는 실행하지 않는다.
