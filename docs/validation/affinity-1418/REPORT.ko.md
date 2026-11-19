# AG-05 VM 표준 Affinity UI 정돈 검증

대상 #1418 · Epic #1413 · 누적 Draft PR #1420 · 31번 클러스터 · 2026-10-19.

## 결과

VM 공통 목록·상세·탭·탭 콘텐츠·MoldDialog를 기준으로 Affinity UI를 정돈했다. AG-D07 필터 번역키, 실제 검증 중 발견한 정책명 중복 표시, 짧은 화면의 고정 도구 모음이 탭 포인터를 가리는 결함을 해결했다. 최종 소스 `b5b941ec0fa5c6bd11c054422e4160bc1ec7ef03`를 31번에 정적 UI로 배포했다.

**23 suites / 315 tests**, 변경 lint·UI production build 통과. 최종 소스의 화면 상태 확인 **54건**, 중복을 제거한 레이아웃 측정 **44건**을 확보했다. 핵심 화면 행렬은 7개 상태 × 2개 테마 × 3개 크기의 **42건**이다. UI가 발급받은 성공 작업 **18개**를 DB의 `job_status=1`과 대조했다. 전용 자원 활성 잔여 10개 카운터는 모두 0이며, 기존 그룹 4개·VM 119개의 Affinity 투영과 원래 FTCTL 수정 파일을 보존했다.

기능의 실제 멤버 변경·영수증·외부 변경 삭제 흐름은 앞선 `b67611f...`에서 검증했다. 마지막 커밋은 Affinity 전용 열/상태 CSS와 설계만 바꿨으며 JS/Vue는 동일하다. 최종 소스에서 42개 반응형 상태, 포인터, 실제 생성과 3개 연결/빈 그룹 삭제를 다시 확인했다.

## 소스와 비교 기준

Europa `d9f068bbbba048313b6d38539651d19444c4d7c7`, AG-04 완료 `1001c6d5d403bf56c425b78549e460fc41eea718`에서 같은 `dhslove/epic-1413-affinity-ui` 브랜치를 이어 사용했다. 검증 중 upstream Europa는 `8c5a92a4d9978229221613f4ff3e36e2df54daeb`로 전진했다. 추가 #1379는 VM 스냅샷 복구 백엔드 7개 파일이며 UI 차이는 0이다. 이 UI 브랜치는 기존 Epic 기준을 유지하고 #1419에서 최신 기준 동기화와 통합 회귀를 진행한다. 현재 PR은 충돌 없이 병합 가능한 Draft다. 원래 FTCTL 작업 트리를 수정하거나 초기화하지 않았다. [설계](../../design/affinity-1413/AG-05.ko.md).

| 영역 | VM 기준과 Affinity 적용 | 기본 크기 비교 증거 및 대화상자 보조 자료 |
| --- | --- | --- |
| 목록 | Autogen/ListView·도구·검색·열 선택·페이지·우클릭 유지. 유형 한글 표시, 긴 값/빈 설명, 정렬 열 최소 너비·헤더/상태 한 줄 표시·표 내부 가로 스크롤 | [VM](evidence/native-vm-list.jpg) / [Affinity](evidence/native-affinity-list.jpg) |
| 상세 | ResourceLayout/InfoCard/DetailsTab·UUID 복사·소유자 링크 유지. 정책 설명·전체 연결 수·소유 범위 일치 | [VM](evidence/native-vm-detail.jpg) / [Affinity](evidence/native-affinity-detail.jpg) |
| 탭 | 공통 배치/라우팅, 상세→가상머신→이벤트 순서, Back 활성 탭 복원 | [VM](evidence/native-vm-tab-content.jpg) / [Affinity](evidence/native-affinity-members.jpg) |
| 콘텐츠 | 제목/설명→텍스트 주 작업→우측 검색/갱신→표→페이지. 상태 색+텍스트·선택 요약·부분 오류/재시도·구분된 빈 상태 | [VM 볼륨](evidence/native-vm-tab-content.jpg) / [Affinity 멤버](evidence/native-affinity-members.jpg) |
| 대화상자 | MoldDialog 프리셋·테마·간격·대상/소유자 요약·본문 스크롤·취소/확인 유지. VM Affinity 변경도 같은 셸로 통합 | [VM ISO](evidence/vm-reference-full-dialog-dark.jpg) / [Affinity 연결](evidence/qualified-add-dark-1440x900.jpg) / [짧은 삭제창](evidence/qualified-delete-light-390x480.jpg) |

