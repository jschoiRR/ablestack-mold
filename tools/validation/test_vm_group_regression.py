#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
# http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
import importlib.util
import os
from pathlib import Path
import types
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('vg06', Path(__file__).with_name('vm_group_regression.py'))
vg = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vg)
VM = '11111111-1111-4111-8111-111111111111'
GROUP = '22222222-2222-4222-8222-222222222222'


class GuardTests(unittest.TestCase):
    def probe(self):
        p = vg.Probe.__new__(vg.Probe)
        p.site = '31'; p.prefix = 'VG06-31-test-'; p.api = Mock()
        p.data = {'vms': [{'id': VM, 'name': p.prefix+'VM', 'fixture_owner': 'admin'}],
                  'groups': [{'id': GROUP, 'name': p.prefix+'A', 'fixture_owner': 'project', 'fixture_suffix': 'A'}],
                  'project': {'id': 'project-id'}}
        return p

    def test_foreign_namespace_is_rejected_before_login(self):
        with patch.object(vg, 'Client') as client, self.assertRaises(AssertionError):
            vg.Probe({'site': '31', 'prefix': 'production-'})
        client.assert_not_called()

    def test_wrong_live_vm_name_is_rejected(self):
        p = self.probe(); p.api.call.return_value = {'virtualmachine': [{'id': VM, 'name': 'existing-production-vm'}]}
        with self.assertRaises(AssertionError): p.vm(p.data['vms'][0])
        p.api.wait.assert_not_called()

    def test_wrong_live_group_name_is_rejected(self):
        p = self.probe(); p.api.call.return_value = {'instancegroup': [{'id': GROUP, 'name': 'existing-production-group'}]}
        with self.assertRaises(AssertionError): p.group('project', 'A')
        p.api.wait.assert_not_called()

    def test_project_group_read_has_project_scope(self):
        p = self.probe(); p.api.call.return_value = {'instancegroup': [{'id': GROUP, 'name': p.prefix+'A'}]}
        p.group('project', 'A')
        p.api.call.assert_called_once_with('listInstanceGroups', id=GROUP, listall=True, projectid='project-id')

    def test_generated_remote_script_compiles_and_argv_has_no_secret(self):
        p = self.probe()
        with patch.dict(os.environ, {'CLOUD_DB_PASSWORD': 'dummy-db-quote\'value', 'CLOUD_SSH_PASSWORD': 'dummy-ssh'}), patch.object(vg.subprocess, 'run') as run:
            def fake(args, **kwargs):
                compile(kwargs['input'], '<remote>', 'exec')
                self.assertNotIn('dummy-db', ' '.join(args)); self.assertNotIn('dummy-ssh', ' '.join(args))
                return types.SimpleNamespace(returncode=0, stdout='{}', stderr='')
            run.side_effect = fake; self.assertEqual(p.db(), {})

    def test_remote_failure_redacts_environment_credentials(self):
        p = self.probe()
        with patch.dict(os.environ, {'CLOUD_DB_PASSWORD': 'dummy-db', 'CLOUD_SSH_PASSWORD': 'dummy-ssh'}), patch.object(vg.subprocess, 'run', return_value=types.SimpleNamespace(returncode=1, stdout='', stderr='failure dummy-db dummy-ssh')):
            with self.assertRaises(RuntimeError) as result: p.db()
        self.assertNotIn('dummy-db', str(result.exception)); self.assertNotIn('dummy-ssh', str(result.exception))

    def test_snapshot_never_changes_membership_or_creates_groups(self):
        p = self.probe(); p.resources = Mock(return_value={'safe': True}); p.db = Mock(return_value={'maps': {VM: []}})
        p.vm = Mock(return_value={'id': VM}); p.change = Mock(); p.create_group = Mock()
        result = p.run('snapshot', 3)
        self.assertEqual(result['resources'], {'safe': True}); p.change.assert_not_called(); p.create_group.assert_not_called()

    def test_async_failure_omits_raw_job_payload(self):
        c = vg.Client.__new__(vg.Client); c.call = Mock(return_value={'jobstatus': 2, 'jobresultcode': 530, 'jobresult': {'password': 'dummy-secret'}})
        with self.assertRaises(RuntimeError) as result: c.wait({'jobid': 'job'})
        self.assertNotIn('dummy-secret', str(result.exception))

    def test_async_timeout_is_bounded(self):
        c = vg.Client.__new__(vg.Client); c.call = Mock()
        with patch.object(vg.time, 'monotonic', side_effect=[0, 301]), self.assertRaises(TimeoutError): c.wait({'jobid': 'job'})
        c.call.assert_not_called()


if __name__ == '__main__': unittest.main()
