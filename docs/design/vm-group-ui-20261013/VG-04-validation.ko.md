# VG-04 #1361 완료 검증

검증 기간: 2026-10-13~14 KST. Epic #1357의 네 번째 작업이다. 그룹 검색을 실제 `listInstanceGroups` 계약에 맞추고 동일 문맥 갱신과 문맥 전환을 구분했다. 사용자의 화면 표준 지적에 따라 정상 조회·갱신·빈 결과의 상단 리본과 성공 확인 시각을 제거했다. 정상 상태는 기존 목록·빈 행·업데이트 버튼의 로딩으로 표시하며 실패 시에만 간결한 안내와 재시도를 제공한다.

## 소스와 설계

- 최신 Upstream Europa `9fa65ce2a27b977e848440cb7c787411c11d585b`를 포함하는 dhslove Epic 브랜치 `codex/epic-1357-vm-group-ui`에서 작업했다. 원본 Windows FTCTL 변경은 보존했다.
- 최종 UI 빌드·배포 소스: `361b123637517e1bc83a6d79430d5bdd03806a4b`. [설계](VG-04-design.ko.md).
- `compute.js`의 가상머신 그룹 Zone 필터만 제거했다. 오토스케일 VM 그룹의 Zone 필터는 유지한다.
- `vmGroupQuery.js`는 이름 정확 일치, keyword 이름/UUID 부분 검색, account/domain/project ACL, ID 및 페이지 파라미터를 분리한다. UI용 q/tab/action/dataView/정렬 값은 서버 요청에서 제외하고 미지원 Zone·알 수 없는/잘못된 URL 값은 정리한다.
- 서버는 ID 순서 페이지를 제공하며 전역 정렬 API가 없다. 이름·계정·도메인·프로젝트 정렬은 현재 페이지 내부에서만 적용하고 URL과 수동 갱신에서 유지한다.
- `AutogenView` 그룹 분기는 목록과 이름 없는 상세 자식 경로 모두 `route.meta.name`을 기준으로 한다. 상세 `tab=comments`는 UI에만 남는다. 공통 다른 자원의 API 경로는 유지한다.
- 세션·소유·프로젝트·route·검색·페이지가 바뀌면 이전 결과·시각·선택을 제거하고 늦은 성공/실패/완료 처리를 거절한다. 같은 문맥 갱신은 화면 데이터를 유지하며 실패 시 마지막 성공 시각을 갱신하지 않는다. 실패·권한 없음·대상 없음은 정상 0개와 구분하고 이전 대상 작업을 차단한다.
- 응답 envelope/배열/UUID/중복/총수와 HTTP 200 오류 응답을 검증한다. 잘못된 부분 응답은 빈 성공으로 처리하지 않는다. timeout은 20초이며 기존 인증 만료 흐름은 유지한다.
- `InstanceGroupQueryStatus`는 실패/권한/없어진 대상에서만 일반 문단과 실제 Ant Design 재시도 버튼을 렌더링한다. 정상 success/info Alert, 정상 시각·정렬 안내 리본은 없다.

## 빌드와 집중 검사

WSL ext4 `/home/ablecloud/work/dhslove/cloud-epic-1357-vm-group-ui`에서 수행했다.

| 검사 | 결과 |
| --- | --- |
| 그룹 계약·조회 경쟁·미지원 값·오류·실제 Ant Design 재시도·정상 리본 부재 + 기존 그룹/공통 자원 집중 회귀 | 13 suites / **195 PASS** |
| 변경 JS/Vue 파일 7개 lint (`--no-fix`) | PASS |
| UI production build (`NODE_OPTIONS=--openssl-legacy-provider npm run build`) | PASS |
| 31/32 실제 API 계약·검색·소유·ACL·페이지 | 각 14개, 총 **28 PASS** |

이번 변경은 UI 모듈만 해당하며 Java/Maven·DB 스키마 변경은 없다. 이전 VG-03 JAR를 유지했고 Maven 모듈/전체 Cloud 빌드를 재실행하지 않았다. GitHub Actions를 수동 실행하지 않았다. 테스트는 focused 실행(`--runTestsByPath --coverage=false`)이며 전체 UI/서버/CI 통과 주장에 해당하지 않는다. 기존 Browserslist·번들 크기 및 테스트의 Vue stub/중복 등록 경고가 남는다. 이전 단계의 UserVmManager 전체 테스트 6건·OAuthActionSchema mock 3건 실패는 이번 단계에서 다시 검증하지 않았다.

## 31/32 실제 UI·API

[화면·요청·상태 근거](evidence/VG-04/ui-evidence.md).