API에 보내는 유형 원문은 유지한다. 엄격/선호 4개 유형의 한글 이름과 도움말을 통일하고, 알 수 없는 유형은 원문으로 표시한다. 도메인 공유 전용 자원 유형은 별도로 설명한다. 결과 카드는 상태 문구와 job ID 복사를 제공하며 다른 VM 작업창의 계약은 변경하지 않는다.

AG-D07은 그룹→VM 목록의 필터 제목을 한글로 표시하고 조회 권한 범위 내 그룹 이름을 사용한다. UUID는 툴팁에 보존하며 이름 조회 실패/권한 없음에서는 UUID로 대체한다. 범위 변경 시 이전 이름을 즉시 지우고 늦은 응답을 무시한다. 프로젝트 선택과 실제 API `projectid`를 유지하는 관련 VM 조회 및 Back 탭 복원을 확인했다.

## 실제 UI 기능과 오류 회귀

기준선은 그룹 4개·VM 119개다. 전용 그룹 25개, Stopped VM 5개, 일반 사용자 계정·프로젝트 각 1개, L2 네트워크 2개를 준비했다. UI에서 관리자·일반 사용자·프로젝트 그룹과 최종 회귀용 그룹 등 4개를 추가 생성했다. 최초 정리 후 Chrome 화면 증거 보완에 전용 그룹 2개·Stopped VM 3개를 별도 준비하고 다시 정리했다. 긴 그룹 이름 191자·설명 255자와 긴 VM 표시 이름을 사용했다. 시험 VM은 시작하지 않았다. API Stopped 상태와 3개 호스트의 전용 VM 실행 도메인 부재도 대조했다.

| 흐름 | 실제 확인 |
| --- | --- |
| 생성 | 4개 정책 선택/설명, 관리자·일반 사용자·프로젝트의 소유자 이름/ROOT 요약, 필수값 오류 초점, 중복 이름 API 431 후 입력 유지, 191자 이름·255자 설명으로 수정 후 최종 job 성공 |
| 목록/상세 | 기본 20행의 다중 페이지·검색·긴 값·0건, 같은 정책 문구와 전체 연결 수, 빈 설명·미반환 값 안내, 원래 UUID/소유자 링크 |
| VM 변경 | 다중 페이지의 기존 선택 2→3 유지·키보드 확인·실제 변경, 단일 MoldDialog, 작은 화면의 본문 스크롤과 하단 버튼 |
| 멤버 | 2행 페이지·검색 0건에서도 전체 수/페이지 밖 선택 보존, 조회 실패 후 데이터/선택 유지·재시도, Stopped VM만 연결/해제, 다른 그룹 연결 보존 |
| 상태/결과 | 실제 Running VM의 “실행 중”과 변경 불가 문구·선택/다음 비활성 확인, 해당 기존 VM에는 변경 요청 없음. GET 4회 실패 시 결과 불명·job 보존·재열기·동일 job 재조회, 변경 POST 재전송 없음 |
| 삭제 | 관리자·일반 사용자·프로젝트의 소유자/전체 영향 수와 VM 보존 안내, 빈 그룹 삭제, 외부 연결 변경 후 새 영향 재확인, 확인된 대상 그룹만 삭제하고 VM·다른 그룹 보존 |
| 탐색/접근 | 관련 VM 필터 한글/그룹 이름·UUID 대체, 프로젝트 API 범위·Back 탭, 폼 오류 초점·Tab 경계·키보드 표 가로 이동·취소/확인, 포인터 중앙 hit test |

