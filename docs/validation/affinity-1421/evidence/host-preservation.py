import pathlib,json,hashlib,subprocess,concurrent.futures,os,sys,time
phase=sys.argv[1]
def read(host):
 raw=subprocess.check_output(['sshpass','-e','ssh','-n','-p','22','-o','StrictHostKeyChecking=yes','root@'+host,'LC_ALL=C timeout 15 virsh list --name --state-running'],stdin=subprocess.DEVNULL,env={**os.environ,'SSHPASS':os.environ['HOST_PASSWORD']},text=True,timeout=45)
 return host,sorted(x for x in raw.splitlines() if x)
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as p:current=dict(p.map(read,['10.10.31.1','10.10.31.2','10.10.31.3']))
pathlib.Path('hosts-'+phase+'-private.json').write_text(json.dumps(current,indent=2));out={h:dict(runningDomains=len(v),sha256=hashlib.sha256(json.dumps(v,separators=(',',':')).encode()).hexdigest()) for h,v in current.items()}
if phase=='after':
 old=json.load(open('hosts-before-private.json'));assert old==current,'Hypervisor running domain set changed; inspect original evidence'
pathlib.Path('hosts-'+phase+'-preservation.json').write_text(json.dumps(dict(time=time.strftime('%FT%T%z'),hosts=out,unchanged=phase=='after'),indent=2));print(json.dumps(out))
