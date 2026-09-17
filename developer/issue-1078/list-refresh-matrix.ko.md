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

# #1078 목록 갱신 적용 매트릭스

기준: upstream/ablestack-europa b92b9b2cb43d8e8386ad29d8c8a65d312b693e24.

구문 검색 181개 후보를 표시 목적/조회 소유자별로 분류했다. 이 표는 소스 적용 경로이며 모든 운영 화면의 브라우저 PASS를 뜻하지 않는다. 실제 테스트 범위는 verification.ko.md에 기록한다.

## 공통 계약

- 최초 응답 후 기존 snapshot을 유지하며 성공 응답으로 교체한다. 동일 조회 범위에서는 전체 loading/빈 배열을 선제 적용하지 않는다.
- 활성 DOM/브라우저에서 요청 완료 후 다음 주기를 예약한다. 실패 시 최대 120초 backoff. 입력 포커스/편집 표식/열린 모달에서는 주기 조회를 유예한다.
- 일반 10초, DR runs 5초, 상세 props 30초, CPU/스토리지/진단/Quota 60초. 변경 API와 호스트 장치 재수집은 자동 호출하지 않는다.
- 조회 범위/요청 token 검증으로 이탈 후 응답을 폐기한다. 실패 시 기존 데이터와 stale 상태를 유지한다.
- 입력용 선택 목록은 선택/옵션 snapshot을 유지하며 기존 열기/검색/수동 조회 경로를 사용한다.

## 후보별 처리

