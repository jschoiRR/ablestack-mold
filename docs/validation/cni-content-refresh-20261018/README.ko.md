# CNI 본문 — Breadcrumb 갱신 깜빡임 보완

2026-10-18, 31번 클러스터. 누적 PR #1409의 `e992a0eb33b` 이후 보완이다. 탭 내부 버튼과 구분해 **Breadcrumb 업데이트**를 기준으로 재현했다.

## 원인과 변경

같은 UUID의 상세 갱신이 진행되면 `resourceActionReady`는 잠시 false가 된다. CNI 본문은 이를 실패/미확인으로 처리해 본문 위에 경고를 추가했다. 실제 요청을 대기시킨 상태에서 본문 텍스트는 같지만 top이286→368px, **82px 이동**했다가 복구됨을 확인했다.

공용 ResourceView가 전달하는 `resourceReadPending`과 loading으로 조회 대기/완료 실패를 구분한다. 대기 중에는 기존 본문과 원문/Base64 표시·스크롤을 유지하고 경고를 삽입하지 않는다. `aria-busy`와 내보내기 차단은 대기에 연결하며, 실패가 확정되면 기존 미확인 경고와 내보내기 차단을 유지한다. 권한·UUID·소유 범위·세션 변경의 초기화나 늦은 응답 차단은 유지한다. 일반 UserData/VM·로컬 미리보기는 기존 동작을 유지한다.

## 집중 검증

- 관련 CNI 본문·일반 UserData 본문·복제·이력·버전 **5 suites / 226 tests PASS**. 실제 ResourceView/CniContent 마운트에서 대기 중 동일 pre 노드·본문 객체·스크롤·Base64 모드 유지, 경고 부재, 복사 차단, 실패 경고 및 복구를 검증했다.
- 변경 파일 lint PASS. UI production build 및 31번 정적 배포를 완료했다. Java/JAR 변경 및 전체 Cloud 빌드/Actions는 이 보완 범위에 없다.
- 본문이 큰17,258바이트 전용 CNI로 확인하며 기존 CNI/클러스터를 수정하지 않는다.


## 최종 실제 UI 검증

1440/390px × 다크/라이트 × 원문/Base64 **8조합**에서 Breadcrumb 갱신의 상위 상세 GET(페이지 크기20)과 후속 본문 GET(크기2)을 각각 대기시켰다. 각 구간과 완료 시점의 본문 영역 내 top 변화는 모두 **0px**였다. 같은 backend DOM 노드·표시 원문/Base64 SHA·수평/수직 내부 스크롤·표시 모드가 유지됐다. 대기 중 내보내기는 잠기고 완료 후 복구됐다. 페이지 자체의 스크롤 이동과 구분하려고 본문 section 기준 상대 위치를 비교했다. [8조합 DOM 근거](after-fix.json), [수정 전82px](before-fix.json).

탭 내부 업데이트도 같은 본문 노드·내용·위치를 유지했다. Breadcrumb 연속5회는 실제 상위5회/본문5회 조회를 실행했고 모두 같은 노드·본문·위치로 완료됐다. [탭 회귀](tab-update.json), [연속 갱신·요청](repeated-breadcrumb.json).

브라우저의 해당 상위 요청만500으로 응답해 실제 실패 후 경고/내보내기 차단을 확인했고, 다음 Breadcrumb 재시도로 원래 본문과 내보내기가 복구됐다. 요청 주입은 모두 해제했다. [실패·복구](failure-recovery.json). 사이트 전체를 끊거나 저장된 CNI를 변조하지 않았다.

## 배포와 정리

최종 정적888개 파일 해시 일치, 활성 index SHA `96034ffd66057b3411783b5091a6e808abb5c4ac0505aecdb4605ef3803612e6`. WEB-INF/config/JAR/PID1589408을 보존하고 Mold active·/client/200·현재 manifest의 parentReadPending 표식을 확인했다. [배포](deployment-verification.txt), [정리 후 런타임](final-runtime.json). 백업 `/root/cni-content-refresh-ui-20261018-222105`.

전용 CNI1개의17,258바이트/SHA를 갱신 후 다시 대조하고 정상 API로 삭제했다. 활성 CNI/코멘트 잔여0, 기존 CNI3개·Kubernetes7개 및 선택 DB36행이 작업 전과 동일하다. DB SHA `2d61f159980ac4fa490f62c74e16ce6024ac1f8bb9210c2c6caaa366c74357f7`. [정리·기존 데이터 보존](cleanup-proof.json). 원래 다크 테마·기본 보기·뷰포트를 복원했다.

전체 Cloud 빌드·Maven/JAR 배포·GitHub Actions는 실행하지 않았다. 집중 테스트와 UI production build/실환경 결과를 전체 CI 성공으로 표현하지 않는다.

[수정 전 대기 화면](screenshots/before-fix-parent-pending.jpg) · [수정 후 같은 갱신 대기](screenshots/after-dark-1440-raw-parent.jpg) · [실패 시 경고 유지](screenshots/parent-failure-warning.jpg).
