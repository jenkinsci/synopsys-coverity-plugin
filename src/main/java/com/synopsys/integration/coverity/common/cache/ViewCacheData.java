/**
 * synopsys-coverity
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
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
package com.synopsys.integration.coverity.common.cache;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.config.CoverityServerConfigBuilder;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.global.CoverityConnectInstance;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.LogLevel;
import com.synopsys.integration.log.PrintStreamIntLogger;

public class ViewCacheData extends BaseCacheData<String> {
    @Override
    public String getDataType() {
        return "View";
    }

    @Override
    public List<String> retrieveData(final CoverityConnectInstance coverityInstance) {
        final IntLogger logger = new PrintStreamIntLogger(System.out, LogLevel.DEBUG);
        List<String> data = Collections.emptyList();
        try {
            logger.info("Attempting retrieval of Coverity Views.");
            final CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
            builder.url(coverityInstance.getCoverityURL().map(URL::toString).orElse(null));
            builder.username(coverityInstance.getCoverityUsername().orElse(null));
            builder.password(coverityInstance.getCoverityPassword().orElse(null));

            final CoverityServerConfig coverityServerConfig = builder.build();
            final WebServiceFactory webServiceFactory = new WebServiceFactory(coverityServerConfig, logger);
            webServiceFactory.connect();

            final ViewService viewService = webServiceFactory.createViewService();
            logger.info("Completed retrieval of Coverity Views.");
            data = new ArrayList<>(viewService.getViews().values());
        } catch (IOException | IntegrationException | URISyntaxException e) {
            logger.error(e);
        }
        return data;
    }
}
