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

CREATE TABLE IF NOT EXISTS `cloud`.`autoscale_creation_requests` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `uuid` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `operation_uuid` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `stage_key` VARCHAR(96) DEFAULT NULL,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `account_id` BIGINT UNSIGNED NOT NULL,
  `command` VARCHAR(64) NOT NULL,
  `fingerprint` CHAR(64) CHARACTER SET ascii NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `resource_id` BIGINT UNSIGNED DEFAULT NULL,
  `resource_uuid` VARCHAR(36) DEFAULT NULL,
  `resource_type` VARCHAR(64) NOT NULL,
  `created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_autoscale_creation_request_uuid` (`uuid`),
  KEY `idx_autoscale_creation_user` (`user_id`, `created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
