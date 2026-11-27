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

-- Schema upgrade from 4.20.4.0 to 4.21.0.0

-- Check for ambiguous legacy/current objects before changing the schema.
DROP PROCEDURE IF EXISTS `cloud`.`SCHEMA_42100_REQUIRE`;
DELIMITER $$
CREATE PROCEDURE `cloud`.`SCHEMA_42100_REQUIRE`(IN valid BOOLEAN, IN problem VARCHAR(128))
BEGIN
    IF NOT COALESCE(valid, FALSE) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = problem;
    END IF;
END$$

DROP PROCEDURE IF EXISTS `cloud`.`SCHEMA_42100_REQUIRE_FK`$$
CREATE PROCEDURE `cloud`.`SCHEMA_42100_REQUIRE_FK`(
    IN target_table VARCHAR(64), IN fk_name VARCHAR(64),
    IN target_column VARCHAR(64), IN parent_table VARCHAR(64))
BEGIN
    DECLARE matching BOOLEAN;
    DECLARE problem VARCHAR(128);
    SELECT COUNT(*) = 1 AND SUM(k.COLUMN_NAME = target_column
        AND k.REFERENCED_TABLE_SCHEMA = 'cloud' AND k.REFERENCED_TABLE_NAME = parent_table
        AND k.REFERENCED_COLUMN_NAME = 'id' AND r.DELETE_RULE = 'CASCADE' AND r.UPDATE_RULE IN ('RESTRICT', 'NO ACTION')) = 1
    INTO matching
    FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r
      ON k.CONSTRAINT_SCHEMA = r.CONSTRAINT_SCHEMA AND k.TABLE_NAME = r.TABLE_NAME AND k.CONSTRAINT_NAME = r.CONSTRAINT_NAME
    WHERE k.TABLE_SCHEMA = 'cloud' AND k.TABLE_NAME = target_table AND k.CONSTRAINT_NAME = fk_name;
    IF NOT COALESCE(matching, FALSE) THEN
        SET problem = CONCAT('Unexpected foreign key definition: ', fk_name);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = problem;
    END IF;
END$$
DELIMITER ;

SET @schema_42100_vm_table_ok = (SELECT COUNT(*) = 1 AND SUM(TABLE_TYPE = 'BASE TABLE') = 1
    FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'cloud' AND TABLE_NAME IN ('user_vm_details', 'vm_instance_details'));
CALL `cloud`.`SCHEMA_42100_REQUIRE`(@schema_42100_vm_table_ok,
    'Expected exactly one of user_vm_details and vm_instance_details to exist as a base table');

SET @schema_42100_object_size_ok = (SELECT COUNT(*) = 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'cloud' AND TABLE_NAME = 'object_store' AND COLUMN_NAME IN ('used_bytes', 'used_size'));
CALL `cloud`.`SCHEMA_42100_REQUIRE`(@schema_42100_object_size_ok,
    'Expected exactly one of object_store.used_bytes and object_store.used_size');

-- The normal Java upgrade also checks scope. Handle it here for databases whose version already skips that step.
SET @schema_42100_scope_type = (SELECT DATA_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'cloud' AND TABLE_NAME = 'configuration' AND COLUMN_NAME = 'scope');
SET @schema_42100_staged_scope_type = (SELECT DATA_TYPE FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'cloud' AND TABLE_NAME = 'configuration' AND COLUMN_NAME = 'new_scope');
CALL `cloud`.`SCHEMA_42100_REQUIRE`(
    (@schema_42100_scope_type = 'varchar' AND (@schema_42100_staged_scope_type IS NULL OR @schema_42100_staged_scope_type = 'bigint'))
    OR (@schema_42100_scope_type IS NULL AND @schema_42100_staged_scope_type = 'bigint')
    OR (@schema_42100_scope_type = 'bigint' AND @schema_42100_staged_scope_type IS NULL),
    'Unexpected cloud.configuration.scope / new_scope state');

-- Bit values correspond to ConfigKey.Scope. Preserve values already stored as decimal strings.
SET @schema_42100_scope_bitmask = 'CASE
    WHEN scope IS NULL OR TRIM(scope) = '''' THEN 0
    WHEN TRIM(scope) = ''Global'' THEN 1
    WHEN TRIM(scope) = ''Zone'' THEN 2
    WHEN TRIM(scope) = ''Cluster'' THEN 4
    WHEN TRIM(scope) = ''StoragePool'' THEN 8
    WHEN TRIM(scope) = ''ManagementServer'' THEN 16
    WHEN TRIM(scope) = ''ImageStore'' THEN 32
    WHEN TRIM(scope) = ''Domain'' THEN 64
    WHEN TRIM(scope) = ''Account'' THEN 128
    WHEN TRIM(scope) REGEXP ''^[0-9]{1,10}$'' THEN
        CASE WHEN CAST(TRIM(scope) AS UNSIGNED) <= 255 THEN CAST(TRIM(scope) AS UNSIGNED) END
    END';
SET @sql := CASE
    WHEN @schema_42100_scope_type = 'varchar' THEN CONCAT('SELECT NOT EXISTS (SELECT 1 FROM cloud.configuration WHERE (',
        @schema_42100_scope_bitmask, ') IS NULL) INTO @schema_42100_scope_valid')
    WHEN @schema_42100_scope_type IS NULL THEN 'SELECT NOT EXISTS (SELECT 1 FROM cloud.configuration WHERE new_scope IS NULL OR new_scope NOT BETWEEN 0 AND 255) INTO @schema_42100_scope_valid'
    ELSE 'SELECT NOT EXISTS (SELECT 1 FROM cloud.configuration WHERE scope IS NULL OR scope NOT BETWEEN 0 AND 255) INTO @schema_42100_scope_valid'
END;
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;
CALL `cloud`.`SCHEMA_42100_REQUIRE`(@schema_42100_scope_valid,
    'Unrecognized or incomplete cloud.configuration.scope values');

SET @sql := IF(@schema_42100_scope_type = 'varchar' AND @schema_42100_staged_scope_type IS NULL,
    'ALTER TABLE cloud.configuration ADD COLUMN new_scope BIGINT NULL', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @sql := IF(@schema_42100_scope_type = 'varchar',
    CONCAT('UPDATE cloud.configuration SET new_scope = ', @schema_42100_scope_bitmask,
        ' WHERE NOT (new_scope <=> (', @schema_42100_scope_bitmask, '))'), 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- Keep the source column until the drop and replacement can commit together.
SET @schema_42100_scope_definition = 'BIGINT NOT NULL DEFAULT 0 COMMENT ''Bitmask for scope(s) of this parameter''';
SET @sql := CASE
    WHEN @schema_42100_scope_type = 'varchar' THEN CONCAT('ALTER TABLE cloud.configuration DROP COLUMN scope, CHANGE COLUMN new_scope scope ', @schema_42100_scope_definition)
    WHEN @schema_42100_scope_type IS NULL THEN CONCAT('ALTER TABLE cloud.configuration CHANGE COLUMN new_scope scope ', @schema_42100_scope_definition)
    WHEN EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud' AND TABLE_NAME = 'configuration'
        AND COLUMN_NAME = 'scope' AND (IS_NULLABLE <> 'NO' OR NOT (COLUMN_DEFAULT <=> '0')))
        THEN CONCAT('ALTER TABLE cloud.configuration MODIFY COLUMN scope ', @schema_42100_scope_definition)
    ELSE 'SELECT 1'
END;
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- Re-apply the VPC default on the legacy schema without resetting later operator changes.
UPDATE `cloud`.`network_offerings` SET conserve_mode = 1
WHERE name = 'DefaultIsolatedNetworkOfferingForVpcNetworks' AND NOT (conserve_mode <=> 1)
  AND EXISTS (SELECT 1 FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = 'cloud' AND TABLE_NAME = 'user_vm_details');

