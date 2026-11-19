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
"""Create a same-name contender only while the browser registration is paused."""
import fixtures as f
s=f.state();a=f.ud.Mold();assert 'race-winner' not in s['userdata']
item=f.ok(a,'registerUserData',name=f.PREFIX+'-race',userdata=f.ud.content('#cloud-config\n# UD1391 concurrent name winner\n'))['userdata']
s['userdata']['race-winner']=item;f.save('runtime-state',s);f.save('race-winner',item)
print('Dedicated concurrent-name contender created')
