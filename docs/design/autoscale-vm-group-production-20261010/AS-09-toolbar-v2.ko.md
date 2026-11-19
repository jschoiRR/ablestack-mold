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

# AS-09 #1355 상단 액션 정렬 재검토 목업 v2

2026-10-12 사용자 요청으로 #1355를 다시 열었다. 이 문서는 **구현 전 승인용 화면 배치 목업**이다. 기존 1차 구현/클러스터 검증 기록은 AS-09-validation.ko.md에 보존한다. Epic 현재 완료7/9, #1355 목업 검토 대기, #1354 최종 통합/장시간 판정은 대기한다.

## 사용자 요구와 범위

1. 기존 액션이 있는 탭의 버튼을 콘텐츠 최상단 왼쪽에 모은다.
2. 기존 변경 주 액션 하나는 아이콘+텍스트의 파란 primary 버튼이다.
3. 나머지 기존 액션은 아이콘+텍스트의 일반 버튼이며, 조회 갱신이 필요한 대상 탭은 마지막에 `ReloadOutlined + 업데이트`를 둔다. 삭제 버튼도 일반 버튼으로 표시하지만 실제 삭제 확인·권한·상태 제약은 유지한다.
4. 버튼 영역 다음에 안내/선택/설정/목록을 배치한다. 여러 줄로 바뀌어도 본문은 버튼 아래에 있고, 오른쪽 정렬/공백 확장으로 업데이트를 밀어내지 않는다.
5. **액션이 없는 탭에 새 버튼을 만들지 않는다.** 공통 상세·이벤트·코멘트 형식도 변경하지 않는다. 생성·확장·축소·활성화 등 새로운 업무 액션을 추가하지 않는다.

## 탭별 기존 액션과 제안 순서

| 대상 탭 | 파란 주 액션 | 일반 버튼 순서 | 마지막 버튼 |
|---|---|---|---|
| 운영 상태 | 없음: 기존 조회 갱신만 있음 | 없음 | 업데이트 (기존 운영 정보 새로고침의 이름/위치 정리) |
| VM 프로필 | 프로필 편집 | 사용자 데이터 변경 / 매개변수 추가 / 매개변수 삭제 | 업데이트 |
| 부하 분산 규칙 | 규칙 편집 | 태그 편집 / 고정 세션 설정 / 규칙 삭제 / VM 연결 해제 | 업데이트 |
| 스케일업·스케일다운 정책 | 정책 추가 | 정책 편집 / 정책 삭제 / 조건 추가 / 조건 편집 / 조건 삭제 | 업데이트 |
| 스케줄 | 스케줄 추가 | 스케줄 편집 / 스케줄 삭제 | 업데이트 |

새로 노출하는 수동 업데이트는 **이미 액션이 있는 위 탭**의 현재 fetchData 등 현재 조회 함수를 사용한다. 새 API/업무 기능은 아니다. 운영 상태처럼 조회 버튼 하나만 있는 탭에는 변경 주 액션을 억지로 만들지 않고 일반 업데이트 하나를 표시한다.

## 콘텐츠 재배치 및 대상 선택

- 툴바는 `a-space wrap`/`a-button`과 AntD 아이콘을 사용한다. 주 액션 하나만 primary, 보조·삭제·업데이트는 default다.
- 프로필: 툴바 → 향후 VM 적용 안내 → 기존 프로필 값 → 배포 매개변수 목록. 기존 표의 삭제는 선택한 매개변수를 대상으로 상단 삭제 버튼에서 실행한다.
- LB: 툴바 → 기존 규칙 속성 → 삭제 제한 안내 → 연결 VM 목록. 연결 VM을 선택하면 기존 연결 해제 액션을 사용한다. 그룹에 연결된 LB 규칙 삭제는 기존과 같이 차단한다.
- 정책: 툴바 → 정책 선택 → 현재 정책 설정 → 조건 목록 → 기존 변경 제한 안내. 조건 편집/삭제는 선택한 조건이 있을 때만 활성화한다. 현재 정책 수/마지막 조건 보호·공유 영향·5연산자·0·상태/권한/보상 계약은 유지한다.
- 스케줄: 툴바 → 실행 계약 안내 → 기존 예약 목록과 페이지. 선택한 예약에 기존 편집/삭제 동작을 연결한다.
- 운영: 업데이트 → 출처 안내·조회 시각 → 기존 운영 요약/평가/멤버/LB/이벤트. 요약을 공통 첫 상세에 다시 넣지 않는다.
- 기존 대화상자 및 확인/취소 고정 footer는 유지한다. 행별 버튼을 옮기는 과정에서 삭제 보호·작업 중 차단·기존 API 콜백/확정 실패 보상 규칙을 변경하지 않는다.

