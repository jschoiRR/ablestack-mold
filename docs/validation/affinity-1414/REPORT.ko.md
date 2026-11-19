# AG-01 / #1414 구현 및 31번 검증

상위 Epic #1413. 작업 브랜치 `dhslove/epic-1413-affinity-ui`.
기준 Europa `d9f068bbbba048313b6d38539651d19444c4d7c7`에서 분리했다. 배포한 UI 소스 커밋은 `6fe17d92db015f530b3db66eb525a020111a26f8`이며, 마지막 문서/증거 커밋과 구분한다.

## 설계와 구현

[설계](../../design/affinity-1413/AG-01.ko.md)에 소유자 조회·제출 직전 검증·비동기 상태와 재시도 계약을 정리했다.

- accountid 대신 VM의 `account` 이름과 `domainid`, 프로젝트 VM은 `projectid`와 `domainid`를 사용한다. 소유 정보가 부족하면 조회와 제출을 차단한다.
- 서버가 반환한 적법한 도메인 공유 그룹을 유지한다. 제출 전 최신 VM의 상태/소유자 및 선택 ID별 소유 범위를 다시 확인한다.
- `updatevirtualmachineresponse.jobid`는 접수로만 취급한다. 최종 jobstatus=1 이후 성공 알림/갱신/닫기, jobstatus=2는 실제 오류와 선택을 유지한다.
- GET의 제한적 재시도, timeout/결과 불명 및 수동 재조회, 중복 POST 차단, 대화상자 닫기/재열기와 보안 범위 변경을 처리한다. receipt는 현재 앱 세션에만 보존한다.
- 기존 jobTracker/API transport는 변경하지 않고 새 Affinity 전용 조합을 사용한다. 생성/삭제·Kubernetes/AutoScale 기존 호출부도 변경하지 않았다. 이번 수정의 공통 영향은 기존 jobTracker/jobRequest 테스트로 확인했다.

## 수정 전 재현

전용 admin VM에서 다른 사용자 그룹이 포함된 5개 후보가 표시됐다. 사용자 그룹을 선택·저장한 직후 UI가 “Affinity 그룹을 변경했습니다.”라고 알렸으나 실제 job `e6776c50-efac-4a82-b35c-6aee82b8eb5e`는 jobstatus=2/errorcode=530으로 끝났다. API/DB의 VM 매핑은 빈 목록이었다.

[수정 전 후보](evidence/01-before-owner.png) · [성공 알림 AX](evidence/02-before-success.ax.txt) · [요청](evidence/02-before-requests.json).
이 재현에서도 성공 알림은 AX로 저장했다. 알림 스크린샷으로 대체했다고 주장하지 않는다.

## 집중 검증

4개 Jest suite의 고유 테스트 50개 통과(컴포넌트 15개 포함). 실제 Ant Design Alert/Button을 렌더링해 재조회·재시도 버튼의 표시와 클릭을 검증했다. 변경 파일 lint도 통과했다.

테스트 범위: 계정/프로젝트·공유 후보 보존, 불완전 소유자, 지원하지 않는 VM, 타 계정 선택 주입 차단, VM 상태·소유자 변경, 빈 목록 전체 해제, 실제 receipt 이름, jobstatus=0/1/2, GET 재시도/소진/timeout/수동 재조회, POST 결과 불명·명시적 API 거절, 중복 제출, 닫기/재열기, 늦은 응답·로그인 범위 변경, 기존 tracker/transport 회귀.

## 전용 자원

| 종류 | 전용 이름/식별자 |
| --- | --- |
| 일반 사용자 | qa-ag01-1019 |
| 프로젝트 | qa-ag01-20261019-project |
| VM | qa-ag01-20261019-admin / user / project, 내부 ID 647/648/649 |
| 계정/프로젝트 그룹 | qa-ag01-20261019-admin / user / project |
| 공유 그룹 | qa-ag01-20261019-shared, ExplicitDedication, 연결된 전용화 자원 0 |
| 전용 네트워크 | user/project L2, VLAN 3148/3149 |

모든 VM은 startvm=false로 만들었으며 시작하지 않았다. ROOT 723/724/725는 Allocated, pool/path=NULL, VM host_id=NULL이다. 기존 admin L2 네트워크는 전용 admin VM의 NIC에 사용했으며 네트워크 구성을 변경하지 않았다. 공유 검증은 기존 관리자 API로 빈 ExplicitDedication 그룹만 생성했고 호스트/클러스터/Zone을 전용화하지 않았다. 일반 생성 UI의 네 가지 타입을 확장한 것은 아니다.

