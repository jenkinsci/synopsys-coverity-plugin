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
import com.synopsys.integration.coverity.common.CoverityAnalysisType;
import com.synopsys.integration.coverity.common.CoverityCheckForIssuesInViewStep;
import com.synopsys.integration.coverity.common.CoverityCommonDescriptor;
import com.synopsys.integration.coverity.common.CoverityRunConfiguration;
import com.synopsys.integration.coverity.common.CoverityToolStep;
import com.synopsys.integration.coverity.common.OnCommandFailure;
import com.synopsys.integration.coverity.common.RepeatableCommand;
import com.synopsys.integration.coverity.tools.CoverityToolInstallation;
import com.synopsys.integration.jenkins.coverity.global.CoverityGlobalConfig;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;

public class CoverityPipelineStep extends Step {
    private final String coverityToolName;
    private final String projectName;
    private final String streamName;
    private final Boolean checkForIssuesInView;
    private final String viewName;
    private final BuildStatus buildStatusForIssues;
    private final Boolean configureChangeSetPatterns;
    private final String changeSetInclusionPatterns;
    private final String changeSetExclusionPatterns;
    private final CoverityRunConfiguration coverityRunConfiguration;
    private final CoverityAnalysisType coverityAnalysisType;
    private final String buildCommand;
    private final RepeatableCommand[] commands;
    private final OnCommandFailure onCommandFailure;
    private final Boolean commandArguments;
    private final String covBuildArguments;
    private final String covAnalyzeArguments;
    private final String covRunDesktopArguments;
    private final String covCommitDefectsArguments;