`ui-checks.json`에는 소스·테마·화면 크기·기대한 문구를 기록했다. `vm-reference-*`는 공통 VM 기준이며, `before-*`와 재현 상태 및 초기 `e8f16c8...`/중간 `9d698b6...` 배포 검증은 최종 소스 PASS에 합산하지 않는다. 변경 전/초기 배포에서 확인한 기능 오류 경로도 해당 소스와 함께 보존했다.

라이트/다크, 1440×900·390×844·390×480에서 목록·생성·긴 상세·일반 상세·멤버·연결·삭제 42개 상태를 확인했다. 가로 스크롤은 표 내부에 두고 대화상자 본문만 세로 스크롤하며 하단 버튼을 유지한다. 고정 도구 모음이 활성화되는 화면 전환에서는 탭 중앙의 실제 hit target과 클릭 후 활성 상태를 함께 확인했다. 이는 완전한 WCAG 감사가 아닌 이번 변경의 조작·초점·배치 확인이다.

## 발견한 추가 결함과 수정 증거

- 정책 한글 표시를 추가한 뒤 공통 type 템플릿도 렌더링돼 원문이 중복됐다. Affinity 경로에서 공통 원문 템플릿을 제외했고, 알려진/알 수 없는 유형 단일 표시와 VM 스냅샷 기존 표시를 3개 회귀 테스트 및 실제 목록으로 확인했다. [재현 텍스트](evidence/reproduced-list-policy-duplicate.txt) · [중복 해결](evidence/policy-single-render.json).
- 390×480에서 크기 변경 뒤 affix 래퍼의 투명한 여백이 탭 중앙을 가렸다. 키보드 전환은 됐으나 포인터 hit target은 래퍼였다. Affinity 래퍼는 입력을 통과시키고 실제 카드만 입력을 받게 수정했다. 모바일의 긴 breadcrumb 이름도 말줄임해 고정 헤더 높이를 제한하고 전체 이름은 요약/상세에 유지했다. [재현](evidence/reproduced-short-affix-tab-hitbox.json) · [최종 포인터 검증](evidence/pointer-checks.json).
- 내장 브라우저는 JPEG 크기 불일치와 CDP PNG의 오래되거나 반복된 렌더 표면 문제가 있었다. 해당 캡처는 시각 합격 근거로 사용하지 않고 DOM/요청/응답/DB 기능 증거만 유지했다. 42개 반응형 상태는 Chrome에서 DOM·조작·기하를 다시 검증했다. 에뮬레이션 클립에도 일부 글자 잔상이 있어 픽셀 전체 합격을 주장하지 않는다. 시각 비교에는 별도로 확보한 기본 1920×927 화면의 native-* 이미지를 우선 사용하며, 클립 이미지는 보조 자료다. `capture-manifest.json`과 각 상태의 browser/imageFile 필드로 구분한다.

- Chrome에서 긴 설명이 있을 때 정렬 열의 최소 너비가 없어 연결 수/계정/도메인 열이 과하게 줄바꿈되는 문제를 추가 확인했다. 헤더 nowrap과 정렬 헤더 최소 80px, 상태 문구 nowrap을 Affinity에 한정해 적용하고 최종 화면에서 확인했다. [재현](evidence/reproduced-chrome-narrow-columns.jpg).

## 빌드·배포와 보존

최종 커밋의 315개 집중 회귀는 AG-01~04 계약, 생성/선택기 소비자, 필터 이름의 권한·범위/응답 경합, 공통 VM 목록·MoldDialog·소유자 관련 회귀를 포함한다. 실제 AntD 표 테스트는 모달 셸만 stub하고 선택·페이지·정렬 검증을 유지했다. 변경 JS/Vue lint 통과. 마지막 추가 변경은 Affinity 전용 LESS와 설계 설명이며 같은 최종 소스에서 UI 빌드와 실브라우저 검증을 수행했다.