## 배포 및 실제 UI 검증

31번 관리 서버에 정적 파일 888개를 반영했다. 전체 webapp 백업은 `/root/ag01-ui-deploy-20261019/webapp-backup`이다. WEB-INF/web.xml, 설정 링크와 설정 내용, JAR 9개 해시 및 Mold PID 1765167을 보존했다. META-INF는 원래 존재하지 않았다. 서비스 재시작·JAR/호스트 패키지 배포·DB 직접 변경을 수행하지 않았다.

- 최종 UI 소스: `6fe17d92db015f530b3db66eb525a020111a26f8`. Windows와 WSL ext4 빌드 트리의 UI/테스트 파일 6개 해시 일치.
- 최종 패키지 SHA256: `beb294424cdbb59d88e0ccc98f32d4e06843d3fcec5fd9d7878f09265689b850`.
- 최종 index SHA256: `a3793128b1b5d16ba1f6bdd5878f7aae7dc4ac382ecc4c00c6c8c8f270423261`.
- 배포 직후와 시험 후 정적 파일 888개 해시 모두 일치. Mold active, WEB-INF 존재, /client/ HTTP 200.
- 기존 FTCTL bundle marker blockingLoadingState/fetchSyncProgress/extractJobId도 유지했다.
- [빌드 로그](evidence/build.log), [50개 테스트 로그](evidence/unit.log), [lint](evidence/lint.log), [소스 해시](evidence/source-manifest.json), [배포](evidence/deployment-final.txt), [시험 후 상태](evidence/final-health.txt).

빌드에는 기존 Browserslist와 asset/entrypoint 크기 경고가 있다. Jest 로그에는 기존 전역 컴포넌트/입력 stub 경고가 있으나 4개 suite/50개 테스트는 모두 통과했다. 전체 Cloud 빌드/CI 결과가 아니다.

| 실제 UI 시험 | 결과와 근거 |
| --- | --- |
| admin 후보 | admin 소유 그룹 + 사용 가능한 도메인 공유 그룹 2개. 타 계정/프로젝트 그룹 미표시. [화면](evidence/03-after-admin-owner.png) |
| admin 연결/전체 해제 | 두 그룹의 최종 jobstatus=1과 API/DB 매핑 2→0 일치. [연결 DB](evidence/admin-attach.db.txt), [해제 DB](evidence/admin-clear.db.txt) |
| 프로젝트 후보/연결/해제 | projectid/domainid로 프로젝트 + 공유 2개 표시, 두 그룹 연결/전체 해제 jobstatus=1과 API/DB 일치. [후보](evidence/08-project-owner.png), [DB](evidence/project-attach.db.txt) |
| 일반 사용자 후보/연결/해제 | 실제 qa-ag01-1019 로그인, 자신의 그룹 + 공유 2개. 실제 jobstatus=0 동안 확인 버튼 비활성/성공 알림 없음, 최종 1 후 닫기. 연결 2→해제 0. [진행 화면](evidence/11-user-pending.png), [DB](evidence/user-attach.db.txt) |
| 권한 오류 | 정상 후보를 선택한 뒤 개발용 요청 가로채기로 전용 타 계정 ID를 POST에 주입. 실제 jobstatus=2/530, 오류 사유/선택/창 유지·성공 알림 없음·DB 매핑 0. 가로채기를 해제하고 같은 선택으로 명시적 재시도 성공. [오류 화면](evidence/06-final-job-failure.png), [DB](evidence/permission-failure.db.txt) |
| 조회 통신 오류/닫기/재열기 | 최종 빌드에서 query GET 4회를 실패시켰다. 창을 닫고 다시 열어도 같은 jobid/선택 유지, 확인 비활성, 결과 불명 안내와 수동 재조회 버튼 표시. [화면](evidence/15-query-unknown.png) |
| 수동 재조회 | job `5dcc2a4f-8d23-411d-bfe9-9254678623a5`만 GET 재조회하여 status=1 확인 후 알림/닫기. 원 POST 1회, 재조회 중 POST 0회, API/DB 두 그룹 연결 일치. [증거](evidence/recheck-proof.json), [DB](evidence/final-manual-recheck.db.txt) |
| 최종 전체 해제 | job `277b375f-a79a-4781-8f13-b169ec4c8c7d` status=1, API/DB 매핑 0. [DB](evidence/final-clear.db.txt) |
| 후보 조회 오류/재시도 | 2행과 1개 선택이 있는 상태에서 검색 GET만 실패시켰다. 행/선택 유지, 오류·재시도 버튼 표시, 확인 비활성. 버튼 재시도로 복구 후 선택 유지, 저장 없이 취소. [실패](evidence/18-lookup-failure-retained.png), [복구](evidence/19-lookup-retry-restored.png) |
| 일반 사용자 타 계정 ACL | 일반 사용자 API로 admin 그룹 조회는 531 거절. 자신의 그룹은 조회 가능. [응답](evidence/user-acl.json) |

