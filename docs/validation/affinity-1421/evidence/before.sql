SELECT 'group_order',uuid,id,name FROM affinity_group WHERE name LIKE 'qa-ag07-%' ORDER BY id;
SELECT 'mapping',g.uuid,v.uuid,v.state,v.instance_name FROM affinity_group_vm_map m JOIN affinity_group g ON g.id=m.affinity_group_id JOIN vm_instance v ON v.id=m.instance_id WHERE g.name LIKE 'qa-ag07-%' ORDER BY g.id,v.id;
SELECT 'shared_access',g.uuid,d.uuid,m.subdomain_access FROM affinity_group_domain_map m JOIN affinity_group g ON g.id=m.affinity_group_id JOIN domain d ON d.id=m.domain_id WHERE g.name LIKE 'qa-ag07-%' ORDER BY g.id;
SELECT 'pending_jobs',COUNT(*) FROM async_job WHERE job_status=0;
