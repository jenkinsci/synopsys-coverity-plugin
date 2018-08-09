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

    public CoverityFailureConditionStep(final Node node, final TaskListener listener, final EnvVars envVars, final FilePath workspace, final Run run) {
        super(node, listener, envVars, workspace, run);
    }

    public boolean runCommonCoverityFailureStep(final Optional<String> optionalBuildStateOnFailure, final Optional<String> optionalProjectName,
            final Optional<String> optionalViewName) {
        final JenkinsCoverityLogger logger = createJenkinsCoverityLogger();
        try {
            if (Result.SUCCESS != getResult()) {
                logger.alwaysLog("Skipping the Synopsys Coverity Failure Condition step because the build was not successful.");
                return false;
            }

            if (!shouldRunFailureStep(logger, optionalBuildStateOnFailure, optionalProjectName, optionalViewName)) {
                logger.warn("Skipping Synopsys Coverity failure condition check.");
                setResult(Result.UNSTABLE);
                return false;
            }
            final JenkinsCoverityInstance coverityInstance = getCoverityInstance();
            if (!validateFailureStepConfiguration(logger, coverityInstance)) {
                logger.error("Synopsys Coverity failure condition configuration is invalid.");
                setResult(Result.FAILURE);
                return false;
            }

            final String buildStateOnFailureString = optionalBuildStateOnFailure.orElse("");
            final BuildState buildStateForIssues = BuildState.valueOf(buildStateOnFailureString);
            final String projectName = handleVariableReplacement(getEnvVars(), optionalProjectName.orElse(""));
            final String viewName = handleVariableReplacement(getEnvVars(), optionalViewName.orElse(""));

            logGlobalConfiguration(coverityInstance, logger);
            logFailureConditionConfiguration(buildStateForIssues, projectName, viewName, logger);

            if (BuildState.NONE == buildStateForIssues) {
                logger.error(String.format("Skipping the Failure Condition check because the Build state configured is '%s'.", buildStateForIssues.getDisplayValue()));
            } else {
                logger.alwaysLog("Checking Synopsys Coverity Failure conditions.");
                final CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
                final URL coverityURL = coverityInstance.getCoverityURL().get();
                builder.url(coverityURL.toString());
                builder.username(coverityInstance.getCoverityUsername().orElse(null));
                builder.password(coverityInstance.getCoverityPassword().orElse(null));

                final CoverityServerConfig coverityServerConfig = builder.build();
                final WebServiceFactory webServiceFactory = new WebServiceFactory(coverityServerConfig, logger, createIntEnvironmentVariables());
                webServiceFactory.connect();

                boolean errorWithProjectOrView = false;
                final Optional<String> optionalProjectId = getProjectIdFromName(projectName, webServiceFactory.createConfigurationService());
                if (!optionalProjectId.isPresent()) {
                    logger.error(String.format("Could not find the Id for project \"%s\". It no longer exists or the current user does not have access to it.", projectName));
                    errorWithProjectOrView = true;
                }
                final ViewService viewService = webServiceFactory.createViewService();
                final Optional<String> optionalViewId = getViewIdFromName(viewName, viewService);
                if (!optionalViewId.isPresent()) {
                    logger.error(String.format("Could not find the Id for view \"%s\". It no longer exists or the current user does not have access to it.", viewName));
                    errorWithProjectOrView = true;
                }
                if (errorWithProjectOrView) {
                    logger.error("Skipping the Failure Condition check because of problems with the Project or View.");
                    setResult(Result.FAILURE);
                    return false;
                }

                final String projectId = optionalProjectId.orElse("");
                final String viewId = optionalViewId.orElse("");

                final int defectSize = getIssueCountVorView(projectId, viewId, viewService, logger);
                logger.info(String.format("[Coverity] Found %s issues for project \"%s\" and view \"%s\"", defectSize, projectName, viewName));

                if (defectSize > 0) {
                    setResult(buildStateForIssues.getResult());
                }
            }
        } catch (final Exception e) {
            logger.error("[ERROR] " + e.getMessage(), e);
            setResult(Result.UNSTABLE);
            return false;
        }
        return true;
    }

    private boolean shouldRunFailureStep(final JenkinsCoverityLogger logger, final Optional<String> optionalBuildStateOnFailure,
            final Optional<String> optionalProjectName, final Optional<String> optionalViewName) {
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

    private Boolean validateFailureStepConfiguration(final JenkinsCoverityLogger logger, final JenkinsCoverityInstance coverityInstance) {
        Boolean shouldContinue = true;

        if (null == coverityInstance) {
            logger.error("No global Synopsys Coverity configuration found.");
            shouldContinue = false;
        } else {
            final Optional<URL> optionalCoverityURL = coverityInstance.getCoverityURL();
            if (!optionalCoverityURL.isPresent()) {
                logger.error("No Coverity URL configured.");
                shouldContinue = false;
            }
            final Optional<String> optionalCoverityUsername = coverityInstance.getCoverityUsername();
            if (!optionalCoverityUsername.isPresent()) {
                logger.error("No Coverity Username configured.");
                shouldContinue = false;
            }
            final Optional<String> optionalCoverityPassword = coverityInstance.getCoverityPassword();
            if (!optionalCoverityPassword.isPresent()) {
                logger.error("No Coverity Password configured.");
                shouldContinue = false;
            }
        }
        return shouldContinue;
    }

    private void logFailureConditionConfiguration(final BuildState buildState, final String projectName, final String viewName, final IntLogger logger) {
        logger.alwaysLog("-- Build state for issues in the view : " + buildState.getDisplayValue());
        logger.alwaysLog("-- Coverity project name : " + projectName);
        logger.alwaysLog("-- Coverity view name : " + viewName);
    }

    private Optional<String> getProjectIdFromName(final String projectName, final ConfigurationService configurationService) throws CovRemoteServiceException_Exception {
        final ProjectFilterSpecDataObj projectFilterSpecDataObj = new ProjectFilterSpecDataObj();
        final List<ProjectDataObj> projects = configurationService.getProjects(projectFilterSpecDataObj);
        for (final ProjectDataObj projectDataObj : projects) {
            if (null != projectDataObj.getId() && null != projectDataObj.getId().getName() && projectDataObj.getId().getName().equals(projectName)) {
                if (null != projectDataObj.getProjectKey()) {
                    return Optional.of(String.valueOf(projectDataObj.getProjectKey()));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> getViewIdFromName(final String viewName, final ViewService viewService) throws IntegrationException, IOException, URISyntaxException {
        final Map<Long, String> views = viewService.getViews();
        for (final Map.Entry<Long, String> view : views.entrySet()) {
            if (view.getValue().equals(viewName)) {
                return Optional.of(String.valueOf(view.getKey()));
            }
        }
        return Optional.empty();
    }

    private int getIssueCountVorView(final String projectId, final String viewId, final ViewService viewService, final IntLogger logger) throws IntegrationException {
        try {
            final int pageSize = 1;
            int defectSize = 0;

            final ViewContents viewContents = viewService.getViewContents(projectId, viewId, pageSize, 0);
            if (null != viewContents) {
                defectSize = viewContents.totalRows.intValue();
            }
            return defectSize;
        } catch (IOException | URISyntaxException e) {
            throw new CoverityIntegrationException(e.getMessage(), e);
        }
    }

}
