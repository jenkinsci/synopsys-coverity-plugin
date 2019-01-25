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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityGlobalConfig;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityToolInstallation;
import com.synopsys.integration.log.IntLogger;

import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.model.Node;
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

    public static Optional<CoverityToolInstallation> getCoverityToolInstallationWithName(final IntLogger logger, final String coverityToolName) {
        final List<CoverityToolInstallation> coverityToolInstallations = getCoverityGlobalConfig().getCoverityToolInstallations();
        if (null == coverityToolInstallations || coverityToolInstallations.isEmpty()) {
            logger.error("[ERROR] No Coverity Static Analysis installations are configured in the Jenkins system config.");
        } else {
            return coverityToolInstallations.stream()
                       .filter(coverityToolInstallation -> coverityToolInstallation.getName().equals(coverityToolName))
                       .findFirst();
        }

        return Optional.empty();
    }

    public static Optional<CoverityToolInstallation> getCoverityToolInstallationForNodeWithName(final JenkinsCoverityLogger logger, final String coverityToolName, final Node node) {
        return getCoverityToolInstallationWithName(logger, coverityToolName)
                   .map(coverityToolInstallation -> convertForNode(coverityToolInstallation, node, logger));
    }

    public static List<CoverityToolInstallation> getGlobalCoverityToolInstallations() {
        return Optional.ofNullable(getCoverityGlobalConfig())
                   .map(CoverityGlobalConfig::getCoverityToolInstallations)
                   .orElseGet(Collections::emptyList);
    }

    public static List<CoverityConnectInstance> getGlobalCoverityConnectInstances() {
        return Optional.ofNullable(getCoverityGlobalConfig())
                   .map(CoverityGlobalConfig::getCoverityConnectInstances)
                   .orElseGet(Collections::emptyList);
    }

    public static CoverityGlobalConfig getCoverityGlobalConfig() {
        return GlobalConfiguration.all().get(CoverityGlobalConfig.class);
    }

    private static CoverityToolInstallation convertForNode(final CoverityToolInstallation coverityToolInstallation, final Node node, final JenkinsCoverityLogger logger) {
        try {
            return coverityToolInstallation.forNode(node, logger.getJenkinsListener());
        } catch (final IOException | InterruptedException e) {
            logger.error("Problem getting the Synopsys Coverity Static Analysis tool on node " + node.getDisplayName() + ": " + e.getMessage());
            logger.debug(null, e);
        }

        return null;
    }

}