WSL ext4에서 UI production build 후 허용 정적 항목만 assets 우선/index 마지막 순서로 교체했다. WEB-INF를 포함한 webapp 전체 교체/삭제는 하지 않았다. 정적 **893개**의 빌드/활성 파일 해시가 일치했다.

- 소스: `b5b941ec0fa5c6bd11c054422e4160bc1ec7ef03`
- 패키지 SHA256: `1e668d4e8a32495d373c67b85e2a076febbecdb30e2f497cd4a1f927d0307f1b`
- 활성 index SHA256: `1588093b1a80335cedaf8b8152a86c3b620ee4c967e0004f1f7de5bc9ade3fd3`
- 백업: `/root/ag05-ui-deploy-columns-20261019/webapp-backup`
- WEB-INF/META-INF 상태·설정 symlink/해시·JAR 9개·PID `1765167` 보존. Mold active, `/client/` HTTP 200. 기존 FTCTL 번들 마커 보존.

API/JAR/DB 스키마/호스트 패키지는 변경하지 않았다. 전체 Cloud/Maven 빌드·전체 Actions CI는 실행하지 않았다. 기존 jsdom/아이콘 중복 경고와 Browserslist 안내는 로그에 보존했다. 등록된 GitHub 체크 없음은 전체 CI 합격을 뜻하지 않는다.

## API·DB 대조와 정리

`ui-network.json`의 UI 변경 요청/응답과 `ui-job-ids.json`의 job UUID를 DB `async_job`의 명령/상태와 대조했다. 성공 작업 18개가 모두 최종 상태 1이며, 중복 이름의 동기 오류 431은 성공 job으로 집계하지 않는다. 작업 상세의 비밀값은 수집하지 않았다. API/DB 체크포인트에서 그룹의 전체 VM ID 집합과 VM 측 연결 목록을 대조하고 다른 그룹 연결 보존과 5개 VM의 Stopped 상태를 확인했다.

검증 완료 후 UI에서 삭제하지 않은 전용 자원도 API로 정리했다. `cleanup.db.txt`의 VM·그룹·매핑·볼륨·NIC·네트워크·계정·사용자·프로젝트·역할 10개 활성 카운터는 모두 0이다. 기존 그룹 4개·VM 119개 Affinity 투영은 정규화 전후 SHA256이 일치한다. 원래 FTCTL 수정 파일 3개도 착수 시 해시와 일치한다. 전체 VM의 무관한 운영 상태 변경까지 보존했다고 확대 해석하지 않는다.

새 검증 API 세션은 메모리에서 사용했고 증거 작성 시 인증값을 즉시 가렸다. 인증 정보는 공개 증거에 포함하지 않는다.

## 다음 이슈와 경계

다음은 **#1419 AG-06**이다. 정리된 기준선에서 새 인벤토리·가용 CPU/메모리/스토리지·호스트 상태를 확인한 뒤 역할·프로젝트·공유/제한 범위·상태·테마/화면 및 VM 생성/백업/VNF/AutoScale 소비자 경로를 통합 검증한다. 엄격/선호 4개 유형의 실제 VM 호스트 배치는 API/DB host ID와 하이퍼바이저 실행 위치를 함께 대조한다. 2개 이상 호스트의 시험 용량을 먼저 확보하며 기존 운영 VM을 임의 정지하지 않는다.

전체 연결 교체 API에는 CAS가 없어 마지막 조회와 쓰기 사이의 동시 변경 창이 남는다. 작업 영수증은 앱 세션 메모리 범위이며 전체 새로고침을 넘는 영속 복구는 제공하지 않는다. 실제 호스트 배치 합격과 Epic 전체 완료를 이번 UI 정돈의 결과로 주장하지 않는다. #1418은 구현·배포·해당 범위 검증 완료로 닫고, Epic #1413과 Draft PR #1420은 다음 검증을 위해 Open/Draft로 유지한다. 병합하지 않는다.
