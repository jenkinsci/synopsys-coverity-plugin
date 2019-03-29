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
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.Slf4jIntLogger;

public class ViewCacheData extends BaseCacheData<String> {
    private final Logger logger = LoggerFactory.getLogger(ViewCacheData.class);

    @Override
    public String getDataType() {
        return "View";
    }

    @Override
    public List<String> retrieveData(final CoverityConnectInstance coverityInstance) {
        List<String> data = Collections.emptyList();
        try {
            logger.info("Attempting retrieval of Coverity Views.");
            final WebServiceFactory webServiceFactory = coverityInstance.getCoverityServerConfig().createWebServiceFactory(new Slf4jIntLogger(logger));
            webServiceFactory.connect();

            final ViewService viewService = webServiceFactory.createViewService();
            logger.info("Completed retrieval of Coverity Views.");
            data = new ArrayList<>(viewService.getViews().values());
        } catch (IOException | IntegrationException e) {
            logger.error(e.getMessage());
            logger.trace("Stack trace:", e);
        }
        return data;
    }
}
