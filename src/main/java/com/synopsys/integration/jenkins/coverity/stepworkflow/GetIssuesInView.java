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
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import java.io.IOException;
import java.util.Map;

import com.synopsys.integration.coverity.api.rest.ViewContents;
import com.synopsys.integration.coverity.api.ws.configuration.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectDataObj;
import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.stepworkflow.AbstractSupplyingSubStep;
import com.synopsys.integration.stepworkflow.SubStepResponse;

public class GetIssuesInView extends AbstractSupplyingSubStep<Integer> {
    private final ConfigurationServiceWrapper configurationServiceWrapper;
    private final ViewService viewService;
    private final String projectName;
    private final String viewName;
    private final CoverityJenkinsIntLogger logger;

    public GetIssuesInView(final CoverityJenkinsIntLogger logger, final ConfigurationServiceWrapper configurationServiceWrapper, final ViewService viewService, final String projectName, final String viewName) {
        this.logger = logger;
        this.configurationServiceWrapper = configurationServiceWrapper;
        this.viewService = viewService;
        this.projectName = projectName;
        this.viewName = viewName;
    }

    public SubStepResponse<Integer> run() {
        try {
            logger.alwaysLog(String.format("Checking for issues in project \"%s\", view \"%s\".", projectName, viewName));
            final String projectId = getProjectIdFromName(projectName);
            final String viewId = getViewIdFromName(viewName);

            final int defectCount;

            final ViewContents viewContents = viewService.getViewContents(projectId, viewId, 1, 0);
            if (null == viewContents) {
                defectCount = 0;
            } else {
                defectCount = viewContents.getTotalRows().intValue();
            }

            return SubStepResponse.SUCCESS(defectCount);
        } catch (final IOException | IntegrationException | CovRemoteServiceException_Exception e) {
            return SubStepResponse.FAILURE(e);
        }
    }

    private String getProjectIdFromName(final String projectName) throws CoverityJenkinsException, CovRemoteServiceException_Exception {
        return configurationServiceWrapper.getProjectByExactName(projectName)
                   .map(ProjectDataObj::getProjectKey)
                   .map(String::valueOf)
                   .orElseThrow(() -> new CoverityJenkinsException(String.format("Could not find the Id for project \"%s\". It either does not exist or the current user does not have access to it.", projectName)));
    }

    private String getViewIdFromName(final String viewName) throws IntegrationException, IOException {
        return viewService.getViews().entrySet().stream()
                   .filter(entry -> entry.getValue() != null)
                   .filter(entry -> entry.getValue().equals(viewName))
                   .findFirst()
                   .map(Map.Entry::getKey)
                   .map(String::valueOf)
                   .orElseThrow(() -> new CoverityJenkinsException(String.format("Could not find the Id for view \"%s\". It either does not exist or the current user does not have access to it.", viewName)));
    }

}
