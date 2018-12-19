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

import org.apache.commons.lang3.StringUtils;

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
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.buildstep.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.global.CoverityConnectInstance;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public class CoverityCheckForIssuesInViewStep extends BaseCoverityStep {
    public CoverityCheckForIssuesInViewStep(final String coverityInstanceUrl, final Node node, final TaskListener listener, final EnvVars envVars, final FilePath workspace, final Run run) {
        super(coverityInstanceUrl, node, listener, envVars, workspace, run);
    }

    public boolean runCoverityCheckForIssuesInViewStep(final CheckForIssuesInView checkForIssuesInView, final String projectName) {
        super.initializeJenkinsCoverityLogger();
        try {
            // getResult() returns null if the build is still in progress
            if (getResult() != null && getResult().isWorseThan(Result.SUCCESS)) {
                logger.alwaysLog("Skipping the Synopsys Coverity Check for Issues in View step because the build was not successful.");
                return false;
            }

            if (!validateCheckForIssuesInViewStepConfiguration(checkForIssuesInView.getBuildStatusForIssues(), projectName, checkForIssuesInView.getViewName())) {
                logger.warn("Skipping the Synopsys Coverity Check for Issues in View step.");
                setResult(Result.UNSTABLE);
                return false;
            }

            final CoverityConnectInstance coverityInstance = verifyAndGetCoverityInstance().orElse(null);
            if (coverityInstance == null) {
                logger.error("Skipping the Synopsys Coverity Check for Issues in View step because no configured Coverity server was detected in the Jenkins System Configuration.");
                return false;
            }

            if (!validateGlobalConfiguration(coverityInstance)) {
                logger.error("Skipping the Synopsys Coverity Check for Issues in View step because the Synopsys Coverity Jenkins System Configuration is invalid.");
                setResult(Result.FAILURE);
                return false;
            }

            final String resolvedProjectName = handleVariableReplacement(getEnvVars(), projectName);
            final String resolvedViewName = handleVariableReplacement(getEnvVars(), checkForIssuesInView.getViewName());

            super.logGlobalConfiguration(coverityInstance);
            logFailureConditionConfiguration(checkForIssuesInView.getBuildStatusForIssues(), resolvedProjectName, resolvedViewName);

            logger.alwaysLog("Checking for issues in project and view.");
            final CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
            builder.url(coverityInstance.getCoverityURL().map(URL::toString).orElse(null));
            builder.username(coverityInstance.getCoverityUsername().orElse(null));
            builder.password(coverityInstance.getCoverityPassword().orElse(null));

            final CoverityServerConfig coverityServerConfig = builder.build();
            final WebServiceFactory webServiceFactory = new WebServiceFactory(coverityServerConfig, logger, createIntEnvironmentVariables());
            webServiceFactory.connect();

            boolean errorWithProjectOrView = false;
            final Optional<String> optionalProjectId = getProjectIdFromName(resolvedProjectName, webServiceFactory.createConfigurationService());
            if (!optionalProjectId.isPresent()) {
                logger.error(String.format("Could not find the Id for project \"%s\". It no longer exists or the current user does not have access to it.", resolvedProjectName));
                errorWithProjectOrView = true;
            }
            final ViewService viewService = webServiceFactory.createViewService();
            final Optional<String> optionalViewId = getViewIdFromName(resolvedViewName, viewService);
            if (!optionalViewId.isPresent()) {
                logger.error(String.format("Could not find the Id for view \"%s\". It no longer exists or the current user does not have access to it.", resolvedViewName));
                errorWithProjectOrView = true;
            }
            if (errorWithProjectOrView) {
                logger.error("Synopsys Coverity Check for Issues in View step failed due to problems accessing the Coverity Project or View.");
                setResult(Result.FAILURE);
                return false;
            }

            final String projectId = optionalProjectId.orElse("");
            final String viewId = optionalViewId.orElse("");

            final int defectSize = getIssueCountForView(projectId, viewId, viewService);
            logger.info(String.format("[Coverity] Found %s issues for project \"%s\" and view \"%s\"", defectSize, resolvedProjectName, resolvedViewName));

            if (defectSize > 0) {
                final BuildStatus buildStatus = checkForIssuesInView.getBuildStatusForIssues();
                if (buildStatus != null) {
                    setResult(buildStatus.getResult());
                } else {
                    logger.error("[ERROR] Attempted to set build status for job, but build status was configured as null. Please check your configuration.");
                }
            }
        } catch (final Exception e) {
            logger.error("[ERROR] " + e.getMessage(), e);
            setResult(Result.UNSTABLE);
            return false;
        }
        return true;
    }

    private boolean validateCheckForIssuesInViewStepConfiguration(final BuildStatus buildStatus, final String projectName, final String viewName) {
        boolean shouldContinue = true;
        if (buildStatus == null) {
            logger.debug("There was no build status configured to set.");
            shouldContinue = false;
        }
        if (StringUtils.isBlank(projectName)) {
            logger.warn("There was no Coverity project name provided.");
            shouldContinue = false;
        }
        if (StringUtils.isBlank(viewName)) {
            logger.warn("There was no Coverity view name provided.");
            shouldContinue = false;
        }
        return shouldContinue;
    }

    private Boolean validateGlobalConfiguration(final CoverityConnectInstance coverityInstance) {
        boolean shouldContinue = true;
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

    private void logFailureConditionConfiguration(final BuildStatus buildStatus, final String projectName, final String viewName) {
        logger.alwaysLog("-- Build state for issues in the view: " + buildStatus.getDisplayName());
        logger.alwaysLog("-- Coverity project name: " + projectName);
        logger.alwaysLog("-- Coverity view name: " + viewName);
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

    private int getIssueCountForView(final String projectId, final String viewId, final ViewService viewService) throws IntegrationException {
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
