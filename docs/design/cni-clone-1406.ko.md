# CNI 복제와 원본 관계 설계 (#1406)

기준: Europa dc31262567f2afe5f6278fae61c1cef7e307fe3e, 누적 e437023fb7d79f1d95be74c2c0f3928b64d2a482. 2026-10-18.

## 등록 및 재시도

CNI 상세의 복제 작업은 전용 CloneCniConfiguration/MoldDialog를 연다. 현재 source UUID·이름·도메인/계정 또는 프로젝트를 고정해 listCniConfiguration으로 다시 읽고 CNI 본문·변수를 초기화한다. 새 이름/본문/변수를 편집하며 파일 가져오기·변수 추출·공용 본문 미리보기를 재사용한다. 원본과 사용 클러스터는 수정하지 않는다. 동일 본문도 새 이름의 복제로 허용한다. 원본/소유 범위는 제출 직전에 재검증한다.

registerCniConfiguration만 호출하며 CNI/원본 소유 범위를 명시한다. 이름·Base64·크기·변수는 기존 등록 검증을 사용한다. 일반 UserData 복제 코드는 변경하지 않는다. 등록 응답 후 owner/name/UUID/본문/변수를 다시 읽어 확인한다. 응답 유실/불완전 응답에서는 제출 스냅샷을 유지하고 다음 버튼은 등록 결과 조회만 한다. 확실한 부재를 확인한 경우 입력을 풀되 다시 등록하려면 추가 명시적 클릭이 필요하다. 동일 이름의 다른 결과는 충돌로 표시한다. 관계 기록 실패는 확인된 target UUID를 유지해 메타데이터만 재시도한다.

CNI 목록·등록·Annotation 목록·추가 API 권한을 모두 요구한다. addAnnotation의 선언 기본 권한은 Admin이지만 실제 동적 역할의 API 목록과 객체 ACL을 기준으로 한다. 31번 일반 계정의 소유 자원 Annotation 추가도 실제 허용됨을 확인했으며, 필요한 API가 없는 역할에는 복제를 노출하지 않는다. 관계 기록 중 권한을 잃으면 완료로 표시하지 않고 확인된 target/제출 스냅샷을 보존해 같은 세션에서 권한 복구 후 명시적으로 재시도한다. 대상/소유/라우트/인증 세션 변경은 이전 읽기 세대를 차단하고 초기화한다. 임의 권한 확대는 하지 않는다.

## 관계 스키마 v1

대상의 USER_DATA Annotation을 기존 저장 패턴으로 사용하되 일반 UserData의 MOLD_USERDATA_VERSION과 구분한다. 원본/대상 UUID, schema version=1, domainid와 projectid 또는 account 전체, before/after 바이트 수를 저장한다. 이름 길이나 한글 계정 때문에 255자 API 제한을 넘을 수 있어 원본 정보를 자르지 않고 UTF-8 JSON→Base64를 160자씩 나눈다.

각 annotation은 `MOLD_CNI_CLONE/1\n` + JSON `{t:targetUUID,n:0기반순번,c:총수,b:Base64조각}`이다. 전체 payload는 `{version:1,sourceid,targetid,owner:{domainid,projectid 또는 account},before,after}`이다. 최대32조각, 각 레코드<=255자. 완전한 순서·총수·정규 Base64·스키마·UUID·소유 범위를 확인한 집합만 관계로 인정한다. 중복 동일 조각은 허용하고 불완전/상충 집합은 성공으로 표시하지 않는다. 저장은 기존 조각을 확인해 생략하며 응답 유실도 다시 조회한다. 부분 기록은 재시도로 완성한다.

CNI 코멘트 탭은 관계 레코드를 간결한 라벨로 표시하고 내부 Base64 프로토콜을 사용자 흐름에 노출하지 않는다. 일반 코멘트/VM/UserData 표시는 유지한다. Annotation은 변경/삭제할 수 있는 보조 관계 정보이며 보안 권한이나 서버의 감사 증거를 대신하지 않는다. 후속 #1407은 이 파서와 CNI/소유 재확인을 사용하며 삭제된 원본/불완전 관계를 처리한다. 이 작업에는 이력 목록/비교 탭을 포함하지 않는다.

## UI·배포 검증

VM 공통 MoldDialog lg·폼·작은 설명표·본문 내부 스크롤·툴바/푸터를 적용한다. 원본 식별·소유 범위와 필요한 오류/진행 결과만 표시하며 상시 설명은 넣지 않는다. 4개 폭×양 테마, 긴 값/본문/변수·로딩/오류/제출·키보드, 일반 UserData/VM 회귀를 검증한다.

31번에서 계정·프로젝트 원본을 복제하고 API 요청/새 UUID/for_cks/원문 바이트/변수/관계 Annotation과 원본·기존 사용 클러스터 참조를 DB/API로 대조한다. 응답 유실과 관계 실패는 해당 브라우저 요청에 한정해 재현하며 실제 저장 여부를 따로 확인한다. 전용 자원만 정리하고 기존 CNI3/Kubernetes7/선택 DB36행을 비교한다. 변경은 UI만이며 정적 배포는 WEB-INF/META-INF/config/JAR/PID를 보존한다. Java 변경 및 Maven/전체 Cloud 빌드는 없다.
