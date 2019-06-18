/**
 * synopsys-coverity
 *
 * Copyright (c) 2019 Synopsys, Inc.
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
package com.synopsys.integration.jenkins.coverity.extensions;

import java.io.Serializable;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityBuildStep;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValidator;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValueProvider;

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
        private transient final CommonFieldValueProvider commonFieldValueProvider;
        private transient final CommonFieldValidator commonFieldValidator;

        public DescriptorImpl() {
            super(CheckForIssuesInView.class);
            load();
            this.commonFieldValueProvider = new CommonFieldValueProvider();
            this.commonFieldValidator = new CommonFieldValidator();
        }

        public ListBoxModel doFillViewNameItems(final @RelativePath("..") @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("updateNow") final boolean updateNow) {
            return commonFieldValueProvider.doFillViewNameItems(coverityInstanceUrl, updateNow);
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
                               .map(commonFieldValidator::doCheckCoverityInstanceUrlIgnoreMessage)
                               .orElseGet(() -> FormValidation.warning("There was a failure validating this field"));
                } else {
                    // If the project is null, we're likely in the snippet generator which has no previous state to get the URL from.
                    // However, the form validation on Coverity project and Coverity stream should be enough to cover our bases, so we can just skip validation here.
                    // --rotte (01/02/2019)

                    return FormValidation.ok();
                }
            }
            return commonFieldValidator.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillBuildStatusForIssuesItems() {
            return CommonFieldValueProvider.getListBoxModelOf(BuildStatus.values());
        }

    }

}
