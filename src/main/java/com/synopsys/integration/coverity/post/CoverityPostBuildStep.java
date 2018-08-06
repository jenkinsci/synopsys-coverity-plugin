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
    private final String buildStateForIssues;
    private final String projectName;
    private final String streamName;
    private final String viewName;
    private final String changeSetNameExcludePatterns;
    private final String changeSetNameIncludePatterns;

    @DataBoundConstructor
    public CoverityPostBuildStep(final String coverityToolName, final Boolean continueOnCommandFailure, final RepeatableCommand[] commands, final String buildStateForIssues,
            final String projectName, final String streamName, final String viewName, final String changeSetNameExcludePatterns, final String changeSetNameIncludePatterns) {
        this.coverityToolName = coverityToolName;
        this.continueOnCommandFailure = continueOnCommandFailure;
        this.commands = commands;
        this.buildStateForIssues = buildStateForIssues;
        this.projectName = projectName;
        this.streamName = streamName;
        this.viewName = viewName;
        this.changeSetNameExcludePatterns = changeSetNameExcludePatterns;
        this.changeSetNameIncludePatterns = changeSetNameIncludePatterns;
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

    public String getChangeSetNameExcludePatterns() {
        return changeSetNameExcludePatterns;
    }

    public String getChangeSetNameIncludePatterns() {
        return changeSetNameIncludePatterns;
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
        final CoverityToolStep coverityToolStep = new CoverityToolStep(build.getBuiltOn(), listener, build.getEnvironment(listener), getWorkingDirectory(build), build, build.getChangeSets());
        final Boolean shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(Optional.ofNullable(coverityToolName), Optional.ofNullable(streamName), Optional.ofNullable(continueOnCommandFailure), Optional.ofNullable(commands),
                Optional.ofNullable(changeSetNameIncludePatterns), Optional.ofNullable(changeSetNameExcludePatterns));
        if (shouldContinueOurSteps) {
            final CoverityFailureConditionStep coverityFailureConditionStep = new CoverityFailureConditionStep(build.getBuiltOn(), listener, build.getEnvironment(listener), getWorkingDirectory(build), build);
            coverityFailureConditionStep.runCommonCoverityFailureStep(Optional.ofNullable(buildStateForIssues), Optional.ofNullable(projectName), Optional.ofNullable(viewName));
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
