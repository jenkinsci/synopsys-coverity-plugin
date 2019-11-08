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

import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.coverity.api.ws.configuration.ConfigurationService;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectFilterSpecDataObj;
import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.function.ThrowingSupplier;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.rest.client.ConnectionResult;

/**
 * This class wraps all UI-based WS code to force Jenkins to use the plugin classloader instead of the war classloader.
 * This is necessary for Java 11+ as we rely on our Java-WS dependencies to make up for the classes removed from the JDK.
 */
public class UiWebServices {
    private final IntLogger logger;

    private UiWebServices(final IntLogger logger) {
        this.logger = logger;
    }

    public static UiWebServices withLogger(final IntLogger logger) {
        return new UiWebServices(logger);
    }

    public ConnectionResult attemptConnectionTo(final CoverityConnectInstance coverityConnectInstance) throws CoverityIntegrationException {
        return wrapWithClassClassloader(() -> {
            final String url = coverityConnectInstance.getCoverityURL().map(URL::toString).orElse(StringUtils.EMPTY);
            final String username = coverityConnectInstance.getCoverityUsername().orElse(null);
            final String password = coverityConnectInstance.getCoverityPassword().orElse(null);

            final CoverityServerConfig coverityServerConfig = CoverityServerConfig.newBuilder().setUrl(url)
                                                                  .setUsername(username)
                                                                  .setPassword(password)
                                                                  .build();

            coverityServerConfig.createWebServiceFactory(logger).connect();
            return coverityServerConfig.attemptConnection(logger);
        });
    }

    public List<ProjectDataObj> getAllProjects(final CoverityConnectInstance coverityConnectInstance) throws CoverityIntegrationException {
        return wrapWithClassClassloader(() -> {
            logger.info("Attempting retrieval of Coverity Projects.");
            final CoverityServerConfig coverityServerConfig = coverityConnectInstance.getCoverityServerConfig();
            final WebServiceFactory webServiceFactory = coverityServerConfig.createWebServiceFactory(logger);
            final ConfigurationService configurationService = webServiceFactory.createConfigurationService();
            final ProjectFilterSpecDataObj projectFilterSpecDataObj = new ProjectFilterSpecDataObj();
            final List<ProjectDataObj> projects = configurationService.getProjects(projectFilterSpecDataObj);
            logger.info("Completed retrieval of Coverity Projects.");
            return projects;
        });
    }

    public Map<Long, String> getAllViews(final CoverityConnectInstance coverityConnectInstance) throws CoverityIntegrationException {
        return wrapWithClassClassloader(() -> {
            logger.info("Attempting retrieval of Coverity Views.");
            final CoverityServerConfig coverityServerConfig = coverityConnectInstance.getCoverityServerConfig();
            final WebServiceFactory webServiceFactory = coverityServerConfig.createWebServiceFactory(logger);
            final ViewService viewService = webServiceFactory.createViewService();
            final Map<Long, String> viewsMap = viewService.getViews();
            logger.info("Completed retrieval of Coverity Views.");
            return viewsMap;
        });
    }

    private <R, E extends Throwable> R wrapWithClassClassloader(final ThrowingSupplier<R, E> supplierToWrap) throws CoverityIntegrationException {
        final Thread thisThread = Thread.currentThread();
        final ClassLoader threadClassloader = thisThread.getContextClassLoader();
        try {
            thisThread.setContextClassLoader(this.getClass().getClassLoader());
            return supplierToWrap.get();
        } catch (final Throwable e) {
            throw new CoverityIntegrationException("An unexpected exception occurred: " + e.getClass().getSimpleName(), e);
        } finally {
            thisThread.setContextClassLoader(threadClassloader);
        }
    }

}