공통 ResourceSchedules를 실제로 조정할 경우 AutoScaleVmGroup 전용 분기/옵션으로 한정하여 다른 자원/VM 예약 화면의 형식을 바꾸지 않는다. 공통 EventsTab/AnnotationsTab/DetailsTab은 이번 변경 대상이 아니다.

## 실제 Vue3 + AntD 구성

Vue3.2.37, Ant Design Vue3.2.20, @ant-design/icons-vue7.0.1을 현재 UI node_modules에서 사용한다. 공통 `ui/src/layouts/ResourceLayout.vue`와 Mold theme tokens/base/components/feedback를 그대로 import한다. 헤더·메뉴·요약 카드의 주변 셸은 현재 Mold 비율/스타일을 재현한 목업 부품이다. 별도 대시보드나 생성 이미지가 아니라 **실제로 렌더링한 Vue 화면의 캡처**다.

소스는 docs/design/.../mockups/AS-09-toolbar-v2 안에만 추가한다. 제품 ui/src/api/server 및 공통 컴포넌트는 변경하지 않는다. 고정된 예시 데이터이며 Cloud API와 연결하지 않는다. 목업에서는 탭 전환·행 선택·정책/조건 대화상자의 레이아웃 예시만 동작한다. 실제 프로필/LB/예약 등의 업무 동작과 대화상자 내용은 기존 구현을 그대로 사용하며 승인 후 적용/검증한다.

## 목업 검증 결과와 승인 기준

- 로컬 개발 미리보기 compile 성공, Vue 렌더링 오류 없음. 제품 모듈/전체 Cloud 빌드·테스트 클러스터 배포는 이 단계에서 수행하지 않는다.
- 기본 Mold 화면 크기1280px에서6탭×라이트/다크 **12장**을 캡처했다. 기존 버튼 모두 아이콘+텍스트, 일반/primary 구분, 마지막 업데이트를 확인했다.
- 콘텐츠 상단 툴바 다음 본문 간격20px; 1024px/390px에서 줄바꿈 후20px/16px 간격과 문서 가로 넘침 없음. 버튼은 왼쪽에서 순서를 유지한다.
- 선택 전 조건 편집/삭제 비활성, 선택 후 활성의 예시를 확인했다. 같은 규칙을 매개변수/연결 VM/예약 선택에 적용한다.
- 공통 상세/이벤트/코멘트에는 새 툴바0개. 원래 코멘트 입력/추가는 목업 범위 밖의 기존 형식을 표현한다.
- 라이트/다크 본문 표시 텍스트 표본 최소 **4.58:1**, primary 버튼 글자5.19:1. 메뉴 선택/헤더 아이콘/라디오 선택 윤곽도 눈으로 확인하고 Mold 색상으로 교정했다. 전체 접근성 인증은 아니다.

2026-10-12 목업 승인 후 **제품 구현·UI 빌드·31/32 배포·실제 UI 및 대화상자 검증을 완료**했다. [구현·검증·제한](AS-09-toolbar-v2-validation.ko.md)을 최신 결과로 사용한다. 아래 목업 단계 설명은 승인용 산출물 범위다. 승인된 후속 구현은 #1355 안에서 완료했고 신규 이슈를 만들지 않았다. #1354는 지시 대기다.

[탭별 라이트/다크 이미지와 실행 방법](mockups/AS-09-toolbar-v2/README.ko.md)
