# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
"""Publish reviewable Korean Markdown after local qualification succeeds."""
import json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parent
REPO='ablecloud-team/ablestack-cloud'
BASE='https://github.com/dhslove/ablestack-cloud/blob/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/'
RAW='https://raw.githubusercontent.com/dhslove/ablestack-cloud/refs/heads/dhslove/epic-1381-userdata-library/docs/validation/userdata-1392-20261016/'
def gh(*args):return subprocess.check_output(['gh',*args,'-R',REPO]).decode('utf-8')
def write(name,body):
 p=ROOT/name;p.write_text(body.rstrip()+'\n',encoding='utf-8',newline='\n');return str(p)
q=json.loads((ROOT/'qualification.json').read_text(encoding='utf-8'))
assert q['matrix_count']==q['matrix_passed']==256 and q['unit_tests']==317
assert all(v==0 for v in q['active_after'].values())
r=json.loads((ROOT/'runtime-final.json').read_text(encoding='utf-8'))
report=f'[최종 보고서]({BASE}report.ko.md) · [점검표]({BASE}audit-matrix.ko.md) · [정량/화면]({BASE}qualification.json) · [배포/해시]({BASE}runtime-final.json) · [정리]({BASE}db-after.json)'
summary=f'''#1392 설계·구현·31번 배포·기능 검증·정리를 완료했습니다. 연결 결함 #1394도 수정·재검증했습니다.

- 목록/상세7탭/8종 모달, VM 생성·재설정과 템플릿/ISO 연계를 검증했습니다. 20px 필드·16px 섹션 간격, VM과 같은32px Footer를 적용했습니다.
- 사용자 지적에 따라 VM 표준에 없는 목록 검색 설명, 참조/본문/버전 탭의 일반 설명·상태 합계와 대화상자의 정책/조작 안내 블록을 제거했습니다. 검색·필터·표·페이지·실제 오류/결과·작업 영향은 유지하고 최종 배포본의 전체 화면을 재수집했습니다. 코멘트 작성자·시각·공개 범위의 대비도 보완했습니다.
- 사용자 첨부 화면의 메뉴/Breadcrumb 사이 세로선도 VM과 대조했습니다. 가로 넘침을 막는 여백을 유지하면서 Breadcrumb 양옆 테두리를 제거했습니다. 모바일 일괄 작업 도구는 기존 버튼·검색창을 줄바꿈하고 비활성 버튼 대비를 기존 테마 토큰으로 보완했습니다.
- 일반/다크×8개 화면 조건의 **256개 화면/모달 검사 통과**. 텍스트≥4.5, 필수 경계/포커스≥3, 중앙 오차≤1px, Header/Footer 스크롤 좌표차0px입니다. 200%는720×450 CSS/dpr2 에뮬레이션입니다.
- 실제 wizard VM 생성·저장 UUID/변수/3075바이트·사용VM1개,11개 사용VM의10+1 페이지, 실제 사용중431, 일괄 부분 성공, 오류/출처 메모만 재시도·코멘트 보존을 UI/API/DB로 대조했습니다.
- 관리자/일반 사용자/도메인 관리자/프로젝트/제한 API 역할을 대표 실제 경로로 확인했습니다. 전체 역할×화면×테마 조합을 실행했다는 의미는 아닙니다.
- 공통 프로젝트 정상 취소·실패 롤백·재시도, 이전 조회 결과, 교차 메뉴 탭 URL 회귀를 수정·검증했습니다. 집중 Jest **15 suites/317 tests**, 변경 파일 ESLint, UI production build 통과입니다.
- 배포 UI `{r['source']}`, app `{next(iter(r['app_files']))}`, 정적{r['static_files_verified']}개·기존JAR/12클래스/WEB-INF/config/PID 보존, mold active,/client/200입니다.
- API/DB 전용 활성 잔여0, 기존 운영 라이브러리 전체 응답 동일. 전체 Cloud CI/호스트 배포/게스트 실행 재시험/PR 병합은 이번 작업에 포함하지 않았습니다.

{report}

![불필요한 설명과 세로선을 제거한 최종 다크 모드 목록]({RAW}delivered-standard-dark-list.png)

Epic 구현/최종 UI 검토는11/11입니다. 다음은 Draft PR #1393 최종 리뷰·병합과 Epic #1381 종료입니다.'''
old=gh('issue','view','1394','--json','body');defect=json.loads(old)['body']
defect=defect.replace('- [ ]','- [x]')
defect+='\n\n추가 재현/수정: 기본 보기 projectid=0/431, 코멘트 실패/새 초안 손실, 권한 없는 코멘트 탭, 부분 연결 오류의 화면 밖 위치, 떠나는 VM/이미지 화면의 목적지 탭 URL 덮어쓰기, 기존 다크 CSS에 덮이는 체크박스/라디오 경계와 키보드 포커스.\n\n'+report
defect+='\n\n최종 사용자 지적 반영: VM에 없는 검색 설명 및 탭/대화상자의 일반 설명 블록 제거. 코멘트 작성자/시각/공개 범위의 양성 데이터 대비 재현·수정과 최종 번들의 전체 화면 재검증을 포함한다.'
defect+='\n\n추가 최종 수정: 사용자 첨부 화면에서 드러난 Breadcrumb 1px 세로 테두리(라이브러리 x256, VM x244) 제거, 모바일 일괄 선택 시 도구 영역129px 가로 넘침과 다크 비활성 보내기4.252:1 대비를 보완했다. before-final-corrections는 실패 재현이며 최종 합격 집계에서 제외한다.'
defect+='\n\n모바일 선택 해제 후 실제 Breadcrumb189px와 Affix/placeholder229px의40px 차이를 확인해 기존 필터의 key 갱신 패턴을 라이브러리 선택 수/기기에만 적용했다. 최종 선택/해제 동작과 높이 일치도 별도로 확인했다.'
gh('issue','edit','1394','--body-file',write('defect-final.ko.md',defect))
gh('issue','comment','1394','--body-file',write('completion-comment.ko.md',summary))
gh('issue','close','1394','--reason','completed')
body=json.loads(gh('issue','view','1392','--json','body'))['body']
body=body.replace('- [ ]','- [x]').replace('현재 작업은 이슈 보완/등록이며 구현·빌드·배포를 수행하지 않는다.','등록 시점의 계획에서 후속 구현·배포·검증으로 진행했다.')
body=body.replace('이 이슈는 최종 검토 작업을 등록하는 것이다. 현재 구현·배포 또는 최종 적합성 검증이 완료되었다는 의미가 아니다.','등록 후 설계·구현·배포·최종 적합성 검증까지 진행했고, 완료 결과와 적용 범위를 아래에 연결했다.')
body+='\n\n## 최종 결과\n\n'+summary
gh('issue','edit','1392','--body-file',write('issue-final.ko.md',body))
gh('issue','comment','1392','--body-file',str(ROOT/'completion-comment.ko.md'))
gh('issue','close','1392','--reason','completed')
pr=json.loads(gh('pr','view','1393','--json','body'))['body']
table=pr.split('## 완료 범위별 근거',1)[1].split('앞선 이슈',1)[0].strip()
table+='\n| #1392 / #1394 | 전체 UI 표준·간격/대비·공통 경로 회귀 및 최종 통합 검증 | [보고서]('+BASE+'report.ko.md) |\n'
prbody=f'''## 해결한 문제와 결과

사용자 데이터 라이브러리 Epic #1381의 등록·선택·재설정·참조/삭제·본문·VM 저장본·복제 버전과 최종 UI 표준을 개선한다. UD-01 #1382~UD-11 #1392와 최종 감사 결함 #1394의 구현·31번 배포·범위 내 검증을 완료했다. **11/11**, Epic은 최종 리뷰/병합을 위해 OPEN, PR은 Draft를 유지한다.

기존 ListView/ResourceLayout/InfoCard/ResourceView/MoldDialog와 UserDataSelection·테마 토큰을 재사용한다. 서버 검색/페이지·소유 범위, 실패 정합성·입력 보존·부분 복구, 사용VM/템플릿/ISO 전용 탭, 원문/Base64 복사·다운로드, VM 저장본 비교와 독립 새 UUID 버전을 제공한다. 기존 참조 VM을 자동 변경하지 않으며 출처 댓글은 변경 가능한 참고 기록이다.

## UD-11 최종 개선

등록/복제20px 필드·삭제/버전16px 섹션·VM32px Footer, 본문 업데이트 도구, 일반/다크 링크·선택/경계·placeholder/hover/focus 대비를 맞췄다. 템플릿/ISO의 기존 본문 그리드를 유지한 공통 모달·서버 선택기·정확한 templateid·연결 완료 대기/부분 실패 복구를 적용했다.

VM 표준에 없는 목록의 검색/정렬 설명과 참조·본문·버전 탭 및 관련 대화상자의 일반 안내 블록은 제거했다. 검색·필터·표·페이지와 실제 작업 상태/오류/영향을 남겼다. 마지막 표시 수정 이후 최종 정적 번들에서 전체256개 화면을 다시 수집했다. 실제 CRUD·권한/장애·VM 생성 대조는 이전 기능 수정 단계의 증거로 명시한다.

프로젝트 API→프로젝트→테마→라우팅을 기다리고 기본id0을 정규화했다. 대시보드는 정상 취소를 처리하고 옛 범위 응답을 차단한다. 코멘트의 실패 초안과 제출 이후 새 초안, 제한 API 탭과 잘못된 URL을 처리하며 교차 메뉴 이동의 이전 탭 설정이 새 URL을 덮지 않도록 수정했다.

## 최종 검증과 배포

{summary.split('Epic 구현/최종 UI 검토는11/11입니다.')[0]}

초기 재현과 최종 통과 자료를 분리했다. 전체 Cloud/GitHub Actions CI 결과가 없으며 단위 검사·UI 빌드·실환경 범위 내 통과를 전체 CI 성공으로 확대하지 않는다. 앞선 공급자/게스트 검증은 #1386 보고서의 범위이며 이번에는 정지 VM의 생성/저장 경로를 검증했다.

## 완료 범위별 근거

{table}

## 다음 단계

추가 구현 하위 이슈는 없다. PR 최종 리뷰·명시적인 병합 요청 후 Epic #1381을 마무리한다. 이 PR에서 병합을 수행하지 않았다.
'''
gh('pr','edit','1393','--title','[Epic #1381][Europa] 사용자 데이터 라이브러리 기능·VM UI 표준 개선 및 최종 검증','--body-file',write('pr-final.ko.md',prbody))
epic=json.loads(gh('issue','view','1381','--json','body'))['body']
epic=epic.replace('- 현재 구현 진행은 **10/11**이다. UD-11 #1392와 이 Epic은 계속 OPEN이다. 전체 UI 최종 표준 검토는 UD-11 #1392에서 수행한다.','- 현재 구현 진행은 **11/11**이며 #1392 최종 UI 검토와 #1394 수정·검증을 완료했다. 이 Epic은 PR #1393 최종 리뷰/병합을 위해 OPEN이다.')
# Earlier reports and dates remain historical evidence. Current criteria are linked below.
epic=epic.replace('- [ ]','- [x]')
epic=epic.replace('이번 검증은 전용 정지 VM의 UI 재설정과 Cloud 저장/API/공급자 계약까지이며 게스트 실행 검증을 완료로 계산하지 않는다.','신규 생성/저장 경로는 #1392, 지원 공급자/게스트 실행은 UD-05 #1386의 실제 검증 보고서로 확인했다. #1392에서 게스트 실행을 다시 수행했다는 의미는 아니다.')
epic+='\n\n## UD-11 최종 결과 / 2026-10-16~17\n\n'+summary+'\n\n공급자·게스트 실행 기준은 UD-05 #1386의 실제 VR/ConfigDrive·게스트 파일/실행 마커 보고서와 이번 wizard 실제 생성/저장 검증을 연결했다. 현재 작업에서 게스트 실행을 재시험하지 않았다. 앞선 UD-09/10의10/11·다음#1392 표기는 당시 진행 이력이다.'
gh('issue','edit','1381','--body-file',write('epic-final.ko.md',epic))
gh('issue','comment','1381','--body-file',str(ROOT/'completion-comment.ko.md'))
print('Published: #1394 CLOSED, #1392 CLOSED; Epic/PR updated, no merge')
