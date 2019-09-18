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
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.coverity.extensions.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.extensions.CleanUpAction;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectUrlFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.FieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ProjectStreamFieldHelper;
import com.synopsys.integration.jenkins.coverity.substeps.CreateMissingProjectsAndStreams;
import com.synopsys.integration.jenkins.coverity.substeps.GetCoverityCommands;
import com.synopsys.integration.jenkins.coverity.substeps.GetIssuesInView;
import com.synopsys.integration.jenkins.coverity.substeps.ProcessChangeLogSets;
import com.synopsys.integration.jenkins.coverity.substeps.RunCoverityCommands;
import com.synopsys.integration.jenkins.coverity.substeps.SetUpCoverityEnvironment;
import com.synopsys.integration.jenkins.coverity.substeps.remote.CoverityRemoteInstallationValidator;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.phonehome.PhoneHomeResponse;
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

    private final OnCommandFailure onCommandFailure;
    private final CoverityRunConfiguration coverityRunConfiguration;
    private final String projectName;
    private final String streamName;
    private final CheckForIssuesInView checkForIssuesInView;
    private final ConfigureChangeSetPatterns configureChangeSetPatterns;
    private final String coverityInstanceUrl;
    private CleanUpAction cleanUpAction;

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
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        final IntEnvironmentVariables intEnvironmentVariables = new IntEnvironmentVariables();
        intEnvironmentVariables.putAll(build.getEnvironment(listener));
        final JenkinsCoverityLogger logger = JenkinsCoverityLogger.initializeLogger(listener, intEnvironmentVariables);
        final PhoneHomeResponse phoneHomeResponse = GlobalValueHelper.phoneHome(logger, coverityInstanceUrl);

        try {
            final FilePath workingDirectory = getWorkingDirectory(build);

            if (Result.ABORTED.equals(build.getResult())) {
                throw new AbortException(FAILURE_MESSAGE + "The build was aborted.");
            }

            final Node node = build.getBuiltOn();
            if (node == null) {
                throw new AbortException(FAILURE_MESSAGE + "Could not access node.");
            }

            final VirtualChannel virtualChannel = node.getChannel();
            if (virtualChannel == null) {
                throw new AbortException(FAILURE_MESSAGE + "Configured node \"" + node.getDisplayName() + "\" is either not connected or offline.");
            }

            String viewName = StringUtils.EMPTY;
            if (checkForIssuesInView != null && checkForIssuesInView.getViewName() != null) {
                viewName = checkForIssuesInView.getViewName();
            }

            final ProcessChangeLogSets processChangeLogSets = new ProcessChangeLogSets(logger, build.getChangeSets(), configureChangeSetPatterns);
            final List<String> changeSet = processChangeLogSets.computeChangeSet();

            final Boolean isSimpleMode = CoverityRunConfiguration.RunConfigurationType.SIMPLE.equals(coverityRunConfiguration.getRunConFigurationType());
            final CoverityRemoteInstallationValidator coverityRemoteInstallationValidator = new CoverityRemoteInstallationValidator(logger, isSimpleMode, (HashMap<String, String>) intEnvironmentVariables.getVariables());
            final String pathToCoverityToolHome = virtualChannel.call(coverityRemoteInstallationValidator);

            final SetUpCoverityEnvironment setUpCoverityEnvironment = new SetUpCoverityEnvironment(logger, intEnvironmentVariables, pathToCoverityToolHome, coverityInstanceUrl, projectName, streamName, viewName, changeSet);
            setUpCoverityEnvironment.setUpCoverityEnvironment();

            final WebServiceFactory webServiceFactory = GlobalValueHelper.createWebServiceFactoryFromUrl(logger, coverityInstanceUrl);
            final ConfigurationServiceWrapper configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();
            final CreateMissingProjectsAndStreams createMissingProjectsAndStreams = new CreateMissingProjectsAndStreams(logger, configurationServiceWrapper, projectName, streamName);
            createMissingProjectsAndStreams.checkAndCreateMissingProjectsAndStreams();

            final GetCoverityCommands getCoverityCommands = new GetCoverityCommands(logger, intEnvironmentVariables, coverityRunConfiguration);
            final List<List<String>> commands = getCoverityCommands.getCoverityCommands();

            final RunCoverityCommands runCoverityCommands = new RunCoverityCommands(logger, intEnvironmentVariables, workingDirectory.getRemote(), commands, onCommandFailure, virtualChannel);
            runCoverityCommands.runCoverityCommands();

            if (checkForIssuesInView != null) {
                if (build.getResult() != null && build.getResult().isWorseThan(Result.SUCCESS)) {
                    throw new AbortException("Skipping the Synopsys Coverity Check for Issues in View step because the build was not successful.");
                }
                final ViewService viewService = webServiceFactory.createViewService();
                final GetIssuesInView getIssuesInView = new GetIssuesInView(logger, configurationServiceWrapper, viewService, projectName, viewName);

                logger.alwaysLog("Checking for issues in view");
                logger.alwaysLog("-- Build state for issues in the view: " + checkForIssuesInView.getBuildStatusForIssues().getDisplayName());
                logger.alwaysLog("-- Coverity project name: " + projectName);
                logger.alwaysLog("-- Coverity view name: " + viewName);

                final int defectCount = getIssuesInView.getTotalIssuesInView();

                if (defectCount > 0) {
                    logger.alwaysLog(String.format("[Coverity] Found %s issues in view.", defectCount));
                    logger.alwaysLog("Setting build status to " + checkForIssuesInView.getBuildStatusForIssues().getResult().toString());
                    build.setResult(checkForIssuesInView.getBuildStatusForIssues().getResult());
                }
            }

            if (CleanUpAction.DELETE_INTERMEDIATE_DIRECTORY.equals(cleanUpAction)) {
                final FilePath intermediateDirectory = new FilePath(workingDirectory, "idir");
                intermediateDirectory.deleteRecursive();
            }

        } catch (final InterruptedException e) {
            logger.error("[ERROR] Synopsys Coverity thread was interrupted.", e);
            build.setResult(Result.ABORTED);
            Thread.currentThread().interrupt();
            return false;
        } catch (final IntegrationException e) {
            logger.error("[ERROR] " + e.getMessage());
            logger.debug(e.getMessage(), e);
            build.setResult(Result.FAILURE);
            return false;
        } catch (final Exception e) {
            logger.error("[ERROR] " + e.getMessage());
            logger.debug(e.getMessage(), e);
            build.setResult(Result.UNSTABLE);
            return false;
        } finally {
            if (null != phoneHomeResponse) {
                phoneHomeResponse.getImmediateResult();
            }
        }

        return true;
    }

    private FilePath getWorkingDirectory(final AbstractBuild<?, ?> build) throws AbortException {
        final FilePath workingDirectory;
        if (build.getWorkspace() == null) {
            // might be using custom workspace
            final Node node = build.getBuiltOn();
            if (node != null) {
                workingDirectory = new FilePath(node.getChannel(), build.getProject().getCustomWorkspace());
            } else {
                throw new AbortException(FAILURE_MESSAGE + "Could not determine working directory");
            }
        } else {
            workingDirectory = build.getWorkspace();
        }
        return workingDirectory;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> implements Serializable {
        private static final long serialVersionUID = -7146909743946288527L;
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
            return FieldHelper.getListBoxModelOf(OnCommandFailure.values());
        }

        public ListBoxModel doFillCleanUpActionItems() {
            return FieldHelper.getListBoxModelOf(CleanUpAction.values());
        }

    }

}
