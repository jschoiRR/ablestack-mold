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

# VG-03 완료 보고서

2026-10-13 / issue #1360 / Epic #1357 / 누적 Draft PR #1364.

## 결과와 소스

이름 입력의 UI·API·서버 규칙, 안전한 한글 오류 안내와 단건/일괄 삭제 영향을 구현했다. 변경 api/server 모듈·UI를 WSL ext4에서 빌드하고 31/32에 배포했다. 실제 브라우저 작업과 API/DB 전후 대조, 전용 자원 정리를 완료했다.

배포 소스는 `d725040444c21a2e6e4dcd2b40c9be15d5228df8`이다. 이 보고서 및 증거를 추가하는 후속 커밋은 배포 코드를 변경하지 않는다. 최신 Upstream Europa `9fa65ce2a27b977e848440cb7c787411c11d585b`와 VG-01·02 완료 코드를 포함한 dhslove Epic 브랜치를 사용했다. 기존 작업 트리의 FTCTL 변경은 보존했다.

[설계](VG-03-design.ko.md) · [UI 요청·DOM·DB 근거](evidence/VG-03/ui-evidence.md)

## 구현

- 생성·이름 변경·이름 기반 자동 생성이 같은 Unicode 경계 공백 제거와 1~255 UTF-16 단위 규칙을 사용한다. 내부 공백·한글·대소문자는 보존한다. 일부 이모지는 2단위로 계산한다.
- 그룹 이름 파라미터에만 원본 입력 상한 1024 UTF-16 단위를 명시했다. 다른 STRING 파라미터의 기본 상한과 공통 ASCII 제어문자 거부를 유지한다.
- 실제 31 런타임에서 API 기본 255 상한이 trim보다 먼저 적용됨을 확인해 명시적 입력 상한을 추가했다. 이어 GenericDaoBase의 Column.length 기본값에 따른 문자열 절단을 확인했다. 저장 한계를 초과하는 supplementary 이름을 저장 전에 거부하도록 UI/서버를 맞췄다. 255단위 문자열은 API 재조회에서 원문과 정확히 같고 256단위는 거부된다. DB 스키마·ORM 엔진 변경은 없다.
- owner 행/활성 그룹 행 잠금 및 트랜잭션으로 소유 범위의 DB collation 중복을 판정한다. 서로 다른 계정/프로젝트의 같은 이름은 허용한다. 기존 이름 일괄 정규화 없이 기존 패딩 이름의 정확한 선택·미변경 요청을 보존한다.
- VM 생성 그룹 이름은 VM·볼륨 예약 전에 검사한다. VG-01의 UUID 연결·명시적 해제와 VG-02의 소유 범위 계약을 유지한다.
- 공통 AutogenView의 그룹 작업에만 정규화·validator·안전한 오류 변환을 적용한다. 중복·빈 값·길이·없어진 대상·ACL·일반 실패를 한글/영어로 안내한다. 실패하면 폼 원본 입력과 대상을 유지한다. 미변경 편집은 POST 없이 안내한다.
- 삭제 요약은 개수·이름·UUID·계정 또는 프로젝트·도메인을 표시하고 실행 UUID를 불변 snapshot으로 유지한다. 그룹 연결·코멘트 제거, VM·볼륨·전원·배치 보존을 명시한다. 이름은 Vue 텍스트로 escape한다. VM 개수를 추가 조회하지 않아 N+1을 만들지 않는다. 상세 작업 메뉴와 행 우클릭 일괄 작업을 유지한다.

## 빌드·집중 테스트

WSL ext4 `/home/ablecloud/work/dhslove/cloud-epic-1357-vm-group-ui`에서 실행했다.

| 검사 | 결과 |
| --- | --- |
| 변경 api 모듈 clean install / UpdateVMGroupParametersTest | 9건 PASS |
| 변경 server 모듈 clean install / Names 23, InputContract 7, Assignment 17, UserVmManager 집중 2 | 49건 PASS |
| 변경 Maven 모듈 Checkstyle/RAT | PASS |
| UI GroupAssignment/Lookup/Ownership/Names 및 AutogenSnapshotActions/ActionFocus/BulkActionLifecycle | 7 suites / 108건 PASS |
| 변경 UI 파일 lint --no-fix / production build | PASS |
| 최종 배포 API 계약 | 31 37건 / 32 37건 PASS |

API InputContract 테스트는 실제 명령 필드의 Parameter annotation과 ParamProcessWorker STRING 처리를 대조한다. 단순 정규화 함수 테스트만으로 입력 상한을 판정하지 않았다.

## 배포와 공간

31 대표 환경 확인 후 32로 확대했다. 이전 VG-01·02 배포/백업과 31의 중간 VG-03 배포 데이터를 식별해 제거했다. 활성 서비스 데이터는 보존했고 최종 롤백 백업은 각 사이트에 1개 유지했다.

