from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from html import escape
import math
ROOT=Path(__file__).resolve().parent
OUT=ROOT/'diagram-drafts'
INK='#172B45'; MUTED='#506378'; BLUE='#146CBA'; TEAL='#008878'; PURPLE='#6554B7'; LINE='#D8E2ED'
class Canvas:
 def __init__(self,h):
  self.im=Image.new('RGB',(1800,h),'white');self.d=ImageDraw.Draw(self.im);self.h=h
  self.svg=[f'<svg xmlns="http://www.w3.org/2000/svg" width="1800" height="{h}" viewBox="0 0 1800 {h}">', '<rect width="100%" height="100%" fill="white"/>']
 def rect(self,xy,fill,outline=None,r=18,width=2):
  self.d.rounded_rectangle(xy,radius=r,fill=fill,outline=outline,width=width)
  x,y,x2,y2=xy;self.svg.append(f'<rect x="{x}" y="{y}" width="{x2-x}" height="{y2-y}" rx="{r}" fill="{fill}" stroke="{outline or "none"}" stroke-width="{width}"/>')
 def line(self,xy,fill=LINE,width=2):
  self.d.line(xy,fill=fill,width=width)
  self.svg.append(f'<polyline points="{" ".join(f"{x},{y}" for x,y in xy)}" fill="none" stroke="{fill}" stroke-width="{width}"/>')
 def text(self,x,y,text,size=32,color=INK,bold=False):
  f=ImageFont.truetype(str(ROOT/'fonts'/('NanumSquareB.ttf' if bold else 'NanumSquareR.ttf')),size)
  box=self.d.textbbox((x,y),text,font=f,anchor='lt')
  assert box[0]>=0 and box[1]>=0 and box[2]<=1800 and box[3]<=self.h,(text,box,self.h)
  self.d.text((x,y),text,font=f,fill=color,anchor='lt')
  self.svg.append(f'<text x="{x}" y="{y}" dominant-baseline="text-before-edge" font-family="NanumSquare" font-size="{size}" font-weight="{700 if bold else 400}" fill="{color}">{escape(text)}</text>')
 def para(self,x,y,text,width,size=30,color=MUTED,bold=False):
  f=ImageFont.truetype(str(ROOT/'fonts'/('NanumSquareB.ttf' if bold else 'NanumSquareR.ttf')),size)
  line=''
  for word in text.split():
   candidate=(line+' '+word).strip()
   if line and self.d.textlength(candidate,font=f)>width:
    self.text(x,y,line,size,color,bold);y+=size+14;line=word
   else:line=candidate
  if line:self.text(x,y,line,size,color,bold);y+=size+14
  return y
 def arrow(self,start,end,color=BLUE,width=4):
  self.line([start,end],color,width)
  x,y=end;dx=end[0]-start[0];dy=end[1]-start[1];n=math.hypot(dx,dy);ux,uy=dx/n,dy/n
  points=[(x,y),(x-16*ux+8*uy,y-16*uy-8*ux),(x-16*ux-8*uy,y-16*uy+8*ux)]
  self.d.polygon(points,fill=color);self.svg.append(f'<polygon points="{" ".join(f"{x},{y}" for x,y in points)}" fill="{color}"/>')
 def badge(self,x,y,t,color):
  self.rect((x,y,x+48,y+48),color,r=24);self.text(x+14,y+10,t,29,'white',True)
 def icon(self,x,y,kind,color):
  if kind=='pc':
   self.rect((x,y,x+54,y+35),'white',color,5,3);self.line([(x+27,y+36),(x+27,y+48)],color,3);self.line([(x+11,y+48),(x+43,y+48)],color,3)
  elif kind=='server':
   for o in [0,25]:
    self.rect((x,y+o,x+54,y+o+20),'white',color,4,3);self.rect((x+8,y+o+7,x+13,y+o+12),color,r=2)
  else:
   self.rect((x,y,x+54,y+45),'white',color,7,3);self.rect((x+12,y+10,x+42,y+34),'white',color,4,3)
 def save(self,name):
  self.im.save(OUT/f'{name}.png',dpi=(240,240));(OUT/f'{name}.svg').write_text('\n'.join(self.svg+['</svg>']))

COLORS=[BLUE,PURPLE,TEAL]
FILLS=['#F0F7FF','#F5F2FF','#EFF9F6']

