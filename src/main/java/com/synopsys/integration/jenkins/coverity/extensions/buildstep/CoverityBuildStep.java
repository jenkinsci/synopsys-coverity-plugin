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
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.annotations.HelpMarkdown;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.extensions.CleanUpAction;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectUrlFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ProjectStreamFieldHelper;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CreateMissingProjectsAndStreams;
import com.synopsys.integration.jenkins.coverity.stepworkflow.GetCoverityCommands;
import com.synopsys.integration.jenkins.coverity.stepworkflow.GetIssuesInView;
import com.synopsys.integration.jenkins.coverity.stepworkflow.ProcessChangeLogSets;
import com.synopsys.integration.jenkins.coverity.stepworkflow.RunCoverityCommands;
import com.synopsys.integration.jenkins.coverity.stepworkflow.SetUpCoverityEnvironment;
import com.synopsys.integration.jenkins.coverity.stepworkflow.ValidateCoverityInstallation;
import com.synopsys.integration.jenkins.extensions.JenkinsSelectBoxEnum;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.phonehome.PhoneHomeResponse;
import com.synopsys.integration.stepworkflow.StepWorkflow;
import com.synopsys.integration.stepworkflow.StepWorkflowResponse;
import com.synopsys.integration.stepworkflow.SubStep;
import com.synopsys.integration.stepworkflow.jenkins.RemoteSubStep;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CoverityBuildStep extends Builder {
    public static final String FAILURE_MESSAGE = "Unable to perform Synopsys Coverity static analysis: ";

    @HelpMarkdown("Specify which Synopsys Coverity connect instance to run this job against.  \r\n"
                      + "The resulting Synopsys Coverity connect instance URL is stored in the $COV_URL environment variable, and will affect both the full and incremental analysis.")
    private final String coverityInstanceUrl;

    @HelpMarkdown("Specify the name of the Coverity project.  \r\n"
                      + "The resulting project name is stored in the $COV_PROJECT environment variable, and will affect both the full and incremental analysis.")
    private final String projectName;

    @HelpMarkdown("Specify the name of the Coverity stream that you would like to use for the commands.  \r\n"
                      + "The resulting stream name is stored in the $COV_STREAM environment variable, and will affect both the full and incremental analysis.")
    private final String streamName;

    private final CheckForIssuesInView checkForIssuesInView;

    private final ConfigureChangeSetPatterns configureChangeSetPatterns;

    private final CoverityRunConfiguration coverityRunConfiguration;

    @HelpMarkdown("Specify the action to take if a Coverity static analysis command fails.")
    private final OnCommandFailure onCommandFailure;

    // Any field set by a DataBoundSetter should be explicitly declared as @Nullable to avoid accidental NPEs -- rotte 10/21/2019
    @Nullable
    @HelpMarkdown("Specify the clean up action to perform on a successful execution.  \r\n"
                      + "Will either persist or delete the intermediate directory created by the specified capture type.")
    private CleanUpAction cleanUpAction;

    private CoverityJenkinsIntLogger logger;

    @DataBoundConstructor
    public CoverityBuildStep(final String coverityInstanceUrl, final String onCommandFailure, final String projectName, final String streamName, final CheckForIssuesInView checkForIssuesInView,
        final ConfigureChangeSetPatterns configureChangeSetPatterns, final CoverityRunConfiguration coverityRunConfiguration) {
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.projectName = projectName;
        this.streamName = streamName;
        this.checkForIssuesInView = checkForIssuesInView;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
        this.coverityRunConfiguration = coverityRunConfiguration;
        this.onCommandFailure = OnCommandFailure.valueOf(onCommandFailure);
    }

    public CleanUpAction getCleanUpAction() {
        return cleanUpAction;
    }

    @DataBoundSetter
    public void setCleanUpAction(final CleanUpAction cleanUpAction) {
        this.cleanUpAction = cleanUpAction;
    }

    public String getCoverityInstanceUrl() {
        return coverityInstanceUrl;
    }

    public OnCommandFailure getOnCommandFailure() {
        return onCommandFailure;
    }

    public ConfigureChangeSetPatterns getConfigureChangeSetPatterns() {
        return configureChangeSetPatterns;
    }

    public CheckForIssuesInView getCheckForIssuesInView() {
        return checkForIssuesInView;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getStreamName() {
        return streamName;
    }

    public CoverityRunConfiguration getCoverityRunConfiguration() {
        return coverityRunConfiguration;
    }

    public CoverityRunConfiguration getDefaultCoverityRunConfiguration() {
        return SimpleCoverityRunConfiguration.DEFAULT_CONFIGURATION();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
        final Thread thread = Thread.currentThread();
        final ClassLoader threadClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(this.getClass().getClassLoader());

        final IntEnvironmentVariables intEnvironmentVariables = new IntEnvironmentVariables(false);
        intEnvironmentVariables.putAll(build.getEnvironment(listener));
        logger = CoverityJenkinsIntLogger.initializeLogger(listener, intEnvironmentVariables);
        final PhoneHomeResponse phoneHomeResponse = GlobalValueHelper.phoneHome(logger, coverityInstanceUrl);

        if (Result.ABORTED.equals(build.getResult())) {
            throw new AbortException(FAILURE_MESSAGE + "The build was aborted.");
        }

        final WebServiceFactory webServiceFactory;
        try {
            webServiceFactory = GlobalValueHelper.createWebServiceFactoryFromUrl(logger, coverityInstanceUrl);
        } catch (final CoverityJenkinsException e) {
            this.handleException(build, Result.UNSTABLE, e);
            return false;
        }

        final Node node = build.getBuiltOn();
        if (node == null) {
            throw new AbortException(FAILURE_MESSAGE + "No node was configured or accessible.");
        }

        final VirtualChannel virtualChannel = node.getChannel();
        if (virtualChannel == null) {
            throw new AbortException(FAILURE_MESSAGE + "Configured node \"" + node.getDisplayName() + "\" is either not connected or offline.");
        }

        final boolean isSimpleMode = CoverityRunConfiguration.RunConfigurationType.SIMPLE.equals(coverityRunConfiguration.getRunConFigurationType());
        final String customWorkingDirectory = isSimpleMode ? ((SimpleCoverityRunConfiguration) coverityRunConfiguration).getCustomWorkingDirectory() : null;
        final String remoteWorkingDirectoryPath = computeRemoteWorkingDirectory(customWorkingDirectory, build.getWorkspace(), build.getProject().getCustomWorkspace());
        final FilePath intermediateDirectory = new FilePath(virtualChannel, remoteWorkingDirectoryPath).child("idir");

        final String coverityToolHome = intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME.toString());
        final String viewName = checkForIssuesInView != null && checkForIssuesInView.getViewName() != null ? checkForIssuesInView.getViewName() : StringUtils.EMPTY;
        final ConfigurationServiceWrapper configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();
        final ViewService viewService = webServiceFactory.createViewService();

        final ValidateCoverityInstallation validateCoverityInstallation = new ValidateCoverityInstallation(logger, isSimpleMode, coverityToolHome);
        final ProcessChangeLogSets processChangeSet = new ProcessChangeLogSets(logger, build.getChangeSets(), configureChangeSetPatterns);
        final SetUpCoverityEnvironment setUpCoverityEnvironment = new SetUpCoverityEnvironment(logger, intEnvironmentVariables, coverityInstanceUrl, projectName, streamName, viewName, intermediateDirectory.getRemote());
        final CreateMissingProjectsAndStreams createMissingProjectsAndStreams = new CreateMissingProjectsAndStreams(logger, configurationServiceWrapper, projectName, streamName);
        final GetCoverityCommands getCoverityCommands = new GetCoverityCommands(logger, intEnvironmentVariables, coverityRunConfiguration);
        final RunCoverityCommands runCoverityCommands = new RunCoverityCommands(logger, intEnvironmentVariables, remoteWorkingDirectoryPath, onCommandFailure, virtualChannel);
        final GetIssuesInView getIssuesInView = new GetIssuesInView(logger, configurationServiceWrapper, viewService, projectName, viewName);

        return StepWorkflow.first(RemoteSubStep.of(virtualChannel, validateCoverityInstallation))
                   .then(processChangeSet)
                   .then(setUpCoverityEnvironment)
                   .then(createMissingProjectsAndStreams)
                   .andSometimes(getCoverityCommands).then(runCoverityCommands).butOnlyIf(intEnvironmentVariables, this::shouldRunCoverityCommands)
                   .andSometimes(getIssuesInView).then(SubStep.ofConsumer(issueCount -> failOnIssuesPresent(issueCount, build, viewName))).butOnlyIf(checkForIssuesInView, Objects::nonNull)
                   .andSometimes(SubStep.ofExecutor(intermediateDirectory::deleteRecursive)).butOnlyIf(cleanUpAction, CleanUpAction.DELETE_INTERMEDIATE_DIRECTORY::equals)
                   .run()
                   .handleResponse(response -> afterPerform(response, build, phoneHomeResponse, thread, threadClassLoader));
    }

    private String computeRemoteWorkingDirectory(final String customWorkingDirectory, final FilePath buildWorkspace, final String customProjectWorkspace) {
        if (StringUtils.isNotBlank(customWorkingDirectory)) {
            return customWorkingDirectory;
        } else if (buildWorkspace != null) {
            return buildWorkspace.getRemote();
        } else {
            return customProjectWorkspace;
        }
    }

    private boolean shouldRunCoverityCommands(final IntEnvironmentVariables intEnvironmentVariables) {
        final boolean analysisIsIncremental;
        if (CoverityRunConfiguration.RunConfigurationType.ADVANCED.equals(coverityRunConfiguration.getRunConFigurationType())) {
            analysisIsIncremental = false;
        } else {
            final SimpleCoverityRunConfiguration simpleCoverityRunConfiguration = (SimpleCoverityRunConfiguration) coverityRunConfiguration;
            final int changeSetSize = Integer.valueOf(intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE.toString(), "0"));
            final CoverityAnalysisType coverityAnalysisType = simpleCoverityRunConfiguration.getCoverityAnalysisType();
            final int changeSetThreshold = simpleCoverityRunConfiguration.getChangeSetAnalysisThreshold();

            analysisIsIncremental = CoverityAnalysisType.COV_RUN_DESKTOP.equals(coverityAnalysisType) || (CoverityAnalysisType.THRESHOLD.equals(coverityAnalysisType) && changeSetSize < changeSetThreshold);
        }

        if (analysisIsIncremental && intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.CHANGE_SET.toString()).isEmpty()) {
            logger.alwaysLog("Skipping Synopsys Coverity static analysis because the analysis type was determined to be Incremental Analysis and the Jenkins $CHANGE_SET was empty.");
            return false;
        }
        return true;
    }

    private void failOnIssuesPresent(final Integer defectCount, final AbstractBuild<?, ?> build, final String viewName) {
        logger.alwaysLog("Checking for issues in view");
        logger.alwaysLog("-- Build state for issues in the view: " + checkForIssuesInView.getBuildStatusForIssues().getDisplayName());
        logger.alwaysLog("-- Coverity project name: " + projectName);
        logger.alwaysLog("-- Coverity view name: " + viewName);

        if (defectCount > 0) {
            logger.alwaysLog(String.format("[Coverity] Found %s issues in view.", defectCount));
            logger.alwaysLog("Setting build status to " + checkForIssuesInView.getBuildStatusForIssues().getResult().toString());
            build.setResult(checkForIssuesInView.getBuildStatusForIssues().getResult());
        }
    }

    private boolean afterPerform(final StepWorkflowResponse stepWorkflowResponse, final AbstractBuild<?, ?> build, final PhoneHomeResponse phoneHomeResponse, final Thread thread, final ClassLoader threadClassLoader) {
        final boolean wasSuccessful = stepWorkflowResponse.wasSuccessful();
        try {
            if (!wasSuccessful) {
                throw stepWorkflowResponse.getException();
            }
        } catch (final InterruptedException e) {
            logger.error("[ERROR] Synopsys Coverity thread was interrupted.", e);
            build.setResult(Result.ABORTED);
            Thread.currentThread().interrupt();
        } catch (final IntegrationException e) {
            this.handleException(build, Result.FAILURE, e);
        } catch (final Exception e) {
            this.handleException(build, Result.UNSTABLE, e);
        } finally {
            thread.setContextClassLoader(threadClassLoader);
            if (phoneHomeResponse != null) {
                phoneHomeResponse.getImmediateResult();
            }
        }

        return stepWorkflowResponse.wasSuccessful();
    }

    private void handleException(final AbstractBuild build, final Result result, final Exception e) {
        logger.error("[ERROR] " + e.getMessage());
        logger.debug(e.getMessage(), e);
        build.setResult(result);
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private final transient CoverityConnectUrlFieldHelper coverityConnectUrlFieldHelper;
        private final transient ProjectStreamFieldHelper projectStreamFieldHelper;

        public DescriptorImpl() {
            super(CoverityBuildStep.class);
            load();

            final Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
            coverityConnectUrlFieldHelper = new CoverityConnectUrlFieldHelper(slf4jIntLogger);
            projectStreamFieldHelper = new ProjectStreamFieldHelper(slf4jIntLogger);
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Execute Synopsys Coverity static analysis";
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        public ListBoxModel doFillCoverityInstanceUrlItems() {
            return coverityConnectUrlFieldHelper.doFillCoverityInstanceUrlItems();
        }

        public FormValidation doCheckCoverityInstanceUrl(@QueryParameter("coverityInstanceUrl") final String coverityInstanceUrl) {
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrl(coverityInstanceUrl);
        }

        public ComboBoxModel doFillProjectNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("updateNow") boolean updateNow) throws InterruptedException {
            if (updateNow) {
                projectStreamFieldHelper.updateNow(coverityInstanceUrl);
            }
            return projectStreamFieldHelper.getProjectNamesForComboBox(coverityInstanceUrl);
        }

        public FormValidation doCheckProjectName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName) {
            return FormValidation.aggregate(Arrays.asList(
                coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl),
                projectStreamFieldHelper.checkForProjectInCache(coverityInstanceUrl, projectName)
            ));
        }

        public ComboBoxModel doFillStreamNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName) throws InterruptedException {
            return projectStreamFieldHelper.getStreamNamesForComboBox(coverityInstanceUrl, projectName);
        }

        public FormValidation doCheckStreamName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName, final @QueryParameter("streamName") String streamName) {
            return FormValidation.aggregate(Arrays.asList(
                coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl),
                projectStreamFieldHelper.checkForStreamInCache(coverityInstanceUrl, projectName, streamName)
            ));
        }

        public ListBoxModel doFillOnCommandFailureItems() {
            return JenkinsSelectBoxEnum.toListBoxModel(OnCommandFailure.values());
        }

        public ListBoxModel doFillCleanUpActionItems() {
            return JenkinsSelectBoxEnum.toListBoxModel(CleanUpAction.values());
        }

    }

}
