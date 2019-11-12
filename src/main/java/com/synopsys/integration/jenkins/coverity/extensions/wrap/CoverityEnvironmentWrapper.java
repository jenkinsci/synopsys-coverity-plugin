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
package com.synopsys.integration.jenkins.coverity.extensions.wrap;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.PasswordMaskingOutputStream;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectUrlFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ProjectStreamFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ViewFieldHelper;
import com.synopsys.integration.jenkins.coverity.substeps.CreateMissingProjectsAndStreams;
import com.synopsys.integration.jenkins.coverity.substeps.ProcessChangeLogSets;
import com.synopsys.integration.jenkins.coverity.substeps.SetUpCoverityEnvironment;
import com.synopsys.integration.jenkins.coverity.substeps.remote.ValidateCoverityInstallation;
import com.synopsys.integration.jenkins.substeps.RemoteSubStep;
import com.synopsys.integration.jenkins.substeps.StepWorkflow;
import com.synopsys.integration.jenkins.substeps.StepWorkflowResponse;
import com.synopsys.integration.jenkins.substeps.SubStep;
import com.synopsys.integration.log.SilentIntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.phonehome.PhoneHomeResponse;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildWrapper;

public class CoverityEnvironmentWrapper extends SimpleBuildWrapper {
    public static final String FAILURE_MESSAGE = "Unable to inject Coverity Environment: ";

    private final String coverityInstanceUrl;
    private final String coverityPassphrase;

    // Any field set by a DataBoundSetter should be explicitly declared as nullable to avoid NPEs
    @Nullable
    private String projectName;

    @Nullable
    private String streamName;

    @Nullable
    private String viewName;

    @Nullable
    private ConfigureChangeSetPatterns configureChangeSetPatterns;

    @Nullable
    private Boolean createMissingProjectsAndStreams;

    @DataBoundConstructor
    public CoverityEnvironmentWrapper(final String coverityInstanceUrl) {
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.coverityPassphrase = GlobalValueHelper.getCoverityInstanceWithUrl(new SilentIntLogger(), coverityInstanceUrl)
                                      .flatMap(CoverityConnectInstance::getCoverityPassword)
                                      .orElse(StringUtils.EMPTY);
    }

    public Boolean getCreateMissingProjectsAndStreams() {
        if (Boolean.FALSE.equals(createMissingProjectsAndStreams)) {
            return null;
        }
        return createMissingProjectsAndStreams;
    }

    @DataBoundSetter
    public void setCreateMissingProjectsAndStreams(@QueryParameter("createMissingProjectsAndStreams") final Boolean createMissingProjectsAndStreams) {
        this.createMissingProjectsAndStreams = createMissingProjectsAndStreams;
    }

    public String getCoverityInstanceUrl() {
        return coverityInstanceUrl;
    }

    public String getProjectName() {
        return projectName;
    }

    @DataBoundSetter
    public void setProjectName(@QueryParameter("projectName") final String projectName) {
        this.projectName = projectName;
    }

    public String getStreamName() {
        return streamName;
    }

    @DataBoundSetter
    public void setStreamName(@QueryParameter("streamName") final String streamName) {
        this.streamName = streamName;
    }

    public String getViewName() {
        return viewName;
    }

    @DataBoundSetter
    public void setViewName(@QueryParameter("viewName") final String viewName) {
        this.viewName = viewName;
    }

    public ConfigureChangeSetPatterns getConfigureChangeSetPatterns() {
        return configureChangeSetPatterns;
    }

    @DataBoundSetter
    public void setConfigureChangeSetPatterns(@QueryParameter("configureChangeSetPatterns") final ConfigureChangeSetPatterns configureChangeSetPatterns) {
        this.configureChangeSetPatterns = configureChangeSetPatterns;
    }

    @Override
    public void setUp(final Context context, final Run<?, ?> build, final FilePath workspace, final Launcher launcher, final TaskListener listener, final EnvVars initialEnvironment) throws IOException, InterruptedException {
        final IntEnvironmentVariables intEnvironmentVariables = new IntEnvironmentVariables(false);
        intEnvironmentVariables.putAll(initialEnvironment);
        final JenkinsCoverityLogger logger = JenkinsCoverityLogger.initializeLogger(listener, intEnvironmentVariables);
        final PhoneHomeResponse phoneHomeResponse = GlobalValueHelper.phoneHome(logger, coverityInstanceUrl);
        if (Result.ABORTED.equals(build.getResult())) {
            throw new AbortException(FAILURE_MESSAGE + "The build was aborted.");
        }

        final RunWrapper runWrapper = new RunWrapper(build, true);

        final Computer computer = workspace.toComputer();
        if (computer == null) {
            throw new AbortException(FAILURE_MESSAGE + "This workspace does not represent a FilePath on a particular Computer.");
        }

        final Node node = computer.getNode();
        if (node == null) {
            throw new AbortException(FAILURE_MESSAGE + "Could not access workspace's node to inject Coverity environment.");
        }

        final VirtualChannel virtualChannel = node.getChannel();
        if (virtualChannel == null) {
            throw new AbortException(FAILURE_MESSAGE + "Configured node \"" + node.getDisplayName() + "\" is either not connected or offline.");
        }

        final String coverityToolHome = intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME.toString());
        final List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets;
        final WebServiceFactory webServiceFactory;
        try {
            changeSets = runWrapper.getChangeSets();
            webServiceFactory = GlobalValueHelper.createWebServiceFactoryFromUrl(logger, coverityInstanceUrl);
        } catch (final Exception e) {
            throw new IOException(FAILURE_MESSAGE + e.getMessage(), e);
        }

