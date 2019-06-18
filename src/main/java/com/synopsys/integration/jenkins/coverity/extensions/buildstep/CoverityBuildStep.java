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
import java.util.List;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.jenkins.coverity.extensions.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityCaptureType;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValidator;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValueProvider;
import com.synopsys.integration.jenkins.coverity.steps.CoverityCheckForIssuesInViewStep;
import com.synopsys.integration.jenkins.coverity.steps.CoverityEnvironmentStep;
import com.synopsys.integration.jenkins.coverity.steps.CoverityToolStep;
import com.synopsys.integration.jenkins.coverity.steps.ProcessChangeSet;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CoverityBuildStep extends Builder {
    private final OnCommandFailure onCommandFailure;
    private final CoverityRunConfiguration coverityRunConfiguration;
    private final String projectName;
    private final String streamName;
    private final CheckForIssuesInView checkForIssuesInView;
    private final ConfigureChangeSetPatterns configureChangeSetPatterns;
    private final String coverityInstanceUrl;

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
        return new SimpleCoverityRunConfiguration(CoverityCaptureType.COV_BUILD, CoverityAnalysisType.COV_ANALYZE, 100, null);
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
        final EnvVars envVars = build.getEnvironment(listener);
        final FilePath workingDirectory = getWorkingDirectory(build);
        final Node node = build.getBuiltOn();
        String viewName = StringUtils.EMPTY;
        if (checkForIssuesInView != null && checkForIssuesInView.getViewName() != null) {
            viewName = checkForIssuesInView.getViewName();
        }

        final ProcessChangeSet processChangeSet = new ProcessChangeSet(node, listener, envVars, workingDirectory, build);
        final List<String> changeSet = processChangeSet.computeChangeSet(build.getChangeSets(), configureChangeSetPatterns);

        final CoverityEnvironmentStep coverityEnvironmentStep = new CoverityEnvironmentStep(node, listener, envVars, workingDirectory, build);
        boolean prerequisiteStepSucceeded = coverityEnvironmentStep.setUpCoverityEnvironment(changeSet, coverityInstanceUrl, projectName, streamName, viewName);

        if (prerequisiteStepSucceeded) {
            final CoverityToolStep coverityToolStep = new CoverityToolStep(node, listener, envVars, workingDirectory, build);
            prerequisiteStepSucceeded = coverityToolStep.runCoverityToolStep(coverityRunConfiguration, changeSet.size(), onCommandFailure);
        }

        if (prerequisiteStepSucceeded && checkForIssuesInView != null) {
            final CoverityCheckForIssuesInViewStep coverityCheckForIssuesInViewStep = new CoverityCheckForIssuesInViewStep(node, listener, envVars, workingDirectory, build);
            coverityCheckForIssuesInViewStep.runCoverityCheckForIssuesInViewStep(coverityInstanceUrl, checkForIssuesInView, projectName);
        }

        return true;
    }

    private FilePath getWorkingDirectory(final AbstractBuild<?, ?> build) {
        final FilePath workingDirectory;
        if (build.getWorkspace() == null) {
            // might be using custom workspace
            workingDirectory = new FilePath(build.getBuiltOn().getChannel(), build.getProject().getCustomWorkspace());
        } else {
            workingDirectory = build.getWorkspace();
        }
        return workingDirectory;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> implements Serializable {
        private static final long serialVersionUID = -7146909743946288527L;
        private final CommonFieldValueProvider commonFieldValueProvider;
        private final CommonFieldValidator commonFieldValidator;

        public DescriptorImpl() {
            super(CoverityBuildStep.class);
            load();
            commonFieldValidator = new CommonFieldValidator();
            commonFieldValueProvider = new CommonFieldValueProvider();
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

        public ListBoxModel doFillOnCommandFailureItems() {
            return CommonFieldValueProvider.getListBoxModelOf(OnCommandFailure.values());
        }

    }

}
