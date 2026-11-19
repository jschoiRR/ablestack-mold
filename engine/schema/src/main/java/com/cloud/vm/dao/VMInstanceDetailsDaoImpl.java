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
package com.cloud.vm.dao;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import org.apache.cloudstack.resourcedetail.ResourceDetailsDaoBase;

import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.db.TransactionLegacy;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.vm.VMInstanceDetailVO;

@Component
public class VMInstanceDetailsDaoImpl extends ResourceDetailsDaoBase<VMInstanceDetailVO> implements VMInstanceDetailsDao {

    static final String VM_IDS_BY_SSH_KEYPAIR = "SELECT DISTINCT vm.id FROM vm_instance vm "
            + "JOIN vm_instance_details detail ON detail.vm_id = vm.id AND detail.name = 'SSH.PublicKey' "
            + "JOIN ssh_keypairs keypair ON keypair.account_id = vm.account_id AND keypair.domain_id = vm.domain_id "
            + "WHERE keypair.keypair_name = ? AND vm.type = 'User' AND vm.removed IS NULL "
            + "AND keypair.public_key IS NOT NULL AND LENGTH(keypair.public_key) > 0 "
            + "AND INSTR(BINARY CONCAT(CHAR(10), REPLACE(detail.value, CHAR(13), ''), CHAR(10)), "
            + "BINARY CONCAT(CHAR(10), REPLACE(keypair.public_key, CHAR(13), ''), CHAR(10))) > 0";

    @Override
    public List<Long> listVmIdsBySSHKeyPairName(String keyPairName, List<Long> accountIds) {
        if (StringUtils.isEmpty(keyPairName)) {
            return Collections.emptyList();
        }
        String sql = VM_IDS_BY_SSH_KEYPAIR;
        if (accountIds != null && !accountIds.isEmpty()) {
            sql += " AND vm.account_id IN (" + String.join(",", Collections.nCopies(accountIds.size(), "?")) + ")";
        }
        // Keys are stored as newline-separated public keys by VM deploy/reset. Match a whole key
        // in this VM's detail and owner namespace, without treating key material as LIKE patterns.
        List<Long> ids = new ArrayList<>();
        TransactionLegacy txn = TransactionLegacy.currentTxn();
        try (PreparedStatement statement = txn.prepareStatement(sql)) {
            statement.setString(1, keyPairName);
            if (accountIds != null) {
                for (int i = 0; i < accountIds.size(); i++) {
                    statement.setLong(i + 2, accountIds.get(i));
                }
            }
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    ids.add(results.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new CloudRuntimeException("Unable to look up virtual machines using the SSH key pair", e);
        }
        return ids;
    }


    @Override
    public void addDetail(long resourceId, String key, String value, boolean display) {
        super.addDetail(new VMInstanceDetailVO(resourceId, key, value, display));
    }

    @Override
    public int removeDetailsWithPrefix(long vmId, String prefix) {
        if (StringUtils.isBlank(prefix)) {
            return 0;
        }
        SearchBuilder<VMInstanceDetailVO> sb = createSearchBuilder();
        sb.and("vmId", sb.entity().getResourceId(), SearchCriteria.Op.EQ);
        sb.and("prefix", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.done();
        SearchCriteria<VMInstanceDetailVO> sc = sb.create();
        sc.setParameters("vmId", vmId);
        sc.setParameters("prefix", prefix + "%");
        return super.remove(sc);
    }
}