        final ConfigurationServiceWrapper configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();

        final RemoteSubStep<Object, Object> validateInstallation = new RemoteSubStep<>(virtualChannel, new ValidateCoverityInstallation(logger, false, coverityToolHome));
        final ProcessChangeLogSets processChangeSet = new ProcessChangeLogSets(logger, changeSets, configureChangeSetPatterns);
        final SetUpCoverityEnvironment setUpCoverityEnvironment = new SetUpCoverityEnvironment(logger, intEnvironmentVariables, coverityInstanceUrl, projectName, streamName, viewName);
        final CreateMissingProjectsAndStreams createMissingProjectsAndStreamsStep = new CreateMissingProjectsAndStreams(logger, configurationServiceWrapper, projectName, streamName);

        StepWorkflow.first(validateInstallation)
            .then(processChangeSet)
            .then(setUpCoverityEnvironment)
            .then(SubStep.Executing.of(() -> intEnvironmentVariables.getVariables().forEach(context::env)))
            .andSometimes(createMissingProjectsAndStreamsStep).butOnlyIf(createMissingProjectsAndStreams, Boolean.TRUE::equals)
            .run()
            .consumeResponse(response -> afterSetUp(logger, phoneHomeResponse, response));
    }

    private void afterSetUp(final JenkinsCoverityLogger logger, final PhoneHomeResponse phoneHomeResponse, final StepWorkflowResponse<Object> response) throws IOException {
        try {
            if (null != phoneHomeResponse) {
                phoneHomeResponse.getImmediateResult();
            }

            if (!response.wasSuccessful()) {
                throw response.getException();
            }
        } catch (final IntegrationException e) {
            logger.debug(null, e);
            throw new AbortException(FAILURE_MESSAGE + e.getMessage());
        } catch (final Exception e) {
            throw new IOException(FAILURE_MESSAGE + e.getMessage(), e);
        }
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public ConsoleLogFilter createLoggerDecorator(@Nonnull final Run<?, ?> build) {
        return new FilterImpl(coverityPassphrase);
    }

    @Symbol("withCoverityEnvironment")
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        private final transient CoverityConnectUrlFieldHelper coverityConnectUrlFieldHelper;
        private final transient ProjectStreamFieldHelper projectStreamFieldHelper;
        private final transient ViewFieldHelper viewFieldHelper;

        public DescriptorImpl() {
            super(CoverityEnvironmentWrapper.class);
            load();

            final Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
            coverityConnectUrlFieldHelper = new CoverityConnectUrlFieldHelper(slf4jIntLogger);
            projectStreamFieldHelper = new ProjectStreamFieldHelper(slf4jIntLogger);
            viewFieldHelper = new ViewFieldHelper(slf4jIntLogger);
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
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
        }

        public ComboBoxModel doFillStreamNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName) throws InterruptedException {
            return projectStreamFieldHelper.getStreamNamesForComboBox(coverityInstanceUrl, projectName);
        }

        public FormValidation doCheckStreamName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName, final @QueryParameter("streamName") String streamName) {
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillViewNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("updateNow") boolean updateNow) throws InterruptedException {
            if (updateNow) {
                viewFieldHelper.updateNow(coverityInstanceUrl);
            }
            return viewFieldHelper.getViewNamesForListBox(coverityInstanceUrl);
        }

        public FormValidation doCheckViewName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
        }

        @Override
        public boolean isApplicable(final AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Inject Coverity environment into the build process";
        }
    }

    private static final class FilterImpl extends ConsoleLogFilter implements Serializable {
        private static final long serialVersionUID = 1787519634824445328L;
        private final String passwordToMask;

        public FilterImpl(final String passwordToMask) {
            this.passwordToMask = passwordToMask;
        }

        @Override
        public OutputStream decorateLogger(final Run ignored, final OutputStream logger) {
            return new PasswordMaskingOutputStream(logger, passwordToMask);
        }
    }

}