def lines_for(text,width,size=40,bold=False):
 face=ImageFont.truetype(str(ROOT/'fonts'/('NanumSquareB.ttf' if bold else 'NanumSquareR.ttf')),size)
 lines=[];line=''
 # Wrap at character boundaries when a path or identifier has no spaces.
 for char in text:
  if char=='\n':lines.append(line);line='';continue
  if line and face.getlength(line+char)>width:lines.append(line.rstrip());line=char.lstrip()
  else:line+=char
 if line:lines.append(line)
 return lines

def write_lines(c,x,y,text,width,size=40,color=MUTED,bold=False):
 for line in lines_for(text,width,size,bold):c.text(x,y,line,size,color,bold);y+=size+14
 return y

def heading(c,title,subtitle):
 c.text(72,48,title,52,INK,True)
 write_lines(c,72,127,subtitle,1656,36)

def flow(name,title,subtitle,stages,note=''):
 prepared=[]
 for label,details in stages:
  labelsize=36 if label=='build-systemvm-kvm' else 42
  titlelines=lines_for(label,405,labelsize,True)
  bodylines=sum(len(lines_for(t,1050,40)) for t in details)
  h=max(142,48+max(len(titlelines)*56,bodylines*54+max(0,len(details)-1)*8))
  prepared.append((label,details,h))
 height=220+sum(h for _,_,h in prepared)+46*(len(stages)-1)+(100+len(lines_for(note,1656,36))*50 if note else 55)
 c=Canvas(height);heading(c,title,subtitle);y=208
 for i,(label,details,h) in enumerate(prepared):
  col=COLORS[i%3];c.rect((72,y,1728,y+h),FILLS[i%3],r=20)
  c.badge(96,y+30,str(i+1),col)
  write_lines(c,170,y+30,label,405,36 if label=='build-systemvm-kvm' else 42,INK,True)
  c.line([(610,y+24),(610,y+h-24)],'#D7E1EB',2)
  yy=y+28
  for detail in details:yy=write_lines(c,646,yy,detail,1050,40)+8
  assert yy<=y+h+8,(name,label,yy,y+h)
  if i<len(prepared)-1:c.arrow((900,y+h+5),(900,y+h+40),col)
  y+=h+46
 if note:write_lines(c,72,y-15,note,1656,36,INK,True)
 c.save(name)

def catalog(name,title,subtitle,rows,note=''):
 prepared=[]
 for path,meaning in rows:
  h=max(106,40+max(len(lines_for(path,650,40,True)),len(lines_for(meaning,875,40)))*54)
  prepared.append((path,meaning,h))
 c=Canvas(235+sum(h+15 for _,_,h in prepared)+(100+len(lines_for(note,1656,36))*50 if note else 35));heading(c,title,subtitle);y=212
 for i,(path,meaning,h) in enumerate(prepared):
  col=COLORS[i%3];child=path.startswith('  ');left=220 if child else 120;c.rect((left,y,1728,y+h),FILLS[i%3],r=16)
  c.line([(88,y-15),(88,y+h+15)],LINE,3)
  if child:c.line([(170,y-15),(170,y+50),(216,y+50)],col,3)
  else:c.line([(88,y+50),(116,y+50)],col,4)
  write_lines(c,left+30,y+22,path.strip(),650-(left-120),40,col,True);write_lines(c,835,y+22,meaning,860,40)
  y+=h+15
 if note:write_lines(c,72,y+10,note,1656,36,INK,True)
 c.save(name)

def rpm():
 c=Canvas(910)
 heading(c,'RPM을 서버 역할별로 나누기','같은 빌드에서 생성한 management · agent · common · usage · ui RPM을 사용합니다')
 c.rect((540,210,1260,328),'#F0F7FF',r=18);c.text(585,246,'연구소 빌드서버의 RPM 결과',42,INK,True)
 c.line([(900,333),(900,378),(450,378),(450,420)],BLUE,4);c.arrow((450,402),(450,426),BLUE)
 c.line([(900,378),(1350,378),(1350,420)],TEAL,4);c.arrow((1350,402),(1350,426),TEAL)
 for x,title,col,fill,items in [(72,'CCVM  ·  4개',BLUE,'#F0F7FF',['cloudstack-management-*.rpm','cloudstack-common-*.rpm','cloudstack-usage-*.rpm','cloudstack-ui-*.rpm']),(944,'KVM 호스트  ·  2개',TEAL,'#EFF9F6',['cloudstack-agent-*.rpm','cloudstack-common-*.rpm'])]:
  c.rect((x,430,x+784,790),fill,r=20);c.text(x+30,462,title,46,INK,True)
  y=545
  for text in items:c.text(x+30,y,text,40,col,True);y+=56
 c.text(72,831,'common은 CCVM과 KVM 호스트에 모두 필요합니다. 서로 다른 버전을 섞지 않습니다.',36,INK,True)
 c.save('서버 역할별 RPM 구성')

