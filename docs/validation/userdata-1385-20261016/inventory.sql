-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements. See the NOTICE file distributed with this
-- work for additional information regarding copyright ownership. The ASF
-- licenses this file to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance with the License.
-- You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
-- WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
-- License for the specific language governing permissions and limitations
-- under the License.
SELECT 'userdata' AS resource,COUNT(*) AS active_residuals FROM user_data WHERE name LIKE 'UD1385-31-20261016-%' AND removed IS NULL
UNION ALL SELECT 'vm',COUNT(*) FROM vm_instance WHERE display_name LIKE 'UD1385-31-20261016-%' AND removed IS NULL
UNION ALL SELECT 'volume',COUNT(*) FROM volumes v JOIN vm_instance i ON i.id=v.instance_id WHERE i.display_name LIKE 'UD1385-31-20261016-%' AND v.removed IS NULL
UNION ALL SELECT 'template',COUNT(*) FROM vm_template WHERE name LIKE 'UD1385-31-20261016-%' AND removed IS NULL
UNION ALL SELECT 'network',COUNT(*) FROM networks WHERE name LIKE 'UD1385-31-20261016-%' AND removed IS NULL
UNION ALL SELECT 'account',COUNT(*) FROM account WHERE account_name LIKE 'ud1385-31-20261016-%' AND removed IS NULL
UNION ALL SELECT 'project',COUNT(*) FROM projects WHERE name='UD1385-31-20261016-project' AND removed IS NULL
UNION ALL SELECT 'project-account',COUNT(*) FROM account a JOIN projects p ON p.project_account_id=a.id WHERE p.name='UD1385-31-20261016-project' AND a.removed IS NULL
UNION ALL SELECT 'domain',COUNT(*) FROM domain WHERE name='ud1385-31-20261016' AND removed IS NULL
UNION ALL SELECT 'role',COUNT(*) FROM roles WHERE name LIKE 'UD1385-31-20261016-%' AND removed IS NULL;
SELECT i.uuid,i.display_name,i.state,i.removed IS NOT NULL AS removed,u.user_data_id,d.uuid AS library_uuid,u.user_data_details,SHA2(u.user_data,256) AS userdata_sha256
FROM user_vm u JOIN vm_instance i ON i.id=u.id LEFT JOIN user_data d ON d.id=u.user_data_id
WHERE i.display_name LIKE 'UD1385-31-20261016-%' ORDER BY i.id;
