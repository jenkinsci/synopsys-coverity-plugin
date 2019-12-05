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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.coverity.ws.CoverityPhoneHomeHelper;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.jenkins.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityGlobalConfig;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.phonehome.PhoneHomeResponse;

import jenkins.model.GlobalConfiguration;

public class GlobalValueHelper {
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

    public static CoverityConnectInstance getCoverityInstanceWithUrlOrDie(final IntLogger logger, final String coverityInstanceUrl) throws CoverityIntegrationException {
        return getCoverityInstanceWithUrl(logger, coverityInstanceUrl).orElseThrow(() -> new CoverityIntegrationException("No Coverity Connect instance is configured with the url " + coverityInstanceUrl));
    }

    public static WebServiceFactory createWebServiceFactoryFromUrl(final IntLogger logger, final String coverityInstanceUrl) throws CoverityJenkinsException {
        try {
            return getCoverityInstanceWithUrl(logger, coverityInstanceUrl)
                       .map(CoverityConnectInstance::getCoverityServerConfig)
                       .map(coverityServerConfig -> coverityServerConfig.createWebServiceFactory(logger))
                       .orElseThrow(
                           () -> new CoverityJenkinsException("Could not connect to Coverity Connect instance with the URL \"" + coverityInstanceUrl + "\". Please validate your connection to this server in the Jenkins System config."));
        } catch (final RuntimeException e) {
            throw new CoverityJenkinsException("There was an error connecting to the Coverity Connect instance with the URL \"" + coverityInstanceUrl + "\"", e);
        }
    }

    public static List<CoverityConnectInstance> getGlobalCoverityConnectInstances() {
        return Optional.ofNullable(getCoverityGlobalConfig())
                   .map(CoverityGlobalConfig::getCoverityConnectInstances)
                   .orElseGet(Collections::emptyList);
    }

    private static CoverityGlobalConfig getCoverityGlobalConfig() {
        return GlobalConfiguration.all().get(CoverityGlobalConfig.class);
    }

    public static PhoneHomeResponse phoneHome(final IntLogger logger, final String coverityInstanceUrl) {
        PhoneHomeResponse phoneHomeResponse = null;
        final ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            final WebServiceFactory webServiceFactory = createWebServiceFactoryFromUrl(logger, coverityInstanceUrl);
            webServiceFactory.connect();

            final Map<String, String> metaData = new HashMap<>();
            final CoverityPhoneHomeHelper coverityPhoneHomeHelper = CoverityPhoneHomeHelper.createAsynchronousPhoneHomeHelper(webServiceFactory, webServiceFactory.createConfigurationService(), executor);
            metaData.put("jenkins.version", JenkinsVersionHelper.getJenkinsVersion());
            phoneHomeResponse = coverityPhoneHomeHelper.handlePhoneHome("synopsys-coverity", JenkinsVersionHelper.getPluginVersion("synopsys-coverity"), metaData);
        } catch (final Exception e) {
            logger.debug(e.getMessage(), e);
        } finally {
            executor.shutdownNow();
        }

        return phoneHomeResponse;
    }
}
