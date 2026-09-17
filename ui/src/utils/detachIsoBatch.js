// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

export async function detachIsoBatch ({ ids, submit, poll, onProgress, isCurrent = () => true }) {
  const results = []
  for (const id of ids) {
    if (!isCurrent()) break
    let result
    try {
      const response = await submit(id)
      if (!response?.jobid) {
        // Without a job ID, do not assume success or repeat a possibly accepted operation.
        result = { jobstatus: null, trackingStatus: 'unknown' }
      } else {
        result = await poll(response.jobid, id)
      }
    } catch (error) {
      // Transport failures can happen after acceptance. Only an explicit API error is a rejection.
      const data = error?.response?.data
      const apiError = data && typeof data === 'object' && Object.values(data).some(value => value?.errorcode && value?.errortext)
      result = apiError
        ? { jobstatus: 2, error }
        : { jobstatus: null, trackingStatus: 'unknown', error }
    }
    results.push({ id, ...result })
    onProgress(results)
    if (result.trackingStatus) break
  }
  return results
}
