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

import java.io.Serializable;

import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.coverity.common.CoverityCommonDescriptor;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

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
        return "Execute Synopsys Coverity static analysis";
    }

    @Override
    public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
        return true;
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

    public ListBoxModel doFillProjectNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName,
        final @QueryParameter("updateNow") boolean updateNow) {
        return coverityCommonDescriptor.doFillProjectNameItems(coverityInstanceUrl, projectName, updateNow);
    }

    public FormValidation doCheckProjectName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
        return coverityCommonDescriptor.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
    }

    public ListBoxModel doFillStreamNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName,
        final @QueryParameter("streamName") String streamName, final @QueryParameter("updateNow") boolean updateNow) {
        return coverityCommonDescriptor.doFillStreamNameItems(coverityInstanceUrl, projectName, streamName, updateNow);
    }

    public FormValidation doCheckStreamName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
        return coverityCommonDescriptor.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
    }

    public ListBoxModel doFillOnCommandFailureItems() {
        return coverityCommonDescriptor.doFillOnCommandFailureItems();
    }

    //////////
    // These methods included here only because Jenkins insists that they be here instead of in CheckForIssuesInView's descriptor, which is what I would expect. We should investigate why this is and see if we can fix it.
    // If we do fix it, the plugin is presently generating the help html files in the CoverityBuildStep folder, which should be changed to the CheckForIssuesInView folder.  - rotte (12/14/2018)
    //public ListBoxModel doFillViewNameItems(final @RelativePath("..") @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("viewName") String viewName,
    //    final @QueryParameter("updateNow") boolean updateNow) {
    //    return ((CheckForIssuesInView.DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(CheckForIssuesInView.class)).doFillViewNameItems(coverityInstanceUrl, viewName, updateNow);
    //}
    //
    //public FormValidation doCheckViewName(final @RelativePath("..") @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
    //    return ((CheckForIssuesInView.DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(CheckForIssuesInView.class)).doCheckViewName(coverityInstanceUrl);
    //
    //}
    //
    //public ListBoxModel doFillBuildStatusForIssuesItems() {
    //    return ((CheckForIssuesInView.DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(CheckForIssuesInView.class)).doFillBuildStatusForIssuesItems();
    //}
    // End methods that seem unnecessary
    //////////

}
