#!/usr/bin/env python3
"""Render handbook diagrams with NanumSquare using bundled Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
HERE = Path(__file__).resolve().parent
FONT_DIR = Path.home() / 'Library/Fonts'
INK, MUTED, BORDER = '#17232F', '#475569', '#CBD5E1'

def font(size, bold=False):
    return ImageFont.truetype(str(FONT_DIR / ('NanumSquareB.ttf' if bold else 'NanumSquareR.ttf')), size)

def canvas(title, size):
    image = Image.new('RGB', size, 'white')
    draw = ImageDraw.Draw(image)
    draw.text((60,35), title, fill=INK, font=font(38,True))
    return image, draw

def wrap(draw, text, width, face):
    lines, line = [], ''
    for word in text.split(' '):
        candidate = (line + ' ' + word).strip()
        if line and draw.textlength(candidate, font=face) > width:
            lines.append(line)
            line = word
        else:
            line = candidate
    if line: lines.append(line)
    return lines

def box(draw, rect, title, lines, fill='#F5F8FB', title_size=28, body_size=24):
    x0,y0,x1,y1 = rect
    draw.rounded_rectangle(rect, radius=14, fill=fill, outline=BORDER, width=2)
    draw.text((x0+24,y0+22), title, fill=INK, font=font(title_size,True))
    y = y0+68
    face = font(body_size)
    for text in lines:
        for line in wrap(draw,text,x1-x0-48,face):
            draw.text((x0+24,y),line,fill=MUTED,font=face)
            y += body_size+10
        y += 8
    if y > y1+8: raise ValueError(f'Text overflow: {title}')

def arrow(draw,start,end):
    color='#516B80'
    draw.line((start,end),fill=color,width=4)
    x,y=end
    dx,dy=end[0]-start[0],end[1]-start[1]
    if abs(dx)>=abs(dy):
        sign=1 if dx>0 else -1
        points=[(x,y),(x-sign*14,y-8),(x-sign*14,y+8)]
    else:
        sign=1 if dy>0 else -1
        points=[(x,y),(x-8,y-sign*14),(x+8,y-sign*14)]
    draw.polygon(points,fill=color)

def create_architecture_diagram(output_path):
    image,d=canvas('Mold의 개발 위치와 실행 계층',(1600,1020))
    box(d,(60,125,500,445),'개발 PC와 브라우저',[
        'macOS · 소스 수정과 UI 테스트','로컬 UI: localhost:5050',
        '/client 요청 → CS_URL로 프록시','로그인과 API 결과를 화면에 표시'])
    box(d,(580,125,1020,445),'CCVM · Mold 관리 서버',[
        'mold.service · HTTP 8080','API 인증·권한과 Async Job',
        '호스트 배치 · 네트워크·볼륨 준비','cloud DB · 상태와 결과 저장'])
    box(d,(1100,125,1540,445),'KVM 호스트 · Mold Agent',[
        'mold-agent.service','관리 서버의 TCP 8250에 연결',
        'Command 수신 · Answer 반환','KVM wrapper → libvirt Java API'])
    arrow(d,(500,280),(580,280))
    arrow(d,(1100,265),(1020,265))
    arrow(d,(1020,345),(1100,345))
    d.text((1040,230),'연결',fill=MUTED,font=font(20))
    d.text((1030,310),'명령',fill=MUTED,font=font(20))
    box(d,(1100,520,1540,790),'libvirt · QEMU/KVM',[
        'domainCreateXML로 VM 실행','실제 도메인과 QEMU 프로세스',
        'virsh는 상태 조회·진단에 사용'],fill='#F1F6F3')
    arrow(d,(1320,445),(1320,520))
    box(d,(60,520,1020,790),'스토리지와 네트워크 서비스',[
        'Glue: Ceph 기반 스토리지 · 실제 풀과 연결 방식은 교육 환경에서 확인',
        'Primary Storage: VM 볼륨 / Secondary Storage: 템플릿·ISO·스냅샷',
        'SSVM: 보조 스토리지 작업 / CPVM: 콘솔 중계',
        'VR: 네트워크 제공자와 Offering 구성에 따라 사용'],fill='#F1F6F3')
    d.text((60,850),'Mold는 Apache CloudStack을 기반으로 자체 기능과 연동을 확장한 제품입니다.',fill=INK,font=font(26,True))
    d.text((60,900),'UI 개발 위치, 관리 서버, 물리 호스트의 실행 위치를 구분해서 명령과 로그를 확인합니다.',fill=MUTED,font=font(25))
    d.text((60,946),'교육 환경 정보와 실제 서비스명·포트·스토리지 구성을 대조하여 확인합니다.',fill=MUTED,font=font(23))
    image.save(output_path,'PNG',dpi=(180,180))

def create_sequence_diagram(output_path):
    image,d=canvas('VM 시작 요청: 접수와 백그라운드 실행',(1600,1280))
    box(d,(60,125,760,370),'UI와 요청 접수',[
        'POST /client/api · command=startVirtualMachine',
        '인증·권한·입력 검사 → Async Job 등록',
        '수락 응답: jobid = Job UUID'],title_size=30,body_size=27)
    box(d,(840,125,1540,370),'UI는 같은 Job을 조회',[
        'queryAsyncJobResult · jobid 전달',
        'jobstatus 0: 진행 중 / 1: 성공 / 2: 실패',
        '완료 결과와 VM 상태를 함께 확인'],title_size=30,body_size=27)
    arrow(d,(760,250),(840,250))
    d.text((60,420),'백그라운드 작업의 논리적 처리 흐름',fill=INK,font=font(30,True))
    d.text((60,470),'Starting 전환과 수락 응답의 관찰 순서는 스레드 실행 시점에 따라 달라질 수 있습니다.',fill=MUTED,font=font(25))
    box(d,(60,540,500,745),'1 서비스와 호스트 배치',[
        'VM 상태·조건 확인','시작 작업과 상태 전환','대상 호스트 선정'],body_size=26)
    box(d,(580,540,1020,745),'2 인프라 준비',[
        'NIC·네트워크 준비','필요한 제공자 구현','볼륨 준비'],body_size=26)
    box(d,(1100,540,1540,745),'3 호스트에서 실행',[
        'StartCommand → KVM wrapper','도메인 XML·디스크·NIC','libvirt domainCreateXML'],body_size=26)
    arrow(d,(500,640),(580,640))
    arrow(d,(1020,640),(1100,640))
    box(d,(60,825,1540,1030),'4 결과 반영과 실패 정리',[
        'Agent의 StartAnswer → 관리 서버 → VM 상태와 Job 결과 갱신',
        '실패한 단계와 정리·재시도 여부를 로그로 확인하고 같은 Job을 조회',
        'Job UUID와 내부 숫자 ID는 async_job.uuid / id로 연결'],body_size=27)
    arrow(d,(1320,745),(1320,825))
    d.text((60,1090),'jobid는 작업 수락의 증거입니다. 성공은 최종 Job 결과로 확인합니다.',fill=INK,font=font(27,True))
    d.text((60,1140),'폴링 통신 오류 ≠ 작업 실패 · Running ≠ 게스트 OS와 애플리케이션 준비 완료',fill=MUTED,font=font(26))
    d.text((60,1190),'근거: ApiServer · VirtualMachineManagerImpl · LibvirtStartCommandWrapper',fill=MUTED,font=font(24))
    image.save(output_path,'PNG',dpi=(180,180))

if __name__ == '__main__':
    create_architecture_diagram(HERE/'mold_architecture_diagram.png')
    create_sequence_diagram(HERE/'mold_sequence_diagram.png')
    print('Generated both onboarding diagrams with NanumSquare.')
