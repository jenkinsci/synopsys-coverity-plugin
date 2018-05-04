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

import com.sig.integration.coverity.Messages;
import com.sig.integration.coverity.common.CoverityCommonStep;
import com.sig.integration.coverity.common.RepeatableCommand;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;

public class CoverityPipelineStep extends AbstractStepImpl {
    private RepeatableCommand[] commands;

    @DataBoundConstructor
    public CoverityPipelineStep(RepeatableCommand[] commands) {
        this.commands = commands;
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
        public DetectPipelineStepDescriptor() {
            super(DetectPipelineExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "sig_coverity";
        }

        @Override
        public String getDisplayName() {
            return Messages.CoverityPipelineStep_getDisplayName();
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
            final CoverityCommonStep coverityCommonStep = new CoverityCommonStep(computer.getNode(), listener, envVars, workspace, run);
            coverityCommonStep.runCommonDetectStep();
            return null;
        }

    }

}
