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

# Changed module and UI build evidence

```json
[
  {
    "suite": "org.apache.cloudstack.api.command.user.vm.UpdateVMGroupParametersTest",
    "tests": "9",
    "failures": "0",
    "errors": "0",
    "skipped": "0",
    "time": "0.619"
  },
  {
    "suite": "com.cloud.vm.VmInstanceGroupAssignmentTest",
    "tests": "14",
    "failures": "0",
    "errors": "0",
    "skipped": "0",
    "time": "0.317"
  },
  {
    "suite": "com.cloud.vm.UserVmManagerImplTest",
    "tests": "18",
    "failures": "0",
    "errors": "0",
    "skipped": "0",
    "time": "10.966"
  }
]
```

## api.log

```text
[INFO] Building Apache CloudStack API 4.23.0.0
[INFO] You have 0 Checkstyle violations.
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.619 s - in org.apache.cloudstack.api.command.user.vm.UpdateVMGroupParametersTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  32.000 s
[INFO] Finished at: 2026-10-13T14:47:05+09:00
```

## server-focused.log

```text
[INFO] Building Apache CloudStack Server 4.23.0.0
[INFO] You have 0 Checkstyle violations.
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 10.966 s - in com.cloud.vm.UserVmManagerImplTest
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.317 s - in com.cloud.vm.VmInstanceGroupAssignmentTest
[INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  24.101 s
[INFO] Finished at: 2026-10-13T14:41:14+09:00
```

## rat.log

```text
[INFO] Apache CloudStack API                                              [jar]
[INFO] Apache CloudStack Server                                           [jar]
[INFO] Building Apache CloudStack API 4.23.0.0                            [1/2]
[INFO] Building Apache CloudStack Server 4.23.0.0                         [2/2]
[INFO] Reactor Summary for Apache CloudStack API 4.23.0.0:
[INFO] Apache CloudStack API .............................. SUCCESS [  4.158 s]
[INFO] Apache CloudStack Server ........................... SUCCESS [  1.644 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  8.769 s
[INFO] Finished at: 2026-10-13T15:18:37+09:00
```

## ui-tests.log

```text

> cloudstack-ui@4.22.0 test:unit /home/ablecloud/work/dhslove/cloud-epic-1357-vm-group-ui/ui
> vue-cli-service test:unit "--runTestsByPath" "tests/unit/views/compute/VmGroupAssignment.spec.js" "--runInBand" "--coverage=false" "--forceExit"

Browserslist: caniuse-lite is outdated. Please run:
  npx update-browserslist-db@latest
  Why you should do it regularly: https://github.com/browserslist/update-db#readme
PASS tests/unit/views/compute/VmGroupAssignment.spec.js
  ✓ preserves membership for unchanged names and an initially ungrouped VM (4ms)
  ✓ sends explicit removal for cleared input and existing IDs for moves (2ms)
  ✓ retains exact legacy names when creating a group or using an older server (1ms)
  ✓ rejects spaces and refuses to silently ignore clear on an older server (4ms)
  ✓ keeps changed VM attributes out of the group-only path (2ms)
  ✓ submits a metadata-only request for group  (2ms)
  ✓ submits a metadata-only request for group B (1ms)
  ✓ submits a metadata-only request for group New
  ✓ retains form values and submits nothing for a rejected whitespace name (23ms)

Test Suites: 1 passed, 1 total
Tests:       9 passed, 9 total
Snapshots:   0 total
Time:        9.093s
Ran all test suites within paths "tests/unit/views/compute/VmGroupAssignment.spec.js".
Force exiting Jest: Have you considered using `--detectOpenHandles` to detect async operations that kept running after all tests finished?
```

## ui-lint.log

```text
Browserslist: caniuse-lite is outdated. Please run:
  npx update-browserslist-db@latest
  Why you should do it regularly: https://github.com/browserslist/update-db#readme
 DONE  No lint errors found!
```

## UI production build

