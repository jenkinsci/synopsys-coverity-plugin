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

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.jenkins.PasswordMaskingOutputStream;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValidator;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValueProvider;
import com.synopsys.integration.jenkins.coverity.steps.CoverityEnvironmentStep;
import com.synopsys.integration.jenkins.coverity.steps.ProcessChangeLogSetsSubStep;
import com.synopsys.integration.log.SilentIntLogger;
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
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildWrapper;

public class CoverityEnvironmentWrapper extends SimpleBuildWrapper {
    private final String coverityInstanceUrl;
    private final String coverityPassphrase;
    private String projectName;
    private String streamName;
    private String viewName;
    private ConfigureChangeSetPatterns configureChangeSetPatterns;

    @DataBoundConstructor
    public CoverityEnvironmentWrapper(final String coverityInstanceUrl) {
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.coverityPassphrase = GlobalValueHelper.getCoverityInstanceWithUrl(new SilentIntLogger(), coverityInstanceUrl)
                                      .flatMap(CoverityConnectInstance::getCoverityPassword)
                                      .orElse(StringUtils.EMPTY);
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
    public void setUp(final Context context, final Run<?, ?> build, final FilePath workspace, final Launcher launcher, final TaskListener listener, final EnvVars initialEnvironment) throws IOException {
        final JenkinsCoverityLogger logger = new JenkinsCoverityLogger(listener);
        final IntEnvironmentVariables intEnvironmentVariables = new IntEnvironmentVariables();
        intEnvironmentVariables.putAll(initialEnvironment);
        logger.setLogLevel(intEnvironmentVariables);

        final RunWrapper runWrapper = new RunWrapper(build, true);

        final Computer computer = workspace.toComputer();
        if (computer == null) {
            throw new AbortException("Could not access workspace's computer to inject Coverity environment.");
        }

        final Node node = computer.getNode();
        if (node == null) {
            throw new AbortException("Could not access workspace's node to inject Coverity environment.");
        }

        final List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets;
        try {
            changeSets = runWrapper.getChangeSets();
        } catch (final Exception e) {
            throw new IOException(e);
        }

        final ProcessChangeLogSetsSubStep processChangeLogSetsSubStep = new ProcessChangeLogSetsSubStep(logger, changeSets, configureChangeSetPatterns);
        final List<String> changeSet = processChangeLogSetsSubStep.computeChangeSet();

        final CoverityEnvironmentStep coverityEnvironmentStep = new CoverityEnvironmentStep(node, listener, initialEnvironment, workspace, build);
        final boolean setUpSuccessful = coverityEnvironmentStep.setUpCoverityEnvironment(changeSet, coverityInstanceUrl, projectName, streamName, viewName);

        if (!setUpSuccessful) {
            throw new AbortException("Could not successfully inject Coverity environment into build process.");
        }

        initialEnvironment.forEach(context::env);
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
        private final transient CommonFieldValidator commonFieldValidator;
        private final transient CommonFieldValueProvider commonFieldValueProvider;

        public DescriptorImpl() {
            super(CoverityEnvironmentWrapper.class);
            load();
            commonFieldValidator = new CommonFieldValidator();
            commonFieldValueProvider = new CommonFieldValueProvider();
        }

        public ListBoxModel doFillCoverityInstanceUrlItems() {
            return commonFieldValueProvider.doFillCoverityInstanceUrlItems();
        }

        public FormValidation doCheckCoverityInstanceUrl(@QueryParameter("coverityInstanceUrl") final String coverityInstanceUrl) {
            return commonFieldValidator.doCheckCoverityInstanceUrl(coverityInstanceUrl);
        }

        public ListBoxModel doFillProjectNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("updateNow") boolean updateNow) {
            return commonFieldValueProvider.doFillProjectNameItems(coverityInstanceUrl, updateNow);
        }

        public FormValidation doCheckProjectName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return commonFieldValidator.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillStreamNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName, final @QueryParameter("updateNow") boolean updateNow) {
            return commonFieldValueProvider.doFillStreamNameItems(coverityInstanceUrl, projectName, updateNow);
        }

        public FormValidation doCheckStreamName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return commonFieldValidator.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillViewNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("updateNow") boolean updateNow) {
            return commonFieldValueProvider.doFillViewNameItems(coverityInstanceUrl, updateNow);
        }

        public FormValidation doCheckViewName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return commonFieldValidator.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
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
