# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.

# Read-only bounded AS-08 observation collector. Credentials arrive once on stdin.
import os,sys,json,time,datetime,threading,subprocess,hashlib,shutil,http.cookiejar,urllib.request,urllib.parse,urllib.error
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
cfg=json.loads(sys.stdin.readline()); password=cfg.pop('password');dbpassword=cfg.pop('dbPassword');out=Path(cfg['output']);out.mkdir(mode=0o700,parents=True,exist_ok=True);os.chmod(out,0o700)
lock=threading.RLock();stop=threading.Event();started=cfg['startEpoch'];deadline=cfg['deadlineEpoch'];last_http=None
status={'phase':'WAITING','pid':os.getpid(),'startEpoch':started,'deadlineEpoch':deadline,'cluster':cfg['cluster'],'http':{},'apiSamples':0,'apiErrors':0,'dbSamples':0,'dbErrors':0,'anomalyCount':0,'maxHttpGapSeconds':0,'maxApiGapSeconds':0}
def stamp():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def save():
 with lock:
  status['updatedAt']=stamp();tmp=out/'status.tmp';tmp.write_text(json.dumps(status,indent=2));tmp.replace(out/'status.json')
def record(kind,data):
 with lock:
  with (out/(kind+'.jsonl')).open('a') as f:f.write(json.dumps({'at':stamp(),'epoch':time.time(),**data},ensure_ascii=False)+'\n')
def anomaly(code,**details):
 with lock:status['anomalyCount']+=1
 record('anomalies',{'code':code,**details})
class Cloud:
 def __init__(self):
  self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()));self.key=None;self.login()
 def request(self,p):
  req=urllib.request.Request('http://127.0.0.1:8080/client/api',data=urllib.parse.urlencode({'response':'json',**p}).encode())
  try:
   with self.opener.open(req,timeout=10) as r:raw=json.load(r)
  except urllib.error.HTTPError as exc:raw=json.load(exc)
  val=next(iter(raw.values())) if len(raw)==1 else raw
  if isinstance(val,dict) and (val.get('errorcode') or val.get('cserrorcode')):raise RuntimeError('API error '+str(val.get('errorcode'))+' '+str(val.get('errortext')))
  return val
 def login(self):self.key=self.request({'command':'login','username':'admin','password':password,'domain':'/'})['sessionkey']
 def call(self,cmd,**p):
  try:return self.request({'command':cmd,'sessionkey':self.key,**p})
  except RuntimeError as exc:raise RuntimeError(cmd+': '+str(exc))
def http_probe(t):
 before=time.monotonic();r={'target':t['name']}
 try:
  with urllib.request.urlopen(t['url'],timeout=3) as response:
   body=response.read(65536);r.update(status=response.status,length=len(body),sha256=hashlib.sha256(body).hexdigest());r['ok']=response.status==200 and r['sha256']==t['bodySha256']
 except Exception as exc:r.update(ok=False,error=type(exc).__name__)
 r['latencyMs']=round((time.monotonic()-before)*1000,2);return r

def http_loop():
 global last_http
 tick=time.monotonic()
 with ThreadPoolExecutor(max_workers=len(cfg['httpTargets'])) as pool:
  while time.time()<=deadline+5 and not stop.is_set():
   now=time.time()
   if last_http:
    gap=now-last_http
    with lock:status['maxHttpGapSeconds']=max(status['maxHttpGapSeconds'],round(gap,3))
    if gap>10:anomaly('HTTP_COLLECTION_GAP',gapSeconds=gap)
   last_http=now
   for r in pool.map(http_probe,cfg['httpTargets']):
    with lock:
     counts=status['http'].setdefault(r['target'],{'requests':0,'ok':0,'failed':0});counts['requests']+=1;counts['ok' if r['ok'] else 'failed']+=1
    record('http',r)
    if not r['ok']:anomaly('HTTP_FAILURE',**r)
   save();tick+=5;stop.wait(max(0,tick-time.monotonic()))

def command(args,timeout=15,env=None):
 r=subprocess.run(args,capture_output=True,text=True,timeout=timeout,env=env);return {'returncode':r.returncode,'stdout':r.stdout.strip()}
