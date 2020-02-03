/**
 * synopsys-coverity
 *
 * Copyright (c) 2020 Synopsys, Inc.
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
import java.util.Map;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.IntLogger;

public class ViewCache extends CoverityConnectDataCache<List<String>> {
    public ViewCache(final IntLogger logger) {
        super(logger);
    }

    @Override
    protected List<String> getFreshData(final WebServiceFactory webServiceFactory) {
        List<String> data = Collections.emptyList();

        try {
            logger.info("Attempting retrieval of Coverity Views.");
            final ViewService viewService = webServiceFactory.createViewService();
            final Map<Long, String> viewMap = viewService.getViews();
            data = new ArrayList<>(viewMap.values());
            logger.info("Completed retrieval of Coverity Views.");
        } catch (IOException | IntegrationException e) {
            logger.error(e.getMessage());
            logger.trace("Stack trace:", e);
        }

        return data;
    }
}
