<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# AS-09 #1355 검증·배포 완료 기록

Epic #1346의 UI 표준 통합 범위를 2026-10-12 완료했다. 누적 Draft PR #1356을 갱신하며 진행은 8/9다. 다음 #1354 통합 E2E·장애·최종 장시간 운영 판정은 사용자 지시를 기다린다.

## 반영한 화면과 공통 컴포넌트 경계

- 기존 Mold ResourceLayout/InfoCard/ListView와 VM의 mold-dialog를 사용한다. 별도 관리 화면은 만들지 않았다.
- 목록은 이름·상태·조정 대상/최소·최대·네트워크/LB·최근 확인·소유자의 6열로 묶는다. 기존 저장 열 설정을 변환하고 검색·정렬·선택·페이지 기능을 유지한다.
- 공통 첫 상세 탭의 오토스케일 요약·평가·알림을 제거하고 **운영 상태** 탭에 한 번만 표시한다. 왼쪽 공통 카드의 기존 details 슬롯에만 그룹의 범위·포트·확인 시각을 추가한다.
- **EventsTab.vue / AnnotationsTab.vue / ResourceLayout.vue / InfoCard.vue는 AS07 완료 HEAD와 byte 동일**하다. VM/볼륨 첫 상세 및 기존 이벤트·코멘트 형식을 실제 화면에서 확인했다.
- 프로필·정책·LB·생성 진행 모달은 VM과 같은 중앙 배치, 스크롤 본문, 고정 확인/취소 footer를 쓴다. 전용 스타일은 오토스케일 화면 클래스에 한정한다.
- 기존 생성 복구 영수증·권한·최신 상태 검증·편집 보상·부분 조회 실패 의미를 보존한다. 공통 탭을 대체하거나 새로운 폴링을 추가하지 않았다.

## 실제 UI 시험에서 찾아 수정한 오류

1. 공유 비공개 이미지: 생성의 기존 OS 선택 블록에 **공유** 분류를 추가하고 동일 소유 범위/Zone/Ready의 executable/sharedexecutable 목록만 사용한다. 프로젝트의 정확 ID 사전 조회가 개인 ACL을 적용하는 문제를 최신 허용 목록에서 선택 ID를 확인하는 방법으로 보완했다.
2. 프로젝트 프로필 서버 생성: 확정 프로젝트 ID를 내부 배포 검증 map에 넣고 임의 owner 값의 덮어쓰기를 막는다. 프로젝트 프로필에만 개인 호출자의 중복 generic DeployVM ACL 검사를 생략하며 finalizeOwner/프로젝트 접근과 AS06의 소유자 기준 모든 이미지·Zone·오퍼링·네트워크·디스크·키·Affinity 검증을 반드시 수행한다. 프로젝트 외 경로와 권한 없는 프로젝트 이미지 거부 회귀는 유지한다.
3. 기존 LB에 protocol이 없으면 알고리즘 값 일부 저장 후 null 비교 오류가 발생했다. 오토스케일 편집에서 기존 의미 TCP를 명시하며 명시된 SSL은 보존한다. 31의 protocol 없는 전용 규칙을 변경/복원하는 UI 시험으로 오류 해소를 확인했다.
4. 생성의 초기 VPC 조회 중첩 params 오류, LB 선택 개수/로딩 prop 누락, 네트워크 중복 링크·프로젝트 소유자 공백을 수정했다. 기본 VM의 네트워크 요청 형식은 변경하지 않았다.
5. 실제 대비가 낮았던 라이트 경로·단계 숫자·필수/오류 안내·비활성 버튼 내부 글자·페이지 번호·hover/focus·일반 사용자 프로젝트 스위치를 전용 테마 토큰으로 보완했다. 스위치의 켜짐/꺼짐 글자와 배경·윤곽·손잡이를 함께 지정한다.
6. 그룹 이름의 hostname 계약은 영문/숫자/하이픈 1~255자로 정확히 안내한다. 한글 그룹명은 명확한 한국어 오류로 거절하고 정책명/코멘트의 한글은 저장한다. UserData 원문·인증 값은 새 요약/공개 자료에 넣지 않는다.

## UI-01~10 실제 검증

