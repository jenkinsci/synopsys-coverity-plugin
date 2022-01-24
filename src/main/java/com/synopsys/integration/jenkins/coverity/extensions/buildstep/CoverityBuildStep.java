/*
 * synopsys-coverity
 *
 * Copyright (c) 2022 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.jenkins.annotations.HelpMarkdown;
import com.synopsys.integration.jenkins.coverity.SynopsysCoverityCredentialsHelper;
import com.synopsys.integration.jenkins.coverity.extensions.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.extensions.CleanUpAction;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectionFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ProjectStreamFieldHelper;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityWorkflowStepFactory;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.extensions.JenkinsSelectBoxEnum;
import com.synopsys.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.wrapper.JenkinsWrapper;
import com.synopsys.integration.log.Slf4jIntLogger;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CoverityBuildStep extends Builder {
    // Jenkins directly serializes the names of the fields, so they are an important part of the plugin's API.
    public static final String FIELD_COVERITY_INSTANCE_URL = "coverityInstanceUrl";
    public static final String FIELD_PROJECT_NAME = "projectName";
    public static final String FIELD_STREAM_NAME = "streamName";
    public static final String NESTED_DESCRIPTOR_CHECK_FOR_ISSUES = "checkForIssuesInView";
    public static final String NESTED_DESCRIPTOR_CONFIGURE_CHANGE_SET_PATTERNS = "configureChangeSetPatterns";
    public static final String NESTED_DESCRIPTOR_COVERITY_RUN_CONFIGURATION = "coverityRunConfiguration";
    public static final String FIELD_ON_COMMAND_FAILURE = "onCommandFailure";
    public static final String FIELD_CLEAN_UP_ACTION = "cleanUpAction";
    public static final String FIELD_OVERRIDE_CREDENTIALS = "overrideDefaultCredentials";
    public static final String FIELD_CREDENTIALS_ID = "credentialsId";

    @HelpMarkdown("Specify which Synopsys Coverity connect instance to run this job against.  \r\n"
                      + "The resulting Synopsys Coverity connect instance URL is stored in the $COV_URL environment variable, and will affect both the full and incremental analysis.")
    private final String coverityInstanceUrl;

    @HelpMarkdown("Specify the name of the Coverity project.  \r\n"
                      + "The resulting project name is stored in the $COV_PROJECT environment variable, and will affect both the full and incremental analysis.")
    private final String projectName;

    @HelpMarkdown("Specify the name of the Coverity stream that you would like to use for the commands.  \r\n"
                      + "The resulting stream name is stored in the $COV_STREAM environment variable, and will affect both the full and incremental analysis.")
    private final String streamName;

    private final CheckForIssuesInView checkForIssuesInView;

    private final ConfigureChangeSetPatterns configureChangeSetPatterns;

    private final CoverityRunConfiguration coverityRunConfiguration;

    @HelpMarkdown("Specify the action to take if a Coverity static analysis command fails.")
    private final OnCommandFailure onCommandFailure;

    // Any field set by a DataBoundSetter should be explicitly declared as @Nullable to avoid accidental NPEs -- rotte 10/21/2019
    @Nullable
    @HelpMarkdown("Specify the clean up action to perform on a successful execution.  \r\n"
                      + "Will either persist or delete the intermediate directory created by the specified capture type.")
    private CleanUpAction cleanUpAction;

    @Nullable
    @HelpMarkdown("Specify the credentials to use with the Synopsys Coverity connect instance.")
    private String credentialsId;

    @Nullable
    private Boolean overrideDefaultCredentials;

    @DataBoundConstructor
    public CoverityBuildStep(String coverityInstanceUrl, String onCommandFailure, String projectName, String streamName, CheckForIssuesInView checkForIssuesInView,
        ConfigureChangeSetPatterns configureChangeSetPatterns, CoverityRunConfiguration coverityRunConfiguration) {
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.projectName = projectName;
        this.streamName = streamName;
        this.checkForIssuesInView = checkForIssuesInView;
        this.configureChangeSetPatterns = configureChangeSetPatterns;
        this.coverityRunConfiguration = coverityRunConfiguration;
        this.onCommandFailure = OnCommandFailure.valueOf(onCommandFailure);
    }

    public CleanUpAction getCleanUpAction() {
        return cleanUpAction;
    }

    @DataBoundSetter
    public void setCleanUpAction(CleanUpAction cleanUpAction) {
        this.cleanUpAction = cleanUpAction;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public Boolean getOverrideDefaultCredentials() {
        return overrideDefaultCredentials;
    }

    @DataBoundSetter
    public void setOverrideDefaultCredentials(Boolean overrideDefaultCredentials) {
        this.overrideDefaultCredentials = overrideDefaultCredentials;
    }

    public String getCoverityInstanceUrl() {
        return coverityInstanceUrl;
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

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        String remoteWorkingDirectoryPath = computeRemoteWorkingDirectory(coverityRunConfiguration, build.getWorkspace(), build.getProject());

        CoverityWorkflowStepFactory coverityWorkflowStepFactory = new CoverityWorkflowStepFactory(build.getEnvironment(listener), build.getBuiltOn(), launcher, listener);
        JenkinsIntLogger logger = coverityWorkflowStepFactory.getOrCreateLogger();
        JenkinsVersionHelper jenkinsVersionHelper = JenkinsWrapper.initializeFromJenkinsJVM().getVersionHelper();

        String resolvedCredentialsId;
        if (Boolean.TRUE.equals(overrideDefaultCredentials)) {
            resolvedCredentialsId = credentialsId;
        } else {
            CoverityConnectInstance coverityConnectInstance = coverityWorkflowStepFactory.getCoverityConnectInstanceFromUrl(coverityInstanceUrl);
            resolvedCredentialsId = coverityConnectInstance.getDefaultCredentialsId();
        }

        CoverityBuildStepWorkflow coverityBuildStepWorkflow = new CoverityBuildStepWorkflow(
            logger,
            jenkinsVersionHelper,
            () -> coverityWorkflowStepFactory.getWebServiceFactoryFromUrl(coverityInstanceUrl, resolvedCredentialsId),
            coverityWorkflowStepFactory,
            build,
            remoteWorkingDirectoryPath,
            coverityInstanceUrl,
            resolvedCredentialsId,
            projectName,
            streamName,
            coverityRunConfiguration,
            configureChangeSetPatterns,
            checkForIssuesInView,
            onCommandFailure,
            cleanUpAction
        );

        return coverityBuildStepWorkflow.perform();
    }

    private String computeRemoteWorkingDirectory(CoverityRunConfiguration coverityRunConfiguration, FilePath buildWorkspace, AbstractProject<?, ?> project) {
        boolean isDefaultCoverityWorkflow = CoverityRunConfiguration.RunConfigurationType.SIMPLE.equals(coverityRunConfiguration.getRunConFigurationType());
        String customWorkingDirectory = isDefaultCoverityWorkflow ? ((SimpleCoverityRunConfiguration) coverityRunConfiguration).getCustomWorkingDirectory() : null;

        if (StringUtils.isNotBlank(customWorkingDirectory)) {
            return customWorkingDirectory;
        } else if (buildWorkspace != null) {
            return buildWorkspace.getRemote();
        } else {
            return project.getCustomWorkspace();
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private final CoverityConnectionFieldHelper coverityConnectionFieldHelper;
        private final ProjectStreamFieldHelper projectStreamFieldHelper;
        private final SynopsysCoverityCredentialsHelper credentialsHelper;

        public DescriptorImpl() {
            super(CoverityBuildStep.class);
            load();

            Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
            coverityConnectionFieldHelper = new CoverityConnectionFieldHelper(slf4jIntLogger);
            projectStreamFieldHelper = new ProjectStreamFieldHelper(slf4jIntLogger);
            credentialsHelper = new SynopsysCoverityCredentialsHelper(slf4jIntLogger, JenkinsWrapper.initializeFromJenkinsJVM());
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "Execute Synopsys Coverity static analysis";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public ListBoxModel doFillCoverityInstanceUrlItems() {
            return coverityConnectionFieldHelper.doFillCoverityInstanceUrlItems();
        }

        public ListBoxModel doFillCredentialsIdItems() {
            return credentialsHelper.listSupportedCredentials();
        }

        public FormValidation doCheckCoverityInstanceUrl(@QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl, @QueryParameter(FIELD_OVERRIDE_CREDENTIALS) Boolean overrideDefaultCredentials, @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId) {
            return coverityConnectionFieldHelper.doCheckCoverityInstanceUrl(coverityInstanceUrl, overrideDefaultCredentials, credentialsId);
        }

        public ComboBoxModel doFillProjectNameItems(@QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl, @QueryParameter(FIELD_OVERRIDE_CREDENTIALS) Boolean overrideDefaultCredentials, @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId, @QueryParameter("updateNow") boolean updateNow) throws InterruptedException {
            if (updateNow) {
                projectStreamFieldHelper.updateNow(coverityInstanceUrl, overrideDefaultCredentials, credentialsId);
            }
            return projectStreamFieldHelper.getProjectNamesForComboBox(coverityInstanceUrl, overrideDefaultCredentials, credentialsId);
        }

        public FormValidation doCheckProjectName(@QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl, @QueryParameter(FIELD_OVERRIDE_CREDENTIALS) Boolean overrideDefaultCredentials, @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId, @QueryParameter(FIELD_PROJECT_NAME) String projectName) {
            FormValidation urlValidation = coverityConnectionFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl, overrideDefaultCredentials, credentialsId);
            if (urlValidation.kind == FormValidation.Kind.ERROR) {
                return urlValidation;
            } else {
                return projectStreamFieldHelper.checkForProjectInCache(coverityInstanceUrl, overrideDefaultCredentials, credentialsId, projectName);
            }
        }

        public ComboBoxModel doFillStreamNameItems(@QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl, @QueryParameter(FIELD_OVERRIDE_CREDENTIALS) Boolean overrideDefaultCredentials, @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId, @QueryParameter(FIELD_PROJECT_NAME) String projectName) throws InterruptedException {
            return projectStreamFieldHelper.getStreamNamesForComboBox(coverityInstanceUrl, overrideDefaultCredentials,credentialsId, projectName);
        }

        public FormValidation doCheckStreamName(@QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl, @QueryParameter(FIELD_OVERRIDE_CREDENTIALS) Boolean overrideDefaultCredentials, @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId, @QueryParameter(FIELD_PROJECT_NAME) String projectName, @QueryParameter(FIELD_STREAM_NAME) String streamName) {
            FormValidation urlValidation = coverityConnectionFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl, overrideDefaultCredentials, credentialsId);
            if (urlValidation.kind == FormValidation.Kind.ERROR) {
                return urlValidation;
            } else {
                return projectStreamFieldHelper.checkForStreamInCache(coverityInstanceUrl, overrideDefaultCredentials, credentialsId, projectName, streamName);
            }
        }

        public ListBoxModel doFillOnCommandFailureItems() {
            return JenkinsSelectBoxEnum.toListBoxModel(OnCommandFailure.values());
        }

        public ListBoxModel doFillCleanUpActionItems() {
            return JenkinsSelectBoxEnum.toListBoxModel(CleanUpAction.values());
        }

        public CoverityRunConfiguration getDefaultCoverityRunConfiguration() {
            return SimpleCoverityRunConfiguration.DEFAULT_CONFIGURATION();
        }

    }

}
