import json,urllib.request,urllib.parse,urllib.error,http.cookiejar,time,pathlib,os,re
base=pathlib.Path(__file__).parent;os.chdir(base)
jar=http.cookiejar.CookieJar();op=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
login=json.load(op.open(urllib.request.Request('http://10.10.31.10:8080/client/api',data=urllib.parse.urlencode(dict(command='login',username=os.environ.get('MOLD_API_USER','admin'),password=os.environ['MOLD_API_PASSWORD'],domain=os.environ.get('MOLD_API_DOMAIN','/'),response='json')).encode())))['loginresponse'];key=login['sessionkey']
actor=os.environ.get('MOLD_API_USER','admin')
secret=re.compile(r'password|secretkey|apikey|sessionkey|token',re.I)
def redact(v):
 if isinstance(v,dict):return {k:('[REDACTED]' if secret.search(k) else redact(x)) for k,x in v.items()}
 if isinstance(v,list):return [redact(x) for x in v]
 return v
def api(command,**kw):
 p=dict(command=command,response='json',sessionkey=key,**kw)
 try:
  with op.open(urllib.request.Request('http://10.10.31.10:8080/client/api',data=urllib.parse.urlencode(p).encode()),timeout=90) as r:result=json.load(r)
 except urllib.error.HTTPError as e:result=json.load(e)
 with open('api-history.jsonl','a') as f:f.write(json.dumps(redact(dict(actor=actor,time=time.strftime('%FT%T%z'),command=command,params=kw,response=result)),ensure_ascii=False)+'\n')
 root=next(iter(result.values()))
 if root.get('errorcode'):raise RuntimeError(str(root))
 return root

def wait(root):
 if not root.get('jobid'):return root
 for _ in range(180):
  r=api('queryAsyncJobResult',jobid=root['jobid'])
  if r.get('jobstatus')==1:return r.get('jobresult',{})
  if r.get('jobstatus')==2:raise RuntimeError(str(r))
  time.sleep(1)
 raise RuntimeError('job timeout '+root['jobid'])
