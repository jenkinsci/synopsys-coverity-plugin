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

package com.synopsys.integration.coverity.pipeline;

import java.io.IOException;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.google.common.collect.ImmutableSet;
import com.synopsys.integration.coverity.common.BuildStatus;
import com.synopsys.integration.coverity.common.CoverityCheckForIssuesInViewStep;
import com.synopsys.integration.coverity.common.CoverityCommonDescriptor;
import com.synopsys.integration.coverity.common.CoverityRunConfiguration;
import com.synopsys.integration.coverity.common.CoverityToolStep;
import com.synopsys.integration.coverity.common.OnCommandFailure;
import com.synopsys.integration.coverity.common.RepeatableCommand;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CoverityPipelineStep extends Step {
    private final String coverityToolName;
    private final OnCommandFailure onCommandFailure;
    private final BuildStatus buildStatusForIssues;
    private final CoverityRunConfiguration coverityRunConfiguration;
    private final String projectName;
    private final String streamName;
    private final String viewName;
    private final String changeSetExclusionPatterns;
    private final String changeSetInclusionPatterns;
    private final Boolean checkForIssuesInView;
    private final Boolean configureChangeSetPatterns;
    private final String coverityInstanceUrl;

    @DataBoundConstructor
    public CoverityPipelineStep(final String coverityInstanceUrl, final String coverityToolName, final String projectName, final String streamName, final Boolean checkForIssuesInView, final String viewName,
        final String buildStatusForIssues, final Boolean configureChangeSetPatterns, final String changeSetInclusionPatterns, final String changeSetExclusionPatterns, final CoverityRunConfiguration coverityRunConfiguration,
        final String onCommandFailure) {
        this.coverityToolName = coverityToolName;
        this.projectName = projectName;
        this.streamName = streamName;
        this.checkForIssuesInView = checkForIssuesInView;
        this.viewName = viewName;
        this.buildStatusForIssues = BuildStatus.valueOf(buildStatusForIssues);
        this.configureChangeSetPatterns = configureChangeSetPatterns;
        this.changeSetInclusionPatterns = changeSetInclusionPatterns;
        this.changeSetExclusionPatterns = changeSetExclusionPatterns;
        this.coverityRunConfiguration = coverityRunConfiguration;
        this.onCommandFailure = OnCommandFailure.valueOf(onCommandFailure);
        this.coverityInstanceUrl = coverityInstanceUrl;
    }

    public String getCoverityToolName() {
        return coverityToolName;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getStreamName() {
        return streamName;
    }

    public boolean getCheckForIssuesInView() {
        return null != checkForIssuesInView && checkForIssuesInView;
    }

    public String getViewName() {
        return viewName;
    }

    public BuildStatus getBuildStatusForIssues() {
        return buildStatusForIssues;
    }

    public boolean getConfigureChangeSetPatterns() {
        return null != configureChangeSetPatterns && configureChangeSetPatterns;
    }

    public String getChangeSetInclusionPatterns() {
        return changeSetInclusionPatterns;
    }

    public String getChangeSetExclusionPatterns() {
        return changeSetExclusionPatterns;
    }

    public CoverityRunConfiguration getCoverityRunConfiguration() {
        return coverityRunConfiguration;
    }

    public OnCommandFailure getOnCommandFailure() {
        return onCommandFailure;
    }

    @Override
    public StepExecution start(final StepContext context) throws Exception {
        return new Execution(this, context);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    private String getCoverityInstanceUrl() {
        return coverityInstanceUrl;
    }

    @Extension(optional = true)
    public static final class DescriptorImpl extends StepDescriptor {
        private final transient CoverityCommonDescriptor coverityCommonDescriptor;

        public DescriptorImpl() {
            coverityCommonDescriptor = new CoverityCommonDescriptor();
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class, EnvVars.class, Computer.class, FilePath.class, Run.class);
        }

        @Override
        public String getFunctionName() {
            return "synopsys_coverity";
        }

        @Override
        public String getDisplayName() {
            return "Synopsys Coverity";
        }

        public ListBoxModel doFillCoverityInstanceUrlItems(@QueryParameter("coverityInstanceUrl") final String coverityInstanceUrl) {
            return coverityCommonDescriptor.doFillCoverityInstanceUrlItems(coverityInstanceUrl);
        }

        public FormValidation doCheckCoverityInstanceUrlItems(@QueryParameter("coverityInstanceUrl") final String coverityInstanceUrl) {
            return coverityCommonDescriptor.doCheckCoverityInstanceUrl(coverityInstanceUrl);
        }

        public ListBoxModel doFillOnCommandFailureItems() {
            return coverityCommonDescriptor.doFillOnCommandFailureItems();
        }

        public ListBoxModel doFillCoverityToolNameItems(final @QueryParameter("coverityToolName") String coverityToolName) {
            return coverityCommonDescriptor.doFillCoverityToolNameItems(coverityToolName);
        }

        public FormValidation doCheckCoverityToolName(final @QueryParameter("coverityToolName") String coverityToolName) {
            return coverityCommonDescriptor.doCheckCoverityToolName(coverityToolName);
        }

        public ListBoxModel doFillBuildStatusForIssuesItems() {
            return coverityCommonDescriptor.doFillBuildStatusForIssuesItems();
        }

        public ListBoxModel doFillCoverityAnalysisTypeItems() {
            return coverityCommonDescriptor.doFillCoverityAnalysisTypeItems();
        }

        public ListBoxModel doFillCoverityRunConfigurationItems() {
            return coverityCommonDescriptor.doFillCoverityRunConfigurationItems();
        }

        public ListBoxModel doFillProjectNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName,
            final @QueryParameter("updateNow") Boolean updateNow) {
            return coverityCommonDescriptor.doFillProjectNameItems(coverityInstanceUrl, projectName, updateNow);
        }

        public FormValidation doCheckProjectName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return coverityCommonDescriptor.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillStreamNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName,
            final @QueryParameter("streamName") String streamName, final @QueryParameter("updateNow") Boolean updateNow) {
            return coverityCommonDescriptor.doFillStreamNameItems(coverityInstanceUrl, projectName, streamName, updateNow);
        }

        public FormValidation doCheckStreamName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return coverityCommonDescriptor.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillViewNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("viewName") String viewName, final @QueryParameter("updateNow") Boolean updateNow) {
            return coverityCommonDescriptor.doFillViewNameItems(coverityInstanceUrl, viewName, updateNow);
        }

        public FormValidation doCheckViewName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return coverityCommonDescriptor.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
        }

        public FormValidation doCheckCovBuildArguments(final @QueryParameter("covBuildArguments") String covBuildArguments) {
            return coverityCommonDescriptor.doCheckCovBuildArguments(covBuildArguments);
        }

        public FormValidation doCheckCovAnalyzeArguments(final @QueryParameter("covAnalyzeArguments") String covAnalyzeArguments) {
            return coverityCommonDescriptor.doCheckCovAnalyzeArguments(covAnalyzeArguments);
        }

        public FormValidation doCheckCovRunDesktopArguments(final @QueryParameter("covRunDesktopArguments") String covRunDesktopArguments) {
            return coverityCommonDescriptor.doCheckCovRunDesktopArguments(covRunDesktopArguments);
        }

        public FormValidation doCheckCovCommitDefectsArguments(final @QueryParameter("covCommitDefectsArguments") String covCommitDefectsArguments) {
            return coverityCommonDescriptor.doCheckCovCommitDefectsArguments(covCommitDefectsArguments);
        }

    }

    private static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 4838600483787700636L;
        private transient TaskListener listener;
        private transient EnvVars envVars;
        private transient CoverityPipelineStep coverityPipelineStep;
        private transient Computer computer;
        private transient FilePath workspace;
        private transient Run run;

        protected Execution(@Nonnull final CoverityPipelineStep step, @Nonnull final StepContext context) throws IOException, InterruptedException {
            super(context);
            listener = context.get(TaskListener.class);
            envVars = context.get(EnvVars.class);
            computer = context.get(Computer.class);
            workspace = context.get(FilePath.class);
            run = context.get(Run.class);
            coverityPipelineStep = step;
        }

        @Override
        protected Void run() throws Exception {
            final RunWrapper runWrapper = new RunWrapper(run, true);
            final CoverityToolStep coverityToolStep = new CoverityToolStep(coverityPipelineStep.getCoverityInstanceUrl(), computer.getNode(), listener, envVars, workspace, run, runWrapper.getChangeSets());
            final boolean shouldContinueOurSteps;

            if (coverityPipelineStep.getCoverityRunConfiguration().getCommands() != null) {
                shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(coverityPipelineStep.getCoverityToolName(), coverityPipelineStep.getStreamName(), coverityPipelineStep.getCoverityRunConfiguration().getCommands(),
                    coverityPipelineStep.getOnCommandFailure(), coverityPipelineStep.getConfigureChangeSetPatterns(), coverityPipelineStep.getChangeSetInclusionPatterns(), coverityPipelineStep.getChangeSetExclusionPatterns());
            } else {
                final RepeatableCommand[] simpleModeCommands = coverityToolStep
                                                                   .getSimpleModeCommands(coverityPipelineStep.getCoverityRunConfiguration().getBuildCommand(), coverityPipelineStep.getCoverityRunConfiguration().getCovBuildArguments(),
                                                                       coverityPipelineStep.getCoverityRunConfiguration().getCovAnalyzeArguments(),
                                                                       coverityPipelineStep.getCoverityRunConfiguration().getCovRunDesktopArguments(), coverityPipelineStep.getCoverityRunConfiguration().getCovCommitDefectsArguments(),
                                                                       coverityPipelineStep.getCoverityRunConfiguration().getCoverityAnalysisType());
                shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(coverityPipelineStep.getCoverityToolName(), coverityPipelineStep.getStreamName(), simpleModeCommands,
                    coverityPipelineStep.getOnCommandFailure(), coverityPipelineStep.getConfigureChangeSetPatterns(), coverityPipelineStep.getChangeSetInclusionPatterns(), coverityPipelineStep.getChangeSetExclusionPatterns());
            }

            if (shouldContinueOurSteps && coverityPipelineStep.getCheckForIssuesInView()) {
                final CoverityCheckForIssuesInViewStep coverityCheckForIssuesInViewStep = new CoverityCheckForIssuesInViewStep(coverityPipelineStep.getCoverityInstanceUrl(), computer.getNode(), listener, envVars, workspace, run);
                coverityCheckForIssuesInViewStep.runCoverityCheckForIssuesInViewStep(coverityPipelineStep.getBuildStatusForIssues(), coverityPipelineStep.getProjectName(), coverityPipelineStep.getViewName());
            }

            return null;
        }

    }

}
