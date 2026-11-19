# AG-03: Affinity 그룹 생성·유형·검색 설계 (#1416)

기준: Europa `d9f068bbbba048313b6d38539651d19444c4d7c7`, Epic #1413 / 누적 Draft PR #1420. AG-01/02 이후 같은 브랜치에서 UI만 변경한다.

## 확인한 계약

- `CreateAffinityGroupCmd`는 `BaseAsyncCreateCmd`이다. 31번 실제 응답은 `createaffinitygroupresponse.id/jobid`; 최종 `queryasyncjobresultresponse.jobstatus=1`에서 `jobresult.affinitygroup`을 얻는다. 접수는 완료가 아니다.
- 이름은 `Upgrade41910to42000.checkAndUpdateAffinityGroupNameCharSetToUtf8mb4`에서 인덱스 호환성을 위해 191자로 변환된다. 실제 DB `SHOW CREATE TABLE affinity_group`도 utf8mb4 `varchar(191)`이다. 과거 255자 스키마도 있지만 이번 UI는 Europa의 191자 안전 범위를 사용한다.
- 이름과 설명의 `@Parameter.length` 미지정 기본값은 255 Java UTF-16 단위이다. 설명 DB가 4096자여도 API 제한은 255이다. 이름은 Unicode 코드포인트 191 및 UTF-16 단위 255를 함께 검증한다. 앞뒤 공백은 전송 전에 제거한다. 중간 공백과 본문은 보존한다.
- 이름 중복은 계정별 DB unique/name 검사와 서버 오류를 기준으로 판단한다. 클라이언트 사전 조회를 유일성 보장으로 사용하지 않는다.
- `listAffinityGroupTypes`는 소유자 인자를 받지 않는다. 로그인 권한의 API에서 받은 원문 type만 전송한다. 31번은 Strict/Non-Strict × affinity/anti-affinity 4개를 반환한다. 새 유형은 원문으로 표시한다.
- `listAffinityGroups`는 name/type/id/virtualmachineid와 공통 account/domainid/projectid/keyword/listall/isrecursive/page/pagesize를 받는다. Zone은 지원하지 않는다.

## 생성과 복구

`MoldDialog(size=md)`와 세로 폼, 공통 버튼/테마를 사용한다. VM 메뉴의 대화상자 기반이며 목록/상세/탭 전체 표준화는 AG-05에 남긴다.

유형은 loading/ready/empty/error를 구분하고 실패·빈 결과에 재조회 버튼을 제공한다. 자동 유형 선택은 하지 않는다. 이름 공백/길이, 설명 길이, 실제 유형, 소유자 준비 여부를 검증한다. 동기 검증 잠금으로 Enter/Ctrl+Enter/연속 클릭의 중복 전송을 막고, 비동기 검증 이후에도 소유자와 보안 세션이 같은지 다시 검사한다.

관리자 소유자 선택은 Affinity 전용 `AffinityGroupOwner`로 격리한다. 기존 VM 생성 공통 선택기의 동작을 바꾸지 않는다. 초기에는 로그인 계정/현재 프로젝트를 후보로 설정하되 API에서 소유자 목록을 확인하기 전까지 준비되지 않은 상태로 둔다. 도메인/소유 유형 전환 시 즉시 이전 선택을 비우고 준비 상태를 내린다. request version 및 unmount 검증으로 늦은 응답을 버린다. 계정·프로젝트는 임의 첫 항목으로 대체하지 않는다. 옵션은 API 페이지를 끝까지 순회하고, 실패/빈 목록에서 생성이 차단된다.

AG-01 작업 레지스트리에 요청 params의 사본을 추가한다. `create-affinity-group` 키로 현재 보안 세션에서 요청과 작업 ID를 유지한다. 작업 실패/명시적 API 오류는 입력·유형·소유자와 오류를 유지하며 수정 재시도를 허용한다. 최종 성공만 알림/목록 갱신/닫기를 수행한다. 결과 불명은 자동 재전송하지 않는다. 작업 ID가 있으면 같은 ID를 GET으로 재조회한다. ID가 없으면 목록/Cloud 작업의 운영자 확인을 안내하고 해당 앱 세션의 재전송을 막는다. 닫기는 서버 작업 취소가 아니다.

이 레지스트리는 앱 메모리 범위이다. 브라우저 전체 새로고침을 넘는 영속 복구는 AG-01과 동일하게 제공하지 않는다. 불명 결과의 수동 재요청 전에는 소유자 목록/서버 작업을 확인해야 한다.

## 검색과 URL

기존 `SearchView`의 Affinity 경로만 보강한다. name/type/domain/account/project 조건을 제공하고 유형 원문 ID와 번역된 표시값을 분리한다. 소유자 필터 옵션의 지연 응답도 버린다. 도메인 변경은 account/project를 비우고, project 선택과 account 선택은 상호 배타적이다. 필터 조회 실패에는 재조회가 제공된다. URL에서 폼을 먼저 복원하므로 재로드/뒤로 가기에도 값이 유지된다.

기존 Zone URL은 조건을 제거한 URL로 replace한 뒤 요청한다. API 인자는 지원 키만 허용한다. 명시적인 project 필터는 전체 프로젝트 스위치에 의해 `-1`로 덮이지 않는다. Affinity의 projectid 검색 URL은 전역 프로젝트 보기를 변경하지 않는다. 검색 오류는 경로를 유지하고 공통 재시도 상태를 사용한다.

## 검증과 배포

- Form/Select/Alert/Button 실 컴포넌트 단위 검증: 중복 수정, 접수/최종 분리, 최종 실패, 결과 불명 재열기, 재전송 차단, 유형 로딩/실패/0/신규 유형, 소유자 race/실패/페이지 경계, 이름/설명/Unicode 경계, URL 정규화/검색 조건/race.
- AG-01/02 및 공통 검색·초점 회귀를 함께 실행하고 변경 파일 lint와 UI production build를 확인한다. Cloud 전체 빌드/호스트 배포는 범위에 없다.
- 31번에 전용 계정·프로젝트·빈 도메인·그룹을 만들고 UI에서 4개 유형, 중복 수정, 계정/프로젝트 생성, 조회 실패/0/지연 상태, 지원 검색·URL을 검증한다. API 최종 job과 DB owner/길이를 대조한다.
- 활성 webapp 백업 후 static 항목만 배포한다. WEB-INF/META-INF/config symlink·해시/JAR/PID를 보존하고 전체 배포 manifest·HTTP 200·mold active를 확인한다.
- 전용 자원을 정리하고 기존 4개 그룹 응답 및 119개 VM의 Affinity 연결 투영이 동일한지 확인한다. AG-04 멤버 관리와 AG-06 VM 배치 검증은 이번 완료 범위에 포함하지 않는다.

## 실제 배포 검증에서 추가한 검색 보호

ROOT 관리자의 listAccounts가 isrecursive=false에도 하위 도메인 계정을 반환하는 것을 확인했다. 계정/프로젝트 옵션을 선택 도메인과 명시적으로 대조하며, 계정 검색은 도메인 선택을 요구한다. 또한 고급 검색을 연 채 브라우저 뒤로 가기를 하면 URL은 바뀌어도 폼은 이전 프로젝트를 표시하는 현상을 재현했다. 열린 폼도 경로 변경 시 지원 필터를 복원하고 옵션 응답 세대를 갱신한다. 공통 검색의 다른 메뉴 동작은 바꾸지 않는다.
