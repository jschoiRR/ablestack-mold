<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements. See the NOTICE file
distributed with this work for additional information
regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the
specific language governing permissions and limitations
under the License.
-->

# 사용 가상머신 탭 업데이트 깜빡임 개선

2026-10-15, cluster 31. #1377 완료 후 사용자 보고에 따른 후속 수정이며 기존 Epic PR #1378에 누적한다. 수정 전 소스 f5d7b0bffe9751a0a770853ebcd9742343a2fc4a, 배포 수정 소스 `6b55d77f3cbf0410298fc428b47bad2f877bf138`.

## 원인과 수정

표의 loading 조건이 `pending && !rows.length`여서 이미 성공한 빈 목록도 업데이트마다 최초 로딩으로 취급했다. 31번 실제 UI에서 요청을 1초 지연해 관찰한 70개 프레임 중 **63개에서 전체 spinner·흐림 효과**가 발생했다. 데이터가 있는 2페이지(9행)는 이 증상이 없었다. 표/헤더 DOM 자체의 교체와 구분하여 재현했다.

조회 성공 범위 loadedScope와 현재 queryScope의 일치 여부로 최초 로딩을 판정한다. 같은 조건에서 다시 조회하면 빈 목록도 기존 표를 유지하고 버튼의 로딩 표시만 사용한다. 최초 조회·검색/키/소유 범위 변경의 로딩과 기존 권한/대상 확인·오류 복구·늦은 응답 차단은 유지한다.

## 검증

- 관련 단위/회귀 **3 suite / 75개 통과**, lint 및 생산 UI 빌드 통과. 빈 목록 갱신/오류 후 재시도 및 최초 조회/검색 범위 변경을 회귀 조건에 추가했다. 이 75개와 이전 본 구현 전체 469개 결과를 구분한다.
- 실제 브라우저 **7개 통과**: 최초 로딩 표시, 빈 목록/19대 목록의 수동 갱신, 빈 목록 자동 갱신, 390×844 밝은/어두운 테마 수동 갱신, 19대 중 2페이지의 자동 갱신·정렬 유지.
- 프레임별로 표 spinner/흐림 여부, table/thead DOM·기존 행의 연결 유지, 행 수·표 높이를 관찰했다. 수정 후 동일 조건 갱신의 전체 spinner/blur **0프레임**, 표/헤더 교체·행 제거 0회. 페이지·정렬을 유지했고 빈 목록/9행 표 높이가 일정했다.
- 실제 API의 0대·19대 조회 결과를 사용하는 전용 키 2개만 임시 등록했다. 기존 VM은 변경하지 않았다. 정리 후 기존 목록/관련 VM UI **2개 통과**, 임시 키 잔여 0, 기존 키 **6개**와 VM SSH 상세 **360행**의 해시 일치.
- 정적 파일 **855개 해시 일치**, WEB-INF/config/JAR·배포 직전 Mold PID **1066169**와 FTCTL markers 보존, Mold active 및 /client/ HTTP 200. 백업 `/root/ssh-keypair-1377-refresh-ui-20261015-154934/static-before.tgz`.

WSL ext4 작업 트리에서 UI만 빌드·배포했다. 전체 Cloud/Maven/RPM/Actions 빌드·전체 CI와 게스트 SSH는 실행하지 않았다. 비밀번호·세션·개인 키·실행 helper는 공개 근거에서 제외했다.

## 화면과 데이터

[수정 전 빈 목록](before-empty-manual-pending.jpg) · [수정 후 빈 목록](after-empty-manual-pending.jpg) · [2페이지 갱신](after-used-manual-pending.jpg) · [모바일 밝은 테마](after-empty-mobile-light-pending.jpg) · [모바일 어두운 테마](after-empty-mobile-dark-pending.jpg)

프레임 집계: ui-before-verification.json / ui-after-verification.json. 테스트 로그: ui-tests.log. 배포/서비스 근거: deployment-verification.txt / runtime-final.txt.
