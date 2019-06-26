/**
 * synopsys-coverity
 *
 * Copyright (c) 2019 Synopsys, Inc.
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityGlobalConfig;
import com.synopsys.integration.log.IntLogger;

import hudson.Plugin;
import hudson.PluginWrapper;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

public class GlobalValueHelper {
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

    public static String getJenkinsVersion() {
        return Jenkins.getVersion().toString();
    }

    public static Optional<CoverityConnectInstance> getCoverityInstanceWithUrl(final IntLogger logger, final String coverityInstanceUrl) {
        final List<CoverityConnectInstance> coverityInstances = getCoverityGlobalConfig().getCoverityConnectInstances();
        if (null == coverityInstances || coverityInstances.isEmpty()) {
            logger.error("[ERROR] No Coverity Connect instances are configured in the Jenkins system config.");
        } else {
            return coverityInstances.stream()
                       .filter(coverityInstance -> coverityInstance.getUrl().equals(coverityInstanceUrl))
                       .findFirst();
        }

        return Optional.empty();
    }

    public static Optional<WebServiceFactory> createWebServiceFactoryFromUrl(final IntLogger logger, final String coverityInstanceUrl) throws IllegalArgumentException {
        return getCoverityInstanceWithUrl(logger, coverityInstanceUrl)
                   .map(CoverityConnectInstance::getCoverityServerConfig)
                   .map(coverityServerConfig -> coverityServerConfig.createWebServiceFactory(logger));
    }

    public static List<CoverityConnectInstance> getGlobalCoverityConnectInstances() {
        return Optional.ofNullable(getCoverityGlobalConfig())
                   .map(CoverityGlobalConfig::getCoverityConnectInstances)
                   .orElseGet(Collections::emptyList);
    }

    private static CoverityGlobalConfig getCoverityGlobalConfig() {
        return GlobalConfiguration.all().get(CoverityGlobalConfig.class);
    }

}
