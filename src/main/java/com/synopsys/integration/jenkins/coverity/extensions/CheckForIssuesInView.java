/**
 * synopsys-coverity
 *
 * Copyright (c) 2020 Synopsys, Inc.
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

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.jenkins.annotations.HelpMarkdown;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ViewFieldHelper;
import com.synopsys.integration.jenkins.extensions.JenkinsSelectBoxEnum;
import com.synopsys.integration.log.Slf4jIntLogger;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;

public class CheckForIssuesInView extends AbstractDescribableImpl<CheckForIssuesInView> {
    @HelpMarkdown("Specify the name of the Coverity view that you would like to check for issues.  \r\n"
                      + "The resulting view name is stored in the $COV_VIEW environment variable, and affects checking for issues in both the full and incremental analysis, if configured.")
    private final String viewName;
    @HelpMarkdown("Specify the build status to set if issues are found in the configured view.")
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
        private transient final ViewFieldHelper viewFieldHelper;

        public DescriptorImpl() {
            super(CheckForIssuesInView.class);
            load();
            final Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(CheckForIssuesInView.class));
            viewFieldHelper = new ViewFieldHelper(slf4jIntLogger);
        }

        public ListBoxModel doFillViewNameItems(final @RelativePath("..") @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("updateNow") final boolean updateNow) throws InterruptedException {
            if (updateNow) {
                viewFieldHelper.updateNow(coverityInstanceUrl);
            }
            return viewFieldHelper.getViewNamesForListBox(coverityInstanceUrl);
        }

        public ListBoxModel doFillBuildStatusForIssuesItems() {
            return JenkinsSelectBoxEnum.toListBoxModel(BuildStatus.values());
        }

    }

}
