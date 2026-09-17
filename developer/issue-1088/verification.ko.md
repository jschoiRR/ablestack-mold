# #1088 다중 VM 삭제 대화상자 검증

## 변경 및 원인

- AutogenView는 다중 삭제 중 선택 항목이 0개가 되면 전용 DestroyVM 컴포넌트를 제거하고 일반 확인 모달 분기로 전환했다. 전용 컴포넌트 선택을 실행된 action으로 유지하도록 수정했다.
- DestroyVM은 열릴 때 선택 ID/표시 항목을 복사한다. 목록 갱신으로 선택이 바뀌어도 단일 폼으로 전환하거나 삭제 대상을 바꾸지 않는다.
- 한 번 제출한 다중 작업은 완료 후에도 재제출할 수 없다. 진행/결과 닫기는 부모 action을 먼저 닫고 선택을 정리한다.
- 비동기 실패 콜백도 작업을 settle한다. Promise.allSettled로 전체/부분 API 실패를 집계하고 단일 폼 검증 오류로 잘못 처리하지 않는다.
- 종료·unmount 뒤 완료된 작업은 이전 화면 갱신을 요청하지 않는다. 서버에서 이미 진행 중인 작업 자체를 취소했다는 뜻은 아니다.
- 결과 컬럼은 복사하고 기존 status 컬럼을 제외해 중복 추가와 원본 변형을 막는다.
- 최초 영구삭제 확인 및 실제 destroyVirtualMachine 파라미터/백엔드 동작은 유지한다.

## Docker 검증

Rocky Linux 9.8 linux/amd64 Docker 볼륨에서 소스 수정·테스트·빌드.
기준 upstream/ablestack-europa: 39f798757d5109e6717386680a75cb497b20a91b.

- UI 전체: 49 suites / 487 tests 통과.
- 신규 DestroyVM 회귀 테스트 5개: 선택 소멸 후 결과 유지, 부분 비동기 실패, API 접수 실패, 진행 중 닫기와 지연 응답, 새 일반 삭제 작업.
- 최초 실행 후 handleSubmit/destroyGroupVMs 재호출에서도 대상당 요청 한 번만 발생하는지 확인.
- API를 모킹하여 검증했다. 실제 사용자 VM 영구삭제는 수행하지 않았다.
- 최종 lint 및 UI 프로덕션 빌드 결과와 배포 화면 확인은 아래 배포 기록에 추가한다.

명령:
```sh
cd ui
npx vue-cli-service lint --no-fix src/views/AutogenView.vue src/views/compute/DestroyVM.vue tests/unit/views/compute/DestroyVM.spec.js
npx vue-cli-service test:unit --runInBand --coverage=false
```

기본 Jest 설정은 전체 소스 커버리지 계측을 수행하므로 기능 테스트에는 coverage=false를 명시했다. 커버리지 백분율을 완료 근거로 사용하지 않는다.

## 배포 방침

13번의 현재 #1083 및 #1086 UI 변경을 임시 Docker worktree에서 보존하고 이번 수정만 추가하여 UI 모듈을 빌드한다. PR diff는 #1088 수정만 포함한다. 관리 서버/에이전트 JAR나 VM 상태는 변경하지 않는다.
UI 설정과 WEB-INF 해시를 배포 전후 비교하고, 기존 asset을 보존한 채 index.html을 마지막에 교체한다.

## 최종 배포 및 브라우저 확인

- 최종 변경 파일 lint 통과. 전체 UI 49 suites / 487 tests 통과 후, 이벤트 검사 문법을 lint 호환 방식으로 정리하고 신규 5개 테스트를 다시 실행하여 통과했다.
- Docker UI 프로덕션 빌드 성공. 소스맵은 기존 개발 빌드 정책대로 제외.
- 13번 UI 배포 완료. 업로드 SHA256, config.json 및 WEB-INF 보존 검증 통과.
- 백업: `/var/tmp/issue1088-20260915-051607/ui-before.tar.gz`.
- 기존 #1083 및 #1086 UI overlay를 유지했다. 백엔드 재시작/교체 없음.

로그인된 13번 브라우저에서 실제 배포 컴포넌트를 대상으로 가상 UUID 2개, destroyVM/$pollJob 모킹을 적용했다. 실제 변경 요청은 XHR 방어 장치로 차단했으며 서버의 VM을 삭제하지 않았다.

| 시나리오 | 수정 전 | 수정 후 |
|---|---|---|
| 모킹 삭제 성공 후 선택을 빈 배열로 변경 | DestroyVM 제거, showAction=true, 일반 영구삭제 확인 폼 재노출 | DestroyVM 유지, 성공 결과 2개 유지, 확인 폼 없음 |
| 완료 후 handleSubmit 재호출 | 비교 대상 외 | 모킹 요청 총 2개 유지 |
| 결과창 닫기 | 비교 대상 외 | showAction=false, DestroyVM 제거, 재확인창 없음 |
| 새 삭제 작업 열기 | 비교 대상 외 | operationStarted=false, expunge=false |
| 다크 테마 결과창 | 기존 #1083 overlay | 성공 상태 및 텍스트 정상 표시 |

검증 후 탭을 새로고침하여 가상 대상·메서드 모킹·XHR 방어 장치를 제거했다. 위 결과는 배포 UI와 상태 전환의 검증이며 실제 영구삭제 성공 테스트를 수행했다는 의미가 아니다. 서버 삭제 API는 변경하지 않았다.
