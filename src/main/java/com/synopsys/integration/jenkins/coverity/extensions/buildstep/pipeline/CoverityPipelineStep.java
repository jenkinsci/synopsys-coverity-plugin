/**
 * synopsys-coverity
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
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
package com.synopsys.integration.jenkins.coverity.extensions.buildstep.pipeline;

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
import com.synopsys.integration.jenkins.coverity.CoverityCheckForIssuesInViewStep;
import com.synopsys.integration.jenkins.coverity.CoverityToolStep;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityCommonDescriptor;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.AdvancedCoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.RepeatableCommand;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.SimpleCoverityRunConfiguration;

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
    private final CoverityRunConfiguration coverityRunConfiguration;
    private final String projectName;
    private final String streamName;
    private final CheckForIssuesInView checkForIssuesInView;
    private final ConfigureChangeSetPatterns configureChangeSetPatterns;
    private final String coverityInstanceUrl;

    @DataBoundConstructor
    public CoverityPipelineStep(final String coverityInstanceUrl, final String coverityToolName, final String projectName, final String streamName, final CheckForIssuesInView checkForIssuesInView,
        final ConfigureChangeSetPatterns configureChangeSetPatterns, final CoverityRunConfiguration coverityRunConfiguration, final String onCommandFailure) {
        this.coverityToolName = coverityToolName;
        this.projectName = projectName;
        this.streamName = streamName;
        this.checkForIssuesInView = checkForIssuesInView;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
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

    public CheckForIssuesInView getCheckForIssuesInView() {
        return checkForIssuesInView;
    }

    public ConfigureChangeSetPatterns getConfigureChangeSetPatterns() {
        return configureChangeSetPatterns;
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

        public ListBoxModel doFillCoverityToolNameItems(final @QueryParameter("coverityToolName") String coverityToolName) {
            return coverityCommonDescriptor.doFillCoverityToolNameItems(coverityToolName);
        }

        public FormValidation doCheckCoverityToolName(final @QueryParameter("coverityToolName") String coverityToolName) {
            return coverityCommonDescriptor.doCheckCoverityToolName(coverityToolName);
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

        public ListBoxModel doFillOnCommandFailureItems() {
            return coverityCommonDescriptor.doFillOnCommandFailureItems();
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

            if (CoverityRunConfiguration.RunConfigurationType.ADVANCED.equals(coverityPipelineStep.getCoverityRunConfiguration().getRunConFigurationType())) {
                final AdvancedCoverityRunConfiguration advancedCoverityRunConfiguration = (AdvancedCoverityRunConfiguration) coverityPipelineStep.getCoverityRunConfiguration();

                shouldContinueOurSteps = coverityToolStep
                                             .runCoverityToolStep(coverityPipelineStep.getCoverityToolName(), coverityPipelineStep.getStreamName(), advancedCoverityRunConfiguration.getCommands(), coverityPipelineStep.getOnCommandFailure(),
                                                 coverityPipelineStep.getConfigureChangeSetPatterns());
            } else {
                final SimpleCoverityRunConfiguration simpleCoverityRunConfiguration = (SimpleCoverityRunConfiguration) coverityPipelineStep.getCoverityRunConfiguration();

                final RepeatableCommand[] simpleModeCommands = coverityToolStep.getSimpleModeCommands(simpleCoverityRunConfiguration);
                shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(coverityPipelineStep.getCoverityToolName(), coverityPipelineStep.getStreamName(), simpleModeCommands, coverityPipelineStep.getOnCommandFailure(),
                    coverityPipelineStep.getConfigureChangeSetPatterns());
            }

            if (shouldContinueOurSteps && coverityPipelineStep.getCheckForIssuesInView() != null) {
                final CoverityCheckForIssuesInViewStep coverityCheckForIssuesInViewStep = new CoverityCheckForIssuesInViewStep(coverityPipelineStep.getCoverityInstanceUrl(), computer.getNode(), listener, envVars, workspace, run);
                coverityCheckForIssuesInViewStep.runCoverityCheckForIssuesInViewStep(coverityPipelineStep.getCheckForIssuesInView(), coverityPipelineStep.getProjectName());
            }

            return null;
        }

    }

}
