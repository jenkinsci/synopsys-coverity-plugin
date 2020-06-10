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
import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.TEMPORARY_AUTH_KEY_PATH;
import static com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType.COV_RUN_DESKTOP;
import static com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType.THRESHOLD;
import static com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityRunConfiguration.RunConfigurationType.ADVANCED;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.coverity.api.rest.ViewContents;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewReportWrapper;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.function.ThrowingSupplier;
import com.synopsys.integration.jenkins.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.coverity.actions.IssueReportAction;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.extensions.BuildStatus;
import com.synopsys.integration.jenkins.coverity.extensions.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.extensions.CleanUpAction;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CleanUpWorkflowService;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityJenkinsStepWorkflow;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityWorkflowStepFactory;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.stepworkflow.StepWorkflow;
import com.synopsys.integration.stepworkflow.StepWorkflowResponse;
import com.synopsys.integration.stepworkflow.SubStep;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.AbortException;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;

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

    public CoverityBuildStepWorkflow(JenkinsIntLogger logger, JenkinsVersionHelper jenkinsVersionHelper, ThrowingSupplier<WebServiceFactory, CoverityJenkinsAbortException> webServiceFactorySupplier,
        CoverityWorkflowStepFactory coverityWorkflowStepFactory, AbstractBuild<?, ?> build, String workspaceRemotePath, String coverityInstanceUrl, String projectName, String streamName, CoverityRunConfiguration coverityRunConfiguration,
        ConfigureChangeSetPatterns configureChangeSetPatterns, CheckForIssuesInView checkForIssuesInView, OnCommandFailure onCommandFailure, CleanUpAction cleanUpAction) {
        super(logger, jenkinsVersionHelper, webServiceFactorySupplier);
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
        String viewName = Optional.ofNullable(checkForIssuesInView).map(CheckForIssuesInView::getViewName).orElse(StringUtils.EMPTY);
        BuildStatus buildStatus = Optional.ofNullable(checkForIssuesInView).map(CheckForIssuesInView::getBuildStatusForIssues).orElse(BuildStatus.SUCCESS);
        boolean shouldValidateVersion = CoverityRunConfiguration.RunConfigurationType.SIMPLE.equals(coverityRunConfiguration.getRunConFigurationType());

        return StepWorkflow.first(coverityWorkflowStepFactory.createStepValidateCoverityInstallation(shouldValidateVersion))
                   .then(coverityWorkflowStepFactory.createStepCreateAuthenticationKeyFile(workspaceRemotePath, coverityInstanceUrl))
                   .then(coverityWorkflowStepFactory.createStepSetUpCoverityEnvironment(build.getChangeSets(), configureChangeSetPatterns, workspaceRemotePath, coverityInstanceUrl, projectName, streamName, viewName))
                   .then(coverityWorkflowStepFactory.createStepCreateMissingProjectsAndStreams(coverityInstanceUrl, projectName, streamName))
                   .andSometimes(coverityWorkflowStepFactory.createStepGetCoverityCommands(coverityRunConfiguration))
                   .then(coverityWorkflowStepFactory.createStepRunCoverityCommands(workspaceRemotePath, onCommandFailure))
                   .butOnlyIf(coverityWorkflowStepFactory.getOrCreateEnvironmentVariables(), intEnvironmentVariables -> this.shouldRunCoverityCommands(intEnvironmentVariables, coverityRunConfiguration))
                   .andSometimes(coverityWorkflowStepFactory.createStepGetIssuesInView(coverityInstanceUrl, projectName, viewName))
                   .then(SubStep.ofConsumer(viewReportWrapper -> handleIssues(viewReportWrapper, build, projectName, viewName, buildStatus)))
                   .butOnlyIf(checkForIssuesInView, Objects::nonNull)
                   .build();
    }

    @Override
    public Boolean perform() throws AbortException {
        StepWorkflowResponse<Object> stepWorkflowResponse = this.runWorkflow();
        boolean wasSuccessful = stepWorkflowResponse.wasSuccessful();
        try {
            if (!wasSuccessful) {
                throw stepWorkflowResponse.getException();
            }
        } catch (InterruptedException e) {
            logger.error("[ERROR] Synopsys Coverity thread was interrupted.", e);
            build.setResult(Result.ABORTED);
            Thread.currentThread().interrupt();
        } catch (IntegrationException e) {
            this.handleException(build, Result.FAILURE, e);
        } catch (Exception e) {
            this.handleException(build, Result.UNSTABLE, e);
        }

        return stepWorkflowResponse.wasSuccessful();
    }

    @Override
    public void cleanUp() throws CoverityJenkinsAbortException {
        IntEnvironmentVariables intEnvironmentVariables = coverityWorkflowStepFactory.getOrCreateEnvironmentVariables();
        CleanUpWorkflowService cleanUpWorkflowService = new CleanUpWorkflowService(logger);
        String authKeyPath = intEnvironmentVariables.getValue(TEMPORARY_AUTH_KEY_PATH.toString());
        if (StringUtils.isNotBlank(authKeyPath)) {
            VirtualChannel virtualChannel = coverityWorkflowStepFactory.getOrCreateVirtualChannel();
            FilePath authKeyFile = new FilePath(virtualChannel, authKeyPath);
            cleanUpWorkflowService.cleanUpAuthenticationFile(authKeyFile);
        }

        if (CleanUpAction.DELETE_INTERMEDIATE_DIRECTORY.equals(cleanUpAction)) {
            FilePath intermediateDirectory = coverityWorkflowStepFactory.getIntermediateDirectory(workspaceRemotePath);
            cleanUpWorkflowService.cleanUpIntermediateDirectory(intermediateDirectory);
        }
    }

    private boolean shouldRunCoverityCommands(IntEnvironmentVariables intEnvironmentVariables, CoverityRunConfiguration coverityRunConfiguration) {
        boolean analysisIsIncremental;
        if (ADVANCED.equals(coverityRunConfiguration.getRunConFigurationType())) {
            analysisIsIncremental = false;
        } else {
            SimpleCoverityRunConfiguration simpleCoverityRunConfiguration = (SimpleCoverityRunConfiguration) coverityRunConfiguration;
            int changeSetSize;
            changeSetSize = Integer.parseInt(intEnvironmentVariables.getValue(CHANGE_SET_SIZE.toString(), "0"));
            CoverityAnalysisType coverityAnalysisType = simpleCoverityRunConfiguration.getCoverityAnalysisType();
            int changeSetThreshold = simpleCoverityRunConfiguration.getChangeSetAnalysisThreshold();

            analysisIsIncremental = COV_RUN_DESKTOP.equals(coverityAnalysisType) || (THRESHOLD.equals(coverityAnalysisType) && changeSetSize < changeSetThreshold);
        }

        String changeSetString = intEnvironmentVariables.getValue(CHANGE_SET.toString());
        if (analysisIsIncremental && StringUtils.isBlank(changeSetString)) {
            logger.alwaysLog("Skipping Synopsys Coverity static analysis because the analysis type was determined to be Incremental Analysis and the Jenkins $CHANGE_SET was empty.");
            return false;
        }
        return true;
    }

    private void handleIssues(ViewReportWrapper viewReportWrapper, AbstractBuild<?, ?> build, String projectName, String viewName, BuildStatus buildStatusOnIssues) {
        logger.alwaysLog("Checking for issues in view");
        logger.alwaysLog("-- Build state for issues in the view: " + buildStatusOnIssues.getDisplayName());
        logger.alwaysLog("-- Coverity project name: " + projectName);
        logger.alwaysLog("-- Coverity view name: " + viewName);

        ViewContents viewContents = viewReportWrapper.getViewContents();
        String viewReportUrl = viewReportWrapper.getViewReportUrl();
        int defectCount = viewContents.getTotalRows().intValue();
        build.addAction(new IssueReportAction(defectCount, viewReportUrl));
        logger.alwaysLog(String.format("[Coverity] Found %s issues: %s", defectCount, viewReportUrl));

        if (defectCount > 0) {
            logger.alwaysLog("Setting build status to " + buildStatusOnIssues.getResult().toString());
            build.setResult(buildStatusOnIssues.getResult());
        }
    }

    private void handleException(AbstractBuild<?, ?> build, Result result, Exception e) {
        logger.error("[ERROR] " + e.getMessage());
        logger.debug(e.getMessage(), e);
        build.setResult(result);
    }

}