def process_metrics():
 pid=command(['systemctl','show','mold','-p','MainPID','--value'])['stdout'];d={'service':command(['systemctl','is-active','mold'])['stdout'],'pid':pid}
 if d['service']!='active' or pid!=cfg['managementPid']:anomaly('MANAGEMENT_PROCESS_CHANGED',**d)
 if pid.isdigit() and pid!='0':
  d['status']={line.split(':')[0]:line.split(':')[1].strip() for line in Path('/proc/'+pid+'/status').read_text().splitlines() if line.startswith(('VmRSS:','VmSize:','Threads:'))};d['fds']=len(list(Path('/proc/'+pid+'/fd').iterdir()))
  jstat=shutil.which('jstat');d['jstatGc']=command([jstat,'-gc',pid]) if jstat else {'unavailable':True}
 metric=cloud.call('listManagementServersMetrics');d['jvmMetrics']=[{k:v for k,v in x.items() if k in ['name','state','heapmemoryused','heapmemorytotal','sessions','threadstotalcount','threadsblockedcount','threadsrunnablecount','collectiontime']} for x in metric.get('managementserver',[])];assert d['jvmMetrics'] and d['jvmMetrics'][0].get('heapmemoryused') is not None,'JVM metrics unavailable'
 d['jarSha256']=hashlib.sha256(Path(cfg['managementJar']).read_bytes()).hexdigest();d['indexSha256']=hashlib.sha256(Path('/usr/share/cloudstack-management/webapp/index.html').read_bytes()).hexdigest()
 if d['jarSha256']!=cfg['managementJarSha256'] or d['indexSha256']!=cfg['indexSha256']:anomaly('DEPLOYMENT_CHANGED')
 return d

def db_sample():
 names=','.join("'"+g['id']+"'" for g in cfg['groups']);q="""SELECT JSON_OBJECT('kind','group','uuid',g.uuid,'state',g.state,'operation',g.operation_uuid,'members',(SELECT COUNT(*) FROM autoscale_vmgroup_vm_map m WHERE m.vmgroup_id=g.id),'lbMembers',(SELECT COUNT(*) FROM load_balancer_vm_map l WHERE l.load_balancer_id=g.load_balancer_id AND l.revoke=0 AND l.removed IS NULL)) FROM autoscale_vmgroups g WHERE g.uuid IN (IDS);
 SELECT JSON_OBJECT('kind','statistics','group',g.uuid,'rows',COUNT(s.id),'oldest',MIN(s.created),'newest',MAX(s.created)) FROM autoscale_vmgroups g LEFT JOIN autoscale_vmgroup_statistics s ON s.vmgroup_id=g.id WHERE g.uuid IN (IDS) GROUP BY g.uuid;
 SELECT JSON_OBJECT('kind','cleanupConfig','name',name,'value',value) FROM configuration WHERE name LIKE 'autoscale%cleanup%';
 SELECT JSON_OBJECT('kind','orphans','vmMaps',(SELECT COUNT(*) FROM autoscale_vmgroup_vm_map m JOIN autoscale_vmgroups g ON g.id=m.vmgroup_id JOIN vm_instance v ON v.id=m.instance_id WHERE g.uuid IN (IDS) AND v.removed IS NOT NULL),'volumeRows',(SELECT COUNT(*) FROM volumes vol JOIN vm_instance v ON v.id=vol.instance_id WHERE v.removed IS NOT NULL AND vol.removed IS NULL AND v.display_name LIKE 'autoScaleVm-AS-PROD-%-AS04-CONCURRENCY-%'));""".replace('IDS',names)
 r=command(['mysql','-uroot','-N','-B','cloud','-e',q],env={**os.environ,'MYSQL_PWD':dbpassword});assert r['returncode']==0,'local DB query failed';rows=[json.loads(l) for l in r['stdout'].splitlines()]
 for r in rows:
  if r['kind']=='orphans' and (r['vmMaps'] or r['volumeRows']):anomaly('ORPHAN_RESOURCE',**r)
 return rows
