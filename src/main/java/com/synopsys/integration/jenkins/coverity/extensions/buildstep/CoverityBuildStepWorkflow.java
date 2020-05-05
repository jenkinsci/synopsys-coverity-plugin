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
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.CHANGE_SET;
import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE;
import static com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType.COV_RUN_DESKTOP;
import static com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType.THRESHOLD;
import static com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityRunConfiguration.RunConfigurationType.ADVANCED;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.coverity.api.ws.configuration.ProjectDataObj;
import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsStepWorkflow;
import com.synopsys.integration.jenkins.coverity.actions.IssueReportAction;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.BuildStatus;
import com.synopsys.integration.jenkins.coverity.extensions.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.extensions.CleanUpAction;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityWorkflowStepFactory;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.stepworkflow.StepWorkflow;
import com.synopsys.integration.stepworkflow.StepWorkflowResponse;
import com.synopsys.integration.stepworkflow.SubStep;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.AbortException;
import hudson.model.AbstractBuild;
import hudson.model.Result;

public class CoverityBuildStepWorkflow extends CoverityJenkinsStepWorkflow<Object> {
    private final CoverityWorkflowStepFactory coverityWorkflowStepFactory;
    private final AbstractBuild<?, ?> build;
    private final String projectName;
    private final String streamName;
    private final CoverityRunConfiguration coverityRunConfiguration;
    @Nullable
    private final ConfigureChangeSetPatterns configureChangeSetPatterns;
    @Nullable
    private final CheckForIssuesInView checkForIssuesInView;
    private final OnCommandFailure onCommandFailure;
    private final CleanUpAction cleanUpAction;
    private final String workspaceRemotePath;
    private final String coverityInstanceUrl;

    public CoverityBuildStepWorkflow(final JenkinsIntLogger logger, final WebServiceFactory webServiceFactory, final CoverityWorkflowStepFactory coverityWorkflowStepFactory, final AbstractBuild<?, ?> build,
        final String workspaceRemotePath, final String coverityInstanceUrl, final String projectName, final String streamName, final CoverityRunConfiguration coverityRunConfiguration,
        final ConfigureChangeSetPatterns configureChangeSetPatterns, final CheckForIssuesInView checkForIssuesInView, final OnCommandFailure onCommandFailure,
        final CleanUpAction cleanUpAction) {
        super(logger, webServiceFactory);
        this.coverityWorkflowStepFactory = coverityWorkflowStepFactory;
        this.build = build;
        this.workspaceRemotePath = workspaceRemotePath;
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.projectName = projectName;
        this.streamName = streamName;
        this.coverityRunConfiguration = coverityRunConfiguration;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
        this.checkForIssuesInView = checkForIssuesInView;
        this.onCommandFailure = onCommandFailure;
        this.cleanUpAction = cleanUpAction;
    }

    @Override
    protected StepWorkflow<Object> buildWorkflow() throws AbortException {
        final String viewName = Optional.ofNullable(checkForIssuesInView).map(CheckForIssuesInView::getViewName).orElse(StringUtils.EMPTY);
        final BuildStatus buildStatus = Optional.ofNullable(checkForIssuesInView).map(CheckForIssuesInView::getBuildStatusForIssues).orElse(BuildStatus.SUCCESS);
        final boolean shouldValidateVersion = CoverityRunConfiguration.RunConfigurationType.SIMPLE.equals(coverityRunConfiguration.getRunConFigurationType());

        return StepWorkflow.first(coverityWorkflowStepFactory.createStepValidateCoverityInstallation(shouldValidateVersion))
                   .then(coverityWorkflowStepFactory.createStepProcessChangeLogSets(build.getChangeSets(), configureChangeSetPatterns))
                   .then(coverityWorkflowStepFactory.createStepSetUpCoverityEnvironment(workspaceRemotePath, coverityInstanceUrl, projectName, streamName, viewName))
                   .then(coverityWorkflowStepFactory.createStepCreateMissingProjectsAndStreams(coverityInstanceUrl, projectName, streamName))
                   .andSometimes(coverityWorkflowStepFactory.createStepGetCoverityCommands(coverityRunConfiguration))
                   .then(coverityWorkflowStepFactory.createStepRunCoverityCommands(coverityInstanceUrl, onCommandFailure))
                   .butOnlyIf(coverityWorkflowStepFactory.getOrCreateEnvironmentVariables(), intEnvironmentVariables -> this.shouldRunCoverityCommands(intEnvironmentVariables, coverityRunConfiguration))
                   .andSometimes(coverityWorkflowStepFactory.createStepGetIssuesInView(coverityInstanceUrl, projectName, viewName))
                   .then(SubStep.ofConsumer(issueCount -> failOnIssuesPresent(issueCount, build, projectName, viewName, buildStatus)))
                   .butOnlyIf(checkForIssuesInView, Objects::nonNull)
                   .andSometimes(coverityWorkflowStepFactory.createStepCleanUpIntermediateDirectory(workspaceRemotePath))
                   .butOnlyIf(cleanUpAction, CleanUpAction.DELETE_INTERMEDIATE_DIRECTORY::equals)
                   .build();
    }

