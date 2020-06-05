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

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;

import com.synopsys.integration.coverity.api.ws.configuration.ConfigurationService;
import com.synopsys.integration.coverity.api.ws.configuration.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectFilterSpecDataObj;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.log.IntLogger;

public class ProjectStreamCache extends CoverityConnectDataCache<List<ProjectDataObj>> {
    public ProjectStreamCache(IntLogger logger) {
        super(logger);
    }

    @Override
    protected List<ProjectDataObj> getFreshData(WebServiceFactory webServiceFactory) {
        List<ProjectDataObj> projects = Collections.emptyList();
        try {
            logger.info("Attempting retrieval of Coverity Projects.");
            ConfigurationService configurationService = webServiceFactory.createConfigurationService();
            ProjectFilterSpecDataObj projectFilterSpecDataObj = new ProjectFilterSpecDataObj();
            projects = configurationService.getProjects(projectFilterSpecDataObj);
            logger.info("Completed retrieval of Coverity Projects.");
        } catch (MalformedURLException | CovRemoteServiceException_Exception e) {
            logger.error(e.getMessage());
            logger.trace("Stack trace:", e);
        }
        return projects;
    }

    @Override
    protected List<ProjectDataObj> getEmptyData() {
        return Collections.emptyList();
    }
}
