/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.pipeline;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.jenkins.annotations.HelpMarkdown;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.SynopsysCoverityCredentialsHelper;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectionFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.IssueViewFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ProjectStreamFieldHelper;
import com.synopsys.integration.jenkins.coverity.service.CoverityCommandsFactory;
import com.synopsys.integration.jenkins.coverity.service.CoverityConfigService;
import com.synopsys.integration.log.Slf4jIntLogger;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class CheckForIssuesStep extends Step implements Serializable {
    public static final String DISPLAY_NAME = "Check for Issues in Coverity View";
    public static final String PIPELINE_NAME = "coverityIssueCheck";
    private static final long serialVersionUID = 3602102048550370960L;

    // Jenkins directly serializes the names of the fields, so they are an important part of the plugin's API.
    // Be aware by changing a field name, you will also need to change these strings and will likely break previous implementations.
    // Jenkins Common provides convenient access to the XSTREAM instance that Jenkins uses to serialize the classes, you can use the serialization methods on that class to rename fields without breaking them.
    // --rotte MAY 2021
    public static final String FIELD_COVERITY_INSTANCE_URL = "coverityInstanceUrl";
    public static final String FIELD_PROJECT_NAME = "projectName";
    public static final String FIELD_STREAM_NAME = "streamName";
    public static final String FIELD_VIEW_NAME = "viewName";
    public static final String FIELD_CREDENTIALS_ID = "credentialsId";
    public static final String FIELD_RETURN_ISSUE_COUNT = "returnIssueCount";

    // Any field set by a DataBoundSetter should be explicitly declared as nullable to avoid NPEs
    @Nullable
    @HelpMarkdown("Specify which Synopsys Coverity connect instance to check for issues.")
    private String coverityInstanceUrl;

    @Nullable
    @HelpMarkdown("Specify the credentials to use with the Synopsys Coverity connect instance.")
    private String credentialsId;

    @Nullable
    @HelpMarkdown("Specify the name of the Coverity project the view is associated with.")
    private String projectName;

    @Nullable
    @HelpMarkdown("Specify the name of the Coverity view that you would like to check for issues.")
    private String viewName;

    @Nullable
    @HelpMarkdown("If checked, will return the number of issues discovered in the specified Coverity view instead of throwing an exception.")
    private Boolean returnIssueCount;

    @DataBoundConstructor
    public CheckForIssuesStep() {
        // All fields are optional, so this constructor exists only to prevent some versions of the pipeline syntax generator from failing
    }

    public String getCredentialsId() {
        if (StringUtils.isNotBlank(credentialsId)) {
            return credentialsId;
        }
        return null;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public Boolean getReturnIssueCount() {
        if (Boolean.FALSE.equals(returnIssueCount)) {
            return null;
        }
        return returnIssueCount;
    }

    @DataBoundSetter
    public void setReturnIssueCount(Boolean returnIssueCount) {
        this.returnIssueCount = returnIssueCount;
    }

    public String getCoverityInstanceUrl() {
        if (StringUtils.isBlank(coverityInstanceUrl)) {
            return null;
        }
        return coverityInstanceUrl;
    }

    @DataBoundSetter
    public void setCoverityInstanceUrl(String coverityInstanceUrl) {
        this.coverityInstanceUrl = coverityInstanceUrl;
    }

    public String getProjectName() {
        if (StringUtils.isBlank(projectName)) {
            return null;
        }
        return projectName;
    }

    @DataBoundSetter
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getViewName() {
        if (StringUtils.isBlank(viewName)) {
            return null;
        }
        return viewName;
    }

    @DataBoundSetter
    public void setViewName(String viewName) {
        this.viewName = viewName;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context);
    }

    @Symbol(PIPELINE_NAME)
    @Extension(optional = true)
    public static final class DescriptorImpl extends StepDescriptor {
        private final CoverityConnectionFieldHelper coverityConnectionFieldHelper;
        private final ProjectStreamFieldHelper projectStreamFieldHelper;
        private final IssueViewFieldHelper issueViewFieldHelper;
        private final SynopsysCoverityCredentialsHelper credentialsHelper;

        public DescriptorImpl() {
            Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
            coverityConnectionFieldHelper = new CoverityConnectionFieldHelper(slf4jIntLogger);
            projectStreamFieldHelper = new ProjectStreamFieldHelper(slf4jIntLogger);
            issueViewFieldHelper = new IssueViewFieldHelper(slf4jIntLogger);
            credentialsHelper = new SynopsysCoverityCredentialsHelper(slf4jIntLogger, Jenkins.getInstance());
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return new HashSet<>(Arrays.asList(TaskListener.class, EnvVars.class));
        }

        @Override
        public String getFunctionName() {
            return PIPELINE_NAME;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return DISPLAY_NAME;
        }

        public ListBoxModel doFillCoverityInstanceUrlItems() {
            return coverityConnectionFieldHelper.doFillCoverityInstanceUrlItems();
        }

        public FormValidation doCheckCoverityInstanceUrl(
            @QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl,
            @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId
        ) {
            return coverityConnectionFieldHelper.doCheckCoverityInstanceUrl(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId);
        }

        public ListBoxModel doFillCredentialsIdItems() {
            return credentialsHelper.listSupportedCredentials();
        }

        public ListBoxModel doFillProjectNameItems(
            @QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl,
            @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId,
            @QueryParameter("updateNow") boolean updateNow
        ) throws InterruptedException {
            if (updateNow) {
                projectStreamFieldHelper.updateNow(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId);
            }
            return projectStreamFieldHelper.getProjectNamesForListBox(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId);
        }

        public FormValidation doCheckProjectName(
            @QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl,
            @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId
        ) {
            return coverityConnectionFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId);
        }

        public ListBoxModel doFillViewNameItems(
            @QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl,
            @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId,
            @QueryParameter("updateNow") boolean updateNow
        ) throws InterruptedException {
            if (updateNow) {
                issueViewFieldHelper.updateNow(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId);
            }
            return issueViewFieldHelper.getViewNamesForListBox(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId);
        }

        public FormValidation doCheckViewName(
            @QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl,
            @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId
        ) {
            return coverityConnectionFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId);
        }

    }

    public class Execution extends SynchronousNonBlockingStepExecution<Integer> {
        private static final long serialVersionUID = -5807577350749324767L;
        private final transient TaskListener listener;
        private final transient EnvVars envVars;
        private final transient Node node;
        private final transient Launcher launcher;
        private final transient Run<?, ?> run;

        protected Execution(@Nonnull StepContext context) throws InterruptedException, IOException {
            super(context);
            listener = context.get(TaskListener.class);
            envVars = context.get(EnvVars.class);
            node = context.get(Node.class);
            launcher = context.get(Launcher.class);
            run = context.get(Run.class);
        }

        @Override
        protected Integer run() throws Exception {
            String unresolvedCoverityInstanceUrl = getRequiredValueOrDie(coverityInstanceUrl, FIELD_COVERITY_INSTANCE_URL, JenkinsCoverityEnvironmentVariable.COVERITY_URL, envVars::get);
            String resolvedCoverityInstanceUrl = Util.replaceMacro(unresolvedCoverityInstanceUrl, envVars);

            String unresolvedProjectName = getRequiredValueOrDie(projectName, FIELD_PROJECT_NAME, JenkinsCoverityEnvironmentVariable.COVERITY_PROJECT, envVars::get);
            String resolvedProjectName = Util.replaceMacro(unresolvedProjectName, envVars);

            String unresolvedViewName = getRequiredValueOrDie(viewName, FIELD_VIEW_NAME, JenkinsCoverityEnvironmentVariable.COVERITY_VIEW, envVars::get);
            String resolvedViewName = Util.replaceMacro(unresolvedViewName, envVars);

            String resolvedCredentialsId;
            if (credentialsId != null) {
                resolvedCredentialsId = credentialsId;
            } else {
                CoverityConnectInstance coverityConnectInstance = CoverityConfigService.fromListener(listener).getCoverityInstanceOrAbort(resolvedCoverityInstanceUrl);
                resolvedCredentialsId = coverityConnectInstance.getDefaultCredentialsId();
            }

            return CoverityCommandsFactory.fromPipeline(listener, envVars, run, launcher, node, null)
                    .getIssueCount(resolvedCoverityInstanceUrl,resolvedCredentialsId,resolvedProjectName,resolvedViewName,returnIssueCount);
        }

        private String getRequiredValueOrDie(String pipelineParameter, String parameterName, JenkinsCoverityEnvironmentVariable environmentVariable, UnaryOperator<String> getter) throws AbortException {
            if (StringUtils.isNotBlank(pipelineParameter)) {
                return pipelineParameter;
            }

            String valueFromEnvironmentVariable = getter.apply(environmentVariable.toString());
            if (StringUtils.isNotBlank(valueFromEnvironmentVariable)) {
                return valueFromEnvironmentVariable;
            }

            throw new AbortException(
                "Coverity issue check failed because required parameter " + parameterName + " was not set. Please set " + parameterName + " or populate $" + environmentVariable.toString() + " with the desired value.");
        }

    }
}
