<!-- Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0 . Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND. See the License for the specific language governing permissions and limitations. -->

# UD-10 복제·버전·변경 정책 설계

대상은 upstream #1391, Epic #1381 / Draft PR #1393이다. 변경 API와 DB 마이그레이션을 추가하지 않고 기존 등록·조회·댓글 API를 사용한다.

## 버전과 소유 범위

- 각 사용자 데이터 UUID는 독립적인 변경 불가능 버전이다. 원본의 이름(`-v2` 제안값), 정규 Base64 본문, 변수 선언을 채우고 본문과 이름을 바꿔 새 UUID를 등록한다. 자동 버전 번호·부모 순서·전체 계보는 추정하지 않는다.
- 원본 UUID·이름·account/domain 또는 project 범위를 동결하고 조회 및 등록 전에 재확인한다. 복제 소유자는 원본과 같으며 선택 중인 ambient 프로젝트로 바뀌지 않는다. 프로젝트는 projectid, 비프로젝트는 account/domainid/ignoreproject를 명시한다.
- UD-01의 소유자 잠금·동일 이름/동일 바이트 중복 금지는 그대로 적용된다. 이름·변수만 바꾼 동일 본문은 새 버전으로 만들 수 없다. 다른 소유자에게 배포하려면 일반 등록과 그 계정의 권한 검증을 별도 수행한다.
- 브라우저는 Base64로 원본을 채워 BOM·CRLF·바이너리를 보존한다. 원문 편집은 명시적으로 선택하고 LF 정규화를 안내한다. 본문 미리보기·복사·다운로드는 공통 UserDataContent를 재사용한다. 본문은 실행하지 않는다.

## API 계약과 복구

1. listUserData(id, 고정 소유자)로 원본과 정규 Base64를 확인한다. 등록 직전 원본 본문·변수 불변을 다시 검사한다.
2. listUserData(name, 고정 소유자)에서 동일 이름을 조회한다. 부분 이름 검색 결과를 정확한 이름과 구분한다. 잘린 응답·불일치는 실패로 처리한다.
3. registerUserData를 POST한다. 응답 및 후속 정확 조회의 UUID·소유자·이름·본문·변수가 일치해야 성공 영수증을 표시한다.
4. 응답 유실·오류 후에는 제출한 스냅샷을 유지하고 입력을 잠근다. 다음 클릭은 조회만 하며 결과가 없다면 입력 잠금을 해제하고 명시적인 다음 등록 클릭을 요구한다. 경쟁 시 최종 서버 중복 검증을 따른다.
5. 새 UUID에 addAnnotation(entitytype=USER_DATA, adminsonly=false)를 호출한다. MOLD_USERDATA_VERSION/1 메모에는 댓글 API의 255자 제한에 맞춰 원본/대상 UUID·바이트 수만 기록한다. 이름·변수 선언은 각 버전의 상세·복제 미리보기에서 확인하며 본문·변수 값·자격 증명을 메모에 넣지 않는다.
6. 등록과 댓글은 원자적 요청이 아니다. 출처 저장 실패는 등록 성공과 별도로 표시하고 댓글만 재시도한다. 응답 유실 시 listAnnotations를 먼저 조회해 같은 메모를 재사용한다. 닫기 전 메모 복사/다운로드를 제공해 수동 복구할 수 있다.
7. 버전·변경 정책 탭은 현재 항목 접근 권한을 먼저 확인하고 페이지별 listAnnotations를 완전 조회한다. 유효한 현재 대상 메모만 표시하며 중복 메모는 합친다. 이 메모는 삭제·가시성 변경 가능한 참고 정보로, 불변 감사 이력이나 실제 적용 이력으로 취급하지 않는다. 권한·누락·통신 오류·출처 없는 항목을 구분한다.

복제 작업 노출에는 동적 API 발견의 listUserData/registerUserData/listAnnotations/addAnnotation이 모두 필요하다. API 자체 ACL이 최종 권한이다. 세션·사용자·프로젝트·원본·경로·권한 변경/해제 후 늦은 응답은 버린다. 대기 중 닫기·라우팅·중복 제출을 잠근다. 결과/참조 링크는 대기 중 활성화하지 않으며, 완료 후에는 현재 작업창을 먼저 닫고 대상 탭으로 이동한다. 재사용되는 AutogenView 안에서 창이 새 원본의 복제로 다시 초기화되는 것을 방지한다.

## 적용과 되돌리기

새 버전 등록은 기존 VM/템플릿/ISO를 자동 변경하지 않는다. 원본의 적용 대상 탭에서 UUID별 전환 대상을 확인한다. VM은 기존 재설정 작업에서 새 UUID와 변수 값을 명시한다. 복원은 이전 UUID와 당시 변수 값으로 재설정하고 저장 본문을 대조한다. 참조 ID와 실제 저장 본문은 다를 수 있으며 게스트 스크립트의 이미 실행된 효과까지 되돌리는 기능은 아니다.

템플릿·ISO 연결 전환은 새 UUID와 기존 정책을 명시하며 이후 생성 VM에만 영향을 준다. 되돌릴 때 이전 UUID/정책을 다시 연결한다. 되돌릴 가능성이 있는 이전 버전은 참조가 없어도 보존하며, 적용 전 UUID·변수 값·연결 정책과 실제 VM 저장 본문을 기록/백업한다. 삭제된 UUID 또는 달라진 템플릿 정책에 대한 동일 복원은 보장하지 않는다. 복제 기록만으로 현재 적용 상태를 추론하지 않는다.

UD-09에서 확인한 BOM 시작 cloud-config의 APPEND MIME 인식 제한을 안내한다. 이 작업은 원본 바이트를 몰래 정규화하지 않는다. 병합용 새 버전은 명시적으로 BOM 없는 지원 형식으로 작성하고 실제 VM 생성 검증을 한다.

## 검증 계획

집중 Jest/ESLint/UI production build 후 31번에 정적 UI만 배포한다. 변경 Maven 모듈·전체 Cloud/GitHub Actions 빌드와 호스트 배포는 필요하지 않다. 기존 JAR/12개 클래스/config/WEB-INF/Mold PID를 보존하고 정적 파일 해시와 HTTP 200을 대조한다.

전용 원본·VM·템플릿을 만들고 UI로 복제한 뒤 원본 참조·저장 본문이 유지되는지 API/DB와 대조한다. 새 버전 적용/이전 버전 복원은 전용 정지 VM/템플릿에서만 확인한다. 일반/다크 1440×1000·1280×720·390×844·390×480, 32px 버튼, 고정 header/footer와 body 스크롤, 키보드·포커스·권한/프로젝트·오류·중복·재시도·지연 응답을 검증한다. 전용 자원을 정리하고 기존 운영 라이브러리 응답이 같음을 검증한다. 전체 UI 통합 판정은 다음 #1392에서 수행한다.
