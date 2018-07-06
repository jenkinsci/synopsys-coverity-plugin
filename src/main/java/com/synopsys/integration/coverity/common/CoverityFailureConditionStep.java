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
package com.synopsys.integration.coverity.common;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.log.IntLogger;
import com.synopsys.integration.coverity.JenkinsCoverityInstance;
import com.synopsys.integration.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.config.CoverityServerConfigBuilder;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.v9.ConfigurationService;
import com.synopsys.integration.coverity.ws.v9.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.ws.v9.ProjectDataObj;
import com.synopsys.integration.coverity.ws.v9.ProjectFilterSpecDataObj;
import com.synopsys.integration.coverity.ws.view.ViewContents;
import com.synopsys.integration.coverity.ws.view.ViewService;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public class CoverityFailureConditionStep extends BaseCoverityStep {

    public CoverityFailureConditionStep(Node node, TaskListener listener, EnvVars envVars, FilePath workspace, Run run) {
        super(node, listener, envVars, workspace, run);
    }

    public boolean runCommonCoverityFailureStep(Optional<String> optionalBuildStateOnFailure, Optional<String> optionalProjectName,
            Optional<String> optionalViewName) {
        final JenkinsCoverityLogger logger = createJenkinsCoverityLogger();
        try {
            if (!shouldRunFailureStep(logger, optionalBuildStateOnFailure, optionalProjectName, optionalViewName)) {
                logger.warn("Skipping Synopsys Coverity failure condition check.");
                getRun().setResult(Result.UNSTABLE);
                return false;
            }
            JenkinsCoverityInstance coverityInstance = getCoverityInstance();
            if (!validateFailureStepConfiguration(logger, coverityInstance)) {
                logger.error("Synopsys Coverity failure condition configuration is invalid.");
                getRun().setResult(Result.FAILURE);
                return false;
            }

            String buildStateOnFailureString = optionalBuildStateOnFailure.orElse("");
            BuildState buildStateForIssues = BuildState.valueOf(buildStateOnFailureString);
            String projectName = handleVariableReplacement(getEnvVars(), optionalProjectName.orElse(""));
            String viewName = handleVariableReplacement(getEnvVars(), optionalViewName.orElse(""));

            logGlobalConfiguration(coverityInstance, logger);
            logFailureConditionConfiguration(buildStateForIssues, projectName, viewName, logger);

            if (BuildState.NONE == buildStateForIssues) {
                logger.error(String.format("Skipping the Failure Condition check because the Build state configured is '%s'.", buildStateForIssues.getDisplayValue()));
            } else {
                logger.alwaysLog("Checking Synopsys Coverity Failure conditions.");
                CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
                URL coverityURL = coverityInstance.getCoverityURL().get();
                builder.url(coverityURL.toString());
                builder.username(coverityInstance.getCoverityUsername().orElse(null));
                builder.password(coverityInstance.getCoverityPassword().orElse(null));

                CoverityServerConfig coverityServerConfig = builder.build();
                WebServiceFactory webServiceFactory = new WebServiceFactory(coverityServerConfig, logger);
                webServiceFactory.connect();

                boolean errorWithProjectOrView = false;
                Optional<String> optionalProjectId = getProjectIdFromName(projectName, webServiceFactory.createConfigurationService());
                if (!optionalProjectId.isPresent()) {
                    logger.error(String.format("Could not find the Id for project \"%s\". It no longer exists or the current user does not have access to it.", projectName));
                    errorWithProjectOrView = true;
                }
                ViewService viewService = webServiceFactory.createViewService();
                Optional<String> optionalViewId = getViewIdFromName(viewName, viewService);
                if (!optionalViewId.isPresent()) {
                    logger.error(String.format("Could not find the Id for view \"%s\". It no longer exists or the current user does not have access to it.", viewName));
                    errorWithProjectOrView = true;
                }
                if (errorWithProjectOrView) {
                    logger.error("Skipping the Failure Condition check because of problems with the Project or View.");
                    getRun().setResult(Result.FAILURE);
                    return false;
                }

                String projectId = optionalProjectId.orElse("");
                String viewId = optionalViewId.orElse("");

                int defectSize = getIssueCountVorView(projectId, viewId, viewService, logger);
                logger.info(String.format("[Coverity] Found %s issues for project \"%s\" and view \"%s\"", defectSize, projectName, viewName));

                if (defectSize > 0) {
                    getRun().setResult(buildStateForIssues.getResult());
                }
            }
        } catch (final Exception e) {
            logger.error("[ERROR] " + e.getMessage(), e);
            getRun().setResult(Result.UNSTABLE);
            return false;
        }
        return true;
    }

    private boolean shouldRunFailureStep(JenkinsCoverityLogger logger, Optional<String> optionalBuildStateOnFailure,
            Optional<String> optionalProjectName, Optional<String> optionalViewName) {
        Boolean shouldContinue = true;
        if (!optionalBuildStateOnFailure.isPresent()) {
            logger.debug("Missing build state to set on failure.");
            shouldContinue = false;
        }
        if (!optionalProjectName.isPresent()) {
            logger.warn("There was no Coverity project name provided.");
            shouldContinue = false;
        }
        if (!optionalViewName.isPresent()) {
            logger.warn("There was no Coverity view name provided.");
            shouldContinue = false;
        }
        return shouldContinue;
    }

    private Boolean validateFailureStepConfiguration(JenkinsCoverityLogger logger, JenkinsCoverityInstance coverityInstance) {
        Boolean shouldContinue = true;

        if (null == coverityInstance) {
            logger.error("No global Synopsys Coverity configuration found.");
            shouldContinue = false;
        } else {
            Optional<URL> optionalCoverityURL = coverityInstance.getCoverityURL();
            if (!optionalCoverityURL.isPresent()) {
                logger.error("No Coverity URL configured.");
                shouldContinue = false;
            }
            Optional<String> optionalCoverityUsername = coverityInstance.getCoverityUsername();
            if (!optionalCoverityUsername.isPresent()) {
                logger.error("No Coverity Username configured.");
                shouldContinue = false;
            }
            Optional<String> optionalCoverityPassword = coverityInstance.getCoverityPassword();
            if (!optionalCoverityPassword.isPresent()) {
                logger.error("No Coverity Password configured.");
                shouldContinue = false;
            }
        }
        return shouldContinue;
    }

    private void logFailureConditionConfiguration(BuildState buildState, String projectName, String viewName, IntLogger logger) {
        logger.alwaysLog("-- Build state for issues in the view : " + buildState.getDisplayValue());
        logger.alwaysLog("-- Coverity project name : " + projectName);
        logger.alwaysLog("-- Coverity view name : " + viewName);
    }

    private Optional<String> getProjectIdFromName(String projectName, ConfigurationService configurationService) throws CovRemoteServiceException_Exception {
        ProjectFilterSpecDataObj projectFilterSpecDataObj = new ProjectFilterSpecDataObj();
        List<ProjectDataObj> projects = configurationService.getProjects(projectFilterSpecDataObj);
        for (ProjectDataObj projectDataObj : projects) {
            if (null != projectDataObj.getId() && null != projectDataObj.getId().getName() && projectDataObj.getId().getName().equals(projectName)) {
                if (null != projectDataObj.getProjectKey()) {
                    return Optional.of(String.valueOf(projectDataObj.getProjectKey()));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> getViewIdFromName(String viewName, ViewService viewService) throws IntegrationException, IOException, URISyntaxException {
        Map<Long, String> views = viewService.getViews();
        for (Map.Entry<Long, String> view : views.entrySet()) {
            if (view.getValue().equals(viewName)) {
                return Optional.of(String.valueOf(view.getKey()));
            }
        }
        return Optional.empty();
    }

    private int getIssueCountVorView(String projectId, String viewId, ViewService viewService, IntLogger logger) throws IntegrationException {
        try {
            int pageSize = 1;
            int defectSize = 0;

            final ViewContents viewContents = viewService.getViewContents(projectId, viewId, pageSize, 0);

            defectSize = viewContents.totalRows.intValue();

            return defectSize;
        } catch (IOException | URISyntaxException e) {
            throw new CoverityIntegrationException(e.getMessage(), e);
        }
    }

}