| 파일 | 분류 | 처리 경로/이유 |
|---|---|---|
| [DrCheckpointManager.vue](../../ui/src/components/dr/DrCheckpointManager.vue) | 상위 갱신 | DR 체크포인트 props/작업 완료. |
| [DrResourceDetailsTab.vue](../../ui/src/components/dr/DrResourceDetailsTab.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [HeaderNotice.vue](../../ui/src/components/header/HeaderNotice.vue) | 작업 이벤트 | 전역 알림 store/job polling. |
| [AnnotationsTab.vue](../../ui/src/components/view/AnnotationsTab.vue) | 명시적 adapter | getAnnotations — 등록된 읽기 loader. |
| [ApiKeyPairsTab.vue](../../ui/src/components/view/ApiKeyPairsTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [BackupMetadata.vue](../../ui/src/components/view/BackupMetadata.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [BulkActionProgress.vue](../../ui/src/components/view/BulkActionProgress.vue) | 작업 이벤트 | 작업 진행 store/props. |
| [BulkActionView.vue](../../ui/src/components/view/BulkActionView.vue) | 작업 snapshot | 선택한 작업 대상과 진행 상태 유지. |
| [DedicateData.vue](../../ui/src/components/view/DedicateData.vue) | 명시적 adapter | fetchDedicatedZones, fetchDedicatedPods, fetchDedicatedClusters, fetchDedicatedHosts — 등록된 읽기 loader. |
| [DetailSettings.vue](../../ui/src/components/view/DetailSettings.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [DetailsTab.vue](../../ui/src/components/view/DetailsTab.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [DomainDeleteConfirm.vue](../../ui/src/components/view/DomainDeleteConfirm.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [EventSidebar.vue](../../ui/src/components/view/EventSidebar.vue) | 명시적 adapter | fetchEvents, fetchAlerts — 등록된 읽기 loader. |
| [EventsTab.vue](../../ui/src/components/view/EventsTab.vue) | 명시적 adapter | fetchEvents — 등록된 읽기 loader. |
| [GPUDevicesTab.vue](../../ui/src/components/view/GPUDevicesTab.vue) | 명시적 adapter | fetchDevicesData — 등록된 읽기 loader. |
| [GPUSummaryTab.vue](../../ui/src/components/view/GPUSummaryTab.vue) | 명시적 adapter | fetchSummaryData — 등록된 읽기 loader. |
| [ImageStoreSelectView.vue](../../ui/src/components/view/ImageStoreSelectView.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [InstanceNicsNetworkSelectListView.vue](../../ui/src/components/view/InstanceNicsNetworkSelectListView.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [InstanceVolumesStoragePoolSelectListView.vue](../../ui/src/components/view/InstanceVolumesStoragePoolSelectListView.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ListResourceTable.vue](../../ui/src/components/view/ListResourceTable.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [ListView.vue](../../ui/src/components/view/ListView.vue) | 공통 renderer | 부모 snapshot 수신, 안정 키 및 선택 재연결. |
| [NicNetworkSelectForm.vue](../../ui/src/components/view/NicNetworkSelectForm.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ObjectListTable.vue](../../ui/src/components/view/ObjectListTable.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [ObjectStoreBrowser.vue](../../ui/src/components/view/ObjectStoreBrowser.vue) | 명시적 adapter | listObjects — 등록된 읽기 loader. |
| [ResourceCountUsage.vue](../../ui/src/components/view/ResourceCountUsage.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [Setting.vue](../../ui/src/components/view/Setting.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [StoragePoolSelectView.vue](../../ui/src/components/view/StoragePoolSelectView.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [VgpuProfilesTab.vue](../../ui/src/components/view/VgpuProfilesTab.vue) | 명시적 adapter | fetchVgpuProfiles — 등록된 읽기 loader. |
| [VmwareData.vue](../../ui/src/components/view/VmwareData.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [VolumesTab.vue](../../ui/src/components/view/VolumesTab.vue) | 명시적 adapter | getVolumes — 등록된 읽기 loader. |
| [WebhookDeliveriesTab.vue](../../ui/src/components/view/WebhookDeliveriesTab.vue) | 명시적 adapter | fetchDeliveries — 등록된 읽기 loader. |
| [WebhookFiltersTab.vue](../../ui/src/components/view/WebhookFiltersTab.vue) | 명시적 adapter | fetchFilters — 등록된 읽기 loader. |
| [DetailsInput.vue](../../ui/src/components/widgets/DetailsInput.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [AutogenView.vue](../../ui/src/views/AutogenView.vue) | 공통 controller | 목록 10초/상세 30초, 조회 범위 및 응답 세대 검증. |
| [AutomationControllerTab.vue](../../ui/src/views/automation/AutomationControllerTab.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [DeployedResourceTab.vue](../../ui/src/views/automation/DeployedResourceTab.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [AutoScaleDownPolicyTab.vue](../../ui/src/views/compute/AutoScaleDownPolicyTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [AutoScaleLoadBalancing.vue](../../ui/src/views/compute/AutoScaleLoadBalancing.vue) | 명시적 adapter | fetchLBRules — 등록된 읽기 loader. |
| [AutoScaleUpPolicyTab.vue](../../ui/src/views/compute/AutoScaleUpPolicyTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [AutoScaleVmProfile.vue](../../ui/src/views/compute/AutoScaleVmProfile.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [ChangeAffinity.vue](../../ui/src/views/compute/ChangeAffinity.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [CreateAutoScaleVmGroup.vue](../../ui/src/views/compute/CreateAutoScaleVmGroup.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [CreateKubernetesCluster.vue](../../ui/src/views/compute/CreateKubernetesCluster.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [DeployVM.vue](../../ui/src/views/compute/DeployVM.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [DeployVnfAppliance.vue](../../ui/src/views/compute/DeployVnfAppliance.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [DestroyVM.vue](../../ui/src/views/compute/DestroyVM.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [FtctlTab.vue](../../ui/src/views/compute/FtctlTab.vue) | 기존 전용 갱신 | cached/runtime 구분과 기존 background refresh 계약 유지. |
| [GuestNetworkTab.vue](../../ui/src/views/compute/GuestNetworkTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [InstanceTab.vue](../../ui/src/views/compute/InstanceTab.vue) | 명시적 adapter | loadDevicesFromDb — 등록된 읽기 loader. |
| [KubernetesServiceTab.vue](../../ui/src/views/compute/KubernetesServiceTab.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [MigrateWizard.vue](../../ui/src/views/compute/MigrateWizard.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ResetSshKeyPair.vue](../../ui/src/views/compute/ResetSshKeyPair.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ResetUserData.vue](../../ui/src/views/compute/ResetUserData.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ResourceSchedules.vue](../../ui/src/views/compute/ResourceSchedules.vue) | 명시적 adapter | fetchSchedules — 등록된 읽기 loader. |
| [BackupSchedule.vue](../../ui/src/views/compute/backup/BackupSchedule.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [DRTable.vue](../../ui/src/views/compute/dr/DRTable.vue) | 명시적 adapter | getDrClusterList — 등록된 읽기 loader. |
| [AffinityGroupSelection.vue](../../ui/src/views/compute/wizard/AffinityGroupSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ComputeOfferingSelection.vue](../../ui/src/views/compute/wizard/ComputeOfferingSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [DiskOfferingSelection.vue](../../ui/src/views/compute/wizard/DiskOfferingSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [LoadBalancerSelection.vue](../../ui/src/views/compute/wizard/LoadBalancerSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [MultiDiskSelection.vue](../../ui/src/views/compute/wizard/MultiDiskSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [MultiNetworkSelection.vue](../../ui/src/views/compute/wizard/MultiNetworkSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [NetworkConfiguration.vue](../../ui/src/views/compute/wizard/NetworkConfiguration.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [NetworkSelection.vue](../../ui/src/views/compute/wizard/NetworkSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [OsBasedImageRadioGroup.vue](../../ui/src/views/compute/wizard/OsBasedImageRadioGroup.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [SecurityGroupSelection.vue](../../ui/src/views/compute/wizard/SecurityGroupSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [SshKeyPairSelection.vue](../../ui/src/views/compute/wizard/SshKeyPairSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [StorageRbdImageSelection.vue](../../ui/src/views/compute/wizard/StorageRbdImageSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [TemplateIsoRadioGroup.vue](../../ui/src/views/compute/wizard/TemplateIsoRadioGroup.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [UserDataSelection.vue](../../ui/src/views/compute/wizard/UserDataSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [VnfNicsSelection.vue](../../ui/src/views/compute/wizard/VnfNicsSelection.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [VolumeDiskOfferingSelectView.vue](../../ui/src/views/compute/wizard/VolumeDiskOfferingSelectView.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [UsageDashboard.vue](../../ui/src/views/dashboard/UsageDashboard.vue) | 기간 보고서 | 사용량 집계 기간 및 보고서 실행 시점 유지. |
| [DesktopTab.vue](../../ui/src/views/desktop/DesktopTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [EventDownload.vue](../../ui/src/views/event/EventDownload.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ExtensionCustomActionsTab.vue](../../ui/src/views/extension/ExtensionCustomActionsTab.vue) | 명시적 adapter | fetchCustomActions — 등록된 읽기 loader. |
| [ExtensionResourcesTab.vue](../../ui/src/views/extension/ExtensionResourcesTab.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [ExternalConfigurationDetails.vue](../../ui/src/views/extension/ExternalConfigurationDetails.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [ParametersInput.vue](../../ui/src/views/extension/ParametersInput.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [AddLdapAccount.vue](../../ui/src/views/iam/AddLdapAccount.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ApiKeyPairPermissionTable.vue](../../ui/src/views/iam/ApiKeyPairPermissionTable.vue) | 명시적 adapter | fetchKeyData — 등록된 읽기 loader. |
| [SSLCertificateTab.vue](../../ui/src/views/iam/SSLCertificateTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [IsoZones.vue](../../ui/src/views/image/IsoZones.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [TemplateVnfSettings.vue](../../ui/src/views/image/TemplateVnfSettings.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [TemplateZones.vue](../../ui/src/views/image/TemplateZones.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [AsyncJobsTab.vue](../../ui/src/views/infra/AsyncJobsTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [ClusterDRSTab.vue](../../ui/src/views/infra/ClusterDRSTab.vue) | 명시적 adapter | fetchDRSPlans — 등록된 읽기 loader. |
| [ConnectedAgentsTab.vue](../../ui/src/views/infra/ConnectedAgentsTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [CpuSockets.vue](../../ui/src/views/infra/CpuSockets.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [DisasterRecoveryClusterVmTab.vue](../../ui/src/views/infra/DisasterRecoveryClusterVmTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [HostInfo.vue](../../ui/src/views/infra/HostInfo.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [HostRedfishTab.vue](../../ui/src/views/infra/HostRedfishTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [IntegrityVerificationTab.vue](../../ui/src/views/infra/IntegrityVerificationTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [ListHostDevicesTab.vue](../../ui/src/views/infra/ListHostDevicesTab.vue) | 명시적 재수집 | agent 장치 스캔 및 기존 할당 정리와 결합. 사용자 재수집 유지; 자동 스캔 금지. |
| [ManagementServerPeerTab.vue](../../ui/src/views/infra/ManagementServerPeerTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [Metrics.vue](../../ui/src/views/infra/Metrics.vue) | 명시적 adapter | fetchDetails — 등록된 읽기 loader. |
| [OobmTab.vue](../../ui/src/views/infra/OobmTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [Resources.vue](../../ui/src/views/infra/Resources.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [SecurityCheckTab.vue](../../ui/src/views/infra/SecurityCheckTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [StorageBrowser.vue](../../ui/src/views/infra/StorageBrowser.vue) | 명시적 adapter | fetchImageStoreObjects, fetchPrimaryStoreObjects — 등록된 읽기 loader. |
| [UsageRecords.vue](../../ui/src/views/infra/UsageRecords.vue) | 기간 보고서 | 기간·계정 보고서 실행/다운로드 snapshot 유지. |
| [WallAlertSilenceTab.vue](../../ui/src/views/infra/WallAlertSilenceTab.vue) | 명시적 adapter | fetchSilences — 등록된 읽기 loader. |
| [DrPlanList.vue](../../ui/src/views/infra/dr/DrPlanList.vue) | 명시적 adapter | fetchList — 등록된 읽기 loader. |
| [DrProtectionInfoTab.vue](../../ui/src/views/infra/dr/DrProtectionInfoTab.vue) | 기존 전용 갱신 | DR protection controller 유지. |
| [DrReplicaTab.vue](../../ui/src/views/infra/dr/DrReplicaTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [DrRestorePointsTab.vue](../../ui/src/views/infra/dr/DrRestorePointsTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [DrRunsTab.vue](../../ui/src/views/infra/dr/DrRunsTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [DrSiteList.vue](../../ui/src/views/infra/dr/DrSiteList.vue) | 명시적 adapter | fetchList, refreshSiteDetail — 등록된 읽기 loader. |
| [DrSyncCheckpointsTab.vue](../../ui/src/views/infra/dr/DrSyncCheckpointsTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [DedicatedVLANTab.vue](../../ui/src/views/infra/network/DedicatedVLANTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [IpRangesTabGuest.vue](../../ui/src/views/infra/network/IpRangesTabGuest.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [IpRangesTabManagement.vue](../../ui/src/views/infra/network/IpRangesTabManagement.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [IpRangesTabPublic.vue](../../ui/src/views/infra/network/IpRangesTabPublic.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [IpRangesTabStorage.vue](../../ui/src/views/infra/network/IpRangesTabStorage.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [ProviderDetail.vue](../../ui/src/views/infra/network/providers/ProviderDetail.vue) | 상위 갱신 | provider 상세 props/구성 작업. |
| [ProviderListView.vue](../../ui/src/views/infra/network/providers/ProviderListView.vue) | 상위 갱신 | physical network/provider 부모 데이터. |
| [RouterHealthCheck.vue](../../ui/src/views/infra/routers/RouterHealthCheck.vue) | 명시적 adapter | getHealthChecks — 등록된 읽기 loader. |
| [AsNumbersTab.vue](../../ui/src/views/infra/zone/AsNumbersTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [BgpPeersTab.vue](../../ui/src/views/infra/zone/BgpPeersTab.vue) | 명시적 adapter | fetchZoneBgpPeer — 등록된 읽기 loader. |
| [IpAddressRangeForm.vue](../../ui/src/views/infra/zone/IpAddressRangeForm.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [Ipv4GuestSubnetsTab.vue](../../ui/src/views/infra/zone/Ipv4GuestSubnetsTab.vue) | 명시적 adapter | fetchZoneIpv4Subnet — 등록된 읽기 loader. |
| [PhysicalNetworksTab.vue](../../ui/src/views/infra/zone/PhysicalNetworksTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [RackDiagramTab.vue](../../ui/src/views/infra/zone/RackDiagramTab.vue) | 상위 갱신 | zone 리소스 표시/랙 편집. |
| [SystemVmsTab.vue](../../ui/src/views/infra/zone/SystemVmsTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [ZoneWizardPhysicalNetworkSetupStep.vue](../../ui/src/views/infra/zone/ZoneWizardPhysicalNetworkSetupStep.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ZoneWizardRegisterTemplate.vue](../../ui/src/views/infra/zone/ZoneWizardRegisterTemplate.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ChangeBgpPeerForNetwork.vue](../../ui/src/views/network/ChangeBgpPeerForNetwork.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ChangeBgpPeerForVpc.vue](../../ui/src/views/network/ChangeBgpPeerForVpc.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [DesktopNicsTable.vue](../../ui/src/views/network/DesktopNicsTable.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [EgressRulesTab.vue](../../ui/src/views/network/EgressRulesTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [EnableStaticNat.vue](../../ui/src/views/network/EnableStaticNat.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [FirewallRules.vue](../../ui/src/views/network/FirewallRules.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [GuestIpRanges.vue](../../ui/src/views/network/GuestIpRanges.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [GuestVlanNetworksTab.vue](../../ui/src/views/network/GuestVlanNetworksTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [ImportNetworkACL.vue](../../ui/src/views/network/ImportNetworkACL.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [IngressEgressRuleConfigure.vue](../../ui/src/views/network/IngressEgressRuleConfigure.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [InternalLBAssignedVmTab.vue](../../ui/src/views/network/InternalLBAssignedVmTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [IpAddressesTab.vue](../../ui/src/views/network/IpAddressesTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [Ipv6FirewallRulesTab.vue](../../ui/src/views/network/Ipv6FirewallRulesTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [LoadBalancing.vue](../../ui/src/views/network/LoadBalancing.vue) | 명시적 adapter | fetchLBRules — 등록된 읽기 loader. |
| [NetworkPermissions.vue](../../ui/src/views/network/NetworkPermissions.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [NicsTab.vue](../../ui/src/views/network/NicsTab.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [NicsTable.vue](../../ui/src/views/network/NicsTable.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [PortForwarding.vue](../../ui/src/views/network/PortForwarding.vue) | 명시적 adapter | fetchPFRules — 등록된 읽기 loader. |
| [RoutersTab.vue](../../ui/src/views/network/RoutersTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [RoutingFirewallRulesTab.vue](../../ui/src/views/network/RoutingFirewallRulesTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [StaticRoutesTab.vue](../../ui/src/views/network/StaticRoutesTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [VnfAppliancesTab.vue](../../ui/src/views/network/VnfAppliancesTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [VpcTab.vue](../../ui/src/views/network/VpcTab.vue) | 명시적 adapter | fetchComments, fetchPrivateGateways, fetchVpnConnections, fetchAclList — 등록된 읽기 loader. |
| [VpcTiersTab.vue](../../ui/src/views/network/VpcTiersTab.vue) | 명시적 adapter | refreshTiers — 등록된 읽기 loader. |
| [DnsRecordsTab.vue](../../ui/src/views/network/dns/DnsRecordsTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [FirewallPolicyTab.vue](../../ui/src/views/network/tungsten/FirewallPolicyTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [FirewallRuleTab.vue](../../ui/src/views/network/tungsten/FirewallRuleTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [FirewallTagTab.vue](../../ui/src/views/network/tungsten/FirewallTagTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [LogicalRouterTab.vue](../../ui/src/views/network/tungsten/LogicalRouterTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [NetworkPolicyTab.vue](../../ui/src/views/network/tungsten/NetworkPolicyTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [TungstenFabricPolicyRule.vue](../../ui/src/views/network/tungsten/TungstenFabricPolicyRule.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [TungstenFabricPolicyTag.vue](../../ui/src/views/network/tungsten/TungstenFabricPolicyTag.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [TungstenNetworkTable.vue](../../ui/src/views/network/tungsten/TungstenNetworkTable.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [AddNetworkOffering.vue](../../ui/src/views/offering/AddNetworkOffering.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [AddVpcOffering.vue](../../ui/src/views/offering/AddVpcOffering.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [CloneNetworkOffering.vue](../../ui/src/views/offering/CloneNetworkOffering.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [CloneVpcOffering.vue](../../ui/src/views/offering/CloneVpcOffering.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ApiDocsPlugin.vue](../../ui/src/views/plugins/ApiDocsPlugin.vue) | 정적 metadata | 로그인 세션 API metadata. |
| [QuotaBalanceTab.vue](../../ui/src/views/plugins/quota/QuotaBalanceTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [QuotaCreditTab.vue](../../ui/src/views/plugins/quota/QuotaCreditTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [QuotaUsageTab.vue](../../ui/src/views/plugins/quota/QuotaUsageTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [AccountsTab.vue](../../ui/src/views/project/AccountsTab.vue) | 명시적 adapter | fetchProjectAccounts — 등록된 읽기 loader. |
| [InvitationsTemplate.vue](../../ui/src/views/project/InvitationsTemplate.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [ProjectRoleTab.vue](../../ui/src/views/project/iam/ProjectRoleTab.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [ConfigurationHierarchy.vue](../../ui/src/views/setting/ConfigurationHierarchy.vue) | 구성 설명 | 설정 적용 계층. |
| [ConfigurationTable.vue](../../ui/src/views/setting/ConfigurationTable.vue) | 상위 갱신 | ConfigurationTab snapshot. |
| [ConfigurationValue.vue](../../ui/src/views/setting/ConfigurationValue.vue) | 편집 보호 | ConfigurationTab draft; 편집 표식으로 polling 유예. |
| [ScheduledSnapshots.vue](../../ui/src/views/storage/ScheduledSnapshots.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [SharedFSTab.vue](../../ui/src/views/storage/SharedFSTab.vue) | 명시적 adapter | fetchStorageServiceData — 등록된 읽기 loader. |
| [SnapshotZones.vue](../../ui/src/views/storage/SnapshotZones.vue) | 명시적 adapter | fetchData — 등록된 읽기 loader. |
| [StorageServiceRuntimeUpgrade.vue](../../ui/src/views/storage/StorageServiceRuntimeUpgrade.vue) | 작업 snapshot | 업그레이드 준비/실행 상태와 선택 대상 유지. |
| [ImportUnmanagedInstance.vue](../../ui/src/views/tools/ImportUnmanagedInstance.vue) | 입력/작업 snapshot | 선택·배포·변경·확인·편집용 목록. 기존 열기/검색/명시적 조회 유지. |
| [ImportVmTasks.vue](../../ui/src/views/tools/ImportVmTasks.vue) | 상위 갱신 | 상위 resource/store/props snapshot을 표시. 열린 편집은 공통 유예 조건 적용. |
| [ManageInstances.vue](../../ui/src/views/tools/ManageInstances.vue) | 명시적 adapter | fetchImportVmTasks — 등록된 읽기 loader. |
| [ManageVolumes.vue](../../ui/src/views/tools/ManageVolumes.vue) | 명시적 adapter | fetchUnmanagedVolumes, fetchManagedVolumes — 등록된 읽기 loader. |

## 후보 외 경로 및 부작용 구분

- EventSidebar: 활성 이벤트/경보 탭만 5초 completion-driven 조회.
- InstanceTab: VM 장치 할당은 DB 조회. background에서는 host LUN 재스캔을 호출하지 않고 기존 설명 재사용.
- SharedFSTab: 기존 atomic runtime snapshot/세대 검증에 60초 controller 연결.
- 기본 section 라우트 목록은 AutogenView 사용. ExtensionResourcesTab은 extension resources computed, ExtensionCustomActionsTab은 adapter 사용.
- ListHostDevicesTab.fetchLunDevices는 단순 DB 읽기가 아니다. ManagementServerImpl.listHostLunDevices가 agent ListHostLunDeviceCommand를 전송하고 기존 UI loader 일부는 할당 정리를 수행한다. 사용자 재수집 동작 유지.
- 보고서/다운로드는 기간 snapshot이므로 자동 실행 제외.
- CPU 합산은 호스트 페이지를 수집한 뒤 한 번 반영. 일반 목록은 현재 페이지만 조회.

분류 합계: 상위 갱신 26, 작업 이벤트 2, 명시적 adapter 93, 작업 snapshot 2, 입력/작업 snapshot 48, 공통 renderer 1, 공통 controller 1, 기존 전용 갱신 2, 기간 보고서 2, 명시적 재수집 1, 정적 metadata 1, 구성 설명 1, 편집 보호 1.
