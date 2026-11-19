# #1415 / AG-02 — 다중 페이지 선택·검색 상태 검증

상위 Epic #1413, 누적 Draft PR #1420. 브랜치 `dhslove/epic-1413-affinity-ui`.
기준 upstream Europa `d9f068bbbba048313b6d38539651d19444c4d7c7`, AG-01 후속 `614cf70dd9f402c8bfbc6d8d3182742bec8bf977`.

**설계·구현·31번 배포·해당 범위 기능 검증 및 시험 자원 정리를 완료했다.** 최종 UI 소스는 `b1dcee86c29916f0abd42888349492e958c7a0ec`다. #1415는 이 결과를 기준으로 완료 처리하며, Epic PR #1420은 후속 이슈를 누적하는 Draft로 유지한다.

## 구현

- 전체 선택 ID와 현재 후보 페이지를 분리하고 Ant Design Table의 `preserveSelectedRowKeys`를 사용한다. 기존 화면 밖 연결을 조회 결과로 교집합 처리하지 않는다.
- 전체 선택 개수·이름 요약, 개별 해제, 모든 페이지 선택 해제 및 표 머리글 체크박스의 현재 페이지 범위를 안내한다.
- current/pageSize를 제어하고 검색·페이지 크기 변경은 1페이지로 초기화한다. 검색은 페이지 크기를 유지한다. 기존 현재 페이지 로컬 정렬은 선택·조회 상태를 바꾸지 않는다.
- 최신 요청 토큰과 소유자·세션·프로젝트·Zone 가드로 늦은 응답/오류를 무시한다. 같은 범위의 조회 실패는 행·개수·선택과 재시도를 유지하며, 범위가 바뀌면 이전 후보·선택을 먼저 비운다.
- 생성·백업 복구·VNF·오토스케일 네 마법사의 공통 선택기와 부모 선택/메타데이터를 보완한다. 사전 채움은 한 번만 적용하고 조회 완료가 사용자 선택을 덮지 않는다.
- AG-01의 소유자/최신 VM 상태/선택별 사전 확인, 중복 POST 차단, 최종 job 성공 후 갱신·닫기를 유지한다. 백엔드 API·JAR·DB 스키마 변경은 없다.

[설계](../../design/affinity-1413/AG-02.ko.md)

## 수정 전 재현

31번의 당시 배포 UI(AG-01 소스 `6fe17d92db0`)와 전용 그룹 14개에서 다음을 재현했다. 재현은 저장 없이 취소했다.

| 절차 | 수정 전 결과 | 증거 |
| --- | --- | --- |
| 1페이지 01 선택 → 2페이지 11 추가 → 1페이지 복귀 | 01 체크 유실 | [화면](evidence/before-selection-loss.png), [AX](evidence/before-selection-loss.txt) |
| 2페이지에서 정확한 14 이름 검색 | 실제 그룹이 있는데 데이터 없음 | [화면](evidence/before-page2-search.png), [AX](evidence/before-page2-search.txt) |
| 생성 마법사에서 같은 01→11 선택/복귀 | 첫 페이지 체크 유실 | [화면](evidence/before-wizard-selection-loss.png), [AX](evidence/before-wizard-selection-loss.txt) |

## 집중 검증

WSL ext4에서 **7 suite / 137 tests 통과**, 변경 파일 lint 통과. 실제 Ant Design Table 체크박스·머리글·페이지·정렬·요약 버튼, 0건의 명시적 건수/페이지, 사전 채움·범위 전환, 네 부모의 전체 ID/최신 조회/오류 보존 통합, 기존 AG-01·jobTracker·API transport·생성 전검증 회귀를 포함한다.

텍스트 증거는 줄 끝 공백과 개행 형식만 정규화했다. 결과와 필드 내용은 유지했다.

[테스트](evidence/unit.txt), [lint](evidence/lint.txt), [최종 UI 빌드](evidence/build.txt), [Windows/WSL 소스 해시](evidence/source-manifest.json).

빌드의 기존 Browserslist·asset/entrypoint 크기 경고와 Jest의 기존 컴포넌트 등록/입력/브라우저 스타일 환경 경고가 있다. 전체 Cloud 빌드 또는 등록된 GitHub CI의 합격을 의미하지 않는다.

## 전용 자원과 API 계약

- 그룹: `qa-ag02-20261019-01`~`14`, admin/ROOT 소유 host anti-affinity.
- VM: `qa-ag02-20261019-vm`, UUID `d3e43a02-2204-433a-9c69-ecf774731389`, 내부 ID 650.
- startvm=false. 기존 admin L2를 NIC 연결에만 사용하고 네트워크 구성을 변경하지 않았다. VM은 시작하지 않고 host_id=NULL, ROOT는 pool/path=NULL 상태로 검증한다.
- 기존 14번 그룹 연결을 API로 미리 저장해 화면 밖 기존 연결 보존의 기준으로 삼았다. [API/DB](evidence/seed-existing.json), [DB 행](evidence/seed-existing.db.txt).
- API: 1페이지 10행/2페이지 4행에 count=14, 단일 검색 1행/count=1, 빈 검색 0행/count=0, 크기 20에서 14행을 확인했다. [실제 응답](evidence/api-pagination-contract.json).

