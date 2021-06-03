/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.jenkins.annotations.HelpMarkdown;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityBuildStep;
import com.synopsys.integration.jenkins.coverity.extensions.utils.IssueViewFieldHelper;
import com.synopsys.integration.jenkins.extensions.ChangeBuildStatusTo;
import com.synopsys.integration.jenkins.extensions.JenkinsSelectBoxEnum;
import com.synopsys.integration.log.Slf4jIntLogger;

import hudson.Extension;
import hudson.RelativePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;

public class CheckForIssuesInView extends AbstractDescribableImpl<CheckForIssuesInView> {
    // Jenkins directly serializes the names of the fields, so they are an important part of the plugin's API.
    // Be aware by changing a field name, you will also need to change these strings and will likely break previous implementations.
    // Jenkins Common provides convenient access to the XSTREAM instance that Jenkins uses to serialize the classes, you can use the serialization methods on that class to rename fields without breaking them.
    // --rotte MAY 2021
    public static final String FIELD_VIEW_NAME = "viewName";
    public static final String FIELD_BUILD_STATUS_FOR_ISSUES = "buildStatusForIssues";
    public static final String PATH_TO_COVERITY_BUILD_STEP = "..";


    @HelpMarkdown("Specify the name of the Coverity view that you would like to check for issues.  \r\n"
                      + "The resulting view name is stored in the $COV_VIEW environment variable, and affects checking for issues in both the full and incremental analysis, if configured.")
    private final String viewName;
    @HelpMarkdown("Specify the build status to set if issues are found in the configured view.")
    private final ChangeBuildStatusTo buildStatusForIssues;

    @DataBoundConstructor
    public CheckForIssuesInView(String viewName, String buildStatusForIssues) {
        this.viewName = viewName;
        this.buildStatusForIssues = ChangeBuildStatusTo.valueOf(buildStatusForIssues);
    }

    public ChangeBuildStatusTo getBuildStatusForIssues() {
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
        private final IssueViewFieldHelper issueViewFieldHelper;

        public DescriptorImpl() {
            super(CheckForIssuesInView.class);
            load();
            Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(CheckForIssuesInView.class));
            issueViewFieldHelper = new IssueViewFieldHelper(slf4jIntLogger);
        }

        public ListBoxModel doFillViewNameItems(
            @RelativePath(PATH_TO_COVERITY_BUILD_STEP) @QueryParameter(CoverityBuildStep.FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl,
            @RelativePath(PATH_TO_COVERITY_BUILD_STEP) @QueryParameter(CoverityBuildStep.FIELD_OVERRIDE_CREDENTIALS) Boolean overrideDefaultCredentials,
            @RelativePath(PATH_TO_COVERITY_BUILD_STEP) @QueryParameter(CoverityBuildStep.FIELD_CREDENTIALS_ID) String credentialsId,
            @QueryParameter("updateNow") boolean updateNow
        ) throws InterruptedException {
            if (updateNow) {
                issueViewFieldHelper.updateNow(coverityInstanceUrl, overrideDefaultCredentials, credentialsId);
            }
            return issueViewFieldHelper.getViewNamesForListBox(coverityInstanceUrl, overrideDefaultCredentials, credentialsId);
        }

        public ListBoxModel doFillBuildStatusForIssuesItems() {
            return JenkinsSelectBoxEnum.toListBoxModel(BuildStatus.values());
        }

    }

}