| 최종 확인 | 31 | 32 |
| --- | --- | --- |
| 변경 클래스 해시 / 정적 파일 해시 | 50 / 843 일치 | 50 / 843 일치 |
| 기존 JAR의 변경 외 entry byte 보존 | 204,442개 | 204,444개 |
| WEB-INF / META-INF | 존재 / 원래 없음 유지 | 존재 / 원래 없음 유지 |
| config.json | 전후 동일 | 전후 동일 |
| 서비스 / /client/ | mold active / HTTP 200 | mold active / HTTP 200 |
| 재시작 후 PID / 검증 종료 시 PID | 706254 동일 | 1202380 동일 |
| 종료 디스크 여유 | 약 328GiB | 약 32GiB |
| 최종 롤백 백업 | /root/epic1357-vg03-deploy-20261013-213931 | /root/epic1357-vg03-deploy-20261013-214442 |

정적 번들에서 새 오류·삭제 요약 및 기존 FTCTL `blockingLoadingState`, `fetchSyncProgress`, `extractJobId` marker를 확인했다. JAR 변경에는 의도된 management 재시작이 필요했다. 호스트 패키지는 변경하지 않았다.

- classes ZIP SHA256: `6ade5ce1ca9c4128f57692d99cb2ca530da8d54afd6e32668ed7859f33a3c550`
- UI TGZ SHA256: `d9fced8991719011af93e914c0c9be80a970f96aa6d5192c27eedf31cce6cced`
- 양 사이트 index.html SHA256: `bcf271f110d39f59fceec8cb7d4ce2316995e77e23de80f063277ef56bf6529f`
- 31 JAR SHA256: `a4a136db94729a8f14b8d4a781235d54b8260415c139222c3f34588b1c574393`
- 32 JAR SHA256: `fc9430a0c26fb6b7245d68a38dec9cee1bbe4b21a4283d7afaebc7722d6d2fd5`

## 실제 기능 검증과 정리

31 라이트·32 다크 UI에서 공백/256단위 입력 차단, trim 한글 생성, 이름 충돌·변경·미변경, 단건/일괄 삭제 확인·취소·완료를 확인했다. 31 HTTP 500 / Cloud errorcode 530 주입에서는 내부 상세를 숨기는 한글 안내, 입력·UUID 보존과 동일 대상 재시도 성공을 확인했다. 탭의 XHR 주입과 캐시 override는 종료 시 해제했다.

각 사이트 API 37건은 필수·ASCII/Unicode 공백·제어문자·trim/내부 공백·255/256 한글·1024/1025 원본 상한·supplementary 255/256단위·DB 대소문자/악센트 충돌·rename 실패 보존·legacy 패딩·미전달 유지·다른 소유의 같은 이름·타소유 ACL·VM 자동 이름 생성·invalid VM 생성 거부·없어진 대상 rename 거부를 포함한다. VM update의 잘못된 이름은 wrapper 530으로 반환되지만 이름 정책의 구체적 marker를 확인했다.

각 사이트에서 빈 그룹 단건 1개, 빈 그룹과 멤버 포함 그룹의 일괄 2개를 실제 UI로 삭제했다. 일괄 결과는 성공 2·실패 0이었다. 취소 후 VM·볼륨·매핑이 기준과 동일했고 삭제 요청은 요약에 표시한 UUID와 일치했다. 삭제 후 그룹 매핑·활성 코멘트는 0이었다. VM의 상태·소유·호스트·템플릿·오퍼링·OS·HA·동적 확장, 볼륨 UUID·상태·크기·pool·path는 전후 동일했다. UI에서도 두 VM이 Stopped로 남았다.

검증 VM 2대와 미할당 볼륨, 네트워크 2개, 프로젝트 2개, 계정 2개 및 이름 경계/API 탐색을 포함한 전용 그룹을 정확한 ID로 정리했다. 각 사이트 대상 VM·볼륨·그룹·매핑·활성 코멘트·네트워크·프로젝트·계정의 활성 DB 건수가 모두 0이다. 최종 UI 그룹 목록도 양쪽 0개다. 기존 VM은 검증 자원으로 사용하지 않았다.

## 제한과 다음 작업

전체 Cloud 빌드·Actions 수동 실행·전체 테스트·전체 lint·전체 CI는 실행하지 않았다. 현재 PR의 check rollup이 비어 있는 상태를 전체 CI 통과로 해석하지 않는다.

추가 OAuthActionSchema 3개 실패는 기존 AutogenView에서도 동일한 `$nextTick` mock 부족으로 재현했다. 이번 그룹 범위에서 수정하지 않았다. VG-01에 기록한 UserVmManager 전체 테스트의 기존 6개 VM 생성 실패와 기존 전체 RAT 실패도 별도 제한으로 남는다. Browserslist·번들 크기 경고가 남는다.

이 작업의 멤버 삭제 검증은 전용 Stopped VM이다. Running VM의 그룹 삭제·실제 QEMU 전원 불변, 모든 역할 정책·고부하, 접근성·좁은 폭·전체 회귀를 인증하지 않는다. 255단위의 실제 저장 및 없어진 대상/ACL 거부는 API·Java/UI 집중 검사로 확인했으며 모든 오류 유형을 양 사이트 실제 브라우저에서 각각 재현한 결과는 아니다. PNG 전환 프레임은 성공 증거로 사용하지 않고 DOM·요청·API·DB를 대조했다.

Epic은 3/6 완료이며 다음 작업은 #1361(VG-04 검색·갱신 정합성)이다. 누적 PR #1364는 Draft로 유지한다.