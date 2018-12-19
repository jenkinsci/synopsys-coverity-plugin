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
package com.synopsys.integration.jenkins.coverity.buildstep;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.coverity.common.BuildStatus;
import com.synopsys.integration.coverity.common.CoverityCommonDescriptor;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CheckForIssuesInView extends AbstractDescribableImpl<CheckForIssuesInView> implements Serializable {
    private static final long serialVersionUID = 850747793907762852L;
    private final String viewName;
    private final BuildStatus buildStatusForIssues;

    @DataBoundConstructor
    public CheckForIssuesInView(final String viewName, final String buildStatusForIssues) {
        this.viewName = viewName;
        this.buildStatusForIssues = BuildStatus.valueOf(buildStatusForIssues);
    }

    public BuildStatus getBuildStatusForIssues() {
        return buildStatusForIssues;
    }

    public String getViewName() {
        return viewName;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CheckForIssuesInView> {
        private transient final CoverityCommonDescriptor coverityCommonDescriptor;

        public DescriptorImpl() {
            super(CheckForIssuesInView.class);
            load();
            this.coverityCommonDescriptor = new CoverityCommonDescriptor();
        }

        public ListBoxModel doFillViewNameItems(final @RelativePath("..") @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("viewName") String viewName,
            final @QueryParameter("updateNow") boolean updateNow) {
            return coverityCommonDescriptor.doFillViewNameItems(coverityInstanceUrl, viewName, updateNow);
        }

        public FormValidation doCheckViewName(final @RelativePath("..") @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return coverityCommonDescriptor.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillBuildStatusForIssuesItems() {
            return coverityCommonDescriptor.doFillBuildStatusForIssuesItems();
        }

    }

}