| 항목 | 확인 결과 | 지원 근거와 범위 |
|---|---|---|
| UI-01 목록 | 31 프로젝트/32 일반 사용자에서 6열, 소유자, 중복 링크 제거. 검색·정렬·체크 선택·열 숨김/복원·20/50 페이지 크기 변경 | 전용 그룹 생성 때 31 목록 기능 실행. 최종 배포 양쪽 목록에 기존 그룹3/4 표시; 여러 실제 데이터 페이지 시험은 집중 회귀와 구분 |
| UI-02 상세 | 첫 상세는 공통 속성 목록, 운영 요약·평가 중복 없음 | 공통 DetailsTab의 autoscale 주입 부재 및 실제 그룹·VM·볼륨 상세 |
| UI-03 운영 | 요약·평가·멤버·LB·최근 이벤트·출처와 조회 시각, Disabled 안내, 수동 새로고침 | 실제 Running1/LB등록1. AS07의 부분 실패/권한/재시도 단위 회귀를 재실행; 모든 장애를 이번 UI에서 다시 주입한 결과는 아님 |
| UI-04 전용 탭 | 프로필 유예0→10 저장 후 모달 닫힘/10 갱신→0 복원. LB 알고리즘 변경/복원. 조건 -1 입력의 한국어 오류·취소, 정책 기간 누락 거절 | 32 프로필/조건, 31 null protocol 규칙의 최종 UI. AS06 정책/공유/권한 편집 회귀 재실행 |
| UI-05 그룹 작업 | 32 interval10→15→10 갱신, Enabled→Disabled, 두 전용 그룹 cleanup=true 삭제 | 최종 기준 그룹은 작업 없음/Disabled로 보존 |
| UI-06 생성 | 양쪽 현재 Mold 6단계 생성 성공, 한글 이름 오류·최종 요약·공유 선택·진행 표시 | 31 최종 Java + UI2cbb6703으로 공유 비공개 이미지 생성; 32 정상 생성은 UI0db7db0b, 이후 최종 폼·대화상자·색상 재검증. 마지막 UI 변경은 CSS 대비이며 생성 전체를 매번 재수행하지 않음. 응답 유실/재진입/보상은 기존 AS02 집중 회귀 재실행 |
| UI-07 공통 회귀 | VM·볼륨 첫 상세, 이벤트7열·코멘트 기존 형식. 한글 메모 추가/삭제 | 공통 Events/Annotations 소스 byte 동일, 실제32 화면 및 VM 스냅샷/ResourceView 집중 회귀 |
| UI-08 한글/테마 | 라이트/다크 본문·입력·오류·버튼·비활성·페이지 번호·프로젝트 스위치 | 최종 목록/운영 텍스트 표본 최소5.19:1, 최종 폼 입력 표본 최소4.60:1. 색상 외 텍스트/아이콘 상태도 유지 |
| UI-09 반응형 | 실제1440/1024/768/390px에서 표 내부 가로 스크롤, 모달 화면 안 footer, 긴 폼 본문, Tab 초점 유지 | 8개 조합별 DOM width/좌표/CSS 기록. 조건/프로필 모달 최대760px, 작은 화면374px. 일반 viewport 이미지와 실제 DOM 수치를 사용 |
| UI-10 저장소/보존 | 31 ROOT479 sparse Ready100GiB, 32 ROOT155 Ready100GiB | 6호스트 실제 qemu 이미지/krbd 연결 및 정리. 기존3/4그룹·3/4VM·ROOT300/400GiB 보존 |

대비 측정은 실제 화면의 표시 텍스트에 대해 CSS 알파 색상과 배경을 합성한 표본이며, 입력은 text-fill-color까지 별도로 측정했다. 테마 전환 애니메이션이 끝난 뒤 수집했다. 모든 픽셀/아이콘·외부 화면·전체 WCAG 접근성 인증으로 확대하지 않는다. 대화상자/생성은 페이지 번호·프로젝트 스위치 수정 전 UI2cbb6703의 변경 없는 폼으로 측정했고 마지막 번들에서 목록/운영 상태를 재확인했다.

## 변경 모듈 빌드

