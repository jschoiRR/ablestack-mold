# AG-06 증거 읽기

검증 소스와 최종 배포 식별은 `qualification.json`과 `deployment-priority-final.json`을 기준으로 합니다. 착수 UI `b5b941ec0fa`에서 생성·역할·배치 경로를 확인하고, 수정 UI `8beac110e7f`에서 소유자 조회·부분 실패·삭제·정책 표시·레이아웃을 재검증한 후 모바일 선택기 CSS만 보완한 최종 UI `1a5b52159fd`를 배포했습니다. 두 소스의 template/script 동일성과 변경 파일은 `source-continuity.json`에 기록했습니다. 최종 선택기 화면 6개 상태를 다시 측정했으며 다른 36개 상태는 기능 소스가 동일한 초기 배포 측정입니다. 첫 CSS 수정 `8588368d2d4`는 공통 모바일 규칙의 specificity를 이기지 못해 실측이 실패했으며 `first-mobile-fix-insufficient.json`에 보존했습니다. 최종 수정은 선택기 내부의 최소 너비만 우선 적용합니다.

| 목적 | 근거 |
| --- | --- |
| 테스트·lint·production build | `unit-mobile-final.txt` (기능 소스 동일), `lint-priority-final.txt`, `build-priority-final.txt` |
| 실제 정책 배치 | `placement-strict-affinity.json`, `placement-strict-anti.json`, `placement-prefer-aff.json`, `placement-prefer-anti.json`, `placement-prefer-anti-forced.json`, `placement-cold-prefer-anti.json` |
| 원래 시험 가정 실패 | `placement-prefer-anti.log`의 assertion은 실패 그대로 보존. 새 배포 시험과 강제 호스트 시험을 원래 시험의 PASS로 바꾸지 않음 |
| 역할·조회 범위 | `domain-role-contract.json`, `domainadmin-read-variants.json`, `fixed-domainadmin-*`, `role-*`, `restricted-*`, `shared-query-final-contract.json` |
| 서버 count/페이지 후속 결함 #1421 | `domainadmin-pagination-defect.json`, `final-domainadmin-count-defect.txt` |
| VM별 부분 실패/실패만 재시도 | `partial-*`, `partial-retry-checkpoint.json` |
| 삭제 영향 변경/재확인·VM/다른 그룹 보존 | `delete-*` |
| UI 최종 작업 영수증 12건과 DB | `ui-job-ids.json`, `ui-jobs-verification.json`, `ui-jobs.db.txt`, `ui-network.json` |
| VM 기준 5영역·테마·반응형 | `final-vm-reference-*`, `matrix-*.txt`, `responsive-matrix.json`, `reproduced-wizard-narrow-overflow.json`, `final-selector-overflow.json` |
| 시험 인벤토리·기존 데이터 보존·정리 | `fixture-summary.json`, `final-fixture-checkpoint.json`, `cleanup-api.json`, `cleanup.db.txt`, `runtime-domain-absence.json`, `preservation.json` |
| 배포 정적 파일·서버 보존 | `deployment-priority-before.json`, `deployment-priority-final.json`, `runtime-final.json`, `package-priority-final.sha256` |

`api-dedicated-history.jsonl`은 전용 자원의 변경·작업 결과와 범위가 지정된 조회만 포함합니다. 인증 정보는 기록 시 가렸고, 기존 운영 VM 전체 응답은 제외했습니다. `preservation.json`은 기존 4개 그룹/119개 VM의 ID·이름·Affinity 투영을 비교하며 운영 VM의 다른 상태가 고정됐다는 뜻은 아닙니다. 삭제 이력과 활성 잔여를 구별합니다. 캡처에 등장하는 전용 자원은 최종 정리 대상이며 UUID를 재사용하지 마세요.

1440/390px 에뮬레이션의 스크린샷 렌더링 제약 때문에 반응형 판정은 DOM 좌표·내부 스크롤/폭·실제 조작을 사용했습니다. 첨부 JPG는 기본 브라우저 viewport의 시각 확인 자료입니다. 전체 Cloud CI, 물리 용량 고갈/호스트 장애, AutoScale/Kubernetes/백업/VNF의 전체 생성·운영 시나리오 합격을 의미하지 않습니다.