def troubleshooting():
 c=Canvas(1260);heading(c,'오류 유형별 확인 순서','증상에 맞는 경로 하나를 선택합니다. 네 경로를 순서대로 모두 실행하는 것은 아닙니다.')
 cases=[('의존성 설치 오류',['Node·npm·CPU 확인','실패한 패키지와 설치 로그','CLT·같은 SHA의 CI 결과']),('로그인·API 오류',['프록시 설정·포트 연결','HTTP 응답·쿠키·세션','계정 Domain·Role 확인']),('SSH 키 불일치',['대상 IP·재설치 이력','새 호스트 키 지문 대조','확인 후 해당 키만 갱신']),('화면 변경이 안 보임',['접속 URL·소스 폴더','한국어·번역 파일 응답','번역 키 사용 위치·새로고침'])]
 for i,(title,steps) in enumerate(cases):
  x=72+(i%2)*850;y=225+(i//2)*470;col=COLORS[i%3];c.rect((x,y,x+804,y+423),FILLS[i%3],r=20);c.text(x+28,y+28,title,44,INK,True)
  yy=y+112
  for k,s in enumerate(steps):
   write_lines(c,x+30,yy,f'{k+1}  {s}',745,38);yy+=91
   if k<2:c.arrow((x+405,yy-26),(x+405,yy-4),col,3)
 c.text(72,1174,'실패한 명령과 첫 오류를 기록하고 원인을 해결한 뒤 다시 확인합니다.',38,INK,True);c.save('오류 유형별 진단 흐름')

def jobs():
 c=Canvas(1160);heading(c,'비동기 Job 결과를 확인하는 순서','jobid는 작업 접수 확인입니다. 성공 여부는 같은 Job의 최종 결과로 확인합니다.')
 c.rect((72,225,857,423),'#F0F7FF',r=18);c.text(100,255,'API가 처음부터 오류를 반환',42,INK,True);c.text(100,326,'응답·인증·권한·입력값 확인',40,MUTED)
 c.rect((930,225,1728,423),'#F5F2FF',r=18);c.text(958,255,'jobid가 반환됨',42,INK,True);c.text(958,326,'queryAsyncJobResult로 같은 Job 조회',36,MUTED)
 c.arrow((1328,430),(1328,485),PURPLE)
 c.rect((72,498,1728,609),'#F3F7FA',r=16);c.text(105,532,'조회 응답의 jobstatus에 따라 다음 확인을 선택',42,INK,True)
 for i,(num,title,lines,col,fill) in enumerate([('0','진행 중',['동일 Job을 계속 조회'],BLUE,'#F0F7FF'),('1','성공',['jobresult와 실제 자원','상태를 함께 확인'],TEAL,'#EFF9F6'),('2','실패',['jobresult.errortext와','서버 로그 확인'],'#B63D46','#FCEEEE')]):
  x=72+i*565;c.arrow((x+262,615),(x+262,660),col);c.rect((x,670,x+525,905),fill,r=18);c.text(x+30,700,f'{num}  {title}',46,col,True)
  yy=783
  for t in lines:c.text(x+30,yy,t,38,MUTED);yy+=54
 c.rect((72,950,1728,1084),'#F3F7FA',r=16);c.text(100,980,'조회 중 통신 오류  ·  결과를 받지 못한 상태',40,INK,True);c.text(100,1034,'연결을 복구한 뒤 같은 Job을 재조회하고 VM 상태도 확인합니다.',38,MUTED)
 c.save('비동기 Job 결과 확인 흐름')

def states():
 c=Canvas(820);heading(c,'VM 시작의 상태 전이','Job 결과와 DB 상태, 호스트 도메인 상태를 같은 시각의 자료로 비교합니다.')
 for x,label,col,fill in [(72,'Stopped',BLUE,'#F0F7FF'),(675,'Starting',PURPLE,'#F5F2FF'),(1278,'Running',TEAL,'#EFF9F6')]:
  c.rect((x,263,x+450,427),fill,r=20);c.text(x+75,315,label,52,col,True)
 c.arrow((530,345),(665,345),BLUE);c.text(545,278,'시작 요청',32,BLUE,True)
 c.arrow((1133,345),(1265,345),TEAL);c.text(1145,278,'시작 성공',32,TEAL,True)
 c.line([(900,435),(900,565),(295,565)],'#B63D46',4);c.arrow((295,565),(295,435),'#B63D46');c.text(335,503,'시작 실패  ·  Starting에서 Stopped로 복귀',38,'#B63D46',True)
 c.text(72,653,'실패한 단계와 자원 정리·재시도 여부를 관리 서버와 Agent 로그로 확인합니다.',36,INK,True)
 c.text(72,718,'Running은 VM 실행 상태입니다. 게스트 OS·네트워크·앱 준비 완료는 별도로 확인합니다.',35,MUTED)
 c.save('VM 시작 상태 전이')

def deployment():
 c=Canvas(1130);heading(c,'소스 수정부터 서버 반영까지','연구소 Rocky Linux 9 빌드서버를 사용하는 기본 개발 흐름')
 stages=[('Mac · 소스 확인',['Maven 빌드와 필요한 테스트','UI 화면 확인']),('Mac · 커밋과 push',['검토한 변경을','원격 저장소에 전달']),('빌드서버 · RPM',['/root/build/europa에서','같은 커밋으로 빌드']),('대상 서버 · 설치',['CCVM·KVM 호스트 역할별로','RPM을 나누어 반영']),('대상 서버 · 기능 확인',['서비스 상태와','수정한 기능의 실제 결과']),('실습 기록',['커밋·RPM·적용 서버와','확인 결과 기록'])]
 for i,(label,details) in enumerate(stages):
  x=72+(i%3)*570;y=225+(i//3)*415;col=COLORS[i%3]
  c.rect((x,y,x+516,y+310),FILLS[i%3],r=18);c.badge(x+24,y+25,str(i+1),col)
  write_lines(c,x+24,y+95,label,468,40,INK,True)
  yy=y+198
  for detail in details:yy=write_lines(c,x+24,yy,detail,468,35)+7
  assert yy<y+315,(label,yy,y)
  if i%3<2:c.arrow((x+522,y+155),(x+560,y+155),col)
 # A wrap connector carries step 3 to step 4 without implying that rows run in reverse.
 c.line([(1470,542),(1470,590),(330,590),(330,625)],PURPLE,4);c.arrow((330,615),(330,633),PURPLE)
 c.text(72,1010,'UI 파일만 수동 반영할 때는 9-5절의 dist 업로드 절차를 따릅니다.',38,INK,True)
 c.save('소스 수정부터 서버 반영까지')

def main():
 OUT.mkdir(exist_ok=True)
 flow('도메인 계정 사용자 관계','도메인과 계정 사용자 관계','조직 범위 · 자원 소유 · 로그인 주체를 구분합니다',[
  ('Domain · 도메인',['계정의 조직 범위와 상하위 계층']),('Account · 계정',['자원 소유와 역할의 기준']),('User · 사용자',['계정에 소속된 로그인 주체'])],'Role은 API 권한, Project는 구성원이 공유하는 자원의 작업 범위입니다.')
 flow('인프라 배치 계층','인프라스트럭처의 배치 계층','Zone은 인프라스트럭처 메뉴 안에 있는 구성 항목입니다',[
  ('Zone',['가장 큰 배치 영역']),('Pod',['Zone 안의 서버 배치 단위']),('클러스터',['같은 배치 그룹으로 관리하는 호스트']),('호스트',['VM을 실행하는 KVM 물리 호스트'])])
 flow('Git 작업과 PR 흐름','Git 작업과 PR 흐름','개발 PC Mac의 저장소에서 변경을 준비하고 검토합니다',[
  ('기준 브랜치 최신화',['기준 소스와 기존 변경을 확인']),('작업 브랜치 생성',['기능 또는 수정 목적의 개인 브랜치']),('소스 수정과 검사',['변경 범위를 확인하고 필요한 검사 수행']),('커밋과 push',['필요한 파일만 커밋하여 원격 저장소로 전달']),('PR 작성과 검토',['변경 이유·검사 결과·화면 또는 로그 기록'])])
 flow('로컬 Maven 빌드 선택','로컬 Maven 빌드와 수정 폴더 선택','처음에는 전체 빌드로 내부 의존성을 준비하고 수정한 모듈을 다시 빌드합니다',[
  ('전체 소스 빌드',['developer · systemvm · noredist 구성','clean install로 로컬 Maven 저장소에 등록']),('수정 폴더 선택',['KVM · server · api · agent · usage · utils','-pl에 저장소 루트 기준 모듈 경로 지정']),('선택한 모듈 빌드',['내부 의존 모듈도 필요하면 -am 추가']),('결과 확인',['BUILD SUCCESS와 해당 target/ 파일 확인','테스트 생략 시 필요한 테스트는 별도로 실행']),('커밋과 push',['최종 RPM은 연구소 빌드서버에서 생성'])])
 catalog('Mold 소스 폴더 구조','Mold 소스 폴더와 역할','mold-europa2 저장소 루트의 주요 디렉터리',[
  ('ui/','Vue 3 웹 콘솔 소스'),('api/','API 요청과 응답 Command 클래스'),('server/','핵심 업무 로직과 Manager·Service 구현'),('engine/','VM·스토리지·네트워크 오케스트레이션'),('core/','기본 데이터 모델·DTO·내부 유틸리티'),('plugins/','하이퍼바이저와 스토리지 확장 플러그인'),('  hypervisors/kvm/','plugins/ 아래 KVM 하이퍼바이저 통신 코드'),('  storage/volume/','plugins/ 아래 주 스토리지 볼륨 연동'),('agent/','KVM 호스트의 Mold Agent 데몬'),('developer/','사내 빌드·릴리즈 정책과 개발 가이드'),('.github/','GitHub Actions workflow 정의')])
 catalog('UI 소스 폴더 구조','UI 소스 폴더와 역할','ui/ 안에서 화면 · API 호출 · 메뉴 정의 · 번역 파일을 찾습니다',[
  ('src/api/index.js','API 호출 래퍼 callAPI · getAPI · postAPI'),('src/components/','재사용 가능한 공통 컴포넌트'),('src/router/','화면 라우터 구성'),('src/config/section/','compute.js 등 메뉴별 뷰 정의'),('src/views/','각 화면 페이지 컴포넌트'),('src/views/compute/','DeployVM.vue 등 인스턴스 목록과 상세 화면'),('src/locales/','i18n 초기화와 언어 로딩'),('public/locales/ko_KR.json','한국어 번역 키와 문구'),('.env.local','개발용 프록시 CS_URL 설정'),('package.json','UI 의존성과 실행 스크립트')])
 flow('화면에서 백엔드 코드 추적','화면에서 백엔드 코드를 찾는 3단계','브라우저의 실제 요청을 기준으로 API Command와 서비스 구현을 연결합니다',[
  ('브라우저 F12',['Network 탭에서 API 이름 확인','예: command=startVirtualMachine']),('api/ 모듈 검색',['@APICommand의 API 이름으로 검색','StartVMCmd.java 확인']),('server/ 서비스 추적',['_userVmService.startVirtualMachine(this)','UserVmManagerImpl.java의 실제 로직 확인'])],'동일한 API에 사용자용·관리자용 Command가 있을 수 있으므로 실제 요청과 클래스도 대조합니다.')
 flow('Codex 개발 작업 흐름','Codex를 이용한 개발 작업 흐름','확인할 수 있는 작은 목표를 정하고 코드와 실행 결과를 직접 검토합니다',[
  ('현재 상태 확인',['프로젝트 폴더·브랜치·SHA·기존 변경 확인']),('읽기 전용 탐색',['실제 파일과 호출 관계를 찾아 근거 확인']),('변경 범위 결정',['목표·수정 파일·검사 방법을 정리']),('구현',['계획한 범위의 소스를 수정']),('검사',['필요한 검사를 실제로 실행']),('변경 차이 검토',['설명과 코드 diff, 실행 결과를 대조']),('커밋과 PR 기록',['변경 이유·검사 결과·미실행 항목 기록'])])
 flow('소스 수정부터 서버 반영까지','소스 수정부터 서버 반영까지','연구소 Rocky Linux 9 빌드서버를 사용하는 기본 개발 흐름',[
  ('Mac\n소스 수정과 확인',['Maven 빌드·필요한 테스트·UI 확인']),('Mac\n커밋과 push',['검토한 변경을 원격 저장소에 전달']),('빌드서버\n소스 갱신과 RPM',['/root/build/europa에서 같은 커밋으로 빌드']),('CCVM·KVM\n파일 전달과 설치',['서버 역할별로 RPM을 나누어 반영']),('대상 서버\n기능 확인',['서비스 상태와 수정한 기능의 실제 결과']),('실습 기록',['커밋·RPM·적용 서버·확인 결과 기록'])])
 deployment();rpm()
 catalog('UI dist 폴더 구성','UI 빌드 결과 dist 폴더','dist 폴더 자체가 아니라 안의 파일과 폴더를 업로드합니다',[
  ('index.html','화면 시작 파일 · 업로드'),('config.json','기존 운영 설정 보존 · 업로드 제외'),('js/','JavaScript 파일 · 업로드'),('css/','화면 스타일 · 업로드'),('assets/','이미지와 글꼴 · 업로드'),('locales/','언어 파일 · 업로드'),('그 밖의 빌드 파일','필요한 빌드 파일 포함 · .DS_Store 제외')])
 flow('UI dist 수동 반영 흐름','UI dist 파일을 CCVM에 반영하기','대상 폴더 /usr/share/cloudstack-management/webapp/',[
  ('Mac · UI 빌드',['npm run build로 dist 생성']),('dist 확인',['빌드 성공과 이번에 생성된 파일 확인']),('CCVM\n기존 파일 정리',['기존 UI 백업 후 WEB-INF와 config.json 보존','그 외 파일과 폴더만 삭제']),('새 파일 업로드',['dist 내부 내용 중 config.json 제외','CCVM의 webapp/에 업로드']),('화면 확인',['로그인과 수정 화면 확인 · 필요 시 캐시 갱신'])],'WEB-INF와 기존 config.json을 삭제하거나 새 빌드 파일로 덮어쓰지 않습니다.')
 troubleshooting()
 flow('인증과 권한 확인 흐름','인증과 권한의 세 가지 확인','로그인 성공만으로 모든 VM이나 API를 사용할 수 있는 것은 아닙니다',[
  ('인증',['호출자는 누구인가 · 세션 또는 서명된 요청','ApiServer의 요청 검증']),('API 역할 권한 RBAC',['이 Role이 해당 API를 허용하는가','API 접근 검사와 RolePermission']),('자원 접근 권한 ACL',['이 호출자가 해당 VM을 조작할 수 있는가','API의 ACL과 서비스의 checkAccess'])],'API와 서비스에서 관련 검사가 수행됩니다. 역할과 자원의 소유 범위를 함께 확인합니다.')
 jobs();states()
 flow('SystemVM 템플릿 생성과 반영 흐름','SystemVM 템플릿 생성과 반영','이미지를 변경하는 업무가 있을 때 진행하는 별도 실습입니다',[
  ('Linux · 템플릿 생성',['tools/appliance/build.sh로 KVM 이미지 생성','qcow2.bz2 결과 확인']),('Mold · 템플릿 등록',['이미지 → 템플릿 · URL 또는 로컬 업로드','SSVM·CPVM: SYSTEM / VR: ROUTING']),('준비 완료 확인',['대상 Zone에서 Ready 상태 확인']),('글로벌 설정 확인',['VR: router.template.kvm을 템플릿 이름과 일치','이미지 버전·내부 다운로드 허용 범위 확인']),('새 시스템 VM에 반영',['정한 시간에 서비스 재시작과 VM 재생성','네트워크·콘솔·다운로드 기능 확인'])],'템플릿 등록만으로 실행 중인 시스템 VM 이미지가 바뀌지는 않습니다.')
 flow('GitHub Actions 개발 Release 흐름','GitHub Actions의 개발 Release 흐름','연구소 빌드서버 작업과 구분되는 자동화 빌드의 내부 실행 순서',[
  ('metadata',['소스 SHA와 제품·Release 메타데이터 준비']),('build-systemvm-kvm',['KVM SystemVM 이미지 빌드']),('build-rpm',['Maven·UI 패키징과 RPM 생성']),('publish-release',['개발 Release와 결과 파일 게시']),('Release 결과 확인',['필수 작업 성공과 DEV-RELEASE.txt 소스 SHA','대상 서버용 RPM과 이미지 파일 확인'])],'RPM 작업 하나의 성공만으로 전체 Release 완료를 판단하지 않습니다.')
 print('Created 18 handbook diagrams with PNG and editable SVG sources.')

if __name__=='__main__':main()
