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
package com.synopsys.integration.jenkins.coverity.substeps;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.v9.ConfigurationService;
import com.synopsys.integration.coverity.ws.v9.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.ws.v9.ProjectDataObj;
import com.synopsys.integration.coverity.ws.v9.ProjectFilterSpecDataObj;
import com.synopsys.integration.coverity.ws.view.ViewContents;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;

public class GetIssuesInView {
    private final WebServiceFactory webServiceFactory;
    private final String projectName;
    private final String viewName;
    private final JenkinsCoverityLogger logger;

    public GetIssuesInView(final JenkinsCoverityLogger logger, final WebServiceFactory webServiceFactory, final String projectName, final String viewName) {
        this.logger = logger;
        this.webServiceFactory = webServiceFactory;
        this.projectName = projectName;
        this.viewName = viewName;
    }

    public Integer getTotalIssuesInView() throws IOException, IntegrationException, CovRemoteServiceException_Exception {
        logger.alwaysLog(String.format("Checking for issues in project \"%s\", view \"%s\".", projectName, viewName));

        webServiceFactory.connect();

        final ConfigurationService configurationService = webServiceFactory.createConfigurationService();
        final String projectId = getProjectIdFromName(projectName, configurationService);

        final ViewService viewService = webServiceFactory.createViewService();
        final String viewId = getViewIdFromName(viewName, viewService);

        final int defectCount;

        final ViewContents viewContents = viewService.getViewContents(projectId, viewId, 1, 0);
        if (null == viewContents) {
            defectCount = 0;
        } else {
            defectCount = viewContents.totalRows.intValue();
        }

        return defectCount;
    }

    private String getProjectIdFromName(final String projectName, final ConfigurationService configurationService) throws CovRemoteServiceException_Exception, CoverityJenkinsException {
        final ProjectFilterSpecDataObj projectFilterSpecDataObj = new ProjectFilterSpecDataObj();
        final List<ProjectDataObj> projects = configurationService.getProjects(projectFilterSpecDataObj);
        return projects.stream()
                   .filter(projectDataObj -> projectDataObjHasName(projectDataObj, projectName))
                   .findFirst()
                   .map(ProjectDataObj::getProjectKey)
                   .map(String::valueOf)
                   .orElseThrow(() -> new CoverityJenkinsException(String.format("Could not find the Id for project \"%s\". It either does not exist or the current user does not have access to it.", projectName)));
    }

    private boolean projectDataObjHasName(final ProjectDataObj projectDataObj, final String name) {
        return projectDataObj.getId() != null && projectDataObj.getId().getName() != null && projectDataObj.getId().getName().equals(name);
    }

    private String getViewIdFromName(final String viewName, final ViewService viewService) throws IntegrationException, IOException {
        return viewService.getViews().entrySet().stream()
                   .filter(entry -> entry.getValue() != null)
                   .filter(entry -> entry.getValue().equals(viewName))
                   .findFirst()
                   .map(Map.Entry::getKey)
                   .map(String::valueOf)
                   .orElseThrow(() -> new CoverityJenkinsException(String.format("Could not find the Id for view \"%s\". It either does not exist or the current user does not have access to it.", viewName)));
    }

}