```text
  dist/css/chunk-76164dea.0ee2ad71.css    0.06 KiB          0.07 KiB
  dist/css/chunk-f8c1fd06.bccd434d.css    0.06 KiB          0.08 KiB
  dist/css/chunk-275a37c6.b5ab1173.css    0.04 KiB          0.06 KiB
  dist/css/chunk-306332a8.afadbd41.css    0.04 KiB          0.06 KiB

  Images and other types of assets omitted.

 DONE  Build complete. The dist directory is ready to be deployed.
 INFO  Check out deployment instructions at https://cli.vuejs.org/guide/deployment.html


> cloudstack-ui@4.22.0 postbuild /home/ablecloud/work/dhslove/cloud-epic-1357-vm-group-ui/ui
> ./postbuild.sh

Post-build: removing all docHelp suffixes in ./public/config.json
[SKIP] ABLESTACK_UI_BUILD_VERSION is not set; keeping buildVersion: V4.0-4.0.15
```

## Unchanged base test failures

```text
	at com.cloud.vm.UserVmManagerImplTest.lambda$testUnmanageUserVMUnsupportedHypervisor$50(UserVmManagerImplTest.java:4301) ~[test-classes/:?]
	at com.cloud.vm.UserVmManagerImplTest.testUnmanageUserVMUnsupportedHypervisor(UserVmManagerImplTest.java:4300) ~[test-classes/:?]
[MockitoHint] UserVmManagerImplTest.validateInputsAndPermissionForUpdateVirtualMachineCommandTest (see javadoc for MockitoHint):
[MockitoHint] 1. Unused... -> at com.cloud.vm.UserVmManagerImplTest.validateInputsAndPermissionForUpdateVirtualMachineCommandTest(UserVmManagerImplTest.java:615)
	at com.cloud.vm.UserVmManagerImplTest.lambda$testUnmanageUserVMInvalidState$48(UserVmManagerImplTest.java:4285) ~[test-classes/:?]
	at com.cloud.vm.UserVmManagerImplTest.testUnmanageUserVMInvalidState(UserVmManagerImplTest.java:4284) ~[test-classes/:?]
[MockitoHint] UserVmManagerImplTest.updateVirtualMachineTestDisplayChanged (see javadoc for MockitoHint):
[MockitoHint] 1. Unused... -> at com.cloud.vm.UserVmManagerImplTest.configureDoNothingForMethodsThatWeDoNotWantToTest(UserVmManagerImplTest.java:746)
[MockitoHint]  ...args ok? -> at com.cloud.vm.UserVmManagerImplTest.updateVirtualMachineTestDisplayChanged(UserVmManagerImplTest.java:630)
	at com.cloud.vm.UserVmManagerImplTest.lambda$testUnmanageUserVMItManagerReturnsFalse$52(UserVmManagerImplTest.java:4324) ~[test-classes/:?]
	at com.cloud.vm.UserVmManagerImplTest.testUnmanageUserVMItManagerReturnsFalse(UserVmManagerImplTest.java:4323) ~[test-classes/:?]
[MockitoHint] UserVmManagerImplTest.testRestoreVMWithVolumeSnapshots (see javadoc for MockitoHint):
[MockitoHint] 1. Unused... -> at com.cloud.vm.UserVmManagerImplTest.testRestoreVMWithVolumeSnapshots(UserVmManagerImplTest.java:1523)
[MockitoHint] UserVmManagerImplTest.moveVmToUserTestValidateVmExistsAndIsNotRunningThrowsInvalidParameterValueException (see javadoc for MockitoHint):
[MockitoHint] 1. Unused... -> at com.cloud.vm.UserVmManagerImplTest.beforeTest(UserVmManagerImplTest.java:537)
[MockitoHint] UserVmManagerImplTest.updateVirtualMachineTestCleanUpFalseAndDetailsNotEmpty (see javadoc for MockitoHint):
[MockitoHint] 1. Unused... -> at com.cloud.vm.UserVmManagerImplTest.configureDoNothingForMethodsThatWeDoNotWantToTest(UserVmManagerImplTest.java:746)
[MockitoHint]  ...args ok? -> at com.cloud.vm.UserVmManagerImplTest.prepareAndExecuteMethodDealingWithDetails(UserVmManagerImplTest.java:714)
[MockitoHint] 2. Unused... -> at com.cloud.vm.UserVmManagerImplTest.prepareAndExecuteMethodDealingWithDetails(UserVmManagerImplTest.java:703)
[MockitoHint] UserVmManagerImplTest.updateVirtualMachineNicTestInvalidNicUserVmThrowInvalidParameterValueException (see javadoc for MockitoHint):
[MockitoHint] 1. Unused... -> at com.cloud.vm.UserVmManagerImplTest.beforeTest(UserVmManagerImplTest.java:537)
	at com.cloud.vm.UserVmManagerImplTest.lambda$testUnmanageUserVMGenericException$54(UserVmManagerImplTest.java:4345) ~[test-classes/:?]
	at com.cloud.vm.UserVmManagerImplTest.testUnmanageUserVMGenericException(UserVmManagerImplTest.java:4344) ~[test-classes/:?]
[ERROR] Tests run: 255, Failures: 2, Errors: 4, Skipped: 0, Time elapsed: 18.337 s <<< FAILURE! - in com.cloud.vm.UserVmManagerImplTest
	at com.cloud.vm.UserVmManagerImplTest.createVirtualMachineWithExistingSnapshot(UserVmManagerImplTest.java:4035)
	at com.cloud.vm.UserVmManagerImplTest.createVirtualMachineWithSnapshotFromVolumeWithNullDataStoreSucceeds(UserVmManagerImplTest.java:4095)
	at com.cloud.vm.UserVmManagerImplTest.createVirtualMachineWithVolumeFromNonZoneScopedStorageFails(UserVmManagerImplTest.java:4111)
	at com.cloud.vm.UserVmManagerImplTest.lambda$createVirtualMachineWithVolumeFromNonZoneScopedStorageFails$44(UserVmManagerImplTest.java:4112)
	at com.cloud.vm.UserVmManagerImplTest.createVirtualMachineWithVolumeFromNonZoneScopedStorageFails(UserVmManagerImplTest.java:4111)
	at com.cloud.vm.UserVmManagerImplTest.createVirtualMachineWithSnapshotFromExpungedLocalStorageVolumeSucceeds(UserVmManagerImplTest.java:4065)
	at com.cloud.vm.UserVmManagerImplTest.createVirtualMachineWithVolumeWithNullDataStoreFails(UserVmManagerImplTest.java:4127)
	at com.cloud.vm.UserVmManagerImplTest.lambda$createVirtualMachineWithVolumeWithNullDataStoreFails$45(UserVmManagerImplTest.java:4128)
	at com.cloud.vm.UserVmManagerImplTest.createVirtualMachineWithVolumeWithNullDataStoreFails(UserVmManagerImplTest.java:4127)
	at com.cloud.vm.UserVmManagerImplTest.createVirtualMachineWithExistingVolume(UserVmManagerImplTest.java:4005)
[ERROR]   UserVmManagerImplTest.createVirtualMachineWithVolumeFromNonZoneScopedStorageFails:4111 unexpected exception type thrown; expected:<com.cloud.exception.InvalidParameterValueException> but was:<java.lang.NullPointerException>
[ERROR]   UserVmManagerImplTest.createVirtualMachineWithVolumeWithNullDataStoreFails:4127 unexpected exception type thrown; expected:<com.cloud.exception.InvalidParameterValueException> but was:<java.lang.NullPointerException>
[ERROR]   UserVmManagerImplTest.createVirtualMachineWithExistingSnapshot:4035 » NullPointer
[ERROR]   UserVmManagerImplTest.createVirtualMachineWithExistingVolume:4005 » NullPointer
[ERROR]   UserVmManagerImplTest.createVirtualMachineWithSnapshotFromExpungedLocalStorageVolumeSucceeds:4065 » NullPointer
[ERROR]   UserVmManagerImplTest.createVirtualMachineWithSnapshotFromVolumeWithNullDataStoreSucceeds:4095 » NullPointer
[ERROR] Tests run: 255, Failures: 2, Errors: 4, Skipped: 0
[INFO] BUILD FAILURE
```
