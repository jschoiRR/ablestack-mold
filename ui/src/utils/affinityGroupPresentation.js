// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.

const policies = {
  'host affinity': 'strict.affinity',
  'host anti-affinity': 'strict.anti',
  'non-strict host affinity': 'non.strict.affinity',
  'non-strict host anti-affinity': 'non.strict.anti',
  ExplicitDedication: 'dedicated'
}

export const affinityPolicy = type => policies[type] || ''
export const affinityResultTone = status => status === 'succeeded' ? 'success' : status === 'failed' ? 'error' : ['unknown', 'timeout', 'cancelled'].includes(status) ? 'warning' : 'processing'
