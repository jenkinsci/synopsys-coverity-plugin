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
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import java.io.IOException;
import java.util.List;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.jenkins.coverity.CoverityEnvironmentStep;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityCommonDescriptor;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.RelativePath;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildWrapper;

public class CoverityEnvironmentWrapper extends SimpleBuildWrapper {
    private final String coverityToolName;
    private final String coverityInstanceUrl;
    private String projectName;
    private String streamName;
    private String viewName;
    private ConfigureChangeSetPatterns configureChangeSetPatterns;

    @DataBoundConstructor
    public CoverityEnvironmentWrapper(final String coverityToolName, final String coverityConnectInstanceName) {
        this.coverityToolName = coverityToolName;
        this.coverityInstanceUrl = coverityConnectInstanceName;
    }

    public String getCoverityInstanceUrl() {
        return coverityInstanceUrl;
    }

    public String getCoverityToolName() {
        return coverityToolName;
    }

    public String getProjectName() {
        return projectName;
    }

    @DataBoundSetter
    public void setProjectName(final String projectName) {
        this.projectName = projectName;
    }

    public String getStreamName() {
        return streamName;
    }

    @DataBoundSetter
    public void setStreamName(final String streamName) {
        this.streamName = streamName;
    }

    public String getViewName() {
        return viewName;
    }

    @DataBoundSetter
    public void setViewName(final String viewName) {
        this.viewName = viewName;
    }

    public ConfigureChangeSetPatterns getConfigureChangeSetPatterns() {
        return configureChangeSetPatterns;
    }

    @DataBoundSetter
    public void setConfigureChangeSetPatterns(final ConfigureChangeSetPatterns configureChangeSetPatterns) {
        this.configureChangeSetPatterns = configureChangeSetPatterns;
    }

    @Override
    public void setUp(final Context context, final Run<?, ?> build, final FilePath workspace, final Launcher launcher, final TaskListener listener, final EnvVars initialEnvironment) throws IOException {
        final RunWrapper runWrapper = new RunWrapper(build, true);

        final Computer computer = workspace.toComputer();
        if (computer == null) {
            throw new AbortException("Cannot retrieve Coverity tools installation");
        }

        final Node node = computer.getNode();
        if (node == null) {
            throw new AbortException("Cannot retrieve Coverity tools installation");
        }

        final List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets;
        try {
            changeSets = runWrapper.getChangeSets();
        } catch (Exception e) {
            throw new IOException(e);
        }

        final CoverityEnvironmentStep coverityEnvironmentStep = new CoverityEnvironmentStep(coverityInstanceUrl, node, listener, initialEnvironment, workspace, build, changeSets);
        coverityEnvironmentStep.setUpCoverityEnvironment(streamName, coverityToolName, configureChangeSetPatterns);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Symbol("withCoverityEnvironment")
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        private final transient CoverityCommonDescriptor coverityCommonDescriptor;

        public DescriptorImpl() {
            super(CoverityEnvironmentWrapper.class);
            load();
            coverityCommonDescriptor = new CoverityCommonDescriptor();
        }

        public ListBoxModel doFillCoverityInstanceUrlItems(@QueryParameter("coverityInstanceUrl") final String coverityInstanceUrl) {
            return coverityCommonDescriptor.doFillCoverityInstanceUrlItems(coverityInstanceUrl);
        }

        public FormValidation doCheckCoverityInstanceUrlItems(@QueryParameter("coverityInstanceUrl") final String coverityInstanceUrl) {
            return coverityCommonDescriptor.doCheckCoverityInstanceUrl(coverityInstanceUrl);
        }

        public ListBoxModel doFillCoverityToolNameItems(@QueryParameter("coverityToolName") final String coverityToolName) {
            return coverityCommonDescriptor.doFillCoverityToolNameItems(coverityToolName);
        }

        public FormValidation doCheckCoverityToolName(@QueryParameter("coverityToolName") final String coverityToolName) {
            return coverityCommonDescriptor.doCheckCoverityToolName(coverityToolName);
        }

        public ListBoxModel doFillProjectNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName, final @QueryParameter("updateNow") boolean updateNow) {
            return coverityCommonDescriptor.doFillProjectNameItems(coverityInstanceUrl, projectName, updateNow);
        }

        public FormValidation doCheckProjectName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return coverityCommonDescriptor.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillStreamNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName, final @QueryParameter("streamName") String streamName,
            final @QueryParameter("updateNow") boolean updateNow) {
            return coverityCommonDescriptor.doFillStreamNameItems(coverityInstanceUrl, projectName, streamName, updateNow);
        }

        public FormValidation doCheckStreamName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return coverityCommonDescriptor.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillViewNameItems(final @RelativePath("..") @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("viewName") String viewName,
            final @QueryParameter("updateNow") boolean updateNow) {
            return coverityCommonDescriptor.doFillViewNameItems(coverityInstanceUrl, viewName, updateNow);
        }

        public FormValidation doCheckViewName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return coverityCommonDescriptor.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
        }

        @Override
        public boolean isApplicable(final AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Inject Coverity environment into the build process";
        }
    }
}
