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

package com.synopsys.integration.coverity.freestyle;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

import com.synopsys.integration.coverity.common.CoverityAnalysisType;
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
    public static final String SIMPLE_RUN_CONFIGURATION = "simple";
    public static final String ADVANCED_RUN_CONFIGURATION = "advanced";
    private final String coverityToolName;
    private final Boolean continueOnCommandFailure;
    private final RepeatableCommand[] commands;
    private final String buildStateForIssues;
    private final String projectName;
    private final String streamName;
    private final String viewName;
    private final String changeSetNameExcludePatterns;
    private final String changeSetNameIncludePatterns;
    private final String coverityRunConfiguration;
    private final CoverityAnalysisType coverityAnalysisType;
    private final String buildCommand;

    @DataBoundConstructor
    public CoverityPostBuildStep(final String coverityToolName, final Boolean continueOnCommandFailure, final RepeatableCommand[] commands, final String buildStateForIssues,
        final String projectName, final String streamName, final String coverityRunConfiguration, final CoverityAnalysisType coverityAnalysisType, final String buildCommand, final String viewName, final String changeSetNameExcludePatterns,
        final String changeSetNameIncludePatterns) {
        this.coverityToolName = coverityToolName;
        this.continueOnCommandFailure = continueOnCommandFailure;
        this.commands = commands;
        this.buildStateForIssues = buildStateForIssues;
        this.projectName = projectName;
        this.streamName = streamName;
        this.coverityRunConfiguration = coverityRunConfiguration;
        this.coverityAnalysisType = coverityAnalysisType;
        this.buildCommand = buildCommand;
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

    public String getCoverityRunConfiguration() {
        return coverityRunConfiguration;
    }

    public CoverityAnalysisType getCoverityAnalysisType() {
        return coverityAnalysisType;
    }

    public String getBuildCommand() {
        return buildCommand;
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

        final boolean shouldContinueOurSteps;
        if (coverityRunConfiguration.equals(ADVANCED_RUN_CONFIGURATION)) {
            shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(coverityToolName, streamName, commands, continueOnCommandFailure, changeSetNameIncludePatterns, changeSetNameExcludePatterns);
        } else {
            RepeatableCommand[] simpleModeCommands = getSimpleModeCommands(buildCommand, coverityAnalysisType);
            shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(coverityToolName, streamName, simpleModeCommands, continueOnCommandFailure, changeSetNameIncludePatterns, changeSetNameExcludePatterns);
        }

        if (shouldContinueOurSteps) {
            final CoverityFailureConditionStep coverityFailureConditionStep = new CoverityFailureConditionStep(build.getBuiltOn(), listener, build.getEnvironment(listener), getWorkingDirectory(build), build);
            coverityFailureConditionStep.runCommonCoverityFailureStep(buildStateForIssues, projectName, viewName);
        }

        return true;
    }

    public FilePath getWorkingDirectory(final AbstractBuild<?, ?> build) {
        final FilePath workingDirectory;
        if (build.getWorkspace() == null) {
            // might be using custom workspace
            workingDirectory = new FilePath(build.getBuiltOn().getChannel(), build.getProject().getCustomWorkspace());
        } else {
            workingDirectory = build.getWorkspace();
        }
        return workingDirectory;
    }

    private RepeatableCommand[] getSimpleModeCommands(final String buildCommand, final CoverityAnalysisType coverityAnalysisType) {
        final RepeatableCommand covBuild = new RepeatableCommand("cov-build --dir ${WORKSPACE}/idir " + buildCommand);
        final RepeatableCommand covCommitDefects = new RepeatableCommand("cov-commit-defects --dir ${WORKSPACE}/idir --host ${COVERITY_HOST} --port ${COVERITY_PORT} --stream ${COV_STREAM}");

        final RepeatableCommand[] commands;
        if (CoverityAnalysisType.COV_ANALYZE.equals(coverityAnalysisType)) {
            final RepeatableCommand covAnalyze = new RepeatableCommand("cov-analyze --dir ${WORKSPACE}/idir");
            commands = new RepeatableCommand[] { covBuild, covAnalyze, covCommitDefects };
        } else if (CoverityAnalysisType.COV_RUN_DESKTOP.equals(coverityAnalysisType)) {
            final RepeatableCommand covRunDesktop = new RepeatableCommand("cov-run-desktop --dir ${WORKSPACE}/idir  --host ${COVERITY_HOST} --stream ${COV_STREAM} ${CHANGE_SET}");
            commands = new RepeatableCommand[] { covBuild, covRunDesktop, covCommitDefects };
        } else {
            commands = new RepeatableCommand[] {};
        }

        return commands;
    }
}