| 경로 | 확인한 결과 |
| --- | --- |
| 31 라이트, 32 다크 정상 목록/빈 목록 | 조회 완료/성공 시각/갱신 리본 없음. 표준 목록과 빈 행 사용 |
| 31 고급 이름 검색 | 이름 정확 일치로 동일 이름의 서로 다른 두 소유 그룹 표시. 폼에 Zone 없음 |
| keyword/소유/URL 재진입 | 한글 이름·부분 이름/UUID, account/domain/project 범위 대조. 남은 Zone/알 수 없는 query 제거, q 입력 복원 |
| 31 페이지·정렬·수동 갱신 | 페이지 2·keyword·페이지 정렬 유지. API에 UI 정렬 없음 |
| 31 지연/HTTP 500 | 기존 행과 마지막 성공 시각 유지, 기존 업데이트 버튼 spinner. 선택 제거·작업 차단·내부 오류 상세 숨김 |
| 31 반복 실패/재시도 | 다시 실패해도 성공 시각 동일. 성공 재시도로 오류 문단 제거·정상 목록 복구 |
| 32 프로젝트→기본 보기 | 기존 Mold의 대시보드 전환 후 그룹 재진입. 프로젝트 1개→기본 28개, 이전 선택/프로젝트 행 제거, API projectid 대조 |
| 32 늦은 검색 응답 | 01 요청을 보류하고 02 성공 후 01 응답 완료. 화면은 02 두 소유 결과만 유지 |
| 32 HTTP 403/531 | 이전 행/선택·표시 시각 제거, 권한 오류와 재시도. 정상 0개로 오인하지 않음. 재시도 성공으로 같은 검색 복구 |
| 32 검색 정상 0·해제 | 표준 검색 빈 행, 리본 없음. clear 버튼으로 keyword 제거·28개 복구 |
| 최종 상세 자식 경로 | 31/32 코멘트 탭 갱신 유지, 실제 API에서 tab 제외. 31 상세 HTTP 500 기존 resource/코멘트 유지·작업 차단·재시도 복구. 32 삭제된 상세를 missing 상태로 표시, 이전 작업 0·대상 URL 유지·404 이동 없음 |

지연·HTTP 500/403은 테스트 브라우저의 해당 `listInstanceGroups` XHR만 일시 보류/응답 주입해 재현했다. 실제 서버 장애·권한 정책 변경을 발생시킨 시험은 아니다. 일반 사용자의 소유/타계정 거부는 실제 API로 별도 확인했다. 브라우저 Fetch 패턴과 캐시 비활성 설정을 종료 시 복구했다. malformed 부분 응답·세션 전환·늦은 실패/뷰 해제는 집중 단위 테스트로 검사했다. 모든 오류를 모든 브라우저에서 반복한 결과는 아니다.

중간 32 프로젝트 전환에서 `Uncaught (in promise)` 콘솔 이벤트 1건을 수집했지만 상세 원인은 보존된 기록만으로 확정할 수 없다. 기능 결과와 원인 미확정 관찰을 구분하며 콘솔 무오류/전체 UI 안정성으로 인증하지 않는다. 최종 상세 경로 보완 전의 `tab` API 전송은 발견 후 수정했고 최종 요청을 다시 확인했다. 중간 전환 프레임 PNG는 최종 근거에 포함하지 않았다.

## 배포 보존 검사

최종 UI archive SHA256: `5453b9453c44cf88af9baaae6d08c4bc1dbcedc60255a17da2588872c268d810`. 활성 `/usr/share/cloudstack-management/webapp`의 정적 파일만 갱신했다. 보호 경로 WEB-INF/META-INF/config.json을 교체하거나 webapp 루트를 삭제하지 않았다. 배포 전 식별된 기존 Epic 배포/백업·stage 데이터를 정리하고 최종 롤백 백업을 사이트별 하나 유지했다.

| 항목 | 31 | 32 |
| --- | --- | --- |
| 관리 서비스 PID | `706254` | `1202380` |
| index SHA256 | `64a373bb64bd46c458cb552c6b96f7bf19a75670f900c9974698de2989c95184` | `64a373bb64bd46c458cb552c6b96f7bf19a75670f900c9974698de2989c95184` |
| config SHA256 | `b7de5f6a1f7a3a17310ee7d34ca801bb316fc6f841039f865fbabf154cd59a13` | `e3839c761e59e77f960b9f9f717055ea91882682a3a036b07e4c69277aac8687` |
| 롤백 백업 | `/root/epic1357-vg04-deploy-20261014-001751` | `/root/epic1357-vg04-deploy-20261014-001943` |
| 서비스 / HTTP | active / 200 | active / 200 |
| 종료 가용 공간 | 약 328GiB | 약 32GiB |


양 서버의 최종 정적 **846개 파일 SHA256**가 산출물과 일치했다. WEB-INF 존재·META-INF 원래 부재·config/JAR SHA256·mold PID를 전후 보존했다. `mold active`, `/client/` HTTP 200 및 기존 FTCTL marker를 확인했다. UI 배포에 management 재시작·JAR/호스트/qemu 패키지 변경은 없었다.

## 전용 자원 정리와 후속 작업

사이트별 admin 빈 그룹 26개·일반 사용자 빈 그룹 2개·프로젝트 빈 그룹 1개를 만들어 검색/페이지/소유를 검증했다. 소유 VM·볼륨·네트워크와 그룹 매핑이 0임을 DB로 확인한 뒤 생성 기록의 UUID 58개, 전용 프로젝트 2개·계정 2개를 API로 정리했다. 최종 상세 보완 검증에 추가한 전용 빈 그룹 2개도 정리했다. 기존 VM 소속이나 전원은 변경하지 않았다.

| 사이트 | 정리 그룹 | 정리 계정 / 프로젝트 | 대상 활성 그룹·매핑·VM·볼륨·네트워크 / 계정·프로젝트 |
| --- | --- | --- | --- |
| 31 | 30 | 1 / 1 | 모두 0 |
| 32 | 30 | 1 / 1 | 모두 0 |


#1361을 완료 처리하고 누적 Draft PR #1364에 결과를 반영한다. Epic 완료 4/6, 다음은 #1362(VG-05 VM 표준 멤버 UI), 이후 #1363 최종 회귀이다. PR merge/Ready 전환 및 Epic 전체 완료는 하지 않는다.