[UI 요청·최종 job 기록](evidence/ui-network.json), [API 교차 확인/fixture 이력](evidence/api-history.json), [오류 주입 조건](evidence/fault-injection.json).

초기 정상 경로와 권한 실패의 화면 03~12는 소스 0918c1453d2의 배포에서 확인했다. 실환경 조회 오류 시험 중 Alert의 action 슬롯에 둔 버튼이 실제로 렌더링되지 않는 문제를 발견해 description 영역으로 옮겼다. 이 변경과 실제 Alert/Button 테스트를 포함한 최종 6fe17d92db0를 다시 빌드/배포하고, 화면 13~20의 조회 실패·닫기/재열기·재조회·정상 연결/전체 해제·후보 재시도를 재검증했다. 요청 증거마다 uiSource를 기록했다. 초기 배포의 버튼 결함을 합격으로 집계하지 않았다.

정상 화면에서는 더 이상 타 계정 그룹을 선택할 수 없다. 권한 실패 확인은 실제 backend job을 생성하는 요청 오류 주입이며, 일반 사용자 정상 UI가 권한 없는 후보를 제안한다는 의미가 아니다. 모든 가로채기는 해제했다. timeout·POST 응답 유실/receipt 누락·보안 범위 변경은 집중 테스트로 검증했으며, 실서버에서 120초 지연 작업이나 POST 유실을 추가로 만들지는 않았다. 현재 캡처는 1280×720 다크 화면이며 전체 테마/모바일/접근성 매트릭스 합격을 의미하지 않는다.

## 정리와 보존

전용 VM 3개, 그룹 4개(공유 포함), 계정 1개, 프로젝트 1개와 내부 프로젝트 계정, 네트워크 2개를 제거했다. API와 DB에서 활성 VM/ROOT 볼륨/NIC/계정/프로젝트/네트워크, 그룹 행 및 VM 연결 각각 0이다. [DB 정리](evidence/cleanup.db.txt).

기존 그룹 4개(계정 3 + 프로젝트 1)의 전체 응답 동일, 기존 VM 119개의 ID·이름·Affinity 목록 동일. [비교 결과](evidence/preservation.json). [정리 후 UI](evidence/20-final-original-list.png)는 기본 보기의 계정 그룹 3개이며 프로젝트 그룹은 별도 범위다.

VM 647/648/649는 removed timestamp가 있는 Expunging, ROOT 723/724/725는 removed가 있는 Destroy 이력이 남는다. VM host_id와 ROOT pool/path는 계속 NULL이다. 삭제 이력까지 물리 제거했다는 의미가 아니다. DB 시간은 UTC다. 원래 Windows FTCTL 작업 트리의 변경 파일 3개 내용 해시도 전후 동일하다. 임시 로그인 파일과 API 세션 파일은 증거에서 제외하고 정리했다.

## 남은 범위와 다음 이슈

다음은 **#1415 / AG-02 / P1**이다. 페이지를 넘겨 선택한 그룹과 현재 VM의 화면 밖 연결을 보존하고, 검색/페이지 크기 변경 시 1페이지로 초기화한다. 10개 초과 후보·정렬·빠른 검색 경합·빈 결과/조회 오류·실제 저장 결과를 검증한다. 같은 Epic 브랜치와 PR에 누적한다.

이후 #1416 생성/API 정합성 → #1417 멤버 관리 → #1418 가상머신 표준의 목록·상세·탭·탭 콘텐츠·대화상자 정돈 → #1419 31번 최종 통합 검증 순서다. 다중 페이지 선택/검색 초기화 결함, 생성 실패 시 폼 종료 등은 이번에 해결했다고 집계하지 않는다. 실제 호스트 배치, 전체 VM 생성 마법사, 모든 역할/테마/화면 크기의 매트릭스는 #1419 범위다.

[Epic PR #1420](https://github.com/ablecloud-team/ablestack-cloud/pull/1420)은 후속 이슈를 누적하는 Draft이며 병합하지 않았다. 사용자 요청에 따라 #1414는 구현·배포·해당 범위 검증 후 완료 처리한다. Epic #1413과 #1415~#1419는 열린 상태를 유지한다. 전체 Cloud 빌드/CI는 요청되지 않았고 실행하지 않는다.