## 배포와 실제 화면 검증

초기 기능 검증은 `dfb7d89de80` 배포에서 수행했다. `c3f5153daf1`에서 0건의 건수/페이지 및 선택 해제 접근성 이름을 보완했다. 실제 다크 테마에서 Typography 보조색이 검정 투명색으로 렌더링되는 것을 확인해, 최종 `b1dcee86c29`에서 본문 색을 상속하도록 고쳤다. [변경 전 계산색](evidence/theme-before.json)은 rgba(0,0,0,0.45), [최종 문구/부모 계산색](evidence/theme-after.json)은 모두 rgba(255,255,255,0.65)다. 중간 화면을 최종 합격 화면으로 집계하지 않았다.

최종 버전에서 페이지·정렬·단일/0건 검색·크기 변경, 응답 역전·오류/재시도, 선택 범위·개별/전체 해제·저장과 생성 마법사 선택/검토를 다시 확인했다. 요청 기록의 uiSource와 [작업/배포 범위](evidence/job-proof.json)로 배포를 구분한다.

- 최종 패키지 SHA256: `bb713eaee262b26fe8b1e8aec0f303d844bea132e6c6dcaab6ccc710cf44e425`.
- 최종 index SHA256: `e19813d488439badcad168ae3cd07e79f708d7e2493ffb726a9d1694a855612d`.
- 정적 파일 **888개**를 반영하고 배포 직후/시험 후 해시가 모두 일치했다. [전체 manifest](evidence/deployment-static-manifest.json).
- WEB-INF/web.xml, 설정 링크/내용, JAR 9개, Mold PID **1765167** 보존. META-INF는 원래 없었다. 서비스 재시작·JAR/호스트 패키지 배포·DB 직접 변경은 하지 않았다.
- Mold active, `/client/` HTTP 200 및 기존 FTCTL marker blockingLoadingState/fetchSyncProgress/extractJobId 유지. 백업 `/root/ag02-ui-deploy-20261019/webapp-backup` 보존.
- [배포 전](evidence/deployment-before.json), [최종 배포](evidence/deployment-after.json), [시험 후 상태](evidence/final-health.json).

| 실제 UI 경로 | 확인 내용 |
| --- | --- |
| 화면 밖 기존 연결 + 페이지 전환 | 기존 14 연결에 01·11 추가 저장. 최종 버전 재열기에서 기존 13개 연결 모두 표시. 새 01·11·14 선택도 1페이지 복귀 후 체크와 전체 3개 보존. [복귀 화면](evidence/qualified-page-selection-retained.png), [기존 연결](evidence/qualified-existing-thirteen.png) |
| 정렬·단일/0건 검색·페이지 크기 | 전체 3개 유지. 2페이지 단일/0건 검색은 모두 1페이지와 각각 1/0건 명시. 크기 20은 1페이지/14행. [단일](evidence/qualified-page2-search.png), [0건](evidence/qualified-empty-page1.png), [크기](evidence/qualified-page-size20.png) |
| 역순 응답 | 01 조회를 보류하고 최신 14 조회를 먼저 완료한 뒤 01 응답 반환, 최종 화면 14/선택 3개 유지. [화면](evidence/qualified-reversed-responses.png) |
| 조회 실패·재시도 | 후보 조회 XHR만 실패, 이전 14 행/개수와 선택 3개 유지·오류/재시도·확인 비활성. 재시도 후 새 02 결과와 전체 선택 유지. [실패](evidence/qualified-lookup-failure.png), [재시도](evidence/qualified-lookup-retry.png) |
| 현재 페이지/전체 선택 범위 | 1페이지 머리글 선택으로 3→12, 머리글 해제로 12→2(화면 밖 11·14 유지). 양쪽 페이지 머리글로 전체 14 선택. [현재 페이지 해제](evidence/qualified-header-page-only.png), [전체 14](evidence/qualified-select-all14.png) |
| 명시적 개별 해제 | 14개 중 01만 요약 버튼으로 해제, 나머지 02~14 총 13개 저장. [화면](evidence/qualified-explicit-remove13.png) |
| 비동기 완료 | 13개 저장의 query status=0 동안 처리 안내·job ID·확인 비활성 유지, 최종 status=1 후 닫기. [처리 중](evidence/qualified-save-thirteen.png), [최종 job](evidence/job-proof.json) |
| 명시적 전체 해제 | 모든 페이지 선택 해제로 13→0, 빈 CSV 제출 후 최종 job/API/DB 0. [선택 해제](evidence/qualified-clear-all.png) |
| 생성 마법사 | 01·11의 페이지 밖 선택·요약·검토 패널 링크와 단일 검색 유지. 최종 배포에서 재확인. [최종 화면](evidence/qualified-wizard-selection.png). 역순 응답/실패·재시도는 동일 helper의 초기 배포에서도 검증. [역순](evidence/wizard-reversed-responses.png), [오류](evidence/wizard-lookup-failure.png), [재시도](evidence/wizard-lookup-retry.png) |
| 소유자 전환 실패 | admin→기존 계정으로 폼 소유자만 전환 후 후보 XHR 실패, 이전 후보·선택 0. admin 복귀 시 14개 재조회. 생성 제출 없음. [실패](evidence/wizard-owner-change-failure.png), [복귀](evidence/wizard-owner-restored.png) |

