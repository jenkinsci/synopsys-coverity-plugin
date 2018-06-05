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

    public boolean runCommonCoverityFailureStep(Optional<String> optionalBuildStateOnFailure, Optional<Boolean> optionalFailOnViewIssues, Optional<String> optionalProjectName,
            Optional<String> optionalViewName) {
        final JenkinsCoverityLogger logger = createJenkinsCoverityLogger();
        try {
            if (!shouldRunFailureStep(logger, optionalBuildStateOnFailure, optionalFailOnViewIssues, optionalProjectName, optionalViewName)) {
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
            BuildState buildStateOnFailure = BuildState.getBuildStateFromDisplayValue(buildStateOnFailureString).orElse(BuildState.FAILURE);
            Boolean failOnViewIssues = optionalFailOnViewIssues.orElse(false);
            String projectName = handleVariableReplacement(getEnvVars(), optionalProjectName.orElse(""));
            String viewName = handleVariableReplacement(getEnvVars(), optionalViewName.orElse(""));

            logger.alwaysLog("Checking Synopsys Coverity Failure conditions.");

            logGlobalConfiguration(coverityInstance, logger);
            logFailureConditionConfiguration(buildStateOnFailure, failOnViewIssues, projectName, viewName, logger);

            CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
            URL coverityURL = coverityInstance.getCoverityURL().get();
            builder.url(coverityURL.toString());
            builder.username(coverityInstance.getCoverityUsername().orElse(null));
            builder.password(coverityInstance.getCoverityPassword().orElse(null));

            CoverityServerConfig coverityServerConfig = builder.build();
            WebServiceFactory webServiceFactory = new WebServiceFactory(coverityServerConfig, logger);
            webServiceFactory.connect();

            Optional<String> optionalProjectId = getProjectIdFromName(projectName, webServiceFactory.createConfigurationService());
            if (!optionalProjectId.isPresent()) {
                logger.error(String.format("Could not find the Id for project \"%s\". It no longer exists or the current user does not have access to it.", projectName));
            }

            ViewService viewService = webServiceFactory.createViewService();

            Optional<String> optionalViewId = getViewIdFromName(viewName, viewService);
            if (!optionalViewId.isPresent()) {
                logger.error(String.format("Could not find the Id for view \"%s\". It no longer exists or the current user does not have access to it.", viewName));
            }

            String projectId = optionalProjectId.orElse("");
            String viewId = optionalViewId.orElse("");

            int defectSize = getIssueCountVorView(projectId, viewId, viewService, logger);
            logger.info(String.format("[Coverity] Found %s issues for project \"%s\" and view \"%s\"", defectSize, projectName, viewName));

            //            DefectServiceWrapper defectServiceWrapper = webServiceFactory.createDefectServiceWrapper();
            //            List<MergedDefectDataObj> mergedDefectDataObjs = defectServiceWrapper.getDefectsForStream(streamName, new MergedDefectFilterSpecDataObj());
            //            Boolean foundQualityIssue = false;
            //            Boolean foundSecurityIssue = false;
            //            for (MergedDefectDataObj defect : mergedDefectDataObjs) {
            //                if (failOnQualityIssues && !foundQualityIssue && null != defect.getIssueKind() && defect.getIssueKind().toUpperCase().equals("QUALITY")) {
            //                    logger.warn(String.format("Setting the Build Result to %s because a quality issue was found for the stream %s", buildStateOnFailure.getDisplayValue(), streamName));
            //                    getRun().setResult(buildStateOnFailure.getResult());
            //                    foundQualityIssue = true;
            //                } else if (failOnSecurityIssues && !foundSecurityIssue && null != defect.getIssueKind() && defect.getIssueKind().toUpperCase().equals("SECURITY")) {
            //                    logger.warn(String.format("Setting the Build Result to %s because a security issue was found for the stream %s", buildStateOnFailure.getDisplayValue(), streamName));
            //                    foundSecurityIssue = true;
            //                    getRun().setResult(buildStateOnFailure.getResult());
            //                }
            //                if (failOnQualityIssues && foundQualityIssue && !failOnSecurityIssues) {
            //                    // If they only want to fail on Quality issues and we found one then lets exit the loop
            //                    break;
            //                } else if (failOnSecurityIssues && foundSecurityIssue && !failOnQualityIssues) {
            //                    // If they only want to fail on Security issues and we found one then lets exit the loop
            //                    break;
            //                } else if (failOnQualityIssues && foundQualityIssue && failOnSecurityIssues && foundSecurityIssue) {
            //                    // If they want to fail on Quality and Security issues and we found both then lets exit the loop
            //                    break;
            //                }
            //            }
        } catch (final Exception e) {
            logger.error("[ERROR] " + e.getMessage(), e);
            getRun().setResult(Result.UNSTABLE);
            return false;
        }
        return true;
    }

    private boolean shouldRunFailureStep(JenkinsCoverityLogger logger, Optional<String> optionalBuildStateOnFailure, Optional<Boolean> optionalFailOnViewIssues,
            Optional<String> optionalProjectName, Optional<String> optionalViewName) {
        Boolean failOnViewIssues = optionalFailOnViewIssues.orElse(false);
        Boolean shouldContinue = true;
        if (!optionalBuildStateOnFailure.isPresent()) {
            logger.debug("Missing build state to set on failure.");
            shouldContinue = false;
        }
        if ((!optionalFailOnViewIssues.isPresent() || !failOnViewIssues)) {
            logger.debug("No failure condition is configured to check.");
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

    private void logFailureConditionConfiguration(BuildState buildState, Boolean failOnViewIssues, String projectName, String viewName, IntLogger logger) {
        logger.alwaysLog("-- Build State on Failure Condition : " + buildState.getDisplayValue());
        logger.alwaysLog("-- Fail on view issues : " + failOnViewIssues);
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