while time.time()<started:time.sleep(min(1,started-time.time()))
status['phase']='OBSERVING';save();worker=threading.Thread(target=http_loop);worker.start();cloud=None;last_api=None;next_api=time.monotonic();index=0
try:
 while time.time()<=deadline+5:
  try:
   if cloud is None:cloud=Cloud()
   rows=[]
   for target in cfg['groups']:
    scope=target.get('scope',{});g=cloud.call('listAutoScaleVmGroups',id=target['id'],**scope)['autoscalevmgroup'][0];v=cloud.call('listVirtualMachines',autoscalevmgroupid=g['id'],listall=True,**scope).get('virtualmachine',[]);lb=cloud.call('listLoadBalancerRuleInstances',id=g['lbruleid'],**scope).get('loadbalancerruleinstance',[])
    if index%5==0:
     profile=cloud.call('listAutoScaleVmProfiles',id=g['vmprofileid'],**scope)['autoscalevmprofile'][0];schedule=cloud.call('listResourceSchedule',resourcetype='AutoScaleVmGroup',resourceid=g['id'],**scope).get('resourceschedule',[])
     immutable={'profile':{k:profile.get(k) for k in ['templateid','serviceofferingid','destroyvmgraceperiod','otherdeployparams','userdataid','userdata','userdatadetails','counterparams']},'min':g['minmembers'],'max':g['maxmembers'],'interval':g['interval'],'up':g['scaleuppolicies'],'down':g['scaledownpolicies'],'schedules':schedule};fingerprint=hashlib.sha256(json.dumps(immutable,sort_keys=True).encode()).hexdigest();frozen=status.setdefault('configFingerprints',{});before=frozen.setdefault(g['id'],fingerprint)
     if fingerprint!=before:anomaly('GROUP_CONFIGURATION_CHANGED',group=g['id'])
    row={'group':g,'members':[{k:m.get(k) for k in ['id','instancename','state','hostid','hostname']} for m in v],'lbIds':[m['id'] for m in lb]};rows.append(row)
    if g['state'] not in ('ENABLED','SCALING'):anomaly('GROUP_NOT_ENABLED',group=g['id'],state=g['state'])
    if not(g['minmembers']<=g['availablevirtualmachinecount']<=g['maxmembers']):anomaly('MEMBER_RANGE',group=g['id'],members=g['availablevirtualmachinecount'])
    if not g['operationinprogress'] and sorted(m['id'] for m in v)!=sorted(m['id'] for m in lb):anomaly('VM_LB_MISMATCH',group=g['id'])
    if g.get('evaluationerror'):anomaly('EVALUATION_ERROR',group=g['id'])
    events=cloud.call('listEvents',resourceid=g['id'],resourcetype='AutoScaleVmGroup',level='ERROR',startdate=cfg['startDate'],enddate=cfg['endDate'],listall=True,page=1,pagesize=100,**scope).get('event',[]);row['errors']=[{k:e.get(k) for k in ['id','created','type','level','state']} for e in events]
    if events:anomaly('GROUP_ERROR_EVENTS',group=g['id'],ids=[e['id'] for e in events])
   now=time.time()
   with lock:
    if last_api:status['maxApiGapSeconds']=max(status['maxApiGapSeconds'],round(now-last_api,3))
    status['apiSamples']+=1;status['latestGroups']=[{'id':x['group']['id'],'state':x['group']['state'],'members':len(x['members']),'lbMembers':len(x['lbIds']),'operation':x['group']['operationinprogress'],'lastcheckedat':x['group'].get('lastcheckedat'),'evaluation':x['group'].get('evaluation')} for x in rows]
   if last_api and now-last_api>100:anomaly('API_COLLECTION_GAP',gapSeconds=now-last_api)
   last_api=now;record('api',{'groups':rows})
  except Exception as exc:
   with lock:status['apiErrors']+=1
   record('api-errors',{'error':type(exc).__name__,'message':str(exc)});anomaly('API_READ_FAILURE',error=type(exc).__name__,message=str(exc));cloud=None
  if index%5==0:
   try:
    db=db_sample();record('db',{'rows':db});status['dbSamples']+=1
   except Exception as exc:status['dbErrors']+=1;anomaly('DB_READ_FAILURE',error=type(exc).__name__)
   try:metrics=process_metrics();record('process',metrics);status['latestProcess']=metrics
   except Exception as exc:anomaly('PROCESS_READ_FAILURE',error=type(exc).__name__)
  save();index+=1;next_api+=60;time.sleep(max(0,min(next_api-time.monotonic(),deadline+5-time.time())))
finally:
 stop.set();worker.join(timeout=10);status['phase']='FINISHED';status['finishedAt']=stamp();save()