-- Re-apply schema change that may be missing from earlier 4.21 installations.
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.op_ha_work', 'reason', 'varchar(32) DEFAULT NULL COMMENT "Reason for the HA work"');

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.backup_schedule', 'max_backups', 'INT(8) UNSIGNED NOT NULL DEFAULT 0 COMMENT ''Maximum number of backups to be retained''');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.backups', 'backup_schedule_id', 'BIGINT(20) UNSIGNED');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.backup_schedule', 'quiescevm', 'tinyint(1) default NULL COMMENT "Quiesce VM before taking backup"');

-- Update default value for the config 'vm.network.nic.max.secondary.ipaddresses' (and value to default value if value is null)
UPDATE `cloud`.`configuration` SET default_value = '10'
WHERE name = 'vm.network.nic.max.secondary.ipaddresses' AND (default_value IS NULL OR default_value = '256');
-- @repair-target cloud.configuration 21008e38f8b5b6538fbd26bea6076c02d8f55ba0914cf8c4b39b965925b15dcb
-- @repair-probe SELECT EXISTS (SELECT 1 FROM cloud.configuration WHERE name='vm.network.nic.max.secondary.ipaddresses' AND value IS NULL)
UPDATE `cloud`.`configuration` SET value = '10' WHERE name = 'vm.network.nic.max.secondary.ipaddresses' AND value IS NULL;

-- Add console_endpoint_creator_address column to cloud.console_session table
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.console_session', 'console_endpoint_creator_address', 'VARCHAR(45)');

-- Add client_address column to cloud.console_session table
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.console_session', 'client_address', 'VARCHAR(45)');

-- Allow default roles to use quotaCreditsList
INSERT IGNORE INTO `cloud`.`role_permissions` (uuid, role_id, rule, permission, sort_order)
SELECT uuid(), role_id, 'quotaCreditsList', permission, sort_order
FROM `cloud`.`role_permissions` rp
WHERE rp.rule = 'quotaStatement'
  AND NOT EXISTS(SELECT 1 FROM cloud.role_permissions rp_ WHERE rp.role_id = rp_.role_id AND rp_.rule = 'quotaCreditsList');

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.host', 'last_mgmt_server_id', 'bigint unsigned DEFAULT NULL COMMENT "last management server this host is connected to" AFTER `mgmt_server_id`');

-- ---------------------------------------------------------
-- CKS Enhancements:
-- ---------------------------------------------------------
-- Add for_cks column to the vm_template table
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.vm_template','for_cks', 'int(1) unsigned DEFAULT "0" COMMENT "if true, the template can be used for CKS cluster deployment"');

-- Add support for different node types service offerings on CKS clusters
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster','control_node_service_offering_id', 'bigint unsigned COMMENT "service offering ID for Control Node(s)"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster','worker_node_service_offering_id', 'bigint unsigned COMMENT "service offering ID for Worker Node(s)"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster','etcd_node_service_offering_id', 'bigint unsigned COMMENT "service offering ID for etcd Nodes"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster','etcd_node_count', 'bigint unsigned COMMENT "number of etcd nodes to be deployed for the Kubernetes cluster"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster','control_node_template_id', 'bigint unsigned COMMENT "template id to be used for Control Node(s)"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster','worker_node_template_id', 'bigint unsigned COMMENT "template id to be used for Worker Node(s)"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster','etcd_node_template_id', 'bigint unsigned COMMENT "template id to be used for etcd Nodes"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster','cni_config_id', 'bigint unsigned COMMENT "user data id representing the associated cni configuration"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster','cni_config_details', 'varchar(4096) DEFAULT NULL COMMENT "user data details representing the values required for the cni configuration associated"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster_vm_map','etcd_node', 'tinyint(1) unsigned NOT NULL DEFAULT 0 COMMENT "indicates if the VM is an etcd node"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster_vm_map','external_node', 'tinyint(1) unsigned NOT NULL DEFAULT 0 COMMENT "indicates if the node was imported into the Kubernetes cluster"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster_vm_map','manual_upgrade', 'tinyint(1) unsigned NOT NULL DEFAULT 0 COMMENT "indicates if the node is marked for manual upgrade and excluded from the Kubernetes cluster upgrade operation"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.kubernetes_cluster_vm_map','kubernetes_node_version', 'varchar(40) COMMENT "version of k8s the cluster node is on"');

CALL `cloud`.`IDEMPOTENT_ADD_FOREIGN_KEY`('cloud.kubernetes_cluster','fk_cluster__control_node_service_offering_id','(`control_node_service_offering_id`)','`service_offering`(`id`)');
CALL `cloud`.`IDEMPOTENT_ADD_FOREIGN_KEY`('cloud.kubernetes_cluster','fk_cluster__worker_node_service_offering_id','(`worker_node_service_offering_id`)','`service_offering`(`id`)');
CALL `cloud`.`IDEMPOTENT_ADD_FOREIGN_KEY`('cloud.kubernetes_cluster','fk_cluster__etcd_node_service_offering_id','(`etcd_node_service_offering_id`)','`service_offering`(`id`)');
CALL `cloud`.`IDEMPOTENT_ADD_FOREIGN_KEY`('cloud.kubernetes_cluster','fk_cluster__control_node_template_id','(`control_node_template_id`)','`vm_template`(`id`)');
CALL `cloud`.`IDEMPOTENT_ADD_FOREIGN_KEY`('cloud.kubernetes_cluster','fk_cluster__worker_node_template_id','(`worker_node_template_id`)','`vm_template`(`id`)');
CALL `cloud`.`IDEMPOTENT_ADD_FOREIGN_KEY`('cloud.kubernetes_cluster','fk_cluster__etcd_node_template_id','(`etcd_node_template_id`)','`vm_template`(`id`)');

CALL `cloud`.`SCHEMA_42100_REQUIRE_FK`('kubernetes_cluster', 'fk_cluster__control_node_service_offering_id', 'control_node_service_offering_id', 'service_offering');
CALL `cloud`.`SCHEMA_42100_REQUIRE_FK`('kubernetes_cluster', 'fk_cluster__worker_node_service_offering_id', 'worker_node_service_offering_id', 'service_offering');
CALL `cloud`.`SCHEMA_42100_REQUIRE_FK`('kubernetes_cluster', 'fk_cluster__etcd_node_service_offering_id', 'etcd_node_service_offering_id', 'service_offering');
CALL `cloud`.`SCHEMA_42100_REQUIRE_FK`('kubernetes_cluster', 'fk_cluster__control_node_template_id', 'control_node_template_id', 'vm_template');
CALL `cloud`.`SCHEMA_42100_REQUIRE_FK`('kubernetes_cluster', 'fk_cluster__worker_node_template_id', 'worker_node_template_id', 'vm_template');
CALL `cloud`.`SCHEMA_42100_REQUIRE_FK`('kubernetes_cluster', 'fk_cluster__etcd_node_template_id', 'etcd_node_template_id', 'vm_template');

UPDATE cloud.kubernetes_cluster_vm_map nodes
JOIN cloud.kubernetes_cluster cluster ON nodes.cluster_id = cluster.id
JOIN cloud.kubernetes_supported_version versions ON cluster.kubernetes_version_id = versions.id
SET nodes.kubernetes_node_version = versions.semantic_version
WHERE cluster.removed IS NULL AND (nodes.kubernetes_node_version IS NULL OR nodes.kubernetes_node_version = '')
  AND versions.semantic_version IS NOT NULL;

-- Add for_cks column to the user_data table to represent CNI Configuration stored as userdata
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.user_data','for_cks', 'int(1) unsigned DEFAULT "0" COMMENT "if true, the user data represent CNI configuration meant for CKS use only"');

-- Add use VR IP as resolver option on VPC
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.vpc','use_router_ip_resolver', 'tinyint(1) DEFAULT 0 COMMENT "use router ip as resolver instead of dns options"');
-- ---------------------------------------------------------
-- END - CKS Enhancements
-- ---------------------------------------------------------

-- Add table for reconcile commands
CREATE TABLE IF NOT EXISTS `cloud`.`reconcile_commands` (
    `id` bigint unsigned NOT NULL UNIQUE AUTO_INCREMENT,
    `management_server_id` bigint unsigned NOT NULL COMMENT 'node id of the management server',
    `host_id` bigint unsigned NOT NULL COMMENT 'id of the host',
    `request_sequence` bigint unsigned NOT NULL COMMENT 'sequence of the request',
    `resource_id` bigint unsigned DEFAULT NULL COMMENT 'id of the resource',
    `resource_type` varchar(255) COMMENT 'type if the resource',
    `state_by_management` varchar(255) COMMENT 'state of the command updated by management server',
    `state_by_agent` varchar(255) COMMENT 'state of the command updated by cloudstack agent',
    `command_name` varchar(255) COMMENT 'name of the command',
    `command_info` MEDIUMTEXT COMMENT 'info of the command',
    `answer_name` varchar(255) COMMENT 'name of the answer',
    `answer_info` MEDIUMTEXT COMMENT 'info of the answer',
    `created` datetime COMMENT 'date the reconcile command was created',
    `removed` datetime COMMENT 'date the reconcile command was removed',
    `updated` datetime COMMENT 'date the reconcile command was updated',
    `retry_count` bigint unsigned DEFAULT 0 COMMENT 'The retry count of reconciliation',
    PRIMARY KEY(`id`),
    INDEX `i_reconcile_command__host_id`(`host_id`),
    CONSTRAINT `fk_reconcile_command__host_id` FOREIGN KEY (`host_id`) REFERENCES `host`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- KVM Incremental Snapshots

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.snapshot_store_ref', 'kvm_checkpoint_path', 'varchar(255)');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.snapshot_store_ref', 'end_of_chain', 'int(1) unsigned');

-- Create table storage_pool_and_access_group_map
CREATE TABLE IF NOT EXISTS `cloud`.`storage_pool_and_access_group_map` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `pool_id` bigint(20) unsigned NOT NULL COMMENT "pool id",
  `storage_access_group` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_storage_pool_and_access_group_map__pool_id` (`pool_id`),
  CONSTRAINT `fk_storage_pool_and_access_group_map__pool_id` FOREIGN KEY (`pool_id`) REFERENCES `storage_pool` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.host', 'storage_access_groups', 'varchar(255) DEFAULT NULL COMMENT "storage access groups for the host"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.cluster', 'storage_access_groups', 'varchar(255) DEFAULT NULL COMMENT "storage access groups for the hosts in the cluster"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.host_pod_ref', 'storage_access_groups', 'varchar(255) DEFAULT NULL COMMENT "storage access groups for the hosts in the pod"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.data_center', 'storage_access_groups', 'varchar(255) DEFAULT NULL COMMENT "storage access groups for the hosts in the zone"');

-- Add featured, sort_key, created, removed columns for guest_os_category
-- Reclassify the legacy catalog once. Keep catalog choices on databases with the new columns.
CREATE TABLE IF NOT EXISTS `cloud`.`ablestack_schema_migration` (
    `name` VARCHAR(191) NOT NULL PRIMARY KEY,
    `state` VARCHAR(16) NOT NULL,
    `updated` DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @schema_42100_legacy_catalog = NOT EXISTS (
    SELECT 1 FROM cloud.ablestack_schema_migration
    WHERE name = 'schema-4.21.0.0-guest-os-catalog-v1' AND state = 'Complete'
) AND (
    NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
        AND TABLE_NAME = 'guest_os_category' AND COLUMN_NAME = 'featured')
    OR EXISTS (SELECT 1 FROM cloud.ablestack_schema_migration
        WHERE name = 'schema-4.21.0.0-guest-os-catalog-v1' AND state = 'Pending')
);
INSERT IGNORE INTO cloud.ablestack_schema_migration (name, state, updated)
SELECT 'schema-4.21.0.0-guest-os-catalog-v1', 'Pending', NOW() WHERE @schema_42100_legacy_catalog;

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.guest_os_category', 'featured', 'tinyint(1) NOT NULL DEFAULT 0 COMMENT "whether the category is featured or not" AFTER `uuid`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.guest_os_category', 'sort_key', 'int NOT NULL DEFAULT 0 COMMENT "sort key used for customising sort method" AFTER `featured`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.guest_os_category', 'created', 'datetime COMMENT "date on which the category was created" AFTER `sort_key`');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.guest_os_category', 'removed', 'datetime COMMENT "date removed if not null" AFTER `created`');

-- Begin: Changes for Guest OS category cleanup
-- Add new OS categories if not present
DROP PROCEDURE IF EXISTS `cloud`.`INSERT_CATEGORY_IF_NOT_EXIST`;
DELIMITER $$
CREATE PROCEDURE `cloud`.`INSERT_CATEGORY_IF_NOT_EXIST`(IN os_name VARCHAR(255))
BEGIN
    IF @schema_42100_legacy_catalog AND NOT EXISTS (SELECT 1 FROM `cloud`.`guest_os_category` WHERE name = os_name)
    THEN
        INSERT INTO `cloud`.`guest_os_category` (name, uuid)
            VALUES (os_name, UUID())
;   END IF
; END$$
DELIMITER ;

CALL `cloud`.`INSERT_CATEGORY_IF_NOT_EXIST`('Fedora');
CALL `cloud`.`INSERT_CATEGORY_IF_NOT_EXIST`('Rocky Linux');
CALL `cloud`.`INSERT_CATEGORY_IF_NOT_EXIST`('AlmaLinux');

-- Move existing guest OS to new categories
DROP PROCEDURE IF EXISTS `cloud`.`UPDATE_CATEGORY_FOR_GUEST_OSES`;
DELIMITER $$
CREATE PROCEDURE `cloud`.`UPDATE_CATEGORY_FOR_GUEST_OSES`(IN category_name VARCHAR(255), IN os_name VARCHAR(255))
catalog_update: BEGIN
    DECLARE category_id BIGINT
;   IF NOT @schema_42100_legacy_catalog THEN
        LEAVE catalog_update
;   END IF
;   SELECT `id` INTO category_id
    FROM `cloud`.`guest_os_category`
    WHERE `name` = category_name
    LIMIT 1
;   IF category_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Category not found'
;   END IF
;   UPDATE `cloud`.`guest_os`
    SET `category_id` = category_id
    WHERE `display_name` LIKE CONCAT('%', os_name, '%')
      AND NOT (guest_os.category_id <=> category_id)
; END$$
DELIMITER ;
CALL `cloud`.`UPDATE_CATEGORY_FOR_GUEST_OSES`('Rocky Linux', 'Rocky Linux');
CALL `cloud`.`UPDATE_CATEGORY_FOR_GUEST_OSES`('AlmaLinux', 'AlmaLinux');
CALL `cloud`.`UPDATE_CATEGORY_FOR_GUEST_OSES`('Fedora', 'Fedora');

-- Move existing guest OS whose category will be deleted to Other category
DROP PROCEDURE IF EXISTS `cloud`.`UPDATE_NEW_AND_DELETE_OLD_CATEGORY_FOR_GUEST_OS`;
DELIMITER $$
CREATE PROCEDURE `cloud`.`UPDATE_NEW_AND_DELETE_OLD_CATEGORY_FOR_GUEST_OS`(IN to_category_name VARCHAR(255), IN from_category_name VARCHAR(255))
catalog_retire: BEGIN
    DECLARE to_category_id BIGINT
;   IF NOT @schema_42100_legacy_catalog THEN
        LEAVE catalog_retire
;   END IF
;   SELECT id INTO to_category_id
    FROM `cloud`.`guest_os_category`
    WHERE `name` = to_category_name
    LIMIT 1
;   IF to_category_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ToCategory not found'
;   END IF
;   UPDATE `cloud`.`guest_os`
    SET `category_id` = to_category_id
    WHERE `category_id` IN (SELECT `id` FROM `cloud`.`guest_os_category` WHERE `name` = from_category_name)
;   UPDATE `cloud`.`guest_os_category` SET `removed`=now() WHERE `name` = from_category_name AND removed IS NULL
; END$$
DELIMITER ;
CALL `cloud`.`UPDATE_NEW_AND_DELETE_OLD_CATEGORY_FOR_GUEST_OS`('Other', 'Novel');
CALL `cloud`.`UPDATE_NEW_AND_DELETE_OLD_CATEGORY_FOR_GUEST_OS`('Other', 'None');
CALL `cloud`.`UPDATE_NEW_AND_DELETE_OLD_CATEGORY_FOR_GUEST_OS`('Other', 'Unix');
CALL `cloud`.`UPDATE_NEW_AND_DELETE_OLD_CATEGORY_FOR_GUEST_OS`('Other', 'Mac');

-- Update featured for existing guest OS categories
UPDATE `cloud`.`guest_os_category` SET featured = 1 WHERE @schema_42100_legacy_catalog AND featured <> 1;

-- Update sort order for all guest OS categories
UPDATE `cloud`.`guest_os_category`
SET `sort_key` = CASE
    WHEN `name` = 'Ubuntu' THEN 1
    WHEN `name` = 'Debian' THEN 2
    WHEN `name` = 'Fedora' THEN 3
    WHEN `name` = 'CentOS' THEN 4
    WHEN `name` = 'Rocky Linux' THEN 5
    WHEN `name` = 'AlmaLinux' THEN 6
    WHEN `name` = 'Oracle' THEN 7
    WHEN `name` = 'RedHat' THEN 8
    WHEN `name` = 'SUSE' THEN 9
    WHEN `name` = 'Windows' THEN 10
    WHEN `name` = 'Other' THEN 11
    ELSE `sort_key`
END WHERE @schema_42100_legacy_catalog;

UPDATE cloud.ablestack_schema_migration SET state = 'Complete', updated = NOW()
WHERE name = 'schema-4.21.0.0-guest-os-catalog-v1' AND state = 'Pending' AND @schema_42100_legacy_catalog;
-- End: Changes for Guest OS category cleanup

-- Update description for configuration: host.capacityType.to.order.clusters
UPDATE `cloud`.`configuration` SET
    `description` = 'The host capacity type (CPU, RAM or COMBINED) is used by deployment planner to order clusters during VM resource allocation'
WHERE `name` = 'host.capacityType.to.order.clusters'
  AND `description` = 'The host capacity type (CPU or RAM) is used by deployment planner to order clusters during VM resource allocation';

-- Whitelabel GUI
CREATE TABLE IF NOT EXISTS `cloud`.`gui_themes` (
    `id` bigint(20) unsigned NOT NULL auto_increment,
    `uuid` varchar(255) UNIQUE,
    `name` varchar(2048) NOT NULL COMMENT 'A name to identify the theme.',
    `description` varchar(4096) DEFAULT NULL COMMENT 'A description for the theme.',
    `css` text DEFAULT NULL COMMENT 'The CSS to be retrieved and imported into the GUI when matching the theme access configurations.',
    `json_configuration` text DEFAULT NULL COMMENT 'The JSON with the configurations to be retrieved and imported into the GUI when matching the theme access configurations.',
    `recursive_domains` tinyint(1) DEFAULT 0 COMMENT 'Defines whether the subdomains of the informed domains are considered. Default value is false.',
    `is_public` tinyint(1) default 1 COMMENT 'Defines whether a theme can be retrieved by anyone when only the `internet_domains_names` is informed. If the `domain_uuids` or `account_uuids` is informed, it is considered as `false`.',
    `created` datetime NOT NULL,
    `removed` datetime DEFAULT NULL,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `cloud`.`gui_themes_details` (
    `id` bigint(20) unsigned NOT NULL auto_increment,
    `gui_theme_id` bigint(20) unsigned NOT NULL COMMENT 'Foreign key referencing the GUI theme on `gui_themes` table.',
    `type` varchar(100) NOT NULL COMMENT 'The type of GUI theme details. Valid options are: `account`, `domain` and `commonName`',
    `value` text NOT NULL COMMENT 'The value of the `type` details. Can be an UUID (account or domain) or internet common name.',
    PRIMARY KEY (`id`),
    CONSTRAINT `fk_gui_themes_details__gui_theme_id` FOREIGN KEY (`gui_theme_id`) REFERENCES `gui_themes`(`id`)
);

-- Create the GPU card table to hold the GPU card information
CREATE TABLE IF NOT EXISTS `cloud`.`gpu_card` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `uuid` varchar(40) NOT NULL UNIQUE,
  `device_id` varchar(4) NOT NULL COMMENT 'device id of the GPU card',
  `device_name` varchar(255) NOT NULL COMMENT 'device name of the GPU card',
  `name` varchar(255) NOT NULL COMMENT 'name of the GPU card',
  `vendor_name` varchar(255) NOT NULL COMMENT 'vendor name of the GPU card',
  `vendor_id` varchar(4) NOT NULL COMMENT 'vendor id of the GPU card',
  `created` datetime NOT NULL COMMENT 'date created',
  PRIMARY KEY (`id`),
  UNIQUE KEY (`vendor_id`, `device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GPU cards supported by CloudStack';

-- Create the vGPU profile table to hold the vGPU profile information.
CREATE TABLE IF NOT EXISTS `cloud`.`vgpu_profile` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `uuid` varchar(40) NOT NULL UNIQUE,
  `name` varchar(255) NOT NULL COMMENT 'name of the vGPU profile',
  `description` varchar(255) DEFAULT NULL COMMENT 'description of the vGPU profile',
  `card_id` bigint unsigned NOT NULL COMMENT 'id of the GPU card',
  `video_ram` bigint unsigned DEFAULT NULL COMMENT 'video RAM of the vGPU profile',
  `max_heads` bigint unsigned DEFAULT NULL COMMENT 'maximum number of heads of the vGPU profile',
  `max_resolution_x` bigint unsigned DEFAULT NULL COMMENT 'maximum resolution x of the vGPU profile',
  `max_resolution_y` bigint unsigned DEFAULT NULL COMMENT 'maximum resolution y of the vGPU profile',
  `max_vgpu_per_pgpu` bigint unsigned DEFAULT NULL COMMENT 'Maximum number of vGPUs per physical GPU',
  `created` datetime NOT NULL COMMENT 'date created',
  PRIMARY KEY (`id`),
  UNIQUE KEY (`name`, `card_id`),
  CONSTRAINT `fk_vgpu_profile_card_id` FOREIGN KEY (`card_id`) REFERENCES `gpu_card`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='vGPU profiles supported by CloudStack';

-- Create the GPU device table to hold the GPU device information on different hosts
CREATE TABLE IF NOT EXISTS `cloud`.`gpu_device` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'id',
  `uuid` varchar(40) NOT NULL UNIQUE,
  `card_id` bigint unsigned NOT NULL COMMENT 'id of the GPU card',
  `vgpu_profile_id` bigint unsigned DEFAULT NULL COMMENT 'id of the vGPU profile.',
  `bus_address` varchar(255) NOT NULL COMMENT 'PCI bus address of the GPU device',
  `type` varchar(32) NOT NULL COMMENT 'type of the GPU device. PCI or MDEV',
  `host_id` bigint unsigned NOT NULL COMMENT 'id of the host where GPU is installed',
  `vm_id` bigint unsigned DEFAULT NULL COMMENT 'id of the VM using this GPU device',
  `numa_node` varchar(255) DEFAULT NULL COMMENT 'NUMA node of the GPU device',
  `pci_root` varchar(255) DEFAULT NULL COMMENT 'PCI root of the GPU device',
  `parent_gpu_device_id` bigint unsigned DEFAULT NULL COMMENT 'id of the parent GPU device. null if it is a physical GPU device and for vGPUs points to the actual GPU',
  `state` varchar(32) NOT NULL COMMENT 'state of the GPU device',
  `managed_state` varchar(32) NOT NULL COMMENT 'resource state of the GPU device',
  PRIMARY KEY (`id`),
  UNIQUE KEY (`bus_address`, `host_id`),
  CONSTRAINT `fk_gpu_devices__card_id` FOREIGN KEY (`card_id`) REFERENCES `gpu_card` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_gpu_devices__host_id` FOREIGN KEY (`host_id`) REFERENCES `host` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_gpu_devices__vm_id` FOREIGN KEY (`vm_id`) REFERENCES `vm_instance` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_gpu_devices__parent_gpu_device_id` FOREIGN KEY (`parent_gpu_device_id`) REFERENCES `gpu_device` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GPU devices installed on hosts';

-- Add references to GPU tables
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.service_offering', 'vgpu_profile_id', 'bigint unsigned DEFAULT NULL COMMENT "vgpu profile ID"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.service_offering', 'gpu_count', 'int unsigned DEFAULT NULL COMMENT "number of GPUs"');
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.service_offering', 'gpu_display', 'boolean DEFAULT false COMMENT "enable GPU display"');
CALL `cloud`.`IDEMPOTENT_ADD_FOREIGN_KEY`('cloud.service_offering', 'fk_service_offering__vgpu_profile_id', '(vgpu_profile_id)', '`vgpu_profile`(`id`)');
CALL `cloud`.`SCHEMA_42100_REQUIRE_FK`('service_offering', 'fk_service_offering__vgpu_profile_id', 'vgpu_profile_id', 'vgpu_profile');

-- Netris Plugin
CREATE TABLE IF NOT EXISTS `cloud`.`netris_providers` (
                                            `id` bigint unsigned NOT NULL auto_increment COMMENT 'id',
                                            `uuid` varchar(40),
                                            `zone_id` bigint unsigned NOT NULL COMMENT 'Zone ID',
                                            `host_id` bigint unsigned NOT NULL COMMENT 'Host ID',
                                            `name` varchar(40),
                                            `url` varchar(255) NOT NULL,
                                            `username` varchar(255) NOT NULL,
                                            `password` varchar(255) NOT NULL,
                                            `site_name` varchar(255) NOT NULL,
                                            `tenant_name` varchar(255) NOT NULL,
                                            `netris_tag` varchar(255) NOT NULL,
                                            `created` datetime NOT NULL COMMENT 'created date',
                                            `removed` datetime COMMENT 'removed date if not null',
                                            PRIMARY KEY (`id`),
                                            CONSTRAINT `fk_netris_providers__zone_id` FOREIGN KEY `fk_netris_providers__zone_id` (`zone_id`) REFERENCES `data_center`(`id`) ON DELETE CASCADE,
                                            INDEX `i_netris_providers__zone_id`(`zone_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- The new provider mappings do not use these flags. Drop only unused legacy columns; retain nonzero values for review.
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'network_offerings' AND COLUMN_NAME = 'for_tungsten'),
    'SELECT NOT EXISTS (SELECT 1 FROM cloud.network_offerings WHERE for_tungsten <> 0) INTO @schema_42100_drop_flag',
    'SELECT 0 INTO @schema_42100_drop_flag');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;
SET @sql := IF(@schema_42100_drop_flag, 'ALTER TABLE cloud.network_offerings DROP COLUMN for_tungsten', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'network_offerings' AND COLUMN_NAME = 'for_nsx'),
    'SELECT NOT EXISTS (SELECT 1 FROM cloud.network_offerings WHERE for_nsx <> 0) INTO @schema_42100_drop_flag',
    'SELECT 0 INTO @schema_42100_drop_flag');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;
SET @sql := IF(@schema_42100_drop_flag, 'ALTER TABLE cloud.network_offerings DROP COLUMN for_nsx', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'vpc_offerings' AND COLUMN_NAME = 'for_nsx'),
    'SELECT NOT EXISTS (SELECT 1 FROM cloud.vpc_offerings WHERE for_nsx <> 0) INTO @schema_42100_drop_flag',
    'SELECT 0 INTO @schema_42100_drop_flag');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;
SET @sql := IF(@schema_42100_drop_flag, 'ALTER TABLE cloud.vpc_offerings DROP COLUMN for_nsx', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- Add next_hop to the static_routes table
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.static_routes', 'next_hop', 'varchar(50) COMMENT "next hop of the static route" AFTER `vpc_gateway_id`');

-- Add `for_router` to `user_ip_address` table
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.user_ip_address', 'for_router', 'tinyint(1) DEFAULT 0 COMMENT "True if the ip address is used by Domain Router to expose services"');

-- Add Netris Autoscaling rules
INSERT INTO `cloud`.`counter` (uuid, provider, source, name, value, created)
SELECT UUID(), 'Netris', 'cpu', 'VM CPU - average percentage', 'vm.cpu.average.percentage', NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cloud.counter WHERE provider = 'Netris' AND source = 'cpu' AND value = 'vm.cpu.average.percentage');
INSERT INTO `cloud`.`counter` (uuid, provider, source, name, value, created)
SELECT UUID(), 'Netris', 'memory', 'VM Memory - average percentage', 'vm.memory.average.percentage', NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM cloud.counter WHERE provider = 'Netris' AND source = 'memory' AND value = 'vm.memory.average.percentage');

-- Rename user_vm_details to vm_instance_details
SET @sql := (SELECT IF(COUNT(*)>0,'RENAME TABLE `cloud`.`user_vm_details` TO `cloud`.`vm_instance_details`','SELECT 1') FROM information_schema.TABLES WHERE TABLE_SCHEMA='cloud' AND TABLE_NAME='user_vm_details');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- Verify the replacement before dropping the legacy constraint. The ADD helper accepts duplicate names.
CALL `cloud`.`IDEMPOTENT_ADD_FOREIGN_KEY`('cloud.vm_instance_details', 'fk_vm_instance_details__vm_id', '(vm_id)', '`vm_instance`(`id`)');
CALL `cloud`.`SCHEMA_42100_REQUIRE_FK`('vm_instance_details', 'fk_vm_instance_details__vm_id', 'vm_id', 'vm_instance');

SET @sql := (SELECT IF(COUNT(*)>0,'ALTER TABLE `cloud`.`vm_instance_details` DROP FOREIGN KEY `fk_user_vm_details__vm_id`','SELECT 1') FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='cloud' AND TABLE_NAME='vm_instance_details' AND CONSTRAINT_NAME='fk_user_vm_details__vm_id' AND CONSTRAINT_TYPE='FOREIGN KEY');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.backup_schedule', 'uuid', 'VARCHAR(40) NULL');
-- @repair-target cloud.backup_schedule a0cb6eddd817a5a8eeacb0aa692418e584cb1aa599b5b47383cdfaf67acc9981
-- @repair-probe SELECT EXISTS (SELECT 1 FROM cloud.backup_schedule WHERE uuid IS NULL OR uuid = '')
UPDATE `cloud`.`backup_schedule` SET uuid = UUID() WHERE uuid IS NULL OR uuid = '';
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'backup_schedule' AND COLUMN_NAME = 'uuid' AND COLUMN_TYPE = 'varchar(40)' AND IS_NULLABLE = 'YES'),
    'ALTER TABLE cloud.backup_schedule MODIFY COLUMN uuid VARCHAR(40) NOT NULL', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- Extension framework
UPDATE `cloud`.`configuration`
SET value = IF(value IS NULL OR value = '', 'External', CONCAT(value, ',External'))
WHERE name = 'hypervisor.list'
  AND (value IS NULL OR FIND_IN_SET('External', value) = 0)
  AND NOT EXISTS (SELECT 1 FROM information_schema.TABLES
      WHERE TABLE_SCHEMA = 'cloud' AND TABLE_NAME = 'extension');

CREATE TABLE IF NOT EXISTS `cloud`.`extension` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `uuid` varchar(40) NOT NULL UNIQUE,
  `name` varchar(255) NOT NULL,
  `description` varchar(4096),
  `type` varchar(255) NOT NULL COMMENT 'Type of the extension: Orchestrator, etc',
  `relative_path` varchar(2048) NOT NULL COMMENT 'Path for the extension relative to the root extensions directory',
  `path_ready` tinyint(1) DEFAULT '0' COMMENT 'True if the extension path is in ready state across management servers',
  `is_user_defined` tinyint(1) DEFAULT '0' COMMENT 'True if the extension is added by admin',
  `state` char(32) NOT NULL COMMENT 'State of the extension - Enabled or Disabled',
  `created` datetime NOT NULL,
  `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`extension_details` (
  `id` bigint unsigned UNIQUE NOT NULL AUTO_INCREMENT COMMENT 'id',
  `extension_id` bigint unsigned NOT NULL COMMENT 'extension to which the detail is related to',
  `name` varchar(255) NOT NULL COMMENT 'name of the detail',
  `value` varchar(255) NOT NULL COMMENT 'value of the detail',
  `display` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'True if the detail can be displayed to the end user',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_extension_details__extension_id` FOREIGN KEY (`extension_id`)
    REFERENCES `extension` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`extension_resource_map` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `extension_id` bigint(20) unsigned NOT NULL,
  `resource_id` bigint(20) unsigned NOT NULL,
  `resource_type` char(255) NOT NULL,
  `created` datetime NOT NULL,
  `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_extension_resource_map__extension_id` FOREIGN KEY (`extension_id`)
      REFERENCES `cloud`.`extension`(`id`) ON DELETE CASCADE,
  INDEX `idx_extension_resource` (`resource_id`, `resource_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`extension_resource_map_details` (
  `id` bigint unsigned UNIQUE NOT NULL AUTO_INCREMENT COMMENT 'id',
  `extension_resource_map_id` bigint unsigned NOT NULL COMMENT 'mapping to which the detail is related',
  `name` varchar(255) NOT NULL COMMENT 'name of the detail',
  `value` varchar(255) NOT NULL COMMENT 'value of the detail',
  `display` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'True if the detail can be displayed to the end user',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_extension_resource_map_details__map_id` FOREIGN KEY (`extension_resource_map_id`)
    REFERENCES `extension_resource_map` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`extension_custom_action` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `uuid` varchar(255) NOT NULL UNIQUE,
  `name` varchar(255) NOT NULL,
  `description` varchar(4096),
  `extension_id` bigint(20) unsigned NOT NULL,
  `resource_type` varchar(255),
  `allowed_role_types` int unsigned NOT NULL DEFAULT '1',
  `success_message` varchar(4096),
  `error_message` varchar(4096),
  `enabled` boolean DEFAULT true,
  `timeout` int unsigned NOT NULL DEFAULT '5' COMMENT 'The timeout in seconds to wait for the action to complete before failing',
  `created` datetime NOT NULL,
  `removed` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_extension_custom_action__extension_id` FOREIGN KEY (`extension_id`)
    REFERENCES `cloud`.`extension`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `cloud`.`extension_custom_action_details` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `extension_custom_action_id` bigint(20) unsigned NOT NULL,
  `name` varchar(255) NOT NULL,
  `value` TEXT NOT NULL,
  `display` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_custom_action_details__action_id` FOREIGN KEY (`extension_custom_action_id`)
    REFERENCES `cloud`.`extension_custom_action`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.vm_template', 'extension_id', 'bigint unsigned DEFAULT NULL COMMENT "id of the extension"');

-- Add built-in Extensions and Custom Actions

DROP PROCEDURE IF EXISTS `cloud`.`INSERT_EXTENSION_IF_NOT_EXISTS`;
DELIMITER $$
CREATE PROCEDURE `cloud`.`INSERT_EXTENSION_IF_NOT_EXISTS`(
    IN ext_name VARCHAR(255),
    IN ext_desc VARCHAR(255),
    IN ext_path VARCHAR(255)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM `cloud`.`extension` WHERE `name` = ext_name
    ) THEN
        INSERT INTO `cloud`.`extension` (
            `uuid`, `name`, `description`, `type`,
            `relative_path`, `path_ready`,
            `is_user_defined`, `state`, `created`, `removed`
        )
        VALUES (
            UUID(), ext_name, ext_desc, 'Orchestrator',
            ext_path, 1, 0, 'Enabled', NOW(), NULL
        )
;   END IF
;END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS `cloud`.`INSERT_EXTENSION_DETAIL_IF_NOT_EXISTS`;
DELIMITER $$
CREATE PROCEDURE `cloud`.`INSERT_EXTENSION_DETAIL_IF_NOT_EXISTS`(
    IN ext_name VARCHAR(255),
    IN detail_key VARCHAR(255),
    IN detail_value TEXT,
    IN display TINYINT(1)
)
BEGIN
    DECLARE ext_id BIGINT
;   SELECT `id` INTO ext_id FROM `cloud`.`extension` WHERE `name` = ext_name LIMIT 1
;   IF NOT EXISTS (
        SELECT 1 FROM `cloud`.`extension_details`
        WHERE `extension_id` = ext_id AND `name` = detail_key
    ) THEN
        INSERT INTO `cloud`.`extension_details` (
            `extension_id`, `name`, `value`, `display`
        )
        VALUES (
            ext_id, detail_key, detail_value, display
        )
;   END IF
;END$$
DELIMITER ;

CALL `cloud`.`INSERT_EXTENSION_IF_NOT_EXISTS`('Proxmox', 'Sample extension for Proxmox written in bash', 'Proxmox/proxmox.sh');
CALL `cloud`.`INSERT_EXTENSION_DETAIL_IF_NOT_EXISTS`('Proxmox', 'orchestratorrequirespreparevm', 'true', 0);
CALL `cloud`.`INSERT_EXTENSION_IF_NOT_EXISTS`('HyperV', 'Sample extension for HyperV written in python', 'HyperV/hyperv.py');

DROP PROCEDURE IF EXISTS `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`;
DELIMITER $$
CREATE PROCEDURE `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`(
    IN ext_name VARCHAR(255),
    IN action_name VARCHAR(255),
    IN action_desc VARCHAR(4096),
    IN resource_type VARCHAR(255),
    IN allowed_roles INT UNSIGNED,
    IN success_msg VARCHAR(4096),
    IN error_msg VARCHAR(4096),
    IN timeout_seconds INT UNSIGNED
)
BEGIN
    DECLARE ext_id BIGINT
;   SELECT `id` INTO ext_id FROM `cloud`.`extension` WHERE `name` = ext_name LIMIT 1
;   IF NOT EXISTS (
        SELECT 1 FROM `cloud`.`extension_custom_action` WHERE `name` = action_name AND `extension_id` = ext_id
    ) THEN
        INSERT INTO `cloud`.`extension_custom_action` (
            `uuid`, `name`, `description`, `extension_id`, `resource_type`,
            `allowed_role_types`, `success_message`, `error_message`,
            `enabled`, `timeout`, `created`, `removed`
        )
        VALUES (
            UUID(), action_name, action_desc, ext_id, resource_type,
            allowed_roles, success_msg, error_msg,
            1, timeout_seconds, NOW(), NULL
        )
;   END IF
;END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS`;
DELIMITER $$
CREATE PROCEDURE `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS` (
    IN ext_name VARCHAR(255),
    IN action_name VARCHAR(255),
    IN param_json TEXT
)
BEGIN
    DECLARE action_id BIGINT UNSIGNED
;   SELECT `eca`.`id` INTO action_id FROM `cloud`.`extension_custom_action` `eca`
    JOIN `cloud`.`extension` `e` ON `e`.`id` = `eca`.`extension_id`
    WHERE `eca`.`name` = action_name AND `e`.`name` = ext_name LIMIT 1
;   IF NOT EXISTS (
        SELECT 1 FROM `cloud`.`extension_custom_action_details`
        WHERE `extension_custom_action_id` = action_id
          AND `name` = 'parameters'
    ) THEN
        INSERT INTO `cloud`.`extension_custom_action_details` (
            `extension_custom_action_id`,
            `name`,
            `value`,
            `display`
        ) VALUES (
            action_id,
            'parameters',
            param_json,
            0
        )
;   END IF
;END$$
DELIMITER ;

CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`('Proxmox', 'ListSnapshots', 'List Instance snapshots', 'VirtualMachine', 15, 'Snapshots fetched for {{resourceName}} in {{extensionName}}', 'List Snapshots failed for {{resourceName}}', 60);
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`('Proxmox', 'CreateSnapshot', 'Create an Instance snapshot', 'VirtualMachine', 15, 'Snapshot created for {{resourceName}} in {{extensionName}}', 'Snapshot creation failed for {{resourceName}}', 60);
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`('Proxmox', 'RestoreSnapshot', 'Restore Instance to the specific snapshot', 'VirtualMachine', 15, 'Successfully restored snapshot for {{resourceName}} in {{extensionName}}', 'Restore snapshot failed for {{resourceName}}', 60);
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`('Proxmox', 'DeleteSnapshot', 'Delete the specified snapshot', 'VirtualMachine', 15, 'Successfully deleted snapshot for {{resourceName}} in {{extensionName}}', 'Delete snapshot failed for {{resourceName}}', 60);
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS`('Proxmox','ListSnapshots','[]');
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS`('Proxmox','CreateSnapshot','[{"name":"snap_name","type":"STRING","validationformat":"NONE","required":true},{"name":"snap_description","type":"STRING","validationformat":"NONE","required":false},{"name":"snap_save_memory","type":"BOOLEAN","validationformat":"NONE","required":false}]');
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS`('Proxmox','RestoreSnapshot','[{"name":"snap_name","type":"STRING","validationformat":"NONE","required":true}]');
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS`('Proxmox','DeleteSnapshot','[{"name":"snap_name","type":"STRING","validationformat":"NONE","required":true}]');
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`('HyperV', 'ListSnapshots', 'List checkpoints/snapshots for the Instance', 'VirtualMachine', 15, 'Snapshots fetched for {{resourceName}} in {{extensionName}}', 'List Snapshots failed for {{resourceName}}', 60);
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`('HyperV', 'CreateSnapshot', 'Create a checkpoint/snapshot for the Instance', 'VirtualMachine', 15, 'Snapshot created for {{resourceName}} in {{extensionName}}', 'Snapshot creation failed for {{resourceName}}', 60);
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`('HyperV', 'RestoreSnapshot', 'Restore Instance to the specified snapshot', 'VirtualMachine', 15, 'Successfully restored snapshot for {{resourceName}} in {{extensionName}}', 'Restore snapshot failed for {{resourceName}}', 60);
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`('HyperV', 'DeleteSnapshot', 'Delete the specified snapshot', 'VirtualMachine', 15, 'Successfully deleted snapshot for {{resourceName}} in {{extensionName}}', 'Delete snapshot failed for {{resourceName}}', 60);
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`('HyperV', 'Suspend', 'Suspend the Instance by freezing its current state in RAM', 'VirtualMachine', 15, 'Successfully suspended {{resourceName}} in {{extensionName}}', 'Suspend failed for {{resourceName}}', 60);
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_IF_NOT_EXISTS`('HyperV', 'Resume', 'Resumes a suspended Instance, restoring CPU execution from memory.', 'VirtualMachine', 15, 'Successfully resumed {{resourceName}} in {{extensionName}}', 'Resume failed for {{resourceName}}', 60);
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS`('HyperV','ListSnapshots','[]');
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS`('HyperV','CreateSnapshot','[{"name":"snapshot_name","type":"STRING","validationformat":"NONE","required":true}]');
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS`('HyperV','RestoreSnapshot','[{"name":"snapshot_name","type":"STRING","validationformat":"NONE","required":true}]');
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS`('HyperV','DeleteSnapshot','[{"name":"snapshot_name","type":"STRING","validationformat":"NONE","required":true}]');
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS`('HyperV', 'Suspend', '[]');
CALL `cloud`.`INSERT_EXTENSION_CUSTOM_ACTION_DETAILS_IF_NOT_EXISTS`('HyperV', 'Resume', '[]');


-- Widen legacy VARCHAR columns without shrinking values from later versions.
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'networks' AND COLUMN_NAME = 'cidr' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH < 255),
    'ALTER TABLE cloud.networks MODIFY COLUMN cidr VARCHAR(255) DEFAULT NULL COMMENT ''CloudStack managed vms get IP address from cidr.In general this cidr also serves as the network CIDR. But in case IP reservation feature is being used by a Guest network, networkcidr is the Effective network CIDR for that network''', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'networks' AND COLUMN_NAME = 'gateway' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH < 255),
    'ALTER TABLE cloud.networks MODIFY COLUMN gateway VARCHAR(255) DEFAULT NULL COMMENT ''gateway(s) for this network configuration''', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'networks' AND COLUMN_NAME = 'ip6_cidr' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH < 1024),
    'ALTER TABLE cloud.networks MODIFY COLUMN ip6_cidr VARCHAR(1024) DEFAULT NULL COMMENT ''IPv6 cidr(s) for this network''', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'networks' AND COLUMN_NAME = 'ip6_gateway' AND DATA_TYPE = 'varchar' AND CHARACTER_MAXIMUM_LENGTH < 1024),
    'ALTER TABLE cloud.networks MODIFY COLUMN ip6_gateway VARCHAR(1024) DEFAULT NULL COMMENT ''IPv6 gateway(s) for this network''', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- Add backup name and description. backup_interval_type was retired in 4.22.1.0.
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.backups', 'name', 'VARCHAR(255) NULL COMMENT "name of the backup"');
-- @repair-target cloud.backups 82b4f56893fb5d852c866d88e953d2a064878bbafa6967b722de261ec70bf1c7
-- @repair-probe SELECT EXISTS (SELECT 1 FROM cloud.backups b JOIN cloud.vm_instance v ON b.vm_id=v.id WHERE (b.name IS NULL OR b.name='') AND v.name IS NOT NULL AND v.name<>'')
UPDATE `cloud`.`backups` backup INNER JOIN `cloud`.`vm_instance` vm ON backup.vm_id = vm.id
SET backup.name = vm.name
WHERE (backup.name IS NULL OR backup.name = '') AND vm.name IS NOT NULL AND vm.name <> '';
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'backups' AND COLUMN_NAME = 'name' AND COLUMN_TYPE = 'varchar(255)' AND IS_NULLABLE = 'YES'),
    'ALTER TABLE cloud.backups MODIFY COLUMN name VARCHAR(255) NOT NULL', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.backups', 'description', 'VARCHAR(1024) COMMENT "description for the backup"');

