#!/usr/bin/env python3
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

"""Run activity scripts with fake BMC/storage commands; no external systems."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPTS = Path(__file__).parent / "vm/hypervisor/kvm"


class ActivityScriptTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.command("date", 'if [ "$1" = "+%s" ]; then echo 1000; else echo timestamp; fi')
        self.command("logger", "exit 0")
        self.command("timeout", 'if [ "$1" = 1 ]; then exit 1; fi; shift; exec "$@"')
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"])

    def tearDown(self):
        self.tmp.cleanup()

    def command(self, name, body):
        path = self.bin / name
        path.write_text("#!/bin/bash\n" + body + "\n")
        path.chmod(0o755)

    def rbd(self, heartbeat="900", status="Watchers: none", error=False):
        self.command("rbd", 'if [[ " $* " == *" image-meta "* ]]; then echo ' + heartbeat +
                     '; else ' + ('exit 1' if error else "echo '" + status + "'") + '; fi')

    def run_rbd(self):
        return subprocess.run(["bash", str(SCRIPTS / "kvmvmactivity_rbd.sh"), "-p", "pool", "-n", "test", "-s", "key",
                               "-h", "127.0.0.1", "-i", "127.0.0.1", "-u", "volume", "-t", "60"],
                              env=self.env, capture_output=True, text=True, timeout=5)

    def test_fresh_heartbeat_is_alive(self):
        self.rbd(heartbeat="990")
        self.assertIn("[HOST STATE : ALIVE]", self.run_rbd().stdout)

    def test_no_watchers_is_known_inactive(self):
        self.rbd()
        self.assertIn("[HOST STATE : DEAD]", self.run_rbd().stdout)

    def test_storage_query_failure_is_unknown(self):
        self.rbd(error=True)
        result = self.run_rbd()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("[HOST STATE : UNKNOWN]", result.stdout)
        self.assertNotIn("[HOST STATE : DEAD]", result.stdout)

    def test_unrecognized_watcher_output_is_unknown(self):
        self.rbd(status="unexpected success output")
        self.assertIn("[HOST STATE : UNKNOWN]", self.run_rbd().stdout)

    def test_closed_network_port_does_not_prove_dead(self):
        self.rbd(status="watcher=127.0.0.1:0/1 client.1 cookie=1")
        self.assertIn("[HOST STATE : UNKNOWN]", self.run_rbd().stdout)

    def test_invalid_heartbeat_is_unknown(self):
        self.rbd(heartbeat="invalid")
        self.assertIn("[HOST STATE : UNKNOWN]", self.run_rbd().stdout)

    def test_future_heartbeat_is_unknown(self):
        self.rbd(heartbeat="1100")
        self.assertIn("[HOST STATE : UNKNOWN]", self.run_rbd().stdout)

    def test_missing_nfs_heartbeat_is_unknown(self):
        result = subprocess.run(["bash", str(SCRIPTS / "kvmvmactivity.sh"), "-i", "127.0.0.1", "-m", str(self.root),
                                 "-h", "host", "-u", "volume", "-d", "900", "-t", "1000"],
                                env=self.env, capture_output=True, text=True, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("[HOST STATE : UNKNOWN]", result.stdout)

    def test_gfs_missing_volume_is_unknown(self):
        hb = self.root / "MOLD-HB"
        hb.mkdir()
        (hb / ("host" + str(self.root).replace("/", "-"))).write_text("900")
        self.command("stat", "exit 1")
        result = subprocess.run(["bash", str(SCRIPTS / "kvmvmactivity_gfs.sh"), "-m", str(self.root),
                                 "-h", "host", "-u", "missing", "-d", "900", "-t", "1000", "-i", "60"],
                                env=self.env, capture_output=True, text=True, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("[HOST STATE : UNKNOWN]", result.stdout)
        self.assertNotIn("[HOST STATE : DEAD]", result.stdout)

    def test_clvm_stale_heartbeat_does_not_inspect_neighbour_processes(self):
        hb = self.root / "MOLD-HB"
        hb.mkdir()
        (hb / "host-pool").write_text("900")
        self.command("ps", "echo unexpected-local-query; exit 1")
        result = subprocess.run(["bash", str(SCRIPTS / "kvmvmactivity_clvm.sh"), "-g", str(self.root),
                                 "-h", "host", "-u", "volume", "-q", "/pool", "-d", "900", "-t", "60"],
                                env=self.env, capture_output=True, text=True, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("[HOST STATE : UNKNOWN]", result.stdout)
        self.assertNotIn("unexpected-local-query", result.stdout)

    def test_clvm_bad_heartbeat_with_no_volumes_is_unknown(self):
        self.command("rbd", "exit 1")
        result = subprocess.run(["bash", str(SCRIPTS / "kvmvmactivity_clvm.sh"), "-p", "pool", "-n", "test",
                                 "-h", "host", "-q", "/pool", "-d", "900", "-t", "60"],
                                env=self.env, capture_output=True, text=True, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("[HOST STATE : UNKNOWN]", result.stdout)
        self.assertNotIn("[HOST STATE : DEAD]", result.stdout)

    def run_nfs_heartbeat(self, timestamp):
        hb = self.root / "MOLD-HB"
        hb.mkdir(exist_ok=True)
        (hb / "host").write_text(timestamp)
        self.command("mount", "exit 0")
        return subprocess.run(["bash", str(SCRIPTS / "kvmheartbeat.sh"), "-i", "127.0.0.1", "-m", str(self.root),
                               "-h", "host", "-r", "-t", "60"], env=self.env, capture_output=True, text=True, timeout=5)

    def test_health_heartbeat_invalid_timestamp_is_unknown(self):
        result = self.run_nfs_heartbeat("invalid")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("[HOST STATE : UNKNOWN]", result.stdout)
        self.assertNotIn("[HOST STATE : ALIVE]", result.stdout)

    def test_stale_nfs_heartbeat_does_not_wrap_exit_status(self):
        # Previously returning age256 as shell exitstatus wrapped to0(ALIVE).
        result = self.run_nfs_heartbeat("744")
        self.assertEqual(0, result.returncode)
        self.assertIn("[HOST STATE : DEAD]", result.stdout)

    def test_rbd_health_query_failure_is_unknown(self):
        self.command("rbd", "exit 1")
        result = subprocess.run(["bash", str(SCRIPTS / "kvmheartbeat_rbd.sh"), "-p", "pool", "-n", "test", "-s", "key",
                                 "-h", "127.0.0.1", "-i", "127.0.0.1", "-r", "-t", "60"],
                                env=self.env, capture_output=True, text=True, timeout=5)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("[HOST STATE : UNKNOWN]", result.stdout)
        self.assertNotIn("[HOST STATE : ALIVE]", result.stdout)


if __name__ == "__main__":
    unittest.main()