- Java 소스 **ef14275a2ad2e700ad6f9606268cbb001db5d020**: api **13건**, server **165건** 통과. WSL ext4 변경 모듈 package·Checkstyle0·모듈 RAT 통과. API/서버 전체 테스트 집합이 아닌 지정한 관련 테스트다.
- 최종 UI 소스 **cd4221a3c7a5169cc56fdaa7442170ecce750af4**: **13 suites /266건**, 집중 lint, production UI build 통과. AS 생성/복구/평가/수명주기/예약/편집/운영·VM ResourceView/스냅샷 회귀를 포함한다.
- UI 테스트의 기존 Vue stub 경고와 Browserslist 데이터 갱신 안내는 보존한다. API/서버 테스트 초반 fixture/import 오류는 수정 후 위 최종 빌드로 판정했다.
- **전체 Cloud/fat JAR/RPM/Actions 빌드 및 전체 CI는 실행하지 않았다.** 기존 설치 JAR의 변경 모듈 클래스만 검증하여 반영했다. qemu 패키지는 변경하지 않았다.

## 최종 배포

| 대상 | 관리 JAR SHA256 | 최종 PID | 정적 UI 백업 |
|---|---|---|---|
|31|`036f448a93ff0c6c156a3dafebd3b26af6621203c60a6caf98b43ff10ef179ac`|471973|`/root/epic1346-as09-ui-20261012-022234`|
|32|`dce0e50d4b0193202ee7c6522434814065d8f4c2ebd3fe74fd42d84373818e19`|938882|`/root/epic1346-as09-ui-20261012-022252`|

UI **ui-as09-cd4221a3c7a.tgz** SHA256 **cf9c1c1bc7f83aa7ad7bb765ed0abcaa66978cc4ab52cb11e6da721b9cfd265b**, UI 입력 manifest SHA256 **ad00b3d413a1fe60a8616cd70d47e5364dfca9d5d3f4054f7bc23ff5571937a1**. 양쪽 정적843파일 SHA를 재검증했다. WEB-INF/config 보존, META-INF는 기존부터 없으며 존재 시 보존하는 절차다. mold active·/client/ HTTP200. 최종 UI 정적 배포에서 관리 JAR/PID는 변하지 않았다. API/server 배포 이전 JAR는 각각 `/root/epic1346-as09-r2-20261012-015555`/`...015632`에 보존한다. 배포 JSON은 클래스별 해시와 소스를 기록하며 다른 ZIP 항목의 내용이 유지됐음을 배포 절차에서 확인했다.

## 실제 저장소 및 테스트 자원 정리

31 전용 VM **i-35-479-VM**은 GFS2 파일의 독립 Sparse qcow2, virtual-size107374182400/actual-size3559247872이며 Thin을 사용하지 않았다. 32 **i-6-155-VM**은 실제 `/dev/rbd/...` krbd block/raw로 연결했다. 전용 LB8089에 짧은 HTTP각20/20 성공을 확인했다.

두 그룹은 **UI cleanup=true로 삭제**했다. 전용2VM·ROOT100GiB×2는 removed/Expunged, 그룹 removed/REVOKE 및 operation 없음이다. 활성 멤버 map·LB map·secondary 참조는0이며 LB map의 revoke=1/removed 이력 행은 정상 tombstone으로 남는다. 6호스트에서 전용 도메인·GFS2 파일·RBD 이미지·krbd 매핑이 없는 것을 확인했다. 전용 프로필·정책2/조건2·LB도 제거했고8089 방화벽 잔여 규칙은 없다.

기존31 **3그룹/3VM/ROOT300GiB**,32 **4그룹/4VM/ROOT400GiB**가 보존되어 있다. AS04 원본397/73 Running1/LB1, Disabled/작업 없음/min1max3·프로필8항목 동일·원 CPU정책 동일·예약6개 Disabled를 확인했다. 기존 LB8084의 최종 짧은 HTTP각20/20 성공/실패0이다. 대량 부하/장시간/무손실 판정은 아니다.

## 완료와 남은 범위

#1355를 완료로 닫고 Epic8/9 및 Draft PR #1356을 갱신한다. 다음은 **#1354 GFS2·krbd UI E2E·장애·최종 장시간 운영 및 프로덕션 판정**이다. 사용자 지시 전 다음 이슈에 착수하지 않는다. 전체 Cloud 빌드/전체 CI, 외부 LB·실제 다중 관리 노드·전체 접근성 인증은 본 PASS 범위가 아니다. AS05 최초 확장 timeout 각4건 미확정 기록과 기존 검증의 제한은 후속 최종 판정까지 유지한다.

[공개 UI/API/DB/물리 저장소·배포 근거](evidence/AS-09/README.ko.md)
