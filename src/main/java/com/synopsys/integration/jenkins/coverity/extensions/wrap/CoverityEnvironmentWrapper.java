/*
 * synopsys-coverity
 *
 * Copyright (c) 2022 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.wrap;

import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.TEMPORARY_AUTH_KEY_PATH;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.jenkins.PasswordMaskingOutputStream;
import com.synopsys.integration.jenkins.annotations.HelpMarkdown;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.SynopsysCoverityCredentialsHelper;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectionFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.IssueViewFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ProjectStreamFieldHelper;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CleanUpWorkflowService;
import com.synopsys.integration.jenkins.coverity.stepworkflow.CoverityWorkflowStepFactory;
import com.synopsys.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.wrapper.JenkinsWrapper;
import com.synopsys.integration.log.SilentIntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildWrapper;

public class CoverityEnvironmentWrapper extends SimpleBuildWrapper {
    // Jenkins directly serializes the names of the fields, so they are an important part of the plugin's API.
    // Be aware by changing a field name, you will also need to change these strings and will likely break previous implementations.
    // Jenkins Common provides convenient access to the XSTREAM instance that Jenkins uses to serialize the classes, you can use the serialization methods on that class to rename fields without breaking them.
    // --rotte MAY 2021
    public static final String FIELD_COVERITY_INSTANCE_URL = "coverityInstanceUrl";
    public static final String FIELD_PROJECT_NAME = "projectName";
    public static final String FIELD_STREAM_NAME = "streamName";
    public static final String NESTED_DESCRIPTOR_CHECK_FOR_ISSUES = "checkForIssuesInView";
    public static final String NESTED_DESCRIPTOR_CONFIGURE_CHANGE_SET_PATTERNS = "configureChangeSetPatterns";
    public static final String NESTED_DESCRIPTOR_COVERITY_RUN_CONFIGURATION = "coverityRunConfiguration";
    public static final String FIELD_ON_COMMAND_FAILURE = "onCommandFailure";
    public static final String FIELD_CLEAN_UP_ACTION = "cleanUpAction";
    public static final String FIELD_CREDENTIALS_ID = "credentialsId";

    @HelpMarkdown("Specify which Synopsys Coverity connect instance to run this job against.")
    private final String coverityInstanceUrl;
    private final String coverityPassphrase;

    // Any field set by a DataBoundSetter should be explicitly declared as nullable to avoid NPEs
    @Nullable
    @HelpMarkdown("Specify the name of the Coverity project.  \r\n"
                      + "The resulting project name is stored in the $COV_PROJECT environment variable, and will affect both the full and incremental analysis.")
    private String projectName;

    @Nullable
    @HelpMarkdown("Specify the name of the Coverity stream that you would like to use for the commands.  \r\n"
                      + "The resulting stream name is stored in the $COV_STREAM environment variable, and will affect both the full and incremental analysis.")
    private String streamName;

    @Nullable
    @HelpMarkdown("Specify the name of the Coverity view that you would like to check for issues.  \r\n"
                      + "The resulting view name is stored in the $COV_VIEW environment variable, and affects checking for issues in both the full and incremental analysis, if configured.")
    private String viewName;

    @Nullable
    private ConfigureChangeSetPatterns configureChangeSetPatterns;

    @Nullable
    private Boolean createMissingProjectsAndStreams;

    @Nullable
    @HelpMarkdown("Specify the credentials to use with the Synopsys Coverity connect instance.")
    private String credentialsId;

    @DataBoundConstructor
    public CoverityEnvironmentWrapper(String coverityInstanceUrl) {
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.coverityPassphrase = GlobalValueHelper.getCoverityInstanceWithUrl(new SilentIntLogger(), coverityInstanceUrl)
                                      .flatMap(coverityConnectInstance -> coverityConnectInstance.getPassphrase(credentialsId))
                                      .orElse(StringUtils.EMPTY);
    }

    public Boolean getCreateMissingProjectsAndStreams() {
        if (Boolean.FALSE.equals(createMissingProjectsAndStreams)) {
            return null;
        }
        return createMissingProjectsAndStreams;
    }

    @DataBoundSetter
    public void setCreateMissingProjectsAndStreams(@QueryParameter("createMissingProjectsAndStreams") Boolean createMissingProjectsAndStreams) {
        this.createMissingProjectsAndStreams = createMissingProjectsAndStreams;
    }

    public String getCoverityInstanceUrl() {
        return coverityInstanceUrl;
    }

    public String getProjectName() {
        return projectName;
    }

    @DataBoundSetter
    public void setProjectName(@QueryParameter("projectName") String projectName) {
        this.projectName = projectName;
    }

    public String getStreamName() {
        return streamName;
    }

    @DataBoundSetter
    public void setStreamName(@QueryParameter("streamName") String streamName) {
        this.streamName = streamName;
    }

    public String getViewName() {
        return viewName;
    }

    @DataBoundSetter
    public void setViewName(@QueryParameter("viewName") String viewName) {
        this.viewName = viewName;
    }

    public ConfigureChangeSetPatterns getConfigureChangeSetPatterns() {
        return configureChangeSetPatterns;
    }

    @DataBoundSetter
    public void setConfigureChangeSetPatterns(@QueryParameter("configureChangeSetPatterns") ConfigureChangeSetPatterns configureChangeSetPatterns) {
        this.configureChangeSetPatterns = configureChangeSetPatterns;
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

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        Node node = Optional.ofNullable(workspace.toComputer())
                        .map(Computer::getNode)
                        .orElse(null);
        RunWrapper runWrapper = new RunWrapper(build, true);

        CoverityWorkflowStepFactory coverityWorkflowStepFactory = new CoverityWorkflowStepFactory(initialEnvironment, node, launcher, listener);
        CoverityJenkinsIntLogger logger = coverityWorkflowStepFactory.getOrCreateLogger();
        JenkinsVersionHelper jenkinsVersionHelper = JenkinsWrapper.initializeFromJenkinsJVM().getVersionHelper();
        List<ChangeLogSet<?>> changeLogSets;
        try {
            changeLogSets = runWrapper.getChangeSets();
        } catch (Exception e) {
            logger.warn(String.format("WARNING: Synopsys Coverity for Jenkins could not determine the change set, %s will be empty and %s will be 0.",
                JenkinsCoverityEnvironmentVariable.CHANGE_SET,
                JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE));

            changeLogSets = Collections.emptyList();
        }

        String resolvedCredentialsId;
        if (credentialsId != null) {
            resolvedCredentialsId = credentialsId;
        } else {
            CoverityConnectInstance coverityConnectInstance = coverityWorkflowStepFactory.getCoverityConnectInstanceFromUrl(coverityInstanceUrl);
            resolvedCredentialsId = coverityConnectInstance.getDefaultCredentialsId();
        }

        CoverityEnvironmentWrapperStepWorkflow coverityEnvironmentWrapperStepWorkflow = new CoverityEnvironmentWrapperStepWorkflow(
            logger,
            jenkinsVersionHelper,
            () -> coverityWorkflowStepFactory.getWebServiceFactoryFromUrl(coverityInstanceUrl, resolvedCredentialsId),
            coverityWorkflowStepFactory,
            context,
            workspace.getRemote(),
            coverityInstanceUrl,
            resolvedCredentialsId,
            projectName,
            streamName,
            viewName,
            createMissingProjectsAndStreams,
            changeLogSets,
            configureChangeSetPatterns
        );
        Boolean environmentInjectedSuccessfully = coverityEnvironmentWrapperStepWorkflow.perform();
        if (Boolean.TRUE.equals(environmentInjectedSuccessfully)) {
            logger.info("Coverity environment injected successfully.");
        }

        context.setDisposer(new DisposerImpl((HashMap<String, String>) coverityWorkflowStepFactory.getOrCreateEnvironmentVariables().getVariables()));
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public ConsoleLogFilter createLoggerDecorator(@Nonnull Run<?, ?> build) {
        return new FilterImpl(coverityPassphrase);
    }

    @Symbol("withCoverityEnvironment")
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        private final CoverityConnectionFieldHelper coverityConnectionFieldHelper;
        private final ProjectStreamFieldHelper projectStreamFieldHelper;
        private final SynopsysCoverityCredentialsHelper credentialsHelper;
        private final IssueViewFieldHelper issueViewFieldHelper;

        public DescriptorImpl() {
            super(CoverityEnvironmentWrapper.class);
            load();

            Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
            coverityConnectionFieldHelper = new CoverityConnectionFieldHelper(slf4jIntLogger);
            projectStreamFieldHelper = new ProjectStreamFieldHelper(slf4jIntLogger);
            issueViewFieldHelper = new IssueViewFieldHelper(slf4jIntLogger);
            credentialsHelper = new SynopsysCoverityCredentialsHelper(slf4jIntLogger, JenkinsWrapper.initializeFromJenkinsJVM());
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

        public ComboBoxModel doFillProjectNameItems(
            @QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl,
            @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId,
            @QueryParameter("updateNow") boolean updateNow
        ) throws InterruptedException {
            if (updateNow) {
                projectStreamFieldHelper.updateNow(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId);
            }
            return projectStreamFieldHelper.getProjectNamesForComboBox(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId);
        }

        public FormValidation doCheckProjectName(
            @QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl,
            @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId
        ) {
            return coverityConnectionFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId);
        }

        public ComboBoxModel doFillStreamNameItems(
            @QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl,
            @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId,
            @QueryParameter(FIELD_PROJECT_NAME) String projectName
        ) throws InterruptedException {
            return projectStreamFieldHelper.getStreamNamesForComboBox(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId, projectName);
        }

        public FormValidation doCheckStreamName(
            @QueryParameter(FIELD_COVERITY_INSTANCE_URL) String coverityInstanceUrl,
            @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId) {
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
            @QueryParameter(FIELD_CREDENTIALS_ID) String credentialsId) {
            return coverityConnectionFieldHelper.doCheckCoverityInstanceUrl(coverityInstanceUrl, StringUtils.isNotBlank(credentialsId), credentialsId);
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Inject Coverity environment into the build process";
        }
    }

    private static final class FilterImpl extends ConsoleLogFilter implements Serializable {
        private static final long serialVersionUID = 1787519634824445328L;
        private final String passwordToMask;

        public FilterImpl(String passwordToMask) {
            this.passwordToMask = passwordToMask;
        }

        @Override
        public OutputStream decorateLogger(Run ignored, OutputStream logger) {
            return new PasswordMaskingOutputStream(logger, passwordToMask);
        }
    }

    private static final class DisposerImpl extends SimpleBuildWrapper.Disposer {
        private static final long serialVersionUID = 4771346213830683656L;
        private final HashMap<String, String> environmentVariables;

        public DisposerImpl(HashMap<String, String> intEnvironmentVariables) {
            this.environmentVariables = intEnvironmentVariables;
        }

        @Override
        public void tearDown(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
            IntEnvironmentVariables intEnvironmentVariables = IntEnvironmentVariables.empty();
            intEnvironmentVariables.putAll(environmentVariables);
            CoverityJenkinsIntLogger logger = CoverityJenkinsIntLogger.initializeLogger(listener, intEnvironmentVariables);
            CleanUpWorkflowService cleanUpWorkflowService = new CleanUpWorkflowService(logger);

            String authKeyPath = intEnvironmentVariables.getValue(TEMPORARY_AUTH_KEY_PATH.toString());
            if (StringUtils.isNotBlank(authKeyPath)) {
                FilePath authKeyFile = new FilePath(launcher.getChannel(), authKeyPath);
                cleanUpWorkflowService.cleanUpAuthenticationFile(authKeyFile);
            }
        }
    }

}
