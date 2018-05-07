/**
 * sig-coverity
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
package com.sig.integration.coverity.pipeline;

import javax.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.sig.integration.coverity.Messages;
import com.sig.integration.coverity.common.CoverityCommonDescriptor;
import com.sig.integration.coverity.common.CoverityCommonStep;
import com.sig.integration.coverity.common.RepeatableCommand;
import com.sig.integration.coverity.post.CoverityPostBuildStepDescriptor;
import com.sig.integration.coverity.tools.CoverityToolInstallation;

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
    private String coverityToolName;
    private Boolean continueOnCommandFailure;
    private RepeatableCommand[] commands;

    @DataBoundConstructor
    public CoverityPipelineStep(String coverityToolName, Boolean continueOnCommandFailure, RepeatableCommand[] commands) {
        this.coverityToolName = coverityToolName;
        this.continueOnCommandFailure = continueOnCommandFailure;
        this.commands = commands;
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

    @Override
    public DetectPipelineStepDescriptor getDescriptor() {
        return (DetectPipelineStepDescriptor) super.getDescriptor();
    }

    @Extension(optional = true)
    public static final class DetectPipelineStepDescriptor extends AbstractStepDescriptorImpl {
        private transient CoverityCommonDescriptor coverityCommonDescriptor;

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
            final CoverityCommonStep coverityCommonStep = new CoverityCommonStep(computer.getNode(), listener, envVars, workspace, run, coverityPipelineStep.getCoverityToolName(), coverityPipelineStep.getContinueOnCommandFailure(),
                    coverityPipelineStep.getCommands());
            coverityCommonStep.runCommonDetectStep();
            return null;
        }

    }

}
