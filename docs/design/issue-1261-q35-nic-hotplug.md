# #1261: Q35 NIC 핫플러그 포트 확보와 실패 복구

## 문제와 원인

NIC 1개로 부팅한 x86 Q35 VM은 libvirt가 기동 장치에 필요한 PCIe root-port와 소수의 빈 포트만 자동 생성한다. 두 번째 NIC를 추가하면 마지막 빈 포트가 사용되어 세 번째 NIC부터 `No more available PCI slots`로 실패할 수 있다. 이는 Kubernetes 배포 고유 문제가 아니라 일반 VM의 PCIe 토폴로지 생성 문제다. NIC 개수 자체가 하드웨어 최대치에 도달한 것은 아니다.

기존 오류 전달에도 결함이 있다. KVM Agent의 `PlugNicAnswer(false, details)`를 관리 서버가 `false`로 바꾸고, VM 작업이 null 결과를 성공으로 직렬화하여 API/UI에 `null job execution result`만 표시한다. 상위 정리 코드는 생성된 NIC를 받지 못한 경우에도 네트워크 기준으로 광범위한 제거를 시도하고 정리 오류가 원인을 가릴 수 있다.

## 변경 계약

1. `LibvirtVMDef`가 전체 GuestDef의 machine type을 DevicesDef에 전달한다. KVM의 `q35` 및 `pc-q35-*`에 PCIe root 1개, root-port 32개와 legacy PCI bridge를 명시한다. 이는 총 root-port 수이며 NIC 32개를 보장한다는 의미가 아니다. 기존 aarch64/s390x 구성은 유지하고 i440fx, 일반 pc, LXC에는 추가하지 않는다. 재직렬화 시 컨트롤러를 누적하지 않는다.
2. 실행 중인 기존 VM의 PCIe 토폴로지는 변경하지 않는다. 포트가 부족한 VM은 Mold에서 정상 정지·시작하여 새 기동 XML을 적용하거나 정지 상태에서 NIC를 추가하고 시작한다. 게스트 내부 reboot만으로는 적용되지 않는다. 운영 VM #168은 정지·재시작·장치 변경 대상에서 제외한다.
3. KVM Agent는 PCI 슬롯 부족 원인과 정지·시작 복구 방법을 반환한다. attach가 실패한 새 VIF의 bookkeeping만 해제하고 공유 bridge는 제거하지 않는다.
4. 관리 서버는 호스트의 구체적인 오류를 예외로 전달해 하위 VM 작업도 실패시키고, 직렬화된 실패를 API/UI까지 보존한다. 인터럽트는 인터럽트 상태를 유지한다.
5. 하위 오케스트레이션은 방금 생성한 NIC만 정리한다. 상위 DHCP 옵션 처리 실패 시에는 반환된 NIC의 ID/UUID/VM/network 소유 관계가 모두 일치할 때만 해당 NIC를 제거한다. 정리 실패는 최초 원인에 suppressed exception으로 연결한다.
6. API 권한/계정·도메인 경계, 정지 상태 NIC 추가, 정상 핫플러그와 기존 네트워크를 유지한다. 새 API나 UI 설명 문구를 추가하지 않는다.

## 검증 계획

- 변경 Maven 모듈만 WSL ext4에서 빌드한다. 전체 Cloud 빌드 및 수동 GitHub Actions 전체 빌드는 요청하지 않는다.
- Q35/canonical machine/기존 아키텍처/비-Q35 및 반복 직렬화, Agent 성공·실패·정리, 하위 작업 결과 및 상위 NIC rollback 단위 회귀를 실행한다.
- 클러스터 31의 전용 계정/도메인/네트워크/VM을 사용한다. 실제 기동 1 NIC → 두 번째 핫플러그 성공 → 세 번째 실패를 수정 전 코드로 재현한다.
- 배포 후 기존 실행 토폴로지에서 UI 실패 원인·복구 안내, failed VM work, NIC/DHCP/주소·네트워크 카운트 정리와 기존 통신을 확인한다.
- 전용 VM을 정지·시작하고 root-port 32개를 실제 XML에서 확인한다. 세 번째와 네 번째 NIC를 UI/API로 추가하고 API·libvirt·게스트 인식/통신을 대조한다. 정지 상태 추가 및 정상 제거도 검증한다.
- 설치된 관리/Agent JAR를 백업하고 변경 클래스 family만 기존 JAR에 반영한다. 기존 #1410 코드, UI/WEB-INF/config, 운영 VM을 보존한다. 전용 fixture를 API로 정리하고 잔여 자원과 기존 응답/DB를 비교한다.

## 범위와 제한

현재 실행 VM에 root-port를 강제로 주입하거나 운영 VM을 일괄 정지하지 않는다. 게스트 OS가 새 NIC를 자동 구성하지 않는 경우 OS 네트워크 설정은 별도이며, 검증 VM에서는 MAC/IP를 대조하여 별도 구성하고 연결을 확인한다. PCIe 디바이스가 많거나 사용자가 별도 XML을 지정한 VM의 무제한 핫플러그를 보장하지 않는다. i440fx나 QEMU 버전 전환은 이 변경에 포함하지 않는다.

## 근거

- libvirt PCI hotplug: https://libvirt.org/pci-hotplug.html
- libvirt PCI controller XML: https://libvirt.org/formatdomain.html#controllers
- 원인 재현 및 배포 결과: [클러스터 31 검증 보고서](../../developer/issue-1261/validation.md) 참조.
