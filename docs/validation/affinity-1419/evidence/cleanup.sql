SELECT 'active_test_vms',COUNT(*) FROM vm_instance WHERE display_name LIKE 'qa-ag06-%' AND removed IS NULL;
SELECT 'test_affinity_groups',COUNT(*) FROM affinity_group WHERE name LIKE 'qa-ag06-%';
SELECT 'test_affinity_maps',COUNT(*) FROM affinity_group_vm_map m LEFT JOIN affinity_group g ON g.id=m.affinity_group_id LEFT JOIN vm_instance v ON v.id=m.instance_id WHERE g.name LIKE 'qa-ag06-%' OR v.display_name LIKE 'qa-ag06-%';
SELECT 'active_test_volumes',COUNT(*) FROM volumes v JOIN vm_instance i ON i.id=v.instance_id WHERE i.display_name LIKE 'qa-ag06-%' AND v.removed IS NULL;
SELECT 'active_test_nics',COUNT(*) FROM nics n JOIN vm_instance v ON v.id=n.instance_id WHERE v.display_name LIKE 'qa-ag06-%' AND n.removed IS NULL;
SELECT 'active_test_networks',COUNT(*) FROM networks WHERE name LIKE 'qa-ag06-%' AND removed IS NULL;
SELECT 'active_test_accounts',COUNT(*) FROM account WHERE (account_name LIKE 'qa-ag06-%' OR account_name LIKE 'PrjAcct-qa-ag06-project%') AND removed IS NULL;
SELECT 'active_test_users',COUNT(*) FROM user WHERE (username='qa-ag06-user' OR account_id IN (SELECT id FROM account WHERE account_name LIKE 'qa-ag06-%' OR account_name LIKE 'PrjAcct-qa-ag06-project%')) AND removed IS NULL;
SELECT 'active_test_projects',COUNT(*) FROM projects WHERE name='qa-ag06-project' AND removed IS NULL;
SELECT 'vm_audit',id,display_name,state,removed FROM vm_instance WHERE display_name LIKE 'qa-ag06-%' ORDER BY id;
SELECT 'test_roles',COUNT(*) FROM roles WHERE name='qa-ag06-readonly';

SELECT 'active_test_domains',COUNT(*) FROM domain WHERE name='qa-ag06-domain' AND removed IS NULL;
