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

import java.io.Serializable;
import java.util.logging.Logger;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.jenkins.coverity.extensions.BuildStatus;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityCommonDescriptor;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.freestyle.CoverityBuildStep;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Build;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CheckForIssuesInView extends AbstractDescribableImpl<CheckForIssuesInView> implements Serializable {
    private static final long serialVersionUID = 850747793907762852L;
    private static final Logger logger = Logger.getLogger(CheckForIssuesInView.class.getName());
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

        public FormValidation doCheckViewName(final @AncestorInPath Project<? extends Project, ? extends Build> project, final @RelativePath("..") @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            if (coverityInstanceUrl == null) {
                // For whatever reason, sometimes when the configuration page for a job is first loaded the form validation will fail to get the coverityInstanceUrl.
                // This is a hack to get around that by looking up all the builders for the project, finding our describable, and getting the value from that instead.
                // --rotte (12/20/2018)

                if (project != null) {
                    return project.getBuilders().stream()
                               .filter(CoverityBuildStep.class::isInstance)
                               .map(CoverityBuildStep.class::cast)
                               .findFirst()
                               .map(CoverityBuildStep::getCoverityInstanceUrl)
                               .map(coverityCommonDescriptor::testConnectionIgnoreSuccessMessage)
                               .orElseGet(() -> FormValidation.warning("There was a failure validating this field"));
                } else {
                    // If the project is null, we're likely in the snippet generator which has no previous state to get the URL from.
                    // However, the form validation on Coverity project and Coverity stream should be enough to cover our bases, so we can just skip validation here.
                    // --rotte (01/02/2019)

                    return FormValidation.ok();
                }
            }
            return coverityCommonDescriptor.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillBuildStatusForIssuesItems() {
            return coverityCommonDescriptor.doFillBuildStatusForIssuesItems();
        }

    }

}