    @Override
    public Boolean perform() throws AbortException {
        final StepWorkflowResponse<Object> stepWorkflowResponse = this.runWorkflow();
        final boolean wasSuccessful = stepWorkflowResponse.wasSuccessful();
        try {
            if (!wasSuccessful) {
                throw stepWorkflowResponse.getException();
            }

            if (checkForIssuesInView != null) {
                final String viewName = checkForIssuesInView.getViewName();
                final ViewService viewService = webServiceFactory.createViewService();
                final ConfigurationServiceWrapper configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();
                final String viewId = viewService.getViews().entrySet().stream()
                                          .filter(entry -> entry.getValue() != null)
                                          .filter(entry -> entry.getValue().equals(viewName))
                                          .findFirst()
                                          .map(Map.Entry::getKey)
                                          .map(String::valueOf)
                                          .orElseThrow(() -> new CoverityJenkinsException(String.format("Could not find the Id for view \"%s\". It either does not exist or the current user does not have access to it.", viewName)));

                final String projectId = configurationServiceWrapper.getProjectByExactName(projectName)
                                             .map(ProjectDataObj::getProjectKey)
                                             .map(String::valueOf)
                                             .orElseThrow(() -> new CoverityJenkinsException(String.format("Could not find the Id for project \"%s\". It either does not exist or the current user does not have access to it.", projectName)));

                build.addAction(new IssueReportAction(coverityInstanceUrl + "/" + "reports.htm#v" + viewId + "/p" + projectId));
            }
        } catch (final InterruptedException e) {
            logger.error("[ERROR] Synopsys Coverity thread was interrupted.", e);
            build.setResult(Result.ABORTED);
            Thread.currentThread().interrupt();
        } catch (final IntegrationException e) {
            this.handleException(build, Result.FAILURE, e);
        } catch (final Exception e) {
            this.handleException(build, Result.UNSTABLE, e);
        }

        return stepWorkflowResponse.wasSuccessful();
    }

    private boolean shouldRunCoverityCommands(final IntEnvironmentVariables intEnvironmentVariables, final CoverityRunConfiguration coverityRunConfiguration) {
        final boolean analysisIsIncremental;
        if (ADVANCED.equals(coverityRunConfiguration.getRunConFigurationType())) {
            analysisIsIncremental = false;
        } else {
            final SimpleCoverityRunConfiguration simpleCoverityRunConfiguration = (SimpleCoverityRunConfiguration) coverityRunConfiguration;
            final int changeSetSize;
            changeSetSize = Integer.parseInt(intEnvironmentVariables.getValue(CHANGE_SET_SIZE.toString(), "0"));
            final CoverityAnalysisType coverityAnalysisType = simpleCoverityRunConfiguration.getCoverityAnalysisType();
            final int changeSetThreshold = simpleCoverityRunConfiguration.getChangeSetAnalysisThreshold();

            analysisIsIncremental = COV_RUN_DESKTOP.equals(coverityAnalysisType) || (THRESHOLD.equals(coverityAnalysisType) && changeSetSize < changeSetThreshold);
        }

        final String changeSetString = intEnvironmentVariables.getValue(CHANGE_SET.toString());
        if (analysisIsIncremental && StringUtils.isBlank(changeSetString)) {
            logger.alwaysLog("Skipping Synopsys Coverity static analysis because the analysis type was determined to be Incremental Analysis and the Jenkins $CHANGE_SET was empty.");
            return false;
        }
        return true;
    }

    private void failOnIssuesPresent(final Integer defectCount, final AbstractBuild<?, ?> build, final String projectName, final String viewName, final BuildStatus buildStatusOnIssues) {
        logger.alwaysLog("Checking for issues in view");
        logger.alwaysLog("-- Build state for issues in the view: " + buildStatusOnIssues.getDisplayName());
        logger.alwaysLog("-- Coverity project name: " + projectName);
        logger.alwaysLog("-- Coverity view name: " + viewName);

        if (defectCount > 0) {
            logger.alwaysLog(String.format("[Coverity] Found %s issues in view.", defectCount));
            logger.alwaysLog("Setting build status to " + buildStatusOnIssues.getResult().toString());
            build.setResult(buildStatusOnIssues.getResult());
        }
    }

    private void handleException(final AbstractBuild<?, ?> build, final Result result, final Exception e) {
        logger.error("[ERROR] " + e.getMessage());
        logger.debug(e.getMessage(), e);
        build.setResult(result);
    }

}
