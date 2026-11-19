# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership. The ASF
# licenses this file to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance with the License.
# You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
"""Read-only database evidence; MYSQL_PWD is provided by the operator environment."""
import json,subprocess
prefix='UD1387-31-20261016'
def query(sql):
 out=subprocess.check_output(['mysql','-uroot','-N','-B','cloud','-e',sql],text=True)
 return [line.split('\t') for line in out.splitlines()]
schema={t:query('SHOW COLUMNS FROM '+t) for t in ['user_data','vm_instance','user_vm','volumes','networks','account','domain','projects','roles']}
condition=" LIKE '"+prefix+"%'"
counts={}
for name,sql in {
 'userdata':"SELECT COUNT(*) FROM user_data WHERE name"+condition+" AND removed IS NULL",
 'vm':"SELECT COUNT(*) FROM vm_instance WHERE display_name"+condition+" AND removed IS NULL",
 'volume':"SELECT COUNT(*) FROM volumes d JOIN vm_instance v ON v.id=d.instance_id WHERE v.display_name"+condition+" AND d.removed IS NULL",
 'network':"SELECT COUNT(*) FROM networks WHERE name"+condition+" AND removed IS NULL",
 'account':"SELECT COUNT(*) FROM account WHERE account_name"+condition+" AND removed IS NULL",
 'project-account':"SELECT COUNT(*) FROM account a JOIN projects p ON p.project_account_id=a.id WHERE p.name"+condition+" AND a.removed IS NULL",
 'domain':"SELECT COUNT(*) FROM domain WHERE name"+condition+" AND removed IS NULL",
 'project':"SELECT COUNT(*) FROM projects WHERE name"+condition+" AND removed IS NULL",
 'role':"SELECT COUNT(*) FROM roles WHERE name"+condition+" AND removed IS NULL"
}.items():counts[name]=int(query(sql)[0][0])
print(json.dumps(dict(schema=schema,active_counts=counts,libraries=query("SELECT uuid,name,account_id,domain_id,removed IS NOT NULL FROM user_data WHERE name LIKE '"+prefix+"%' ORDER BY uuid"),vms=query("SELECT v.uuid,v.instance_name,v.display_name,v.state,v.removed IS NOT NULL,u.user_data_id,d.uuid FROM vm_instance v JOIN user_vm u ON u.id=v.id LEFT JOIN user_data d ON d.id=u.user_data_id WHERE v.display_name LIKE '"+prefix+"%'")),ensure_ascii=False,indent=2))
