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

import java.io.Serializable;

import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.coverity.Messages;
import com.synopsys.integration.coverity.common.CoverityCommonDescriptor;
import com.synopsys.integration.jenkins.coverity.global.CoverityGlobalConfig;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;

@Extension
public class CoverityBuildStepDescriptor extends BuildStepDescriptor<Builder> implements Serializable {
    private static final long serialVersionUID = -7146909743946288527L;
    private final transient CoverityCommonDescriptor coverityCommonDescriptor;

    public CoverityBuildStepDescriptor() {
        super(CoverityBuildStep.class);
        load();
        coverityCommonDescriptor = new CoverityCommonDescriptor();
    }

    @Override
    public String getDisplayName() {
        return Messages.CoverityBuildStep_getDisplayName();
    }

    @Override
    public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
        return true;
    }

    private CoverityGlobalConfig getCoverityGlobalConfig() {
        return GlobalConfiguration.all().get(CoverityGlobalConfig.class);
    }

    public ListBoxModel doFillCoverityToolNameItems() {
        return coverityCommonDescriptor.doFillCoverityToolNameItems(getCoverityGlobalConfig().getCoverityToolInstallations());
    }

    public FormValidation doCheckCoverityToolName(@QueryParameter("coverityToolName") final String coverityToolName) {
        return coverityCommonDescriptor.doCheckCoverityToolName(getCoverityGlobalConfig().getCoverityToolInstallations(), coverityToolName);
    }

    public ListBoxModel doFillBuildStatusForIssuesItems() {
        return coverityCommonDescriptor.doFillBuildStatusForIssuesItems();
    }

    public ListBoxModel doFillCoverityAnalysisTypeItems() {
        return coverityCommonDescriptor.doFillCoverityAnalysisTypeItems();
    }

    public ListBoxModel doFillOnCommandFailureItems() {
        return coverityCommonDescriptor.doFillOnCommandFailureItems();
    }

    public ListBoxModel doFillProjectNameItems(final @QueryParameter("projectName") String projectName, final @QueryParameter("updateNow") boolean updateNow) {
        return coverityCommonDescriptor.doFillProjectNameItems(projectName, updateNow);
    }

    public FormValidation doCheckProjectName() {
        return coverityCommonDescriptor.testConnectionSilently();
    }

    public ListBoxModel doFillStreamNameItems(final @QueryParameter("projectName") String projectName, final @QueryParameter("streamName") String streamName, final @QueryParameter("updateNow") boolean updateNow) {
        return coverityCommonDescriptor.doFillStreamNameItems(projectName, streamName, updateNow);
    }

    public FormValidation doCheckStreamName() {
        return coverityCommonDescriptor.testConnectionSilently();
    }

    public ListBoxModel doFillViewNameItems(final @QueryParameter("viewName") String viewName, final @QueryParameter("updateNow") boolean updateNow) {
        return coverityCommonDescriptor.doFillViewNameItems(viewName, updateNow);
    }

    public FormValidation doCheckViewName() {
        return coverityCommonDescriptor.testConnectionSilently();
    }

}
