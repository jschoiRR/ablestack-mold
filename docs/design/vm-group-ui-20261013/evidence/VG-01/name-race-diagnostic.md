<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements. See the NOTICE file
distributed with this work for additional information
regarding copyright ownership. The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied. See the License for the
specific language governing permissions and limitations
under the License.
-->

# First same-name race timeout and DB recovery

First attempt exceeded the 40-second client response timeout. InnoDB showed global redo log mutex waits and unrelated VM statistics COMMIT waits. No production source was changed based on this event.

```text
25590853	RUNNING	2026-10-13 15:09:35	NULL	354856	33	DELETE FROM vm_stats WHERE vm_stats.timestamp < '2026-10-13 05:09:35'
25590839	RUNNING	2026-10-13 15:09:17	NULL	354754	1	COMMIT
25590818	RUNNING	2026-10-13 15:08:55	NULL	354854	1	COMMIT
25590814	RUNNING	2026-10-13 15:08:54	NULL	354855	1	COMMIT
25590812	RUNNING	2026-10-13 15:08:53	NULL	354853	1	COMMIT
--Thread 139970963523136 has waited at log0meb.cc line 1781 for 0 seconds the semaphore:
Mutex at 0x7f4dd9da0600, Mutex LOG_FILES created log0log.cc:642, locked by 139971982890560
--Thread 139970960352832 has waited at log0meb.cc line 1781 for 0 seconds the semaphore:
Mutex at 0x7f4dd9da0600, Mutex LOG_FILES created log0log.cc:642, locked by 139971982890560
--Thread 139971201488448 has waited at log0meb.cc line 1781 for 41 seconds the semaphore:
Mutex at 0x7f4dd9da0600, Mutex LOG_FILES created log0log.cc:642, locked by 139971982890560
--Thread 139970957182528 has waited at log0meb.cc line 1781 for 0 seconds the semaphore:
Mutex at 0x7f4dd9da0600, Mutex LOG_FILES created log0log.cc:642, locked by 139971982890560
--Thread 139971913643584 has waited at log0meb.cc line 1781 for 51 seconds the semaphore:
Mutex at 0x7f4dd9da0600, Mutex LOG_FILES created log0log.cc:642, locked by 139971982890560
--Thread 139970966689344 has waited at log0meb.cc line 1781 for 28 seconds the semaphore:
Mutex at 0x7f4dd9da0600, Mutex LOG_FILES created log0log.cc:642, locked by 139971982890560
--Thread 139971911530048 has waited at log0meb.cc line 1781 for 31 seconds the semaphore:
Mutex at 0x7f4dd9da0600, Mutex LOG_FILES created log0log.cc:642, locked by 139971982890560
Mutex at 0x7f4dd9da0600, Mutex LOG_FILES created log0log.cc:642, locked by 139971982890560
--Thread 139971200431680 has waited at log0meb.cc line 1781 for 28 seconds the semaphore:
Mutex at 0x7f4dd9da0600, Mutex LOG_FILES created log0log.cc:642, locked by 139971982890560
--Thread 139971204654656 has waited at log0meb.cc line 1781 for 38 seconds the semaphore:
Mutex at 0x7f4dd9da0600, Mutex LOG_FILES created log0log.cc:642, locked by 139971982890560
--Thread 139971214165568 has waited at log0meb.cc line 1781 for 51 seconds the semaphore:
```

## Recheck

```text
Filesystem           Size  Used Avail Use% Mounted on
/dev/mapper/rl-root  383G   55G  329G  15% /
TIME	2026-10-13 15:10:58
PENDING_COMMIT	0
LOCK_WAIT	0
GROUPS	VG01-31-same-name-race	1
MAP	88c44092-351d-43d1-96e4-9d40d76295b7	1	VG01-31-same-name-race
MAP	c7eafff9-f50c-4f83-8b60-7b2493edc79d	0	NULL
Innodb_log_waits	0

```

## Retry
31: 6 successful requests, one group, 39.316 seconds. 32: 6 successful requests, one group, 10.124 seconds. These are functional checks, not a response-time SLA.
