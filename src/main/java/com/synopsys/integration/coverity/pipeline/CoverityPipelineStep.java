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
import hudson.model.AutoCompletionCandidates;
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
    private final String buildStateOnFailure;
    private final Boolean failOnQualityIssues;
    private final Boolean failOnSecurityIssues;
    private final String streamName;

    @DataBoundConstructor
    public CoverityPipelineStep(String coverityToolName, Boolean continueOnCommandFailure, RepeatableCommand[] commands, String buildStateOnFailure, Boolean failOnQualityIssues,
            Boolean failOnSecurityIssues, String streamName) {
        this.coverityToolName = coverityToolName;
        this.continueOnCommandFailure = continueOnCommandFailure;
        this.commands = commands;
        this.buildStateOnFailure = buildStateOnFailure;
        this.failOnQualityIssues = failOnQualityIssues;
        this.failOnSecurityIssues = failOnSecurityIssues;
        this.streamName = streamName;
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

    public String getBuildStateOnFailure() {
        return buildStateOnFailure;
    }

    public Boolean getFailOnQualityIssues() {
        return failOnQualityIssues;
    }

    public Boolean getFailOnSecurityIssues() {
        return failOnSecurityIssues;
    }

    public String getStreamName() {
        return streamName;
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
            return "sig_coverity";
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

        public FormValidation doCheckCoverityToolName(@QueryParameter("coverityToolName") String coverityToolName) {
            return coverityCommonDescriptor.doCheckCoverityToolName(getCoverityToolInstallations(), coverityToolName);
        }

        public ListBoxModel doFillBuildStateOnFailureItems() {
            return coverityCommonDescriptor.doFillBuildStateOnFailureItems();
        }

        // for doAutoComplete the variable will always be named value
        public AutoCompletionCandidates doAutoCompleteStreamName(@QueryParameter("value") String streamName) {
            return coverityCommonDescriptor.doAutoCompleteStreamName(streamName);
        }

        public FormValidation doCheckStreamName(@QueryParameter("streamName") String streamName) {
            return coverityCommonDescriptor.doCheckStreamName(streamName);
        }
    }

    public static final class DetectPipelineExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        @StepContextParameter
        transient TaskListener listener;
        @StepContextParameter
        transient EnvVars envVars;
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
            final CoverityToolStep coverityToolStep = new CoverityToolStep(computer.getNode(), listener, envVars, workspace, run);
            Boolean shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(Optional.ofNullable(coverityPipelineStep.getCoverityToolName()), Optional.ofNullable(coverityPipelineStep.getContinueOnCommandFailure()),
                    Optional.ofNullable(coverityPipelineStep.getCommands()));
            if (shouldContinueOurSteps) {
                CoverityFailureConditionStep coverityFailureConditionStep = new CoverityFailureConditionStep(computer.getNode(), listener, envVars, workspace, run);
                coverityFailureConditionStep.runCommonCoverityFailureStep(Optional.ofNullable(coverityPipelineStep.getBuildStateOnFailure()), Optional.ofNullable(coverityPipelineStep.getFailOnQualityIssues()),
                        Optional.ofNullable(coverityPipelineStep.getFailOnSecurityIssues()), Optional.ofNullable(coverityPipelineStep.getStreamName()));
            }
            return null;
        }

    }

}
