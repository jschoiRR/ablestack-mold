# VG-05 #1362 설계

2026-10-14. Epic #1357의 다섯 번째 작업. 최신 Europa 9fa65ce2a27 포함, 선행 VG-01~04 누적 브랜치에서 수행한다.

## UI 탭 배치

사용자 요구는 새로운 탭의 메뉴와 컨텐츠 배치다. 가상머신 InstanceTab의 a-spin / a-tabs 구조, mixinDevice.resourceTabPosition, animated=false, DetailsTab·AnnotationsTab을 재사용한다. 그룹의 기존 ResourceLayout/InfoCard를 유지하며 오른쪽 카드에 하나의 InstanceGroupTab을 넣는다. 상세 / 가상머신 / 코멘트 탭은 데스크톱·태블릿에서 왼쪽 메뉴/오른쪽 컨텐츠, 모바일에서 위쪽 메뉴/아래 컨텐츠다. 새 카드·별도 상단 대시보드·정상 조회 리본은 추가하지 않는다. VM 상세와 탭 컨테이너 여백/좌표를 실제 화면에서 대조한다.

## 조회 계약

listInstanceGroups의 ACL을 통과해 실제 반환되는 그룹 ID만 대상으로 멤버 수/상태를 배치 집계한다. virtualmachinecount와 virtualmachinestates를 추가한다. 동일 소유 user VM만 포함하고 removed·Destroyed·Expunging을 제외한다. root admin 외 숨김 VM은 제외한다. 멤버 탭도 listVirtualMachines state=present 및 대상 account/domain/project·groupid를 사용해 같은 범위와 대조한다. VM 조회 API 권한이 없으면 집계 필드를 제공하지 않으며 UI는 0으로 꾸미지 않고 권한 제한을 안내한다. 기존 AccountManager.checkApiAccessForUser를 사용해 사용자/프로젝트 역할과 호출 API 키 제한을 검사하며 별도 권한 검사기 Bean을 주입하지 않는다. 그룹당 VM API/SQL 조회를 추가하지 않는다. DB 스키마 변경은 없다.

## 멤버 관리

공통 ListView·Status·페이지·폼·ResourceContextMenu를 재사용한다. 멤버 행은 VM 표시 이름·상태·계정/도메인/프로젝트·현재 그룹과 VM 상세 링크를 표시한다. 연결은 같은 소유 범위의 VM만 후보로 조회하며 이미 현재 그룹에 속한 행은 선택할 수 없다. 다른 그룹 VM은 원래 그룹→대상 그룹 이동임을 확인 화면에 표시한다. 이동은 같은 소유 범위의 기존 UUID 그룹만, 해제는 cleargroup=true를 사용한다. 그룹 변경 요청은 id+groupid 또는 id+cleargroup만 보내며 전원/볼륨/VM 삭제 API를 호출하지 않는다.

단건·일괄은 공통 작업 메뉴에서 동일 확인 폼을 사용한다. 확인한 VM UUID·현재 소속·소유와 대상 UUID를 snapshot으로 고정한다. 실행 직전 VM·원래 그룹·대상 그룹을 다시 확인해 삭제/이동/소유 변경 시 그 VM의 요청을 거절한다. 부분 실패는 성공/실패를 각각 표시하고 성공 항목을 다시 실행하지 않는다. API 실패 내부 상세는 기존 안전한 한글/영어 처리로 숨긴다. 소유/세션/대상 변화 또는 오래된 조회 결과에서는 작업을 차단한다.

## 갱신과 검증

탭·멤버 검색/페이지/정렬을 URL UI query로 유지하고 서버에는 지원 값만 전달한다. 같은 문맥 갱신은 기존 행을 유지하고 실패 안내+재시도를 제공한다. 권한 거부/문맥 변경은 행·선택·초안 제거, generation/scope로 늦은 응답 거절. 필터 결과 개수와 전체 그룹 멤버 수는 구분한다. 멤버 작업 후 부모 집계와 멤버를 새로 조회한다.

변경 api/server Maven 모듈만 WSL ext4에서 테스트·빌드하고 UI 집중 회귀/lint/production build를 수행한다. 전용 빈 그룹·Stopped VM을 사용해31/32 관리자·일반 사용자·프로젝트의 연결→이동→해제·일괄·취소·ACL/실패/경쟁·역링크·VM/볼륨 보존을 대조한다. 라이트/다크·좁은 폭·키보드·포커스 및 VM 탭 배치 좌표를 기록한다. 활성 webapp 보호 경로·JAR 변경 외 entry·config·HTTP·서비스·해시를 검증하며 전체 Cloud/전체 CI 및 VG-06 완료로 표시하지 않는다.

모바일 멤버·후보 표는 최소760px와 내부 가로 스크롤을 사용한다. 후보는20개 고정 서버 페이지이며 지원하지 않는 페이지 크기 변경기를 표시하지 않는다.

## UI 배치 후속 기준 (2026-10-14)

멤버 탭의 버튼은 상단 왼쪽, 검색은 오른쪽이며 요약은 툴바 아래에 표시합니다. 기존 Ant Design Vue 페이지네이션을 오른쪽에 정렬합니다. 멤버 대화상자는 VM 일정 대화상자와 같은 centered·높이 제한·본문 스크롤 방식을 사용하고, 그룹 이름과 계정명을 각각 한 행에 표시합니다. UUID·도메인은 상단 요약에 표시하지 않습니다. 상세 결과는 [후속 보고서](VG-05-layout-followup.ko.md)를 참조합니다.
