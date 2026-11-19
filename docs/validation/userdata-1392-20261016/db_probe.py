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
prefix='UD1392-31-20261016'
def query(sql):
 out=subprocess.check_output(['mysql','-uroot','-N','-B','cloud','-e',sql],text=True)
 return [line.split('\t') for line in out.splitlines()]
schema={t:query('SHOW COLUMNS FROM '+t) for t in ['user_data','vm_instance','user_vm','volumes','networks','account','domain','projects','roles','vm_template','annotations']}
condition=" LIKE '"+prefix+"%'"
counts={}
for name,sql in {
 'template-iso':"SELECT COUNT(*) FROM vm_template WHERE name"+condition+" AND removed IS NULL",
 'userdata':"SELECT COUNT(*) FROM user_data WHERE name"+condition+" AND removed IS NULL",
 'vm':"SELECT COUNT(*) FROM vm_instance WHERE display_name"+condition+" AND removed IS NULL",
 'volume':"SELECT COUNT(*) FROM volumes d JOIN vm_instance v ON v.id=d.instance_id WHERE v.display_name"+condition+" AND d.removed IS NULL",
 'network':"SELECT COUNT(*) FROM networks WHERE name"+condition+" AND removed IS NULL",
 'account':"SELECT COUNT(*) FROM account WHERE account_name"+condition+" AND removed IS NULL",
 'project-account':"SELECT COUNT(*) FROM account a JOIN projects p ON p.project_account_id=a.id WHERE p.name"+condition+" AND a.removed IS NULL",
 'domain':"SELECT COUNT(*) FROM domain WHERE name"+condition+" AND removed IS NULL",
 'project':"SELECT COUNT(*) FROM projects WHERE name"+condition+" AND removed IS NULL",
 'annotations':"SELECT COUNT(*) FROM annotations a JOIN user_data d ON d.uuid=a.entity_uuid WHERE a.entity_type='USER_DATA' AND d.name"+condition+" AND a.removed IS NULL",
 'role':"SELECT COUNT(*) FROM roles WHERE name"+condition+" AND removed IS NULL",
 'annotations-all-kinds':"SELECT COUNT(*) FROM annotations WHERE removed IS NULL AND entity_uuid IN (SELECT uuid FROM user_data WHERE name"+condition+" UNION SELECT uuid FROM vm_instance WHERE display_name"+condition+" UNION SELECT uuid FROM vm_template WHERE name"+condition+")"
}.items():counts[name]=int(query(sql)[0][0])
print(json.dumps(dict(source_notes=query("SELECT a.uuid,a.entity_uuid,a.annotation,a.admins_only FROM annotations a JOIN user_data d ON d.uuid=a.entity_uuid WHERE a.entity_type='USER_DATA' AND d.name"+condition+" AND a.removed IS NULL"),vm_body_hashes=query("SELECT v.uuid,OCTET_LENGTH(FROM_BASE64(u.user_data)),SHA2(FROM_BASE64(u.user_data),256),u.user_data_details FROM vm_instance v JOIN user_vm u ON u.id=v.id WHERE v.display_name LIKE '"+prefix+"%' AND v.removed IS NULL"),content_bytes=query("SELECT uuid,OCTET_LENGTH(FROM_BASE64(user_data)),SHA2(FROM_BASE64(user_data),256),params FROM user_data WHERE name LIKE '"+prefix+"%' AND removed IS NULL"),template_references=query("SELECT t.uuid,t.name,t.format,t.user_data_id,d.uuid,t.user_data_link_policy FROM vm_template t LEFT JOIN user_data d ON d.id=t.user_data_id WHERE t.name LIKE '"+prefix+"%' AND t.removed IS NULL"),schema=schema,active_counts=counts,libraries=query("SELECT uuid,name,account_id,domain_id,removed IS NOT NULL FROM user_data WHERE name LIKE '"+prefix+"%' ORDER BY uuid"),vms=query("SELECT v.uuid,v.instance_name,v.display_name,v.state,v.removed IS NOT NULL,u.user_data_id,d.uuid FROM vm_instance v JOIN user_vm u ON u.id=v.id LEFT JOIN user_data d ON d.id=u.user_data_id WHERE v.display_name LIKE '"+prefix+"%'")),ensure_ascii=False,indent=2))
