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
package com.cloud.hypervisor.kvm.resource;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.cloud.vm.KvmTpmConfig;

/** Parse libvirt's actual software TPM capabilities, never a package/config-file heuristic. */
public final class TpmCapabilities {
    private TpmCapabilities() { }

    public static Map<String, String> parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        org.w3c.dom.Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        XPath xpath = XPathFactory.newDefaultInstance().newXPath();
        String base = "/domainCapabilities/devices/tpm[@supported='yes']";
        boolean emulator = (Boolean) xpath.evaluate("boolean(" + base + "/enum[@name='backendModel']/value[text()='emulator'])",
                document, javax.xml.xpath.XPathConstants.BOOLEAN);
        Map<String, String> result = new HashMap<>();
        String models = values(xpath, document, base + "/enum[@name='model']/value");
        String versions = values(xpath, document, base + "/enum[@name='backendVersion']/value");
        result.put("host.tpm.enable", Boolean.toString(emulator && !models.isEmpty() && !versions.isEmpty()));
        result.put(KvmTpmConfig.HOST_MODELS, emulator ? models : "");
        result.put(KvmTpmConfig.HOST_VERSIONS, emulator ? versions : "");
        return result;
    }

    private static String values(XPath xpath, org.w3c.dom.Document document, String path) throws Exception {
        NodeList nodes = (NodeList) xpath.evaluate(path, document, javax.xml.xpath.XPathConstants.NODESET);
        List<String> values = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) { values.add(nodes.item(i).getTextContent().trim()); }
        return String.join(",", values);
    }
}
