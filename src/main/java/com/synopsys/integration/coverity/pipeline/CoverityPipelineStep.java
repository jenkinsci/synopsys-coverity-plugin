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

import java.util.Optional;

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.coverity.Messages;
import com.synopsys.integration.coverity.common.CoverityCommonDescriptor;
import com.synopsys.integration.coverity.common.CoverityFailureConditionStep;
import com.synopsys.integration.coverity.common.CoverityToolStep;
import com.synopsys.integration.coverity.common.RepeatableCommand;
import com.synopsys.integration.coverity.post.CoverityPostBuildStepDescriptor;
import com.synopsys.integration.coverity.tools.CoverityToolInstallation;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class CoverityPipelineStep extends AbstractStepImpl {
    private final String coverityToolName;
    private final Boolean continueOnCommandFailure;
    private final RepeatableCommand[] commands;
    private final String buildStateForIssues;
    private final String projectName;
    private final String streamName;
    private final String viewName;

    @DataBoundConstructor
    public CoverityPipelineStep(final String coverityToolName, final Boolean continueOnCommandFailure, final RepeatableCommand[] commands, final String buildStateForIssues,
            final String projectName, final String streamName, final String viewName) {
        this.coverityToolName = coverityToolName;
        this.continueOnCommandFailure = continueOnCommandFailure;
        this.commands = commands;
        this.buildStateForIssues = buildStateForIssues;
        this.projectName = projectName;
        this.streamName = streamName;
        this.viewName = viewName;
    }

    public String getCoverityToolName() {
        return coverityToolName;
    }

    public boolean getContinueOnCommandFailure() {
        if (null != continueOnCommandFailure) {
            return continueOnCommandFailure;
        }
        return false;
    }

    public RepeatableCommand[] getCommands() {
        return commands;
    }

    public String getBuildStateForIssues() {
        return buildStateForIssues;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getStreamName() {
        return streamName;
    }

    public String getViewName() {
        return viewName;
    }

    @Override
    public DetectPipelineStepDescriptor getDescriptor() {
        return (DetectPipelineStepDescriptor) super.getDescriptor();
    }

    @Extension(optional = true)
    public static final class DetectPipelineStepDescriptor extends AbstractStepDescriptorImpl {
        private final transient CoverityCommonDescriptor coverityCommonDescriptor;

        public DetectPipelineStepDescriptor() {
            super(DetectPipelineExecution.class);
            coverityCommonDescriptor = new CoverityCommonDescriptor();
        }

        @Override
        public String getFunctionName() {
            return "synopsys_coverity";
        }

        @Override
        public String getDisplayName() {
            return Messages.CoverityPipelineStep_getDisplayName();
        }

        private CoverityPostBuildStepDescriptor getCoverityPostBuildStepDescriptor() {
            return Jenkins.getInstance().getDescriptorByType(CoverityPostBuildStepDescriptor.class);
        }

        private CoverityToolInstallation[] getCoverityToolInstallations() {
            return getCoverityPostBuildStepDescriptor().getCoverityToolInstallations();
        }

        public ListBoxModel doFillCoverityToolNameItems() {
            return coverityCommonDescriptor.doFillCoverityToolNameItems(getCoverityToolInstallations());
        }

        public FormValidation doCheckCoverityToolName(final @QueryParameter("coverityToolName") String coverityToolName) {
            return coverityCommonDescriptor.doCheckCoverityToolName(getCoverityToolInstallations(), coverityToolName);
        }

        public ListBoxModel doFillBuildStateForIssuesItems() {
            return coverityCommonDescriptor.doFillBuildStateForIssuesItems();
        }

        public ListBoxModel doFillProjectNameItems(final @QueryParameter("projectName") String projectName, final @QueryParameter("updateNow") Boolean updateNow) {
            return coverityCommonDescriptor.doFillProjectNameItems(projectName, updateNow);
        }

        public ListBoxModel doFillStreamNameItems(final @QueryParameter("projectName") String projectName, final @QueryParameter("streamName") String streamName, final @QueryParameter("updateNow") Boolean updateNow) {
            return coverityCommonDescriptor.doFillStreamNameItems(projectName, streamName, updateNow);
        }

        public ListBoxModel doFillViewNameItems(final @QueryParameter("viewName") String viewName, final @QueryParameter("updateNow") Boolean updateNow) {
            return coverityCommonDescriptor.doFillViewNameItems(viewName, updateNow);
        }
    }

    public static final class DetectPipelineExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 4838600483787700636L;
        @StepContextParameter
        private transient TaskListener listener;
        @StepContextParameter
        private transient EnvVars envVars;
        @Inject
        private transient CoverityPipelineStep coverityPipelineStep;
        @StepContextParameter
        private transient Computer computer;
        @StepContextParameter
        private transient FilePath workspace;
        @StepContextParameter
        private transient Run run;

        @Override
        protected Void run() throws Exception {
            final RunWrapper runWrapper = new RunWrapper(run, true);
            final CoverityToolStep coverityToolStep = new CoverityToolStep(computer.getNode(), listener, envVars, workspace, run, runWrapper.getChangeSets());
            final Boolean shouldContinueOurSteps = coverityToolStep
                    .runCoverityToolStep(Optional.ofNullable(coverityPipelineStep.getCoverityToolName()), Optional.ofNullable(coverityPipelineStep.getStreamName()), Optional.ofNullable(coverityPipelineStep.getContinueOnCommandFailure()),
                            Optional.ofNullable(coverityPipelineStep.getCommands()));
            if (shouldContinueOurSteps) {
                final CoverityFailureConditionStep coverityFailureConditionStep = new CoverityFailureConditionStep(computer.getNode(), listener, envVars, workspace, run);
                coverityFailureConditionStep.runCommonCoverityFailureStep(Optional.ofNullable(coverityPipelineStep.getBuildStateForIssues()),
                        Optional.ofNullable(coverityPipelineStep.getProjectName()), Optional.ofNullable(coverityPipelineStep.getViewName()));
            }
            return null;
        }

    }

}
