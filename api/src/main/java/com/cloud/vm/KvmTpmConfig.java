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
package com.cloud.vm;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.cloud.exception.InvalidParameterValueException;

/** Canonical KVM TPM configuration. Legacy API values remain input aliases. */
public final class KvmTpmConfig {
    public static final String HOST_VERSIONS = "host.tpm.versions";
    public static final String HOST_MODELS = "host.tpm.models";
    public static final String MODEL = VmDetailConstants.VIRTUAL_TPM_MODEL;
    public static final String VERSION = VmDetailConstants.VIRTUAL_TPM_VERSION;
    public static final String LEGACY = VmDetailConstants.TPM_VERSION;
    private final String model;
    private final String version;

    private KvmTpmConfig(String model, String version) {
        this.model = model;
        this.version = version;
    }

    public boolean isEnabled() { return model != null; }
    public String getModel() { return model; }
    public String getVersion() { return version; }

    public static boolean isKey(String key) {
        return LEGACY.equals(key) || MODEL.equals(key) || VERSION.equals(key);
    }

    public static boolean hasSettings(Map<String, String> details) {
        return details != null && details.keySet().stream().anyMatch(KvmTpmConfig::isKey);
    }

    private static String clean(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    public static String normalizeVersion(String value) {
        value = clean(value);
        if (value == null || "NONE".equalsIgnoreCase(value)) {
            return null;
        }
        switch (value.toUpperCase(Locale.ROOT)) {
            case "V1_2": case "1.2": return "1.2";
            case "V2_0": case "2.0": return "2.0";
            default: throw new InvalidParameterValueException("Invalid TPM version: " + value + ". Use NONE, 1.2 or 2.0.");
        }
    }

    /** Existing NONE was automatically persisted by older deploy APIs; modern model is authoritative there. */
    public static KvmTpmConfig resolve(Map<String, String> values, boolean newRequest) {
        Map<String, String> details = values == null ? Map.of() : values;
        String legacy = normalizeVersion(details.get(LEGACY));
        String model = clean(details.get(MODEL));
        String version = clean(details.get(VERSION));
        if (model == null) {
            if (version != null) {
                throw new InvalidParameterValueException("virtual.tpm.version requires virtual.tpm.model.");
            }
            return legacy == null ? new KvmTpmConfig(null, null) : new KvmTpmConfig("tpm-tis", legacy);
        }
        if (!Arrays.asList("tpm-tis", "tpm-crb").contains(model)) {
            throw new InvalidParameterValueException("Invalid TPM model: " + model);
        }
        version = version == null ? "2.0" : normalizeVersion(version);
        if (version == null || ("tpm-crb".equals(model) && !"2.0".equals(version))) {
            throw new InvalidParameterValueException("TPM CRB requires version 2.0; enabled TPM cannot use NONE.");
        }
        if ((legacy != null && !legacy.equals(version))
                || (newRequest && details.containsKey(LEGACY) && legacy == null)) {
            throw new InvalidParameterValueException("Conflicting legacy and virtual TPM settings.");
        }
        return new KvmTpmConfig(model, version);
    }

    public void apply(Map<String, String> details) {
        details.remove(MODEL);
        details.remove(VERSION);
        details.put(LEGACY, isEnabled() ? version : "NONE");
        if (isEnabled()) {
            details.put(MODEL, model);
            details.put(VERSION, version);
        }
    }

    public static Map<String, String> forCreation(Map<String, String> inherited, Map<String, String> request) {
        KvmTpmConfig requested = resolve(request, true);
        KvmTpmConfig effective = requested;
        if (hasSettings(inherited)) {
            effective = resolve(inherited, false);
            if (hasSettings(request) && !effective.equals(requested)) {
                throw new InvalidParameterValueException("TPM settings conflict with the inherited template TPM configuration.");
            }
        }
        Map<String, String> result = new HashMap<>(request);
        effective.apply(result);
        return result;
    }

    /** No metadata-only alteration of an existing TPM identity through generic details editing. */
    public static void validateUpdate(Map<String, String> oldDetails, Map<String, String> incoming, boolean cleanup) {
        Map<String, String> proposed = new HashMap<>(oldDetails);
        if (cleanup && resolve(oldDetails, false).isEnabled()) {
            throw new InvalidParameterValueException("TPM settings cannot be removed by cleanupdetails. Preserve the existing TPM state.");
        }
        if (!hasSettings(incoming)) { return; }
        proposed.putAll(incoming);
        if (incoming.containsKey(LEGACY) && !Objects.equals(incoming.get(LEGACY), oldDetails.get(LEGACY))
                && !Objects.equals(normalizeVersion(incoming.get(LEGACY)), resolve(oldDetails, false).getVersion())) {
            throw new InvalidParameterValueException("Changing an existing TPM version or disabling it requires a state-preserving operation.");
        }
        if (!resolve(oldDetails, false).equals(resolve(proposed, false))
                || (!Objects.equals(oldDetails.get(MODEL), proposed.get(MODEL)))) {
            throw new InvalidParameterValueException("Changing an existing VM TPM device requires a separate state-preserving operation.");
        }
    }

    public boolean supportedBy(Map<String, String> hostDetails) {
        return !isEnabled() || (hostDetails != null && "true".equals(hostDetails.get("host.tpm.enable"))
                && contains(hostDetails.get(HOST_VERSIONS), version) && contains(hostDetails.get(HOST_MODELS), model));
    }

    private static boolean contains(String csv, String value) {
        return csv != null && Arrays.asList(csv.split(",")).contains(value);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof KvmTpmConfig)) { return false; }
        KvmTpmConfig config = (KvmTpmConfig) other;
        return Objects.equals(model, config.model) && Objects.equals(version, config.version);
    }

    @Override public int hashCode() { return Objects.hash(model, version); }
}
