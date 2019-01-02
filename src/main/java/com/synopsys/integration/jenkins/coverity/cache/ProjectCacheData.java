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
package com.synopsys.integration.jenkins.coverity.cache;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.config.CoverityServerConfigBuilder;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.v9.ConfigurationService;
import com.synopsys.integration.coverity.ws.v9.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.ws.v9.ProjectDataObj;
import com.synopsys.integration.coverity.ws.v9.ProjectFilterSpecDataObj;
import com.synopsys.integration.exception.EncryptionException;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.LogLevel;
import com.synopsys.integration.log.PrintStreamIntLogger;

public class ProjectCacheData extends BaseCacheData<ProjectDataObj> {
    @Override
    public String getDataType() {
        return "Project";
    }

    @Override
    public List<ProjectDataObj> retrieveData(final CoverityConnectInstance coverityInstance) {
        final IntLogger logger = new PrintStreamIntLogger(System.out, LogLevel.DEBUG);
        List<ProjectDataObj> projects = Collections.emptyList();
        try {
            logger.info("Attempting retrieval of Coverity Projects.");
            final CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
            builder.url(coverityInstance.getCoverityURL().map(URL::toString).orElse(null));
            builder.username(coverityInstance.getCoverityUsername().orElse(null));
            builder.password(coverityInstance.getCoverityPassword().orElse(null));

            final CoverityServerConfig coverityServerConfig = builder.build();
            final WebServiceFactory webServiceFactory = new WebServiceFactory(coverityServerConfig, logger);
            webServiceFactory.connect();

            final ConfigurationService configurationService = webServiceFactory.createConfigurationService();
            final ProjectFilterSpecDataObj projectFilterSpecDataObj = new ProjectFilterSpecDataObj();
            projects = configurationService.getProjects(projectFilterSpecDataObj);
            logger.info("Completed retrieval of Coverity Projects.");
        } catch (EncryptionException | MalformedURLException | CoverityIntegrationException | CovRemoteServiceException_Exception e) {
            logger.error(e);
        }
        return projects;
    }
}
