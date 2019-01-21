/**
 * synopsys-coverity
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.jenkins.coverity;

import java.util.Optional;
import java.util.stream.Stream;

import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityGlobalConfig;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityToolInstallation;
import com.synopsys.integration.log.IntLogger;

import hudson.Plugin;
import hudson.PluginWrapper;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

public class PluginHelper {
    public static final String UNKNOWN_VERSION = "<unknown>";

    public static String getPluginVersion() {
        String pluginVersion = UNKNOWN_VERSION;
        final Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            // Jenkins still active
            final Plugin p = jenkins.getPlugin("synopsys-coverity");
            if (p != null) {
                // plugin found
                final PluginWrapper pw = p.getWrapper();
                if (pw != null) {
                    pluginVersion = pw.getVersion();
                }
            }
        }
        return pluginVersion;
    }

    public static CoverityGlobalConfig getCoverityGlobalConfig() {
        return GlobalConfiguration.all().get(CoverityGlobalConfig.class);
    }

    public static CoverityConnectInstance[] getCoverityConnectInstances() {
        return getCoverityGlobalConfig().getCoverityConnectInstances();
    }

    public static CoverityToolInstallation[] getCoverityToolInstallations() {
        return getCoverityGlobalConfig().getCoverityToolInstallations();
    }

    public static Optional<CoverityConnectInstance> getCoverityInstanceFromUrl(final IntLogger logger, final String coverityInstanceUrl) {
        final CoverityConnectInstance[] coverityInstances = getCoverityGlobalConfig().getCoverityConnectInstances();
        if (null == coverityInstances || coverityInstances.length == 0) {
            logger.error("[ERROR] No Coverity instance configured in Jenkins.");
        } else {
            return Stream.of(coverityInstances)
                       .filter(coverityInstance -> coverityInstance.getUrl().equals(coverityInstanceUrl))
                       .findFirst();
        }

        return Optional.empty();
    }

}
