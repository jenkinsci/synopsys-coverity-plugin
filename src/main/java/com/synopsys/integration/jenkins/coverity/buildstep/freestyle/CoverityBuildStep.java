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

package com.synopsys.integration.jenkins.coverity.buildstep.freestyle;

import java.io.IOException;

import org.kohsuke.stapler.DataBoundConstructor;

import com.synopsys.integration.coverity.common.BuildStatus;
import com.synopsys.integration.coverity.common.CoverityCheckForIssuesInViewStep;
import com.synopsys.integration.coverity.common.CoverityToolStep;
import com.synopsys.integration.coverity.common.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.buildstep.AdvancedCoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.buildstep.CoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.buildstep.RepeatableCommand;
import com.synopsys.integration.jenkins.coverity.buildstep.SimpleCoverityRunConfiguration;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;

public class CoverityBuildStep extends Builder {
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
    public CoverityBuildStep(final String coverityInstanceUrl, final String coverityToolName, final String onCommandFailure, final String buildStatusForIssues, final String projectName, final String streamName, final String viewName,
        final String changeSetExclusionPatterns, final String changeSetInclusionPatterns, final Boolean checkForIssuesInView, final Boolean configureChangeSetPatterns, final CoverityRunConfiguration coverityRunConfiguration) {
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.coverityToolName = coverityToolName;
        this.onCommandFailure = OnCommandFailure.valueOf(onCommandFailure);
        this.buildStatusForIssues = BuildStatus.valueOf(buildStatusForIssues);
        this.projectName = projectName;
        this.streamName = streamName;
        this.viewName = viewName;
        this.changeSetExclusionPatterns = changeSetExclusionPatterns;
        this.changeSetInclusionPatterns = changeSetInclusionPatterns;
        this.checkForIssuesInView = checkForIssuesInView;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
        this.coverityRunConfiguration = coverityRunConfiguration;
    }

    public String getCoverityInstanceUrl() {
        return coverityInstanceUrl;
    }

    public String getCoverityToolName() {
        return coverityToolName;
    }

    public OnCommandFailure getOnCommandFailure() {
        return onCommandFailure;
    }

    public boolean getConfigureChangeSetPatterns() {
        return null != configureChangeSetPatterns && configureChangeSetPatterns;
    }

    public boolean getCheckForIssuesInView() {
        return null != checkForIssuesInView && checkForIssuesInView;
    }

    public BuildStatus getBuildStatusForIssues() {
        return buildStatusForIssues;
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

    public String getChangeSetExclusionPatterns() {
        return changeSetExclusionPatterns;
    }

    public String getChangeSetInclusionPatterns() {
        return changeSetInclusionPatterns;
    }

    public CoverityRunConfiguration getCoverityRunConfiguration() {
        return coverityRunConfiguration;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public CoverityBuildStepDescriptor getDescriptor() {
        return (CoverityBuildStepDescriptor) super.getDescriptor();
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        final CoverityToolStep coverityToolStep = new CoverityToolStep(coverityInstanceUrl, build.getBuiltOn(), listener, build.getEnvironment(listener), getWorkingDirectory(build), build, build.getChangeSets());

        final boolean shouldContinueOurSteps;
        if (CoverityRunConfiguration.RunConfigurationType.ADVANCED.equals(coverityRunConfiguration.getRunConFigurationType())) {
            shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(coverityToolName, streamName, ((AdvancedCoverityRunConfiguration) coverityRunConfiguration).getCommands(), onCommandFailure, this.getConfigureChangeSetPatterns(),
                changeSetInclusionPatterns, changeSetExclusionPatterns);
        } else {
            final RepeatableCommand[] simpleModeCommands = coverityToolStep.getSimpleModeCommands((SimpleCoverityRunConfiguration) coverityRunConfiguration);
            shouldContinueOurSteps = coverityToolStep
                                         .runCoverityToolStep(coverityToolName, streamName, simpleModeCommands, onCommandFailure, this.getConfigureChangeSetPatterns(), changeSetInclusionPatterns, changeSetExclusionPatterns);
        }

        if (shouldContinueOurSteps && getCheckForIssuesInView()) {
            final CoverityCheckForIssuesInViewStep coverityCheckForIssuesInViewStep = new CoverityCheckForIssuesInViewStep(coverityInstanceUrl, build.getBuiltOn(), listener, build.getEnvironment(listener), getWorkingDirectory(build),
                build);
            coverityCheckForIssuesInViewStep.runCoverityCheckForIssuesInViewStep(buildStatusForIssues, projectName, viewName);
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

}
