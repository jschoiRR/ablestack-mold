-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--   http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.

--;
-- Schema upgrade from ablestack-bronto to ablestack-cerato
--;
CALL ADD_GUEST_OS_AND_HYPERVISOR_MAPPING (6, 'Windows 11 (64-bit)', 'KVM', 'default', 'Windows 11 (64-bit)');

CALL `cloud`.`IDEMPOTENT_CHANGE_COLUMN`('cloud.host', 'version', 'version', 'VARCHAR(100) NOT NULL');
