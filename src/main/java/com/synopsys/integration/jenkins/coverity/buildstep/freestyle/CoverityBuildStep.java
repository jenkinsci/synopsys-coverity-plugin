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

import com.synopsys.integration.coverity.common.CoverityAnalysisType;
import com.synopsys.integration.coverity.common.CoverityCheckForIssuesInViewStep;
import com.synopsys.integration.coverity.common.CoverityToolStep;
import com.synopsys.integration.coverity.common.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.buildstep.AdvancedCoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.buildstep.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.buildstep.ConfigureChangeSetPatterns;
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
    private final CoverityRunConfiguration coverityRunConfiguration;
    private final String projectName;
    private final String streamName;
    private final CheckForIssuesInView checkForIssuesInView;
    private final ConfigureChangeSetPatterns configureChangeSetPatterns;
    private final String coverityInstanceUrl;

    @DataBoundConstructor
    public CoverityBuildStep(final String coverityInstanceUrl, final String coverityToolName, final String onCommandFailure, final String projectName, final String streamName, final CheckForIssuesInView checkForIssuesInView,
        final ConfigureChangeSetPatterns configureChangeSetPatterns, final CoverityRunConfiguration coverityRunConfiguration) {
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.coverityToolName = coverityToolName;
        this.onCommandFailure = OnCommandFailure.valueOf(onCommandFailure);
        this.projectName = projectName;
        this.streamName = streamName;
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

    public ConfigureChangeSetPatterns getConfigureChangeSetPatterns() {
        return configureChangeSetPatterns;
    }

    public CheckForIssuesInView getCheckForIssuesInView() {
        return checkForIssuesInView;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getStreamName() {
        return streamName;
    }

    public CoverityRunConfiguration getCoverityRunConfiguration() {
        return coverityRunConfiguration;
    }

    public CoverityRunConfiguration getDefaultCoverityRunConfiguration() {
        return new SimpleCoverityRunConfiguration(CoverityAnalysisType.COV_ANALYZE, "", null);
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
            final AdvancedCoverityRunConfiguration advancedCoverityRunConfiguration = (AdvancedCoverityRunConfiguration) coverityRunConfiguration;

            shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(coverityToolName, streamName, advancedCoverityRunConfiguration.getCommands(), onCommandFailure, configureChangeSetPatterns);
        } else {
            final SimpleCoverityRunConfiguration simpleCoverityRunConfiguration = (SimpleCoverityRunConfiguration) coverityRunConfiguration;

            final RepeatableCommand[] simpleModeCommands = coverityToolStep.getSimpleModeCommands(simpleCoverityRunConfiguration);
            shouldContinueOurSteps = coverityToolStep.runCoverityToolStep(coverityToolName, streamName, simpleModeCommands, onCommandFailure, configureChangeSetPatterns);
        }

        if (shouldContinueOurSteps && checkForIssuesInView != null) {
            final CoverityCheckForIssuesInViewStep coverityCheckForIssuesInViewStep = new CoverityCheckForIssuesInViewStep(coverityInstanceUrl, build.getBuiltOn(), listener, build.getEnvironment(listener), getWorkingDirectory(build),
                build);
            coverityCheckForIssuesInViewStep.runCoverityCheckForIssuesInViewStep(checkForIssuesInView, projectName);
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