    @DataBoundConstructor
    public CoverityPipelineStep(final String coverityToolName, final String projectName, final String streamName, final Boolean checkForIssuesInView, final String viewName, final BuildStatus buildStatusForIssues,
        final Boolean configureChangeSetPatterns, final String changeSetInclusionPatterns, final String changeSetExclusionPatterns, final CoverityRunConfiguration coverityRunConfiguration, final CoverityAnalysisType coverityAnalysisType,
        final String buildCommand, final RepeatableCommand[] commands, final OnCommandFailure onCommandFailure, final Boolean commandArguments, final String covBuildArguments, final String covAnalyzeArguments,
        final String covRunDesktopArguments, final String covCommitDefectsArguments) {
        this.coverityToolName = coverityToolName;
        this.projectName = projectName;
        this.streamName = streamName;
        this.checkForIssuesInView = checkForIssuesInView;
        this.viewName = viewName;
        this.buildStatusForIssues = buildStatusForIssues;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
        this.changeSetInclusionPatterns = changeSetInclusionPatterns;
        this.changeSetExclusionPatterns = changeSetExclusionPatterns;
        this.coverityRunConfiguration = coverityRunConfiguration;
        this.coverityAnalysisType = coverityAnalysisType;
        this.buildCommand = buildCommand;
        this.commands = commands;
        this.onCommandFailure = onCommandFailure;
        this.commandArguments = commandArguments;
        this.covBuildArguments = covBuildArguments;
        this.covAnalyzeArguments = covAnalyzeArguments;
        this.covRunDesktopArguments = covRunDesktopArguments;
        this.covCommitDefectsArguments = covCommitDefectsArguments;
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

    public String getBuildCommand() {
        return buildCommand;
    }

    public CoverityAnalysisType getCoverityAnalysisType() {
        return coverityAnalysisType;
    }

    public RepeatableCommand[] getCommands() {
        return commands;
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

    public String getCovBuildArguments() {
        return covBuildArguments;
    }

    public Boolean getCommandArguments() {
        return commandArguments;
    }

    public String getCovAnalyzeArguments() {
        return covAnalyzeArguments;
    }

    public String getCovRunDesktopArguments() {
        return covRunDesktopArguments;
    }

    public String getCovCommitDefectsArguments() {
        return covCommitDefectsArguments;
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

        private CoverityGlobalConfig getCoverityGlobalConfigDescriptor() {
            return GlobalConfiguration.all().get(CoverityGlobalConfig.class);
        }

        private CoverityToolInstallation[] getCoverityToolInstallations() {
            return getCoverityGlobalConfigDescriptor().getCoverityToolInstallations();
        }

        public ListBoxModel doFillOnCommandFailureItems() {
            return coverityCommonDescriptor.doFillOnCommandFailureItems();
        }

        public ListBoxModel doFillCoverityToolNameItems() {
            return coverityCommonDescriptor.doFillCoverityToolNameItems(getCoverityToolInstallations());
        }

        public FormValidation doCheckCoverityToolName(final @QueryParameter("coverityToolName") String coverityToolName) {
            return coverityCommonDescriptor.doCheckCoverityToolName(getCoverityToolInstallations(), coverityToolName);
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

        public ListBoxModel doFillProjectNameItems(final @QueryParameter("projectName") String projectName, final @QueryParameter("updateNow") Boolean updateNow) {
            return coverityCommonDescriptor.doFillProjectNameItems(projectName, updateNow);
        }

        public FormValidation doCheckProjectName() {
            return coverityCommonDescriptor.testConnectionSilently();
        }

        public ListBoxModel doFillStreamNameItems(final @QueryParameter("projectName") String projectName, final @QueryParameter("streamName") String streamName, final @QueryParameter("updateNow") Boolean updateNow) {
            return coverityCommonDescriptor.doFillStreamNameItems(projectName, streamName, updateNow);
        }

        public FormValidation doCheckStreamName() {
            return coverityCommonDescriptor.testConnectionSilently();
        }

        public ListBoxModel doFillViewNameItems(final @QueryParameter("viewName") String viewName, final @QueryParameter("updateNow") Boolean updateNow) {
            return coverityCommonDescriptor.doFillViewNameItems(viewName, updateNow);
        }

        public FormValidation doCheckViewName() {
            return coverityCommonDescriptor.testConnectionSilently();
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
            final CoverityToolStep coverityToolStep = new CoverityToolStep(computer.getNode(), listener, envVars, workspace, run, runWrapper.getChangeSets());
            final boolean shouldContinueOurSteps;

            if (CoverityRunConfiguration.ADVANCED.equals(coverityPipelineStep.getCoverityRunConfiguration())) {
                shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(coverityPipelineStep.getCoverityToolName(), coverityPipelineStep.getStreamName(), coverityPipelineStep.getCommands(),
                    coverityPipelineStep.getOnCommandFailure(), coverityPipelineStep.getConfigureChangeSetPatterns(), coverityPipelineStep.getChangeSetInclusionPatterns(), coverityPipelineStep.getChangeSetExclusionPatterns());
            } else {
                final RepeatableCommand[] simpleModeCommands = coverityToolStep.getSimpleModeCommands(coverityPipelineStep.getBuildCommand(), coverityPipelineStep.getCovBuildArguments(), coverityPipelineStep.getCovAnalyzeArguments(),
                    coverityPipelineStep.getCovRunDesktopArguments(), coverityPipelineStep.getCovCommitDefectsArguments(), coverityPipelineStep.getCoverityAnalysisType());
                shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(coverityPipelineStep.getCoverityToolName(), coverityPipelineStep.getStreamName(), simpleModeCommands,
                    coverityPipelineStep.getOnCommandFailure(), coverityPipelineStep.getConfigureChangeSetPatterns(), coverityPipelineStep.getChangeSetInclusionPatterns(), coverityPipelineStep.getChangeSetExclusionPatterns());
            }

            if (shouldContinueOurSteps && coverityPipelineStep.getCheckForIssuesInView()) {
                final CoverityCheckForIssuesInViewStep coverityCheckForIssuesInViewStep = new CoverityCheckForIssuesInViewStep(computer.getNode(), listener, envVars, workspace, run);
                coverityCheckForIssuesInViewStep.runCoverityCheckForIssuesInViewStep(coverityPipelineStep.getBuildStatusForIssues(), coverityPipelineStep.getProjectName(), coverityPipelineStep.getViewName());
            }

            return null;
        }

    }

}