-- Create backup details table
CREATE TABLE IF NOT EXISTS `cloud`.`backup_details` (
  `id` bigint unsigned NOT NULL auto_increment,
  `backup_id` bigint unsigned NOT NULL COMMENT 'backup id',
  `name` varchar(255) NOT NULL,
  `value` TEXT NOT NULL,
  `display` tinyint(1) NOT NULL DEFAULT 1 COMMENT 'Should detail be displayed to the end user',
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_backup_details__backup_id` FOREIGN KEY `fk_backup_details__backup_id`(`backup_id`) REFERENCES `backups`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- Initialize missing backup volume metadata. Existing snapshots keep their recorded inventory.
UPDATE `cloud`.`backups` b
INNER JOIN `cloud`.`vm_instance` vm ON b.vm_id = vm.id
SET b.backed_volumes = (
    SELECT COALESCE(
        CAST(
            JSON_ARRAYAGG(
                JSON_OBJECT(
                     'uuid', v.uuid,
                     'type', v.volume_type,
                     'size', v.size,
                     'path', v.path,
                     'deviceId', v.device_id,
                     'diskOfferingId', doff.uuid,
                     'minIops', v.min_iops,
                     'maxIops', v.max_iops
                )
            ) AS CHAR
        ),
        '[]'
    )
    FROM `cloud`.`volumes` v
    LEFT JOIN `cloud`.`disk_offering` doff ON v.disk_offering_id = doff.id
    WHERE v.instance_id = vm.id
)
WHERE b.backed_volumes IS NULL OR b.backed_volumes = '';

-- Initialize missing VM backup volume metadata.
UPDATE `cloud`.`vm_instance` vm
SET vm.backup_volumes = (
    SELECT COALESCE(
        CAST(
            JSON_ARRAYAGG(
                JSON_OBJECT(
                     'uuid', v.uuid,
                     'type', v.volume_type,
                     'size', v.size,
                     'path', v.path,
                     'deviceId', v.device_id,
                     'diskOfferingId', doff.uuid,
                     'minIops', v.min_iops,
                     'maxIops', v.max_iops
                )
            ) AS CHAR
        ),
        '[]'
    )
    FROM `cloud`.`volumes` v
    LEFT JOIN `cloud`.`disk_offering` doff ON v.disk_offering_id = doff.id
    WHERE v.instance_id = vm.id
)
WHERE vm.backup_offering_id IS NOT NULL
AND (vm.backup_volumes IS NULL OR vm.backup_volumes = '');

-- Fill missing attributes only where the saved volume still exists on the same VM.
DROP PROCEDURE IF EXISTS `cloud`.`SCHEMA_42100_FILL_BACKUP_VOLUME_DETAILS`;
DELIMITER $$
CREATE PROCEDURE `cloud`.`SCHEMA_42100_FILL_BACKUP_VOLUME_DETAILS`()
BEGIN
    DECLARE finished BOOLEAN DEFAULT FALSE;
    DECLARE is_backup BOOLEAN;
    DECLARE record_id, owner_vm_id BIGINT UNSIGNED;
    DECLARE payload LONGTEXT;
    DECLARE entry_index INT;
    DECLARE entry_path VARCHAR(32);
    DECLARE saved_uuid, offering_uuid VARCHAR(255);
    DECLARE device_id_value, min_iops_value, max_iops_value BIGINT;
    DECLARE changed BOOLEAN;
    DECLARE inventories CURSOR FOR
        SELECT id, vm_id, backed_volumes, TRUE FROM cloud.backups WHERE JSON_VALID(backed_volumes)
        UNION ALL
        SELECT id, id, backup_volumes, FALSE FROM cloud.vm_instance
        WHERE backup_offering_id IS NOT NULL AND JSON_VALID(backup_volumes);
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = TRUE;

    OPEN inventories;
    inventory_loop: LOOP
        FETCH inventories INTO record_id, owner_vm_id, payload, is_backup;
        IF finished THEN LEAVE inventory_loop; END IF;
        IF JSON_TYPE(payload) <> 'ARRAY' THEN ITERATE inventory_loop; END IF;

        SET changed = FALSE;
        SET entry_index = 0;
        WHILE entry_index < JSON_LENGTH(payload) DO
            SET entry_path = CONCAT('$[', entry_index, ']');
            SET saved_uuid = JSON_UNQUOTE(JSON_EXTRACT(payload, CONCAT(entry_path, '.uuid')));
            IF saved_uuid IS NOT NULL AND NOT JSON_CONTAINS_PATH(payload, 'all',
                CONCAT(entry_path, '.deviceId'), CONCAT(entry_path, '.diskOfferingId'),
                CONCAT(entry_path, '.minIops'), CONCAT(entry_path, '.maxIops')) THEN
                SELECT MAX(v.device_id), MAX(d.uuid), MAX(v.min_iops), MAX(v.max_iops)
                INTO device_id_value, offering_uuid, min_iops_value, max_iops_value
                FROM cloud.volumes v LEFT JOIN cloud.disk_offering d ON v.disk_offering_id = d.id
                WHERE v.instance_id = owner_vm_id AND v.uuid = saved_uuid;

                IF device_id_value IS NOT NULL OR offering_uuid IS NOT NULL OR min_iops_value IS NOT NULL OR max_iops_value IS NOT NULL THEN
                    SET payload = JSON_INSERT(payload,
                        CONCAT(entry_path, '.deviceId'), device_id_value,
                        CONCAT(entry_path, '.diskOfferingId'), offering_uuid,
                        CONCAT(entry_path, '.minIops'), min_iops_value,
                        CONCAT(entry_path, '.maxIops'), max_iops_value);
                    SET changed = TRUE;
                END IF;
            END IF;
            SET entry_index = entry_index + 1;
        END WHILE;

        IF changed AND OCTET_LENGTH(payload) <= 65535 THEN
            IF is_backup THEN
                UPDATE cloud.backups SET backed_volumes = payload WHERE id = record_id;
            ELSE
                UPDATE cloud.vm_instance SET backup_volumes = payload WHERE id = record_id;
            END IF;
        END IF;
    END LOOP;
    CLOSE inventories;
END$$
DELIMITER ;
CALL `cloud`.`SCHEMA_42100_FILL_BACKUP_VOLUME_DETAILS`();
DROP PROCEDURE `cloud`.`SCHEMA_42100_FILL_BACKUP_VOLUME_DETAILS`;

-- Add column allocated_size to object_store table. Rename column 'used_bytes' to 'used_size'
CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.object_store', 'allocated_size', 'bigint unsigned COMMENT "allocated size in bytes"');
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'object_store' AND COLUMN_NAME = 'used_bytes'),
    'ALTER TABLE cloud.object_store RENAME COLUMN used_bytes TO used_size', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

-- The source size column has varied over time. Widen older integer types only.
SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'object_store' AND COLUMN_NAME = 'total_size'
    AND DATA_TYPE IN ('tinyint', 'smallint', 'mediumint', 'int')),
    'ALTER TABLE cloud.object_store MODIFY COLUMN total_size BIGINT UNSIGNED COMMENT ''total size in bytes''', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

UPDATE `cloud`.`object_store`
JOIN (
    SELECT object_store_id, SUM(quota) AS total_quota
    FROM `cloud`.`bucket`
    WHERE removed IS NULL
    GROUP BY object_store_id
) buckets_quota_sum_view ON `object_store`.id = buckets_quota_sum_view.object_store_id
SET `object_store`.allocated_size = buckets_quota_sum_view.total_quota
WHERE `object_store`.allocated_size IS NULL;

CALL `cloud`.`IDEMPOTENT_ADD_COLUMN`('cloud.console_session', 'domain_id', 'BIGINT UNSIGNED NULL');

UPDATE `cloud`.`console_session` cs
JOIN `cloud`.`account` acc ON acc.id = cs.account_id
SET cs.domain_id = acc.domain_id
WHERE (cs.domain_id IS NULL OR cs.domain_id = 0) AND acc.domain_id IS NOT NULL;

SET @sql := IF(EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = 'cloud'
    AND TABLE_NAME = 'console_session' AND COLUMN_NAME = 'domain_id'
    AND REPLACE(COLUMN_TYPE, '(20)', '') = 'bigint unsigned' AND IS_NULLABLE = 'YES'),
    'ALTER TABLE cloud.console_session MODIFY COLUMN domain_id BIGINT UNSIGNED NOT NULL', 'SELECT 1');
PREPARE s FROM @sql;
EXECUTE s;
DEALLOCATE PREPARE s;

DROP PROCEDURE `cloud`.`SCHEMA_42100_REQUIRE_FK`;
DROP PROCEDURE `cloud`.`SCHEMA_42100_REQUIRE`;