최종 배포의 실제 UI 저장 세 번은 각각 한 번의 명시적 POST다. 모든 ID 집합이 API/DB와 일치했다.

| 선택 | 최종 job | 결과 | API/DB 증거 |
| --- | --- | --- | --- |
| 01·11·14, 3개 | `30ee3baf-cc97-45e6-9d8c-dbef5d631c9a` | status=1 | [집합](evidence/save-three.json), [DB](evidence/save-three.db.txt) |
| 02~14, 13개 | `e3ae97fb-9833-4472-bb26-04045cff9262` | status=0→1 | [집합](evidence/save-thirteen.json), [DB](evidence/save-thirteen.db.txt) |
| 전체 해제, 0개 | `33d7abde-5b43-42bc-b7d9-b695eb9af880` | status=1 | [집합](evidence/final-clear.json), [DB](evidence/final-clear.db.txt) |

[UI 요청과 최종 job](evidence/ui-network.json), [오류 주입 조건](evidence/fault-injection.json), [3개 저장 API/DB](evidence/save-three.json), [DB](evidence/save-three.db.txt).

Fetch 오류 주입은 listAffinityGroups XHR에만 적용했다. Document나 변경 POST를 가로채지 않고, 역순 응답도 실제 서버 응답을 지연/해제했다. 가로채기와 임시 캐시 비활성 설정을 해제했다. [API 이력](evidence/api-history.json)의 빌드 대기 중 별도 API 세션 만료 401은 재로그인 후 재조회했으며, 기능 실패/합격 사례로 집계하지 않았다.

생성 계열 네 종류의 실제 배포, 실제 호스트 분산/배치, 전체 역할·화면 크기·테마/접근성 매트릭스는 이번 결과에 포함하지 않는다. 생성 마법사에서 생성 버튼은 누르지 않았다.

## 정리와 다음 순서

전용 VM 1개·그룹 14개를 정리했다. API에서 시험 VM/그룹 각각 0, DB 활성 VM/ROOT 볼륨/NIC/매핑/그룹 행 각각 0이다. [정리 DB](evidence/cleanup.db.txt), [API/원상 비교](evidence/preservation.json), [정리 후 기본 보기 3개 목록](evidence/qualified-clean-original-list.png).

기존 그룹 4개(계정 3개와 프로젝트 1개)의 전체 응답과 기존 VM 119개의 ID·이름·정렬된 Affinity ID 집합이 전후 동일하다. 두 정규화 해시도 전후 일치한다. 원래 Windows FTCTL 작업 트리의 변경 파일 3개 내용 해시도 일치했다. [보존 해시](evidence/preserved-ftctl-hashes.json).

VM 650은 removed timestamp가 있는 Expunging, ROOT 726은 removed가 있는 Destroy 이력이 남는다. VM host_id와 ROOT pool/path는 계속 NULL이다. 활성 잔여 0은 삭제 이력의 물리 제거를 뜻하지 않는다. DB 시간은 UTC다. 별도 API 세션을 로그아웃하고 임시 세션 파일을 제거했다. 전용 계정/프로젝트를 추가 생성하지 않았다.

다음은 **#1416 / AG-03 / P2**다. API 기반 유형 목록, 중복/검증 오류 시 생성 폼 보존, 비동기 생성 완료 계약과 미지원 Zone 검색 정리를 수행한다. 같은 Epic 브랜치와 Draft PR #1420에 누적한다.

이후 #1417 멤버 관리 → #1418 가상머신 표준의 목록·상세·탭·탭 콘텐츠·대화상자 정돈 → #1419 최종 통합 및 실제 배치 검증 순서다. #1413과 후속 이슈는 열어 두고, #1415는 구현·배포·해당 범위 기능 검증 후 사용자 요청에 따라 완료 처리한다. PR 병합은 이번 요청 범위가 아니다.
