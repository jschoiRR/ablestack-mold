import json,pathlib,os,urllib.request,urllib.parse,http.cookiejar,contextlib
import client
from client import api,wait,redact
p=pathlib.Path('fixtures.json');f=json.loads(p.read_text()) if p.exists() else dict(domains={},accounts={},groups={},networks={},vms=[])
root='7ef6b29a-b253-11f1-a895-0024814c313f'
admin=(client.op,client.key)
def save():p.write_text(json.dumps(redact(f),ensure_ascii=False,indent=2))
def domain(key,parent):
 if key not in f['domains']:f['domains'][key]=api('createDomain',name='qa-ag07-'+key,parentdomainid=parent)['domain'];save()
 return f['domains'][key]['id']
def account(key,domainkey,typ=0,roleid=None):
 if key not in f['accounts']:
  args=dict(username='qa-ag07-'+key,password=os.environ['MOLD_TEST_PASSWORD'],firstname='AG07',lastname=key,email='qa-ag07@example.invalid',account='qa-ag07-'+key,domainid=f['domains'][domainkey]['id'])
  args.update(dict(roleid=roleid) if roleid else dict(accounttype=typ))
  f['accounts'][key]=api('createAccount',**args)['account'];save()
 return f['accounts'][key]
def group(key,typ='host affinity',**owner):
 if key not in f['groups']:f['groups'][key]=wait(api('createAffinityGroup',name='qa-ag07-'+key,type=typ,description='AG07 disposable query qualification',**owner))['affinitygroup'];save()
 return f['groups'][key]
@contextlib.contextmanager
def role(key):
 old=(client.op,client.key,client.actor)
 if key=='admin':client.op,client.key=admin;client.actor='admin'
 else:
  domainpath='/qa-ag07-parent/qa-ag07-child' if key=='child' else '/qa-ag07-peer' if key=='peer' else '/qa-ag07-parent'
  jar=http.cookiejar.CookieJar();client.op=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
  response=json.load(client.op.open(urllib.request.Request('http://10.10.31.10:8080/client/api',data=urllib.parse.urlencode(dict(command='login',username='qa-ag07-'+key,password=os.environ['MOLD_TEST_PASSWORD'],domain=domainpath,response='json')).encode())))
  client.key=response['loginresponse']['sessionkey'];client.actor=key
 try:yield
 finally:client.op,client.key,client.actor=old
