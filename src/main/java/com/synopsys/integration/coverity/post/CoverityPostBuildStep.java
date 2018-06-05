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
package com.synopsys.integration.coverity.post;

import java.io.IOException;
import java.util.Optional;

import org.kohsuke.stapler.DataBoundConstructor;

import com.synopsys.integration.coverity.common.CoverityFailureConditionStep;
import com.synopsys.integration.coverity.common.CoverityToolStep;
import com.synopsys.integration.coverity.common.RepeatableCommand;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;

public class CoverityPostBuildStep extends Recorder {
    private final String coverityToolName;
    private final Boolean continueOnCommandFailure;
    private final RepeatableCommand[] commands;
    private final String buildStateOnFailure;
    private final Boolean failOnViewIssues;
    private final String projectName;
    private final String streamName;
    private final String viewName;

    @DataBoundConstructor
    public CoverityPostBuildStep(String coverityToolName, Boolean continueOnCommandFailure, RepeatableCommand[] commands, String buildStateOnFailure, Boolean failOnViewIssues,
            String projectName, String streamName, String viewName) {
        this.coverityToolName = coverityToolName;
        this.continueOnCommandFailure = continueOnCommandFailure;
        this.commands = commands;
        this.buildStateOnFailure = buildStateOnFailure;
        this.failOnViewIssues = failOnViewIssues;
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

    public String getBuildStateOnFailure() {
        return buildStateOnFailure;
    }

    public Boolean getFailOnViewIssues() {
        return failOnViewIssues;
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
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public CoverityPostBuildStepDescriptor getDescriptor() {
        return (CoverityPostBuildStepDescriptor) super.getDescriptor();
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        final CoverityToolStep coverityToolStep = new CoverityToolStep(build.getBuiltOn(), listener, build.getEnvironment(listener), getWorkingDirectory(build), build);
        Boolean shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(Optional.ofNullable(coverityToolName), Optional.ofNullable(streamName), Optional.ofNullable(continueOnCommandFailure), Optional.ofNullable(commands));
        if (shouldContinueOurSteps) {
            CoverityFailureConditionStep coverityFailureConditionStep = new CoverityFailureConditionStep(build.getBuiltOn(), listener, build.getEnvironment(listener), getWorkingDirectory(build), build);
            coverityFailureConditionStep.runCommonCoverityFailureStep(Optional.ofNullable(buildStateOnFailure), Optional.ofNullable(failOnViewIssues), Optional.ofNullable(projectName), Optional.ofNullable(viewName));
        }

        return true;
    }

    public FilePath getWorkingDirectory(final AbstractBuild<?, ?> build) {
        FilePath workingDirectory = null;
        if (build.getWorkspace() == null) {
            // might be using custom workspace
            workingDirectory = new FilePath(build.getBuiltOn().getChannel(), build.getProject().getCustomWorkspace());
        } else {
            workingDirectory = build.getWorkspace();
        }
        return workingDirectory;
    }
}
