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

# SSH 키 쌍 상세 대상 정합성 설계

Epic #1369의 #1371을 구현하며 기존 Epic PR #1378에 누적한다. API UUID 조건은 #1370에서 먼저 구현·배포했다. UI는 응답의 첫 행을 무조건 선택하지 않고 요청한 식별자와 소유 범위를 확인해야 한다.

## 경로와 소유 범위

목록의 이름과 코멘트 아이콘은 `sshKeyPairLink`를 함께 사용해 `/ssh/<UUID>`로 이동한다. 계정 키는 account/domainid, 프로젝트 키는 projectid/domainid를 전달한다. 코멘트 아이콘은 같은 경로·범위에 `tab=comments`만 추가한다. 다른 리소스의 코멘트 경로는 보존한다.

VM 상세의 keypairs는 이름만 제공하므로 `sshKeyPairNameLink`에서 이름을 URL encode하고 VM의 account/domain 또는 project 범위를 전달한다. `keypairname` query는 UUID처럼 생긴 이름도 이름 조회로 구분하기 위한 UI 내부 표식이다. API에는 전달하지 않는다.

SSH 상세의 명시적인 projectid는 공통 switchProject에서 전역 프로젝트를 변경하거나 query를 삭제하지 않는다. 기본 보기에 있는 사용자가 프로젝트 키 링크를 직접 열어도 그 소유 범위를 유지한다. 다른 리소스의 기존 프로젝트 전환은 보존한다.

기존 일반 이름 URL은 유지한다. 소유 범위 없는 이름이 여러 키에 대응하면 모호한 결과로 처리하고 목록에서 다시 선택하도록 안내한다. UUID URL은 UUID로만 조회하며 이름 조회로 fallback하지 않는다.

## 응답 검증과 상태

`sshKeyPairIdentity.js`가 경로, 조회 인자, 소유 범위, 결과 검증을 담당한다.

- 조회 결과는 count=1과 행 1개가 모두 만족해야 한다.
- UUID 경로는 반환 UUID와 일치해야 한다. 이름 경로는 이름 원문과 정확히 일치해야 한다.
- 전달된 account/domain/project가 있으면 반환 소유 범위도 일치해야 한다.
- 상세 조회의 page/pagesize는 1/2로 고정해 목록 pagination의 영향을 받지 않고 단건 여부를 확인한다.
- 명시적인 계정 범위에서는 전역 프로젝트 interceptor가 다른 projectid를 넣지 않도록 ignoreproject를 사용한다. 프로젝트는 실제 projectid를 전달한다. 이 내부 플래그는 공통 request interceptor에서 제거한다.

| 상태 | 화면과 작업 |
| --- | --- |
| loading | 요청 대상을 조회 중이며 상세·작업을 제공하지 않음 |
| ready | 검증된 대상의 상세·작업 제공 |
| refreshing | 같은 대상을 유지하며 갱신 중 작업을 잠시 차단 |
| missing | 없는 키 또는 조회 불가 안내, 상세·작업 제거 |
| ambiguous | 동일 이름의 다중 결과 안내, 상세·작업 제거 |
| mismatch | 요청과 반환 대상/소유 범위 불일치 안내, 상세·작업 제거 |
| forbidden/error | 권한/조회 오류 안내, 상세·작업 제거 |

실패 상태에는 업데이트와 SSH 키 쌍 목록 진입을 제공한다. 다른 키를 보여주거나 오류를 미사용/빈 정상 상태로 표현하지 않는다. 오류 본문의 비공개 데이터를 화면에 노출하지 않는다.

## 비동기 조회와 작업 차단

SSH 상세의 scope는 경로, 요청 식별자·소유 범위, 필터, 현재 프로젝트, 호출 사용자, 세션으로 구분한다. 탭 query 변화는 같은 대상 scope를 유지한다. scope 또는 request version이 바뀐 뒤 도착한 응답은 폐기한다.

소유 범위·사용자·프로젝트가 바뀌면 이전 상세와 열린 대화상자를 비운다. ResourceView의 key에도 SSH scope를 사용해 같은 이름의 다른 소유자 간에 탭 캐시가 공유되지 않도록 한다. 같은 대상 갱신에서는 검증된 상세와 탭을 보존한다.

도구 영역과 ResourceView의 작업 노출, execAction, 제출 버튼, execSubmit을 검증 상태와 연결한다. 다른 resource를 가진 지연 작업을 차단하고 폼 검증 Promise 완료 시 scope를 다시 검사한다. 계정 키 삭제의 단건·일괄 mapping에도 ignoreproject를 넣어 전역 프로젝트가 실제 삭제 대상을 바꾸지 않도록 한다.

## 검증과 배포

유틸, ListView 렌더링, AutogenView 조회·작업 경로 테스트로 UUID/이름/소유 범위, 다중·불일치·권한·오류 결과, 지연 응답, 갱신과 제출 차단을 검증한다. 기존 AutogenView·ResourceView·VM 그룹·목록 렌더링 테스트로 공통 리소스 동작을 확인한다.

31번 실제 브라우저에서 코멘트 유무·등록 순서, 동일 이름의 계정/도메인/프로젝트, 직접 URL·새로고침·뒤로/앞으로·VM 상세 링크를 확인한다. 없는 대상과 모호한 이름은 실제 API로, 잘못된 UUID 응답과 장애는 해당 브라우저의 일시적인 응답 주입으로 검증하고 주입 설정을 제거한다.

WSL ext4에서 UI 모듈만 빌드하고 정적 파일만 배포한다. 활성 webapp의 WEB-INF/META-INF, config.json, 관리 JAR과 Mold PID를 보존한다. 기존 정적 파일을 백업하고 새 자산을 먼저 복사한 뒤 index.html을 마지막에 교체한다. 배포 후 파일 해시·HTTP 200·실제 렌더링을 확인한다.

키 이름 표시 개선은 #1374에서 추적하므로 이번 링크 변경은 기존 표시 규칙을 보존한다. 관련 VM 조회와 대화상자 재배치는 #1372 및 #1375~#1377의 후속 범위다.
