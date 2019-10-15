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
package com.synopsys.integration.jenkins.coverity.extensions.pipeline;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
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

import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectUrlFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ProjectStreamFieldHelper;
import com.synopsys.integration.jenkins.coverity.extensions.utils.ViewFieldHelper;
import com.synopsys.integration.jenkins.coverity.substeps.GetIssuesInView;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.phonehome.PhoneHomeResponse;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CheckForIssuesStep extends Step implements Serializable {
    public static final String DISPLAY_NAME = "Check for Issues in Coverity View";
    public static final String PIPELINE_NAME = "coverityIssueCheck";
    private static final long serialVersionUID = 3602102048550370960L;

    private String coverityInstanceUrl;
    private String projectName;
    private String viewName;
    private Boolean returnIssueCount;

    @DataBoundConstructor
    public CheckForIssuesStep() {
        // All fields are optional, so this constructor exists only to prevent some versions of the pipeline syntax generator from failing
    }

    public Boolean getReturnIssueCount() {
        if (Boolean.FALSE.equals(returnIssueCount)) {
            return null;
        }
        return returnIssueCount;
    }

    @DataBoundSetter
    public void setReturnIssueCount(final Boolean returnIssueCount) {
        this.returnIssueCount = returnIssueCount;
    }

    public String getCoverityInstanceUrl() {
        if (StringUtils.isBlank(coverityInstanceUrl)) {
            return null;
        }
        return coverityInstanceUrl;
    }

    @DataBoundSetter
    public void setCoverityInstanceUrl(final String coverityInstanceUrl) {
        this.coverityInstanceUrl = coverityInstanceUrl;
    }

    public String getProjectName() {
        if (StringUtils.isBlank(projectName)) {
            return null;
        }
        return projectName;
    }

    @DataBoundSetter
    public void setProjectName(final String projectName) {
        this.projectName = projectName;
    }

    public String getViewName() {
        if (StringUtils.isBlank(viewName)) {
            return null;
        }
        return viewName;
    }

    @DataBoundSetter
    public void setViewName(final String viewName) {
        this.viewName = viewName;
    }

    @Override
    public StepExecution start(final StepContext context) throws Exception {
        return new Execution(context);
    }

    @Symbol(PIPELINE_NAME)
    @Extension(optional = true)
    public static final class DescriptorImpl extends StepDescriptor {
        private transient final CoverityConnectUrlFieldHelper coverityConnectUrlFieldHelper;
        private transient final ProjectStreamFieldHelper projectStreamFieldHelper;
        private transient final ViewFieldHelper viewFieldHelper;

        public DescriptorImpl() {
            final Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
            coverityConnectUrlFieldHelper = new CoverityConnectUrlFieldHelper(slf4jIntLogger);
            projectStreamFieldHelper = new ProjectStreamFieldHelper(slf4jIntLogger);
            viewFieldHelper = new ViewFieldHelper(slf4jIntLogger);
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
            return coverityConnectUrlFieldHelper.doFillCoverityInstanceUrlItems();
        }

        public FormValidation doCheckCoverityInstanceUrl(@QueryParameter("coverityInstanceUrl") final String coverityInstanceUrl) {
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrl(coverityInstanceUrl);
        }

        public ListBoxModel doFillProjectNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("updateNow") boolean updateNow) throws InterruptedException {
            if (updateNow) {
                projectStreamFieldHelper.updateNow(coverityInstanceUrl);
            }
            return projectStreamFieldHelper.getProjectNamesForListBox(coverityInstanceUrl);
        }

        public FormValidation doCheckProjectName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillViewNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("updateNow") final boolean updateNow) throws InterruptedException {
            if (updateNow) {
                viewFieldHelper.updateNow(coverityInstanceUrl);
            }
            return viewFieldHelper.getViewNamesForListBox(coverityInstanceUrl);
        }

        public FormValidation doCheckViewName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return coverityConnectUrlFieldHelper.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
        }

    }

    public class Execution extends SynchronousNonBlockingStepExecution {
        private static final long serialVersionUID = -5807577350749324767L;
        private transient TaskListener listener;
        private transient EnvVars envVars;

        protected Execution(@Nonnull final StepContext context) throws InterruptedException, IOException {
            super(context);
            listener = context.get(TaskListener.class);
            envVars = context.get(EnvVars.class);
        }

        @Override
        protected Integer run() throws Exception {
            final IntEnvironmentVariables intEnvironmentVariables = new IntEnvironmentVariables(false);
            intEnvironmentVariables.putAll(envVars);
            final JenkinsCoverityLogger logger = JenkinsCoverityLogger.initializeLogger(listener, intEnvironmentVariables);

            final PhoneHomeResponse phoneHomeResponse = GlobalValueHelper.phoneHome(logger, coverityInstanceUrl);
            try {
                final String unresolvedCoverityInstanceUrl = getRequiredValueOrDie(coverityInstanceUrl, "coverityInstanceUrl", JenkinsCoverityEnvironmentVariable.COVERITY_URL, intEnvironmentVariables::getValue);
                final String resolvedCoverityInstanceUrl = Util.replaceMacro(unresolvedCoverityInstanceUrl, intEnvironmentVariables.getVariables());

                final String unresolvedProjectName = getRequiredValueOrDie(projectName, "projectName", JenkinsCoverityEnvironmentVariable.COVERITY_PROJECT, intEnvironmentVariables::getValue);
                final String resolvedProjectName = Util.replaceMacro(unresolvedProjectName, intEnvironmentVariables.getVariables());

                final String unresolvedViewName = getRequiredValueOrDie(viewName, "viewName", JenkinsCoverityEnvironmentVariable.COVERITY_VIEW, intEnvironmentVariables::getValue);
                final String resolvedViewName = Util.replaceMacro(unresolvedViewName, intEnvironmentVariables.getVariables());

                final WebServiceFactory webServiceFactory = GlobalValueHelper.createWebServiceFactoryFromUrl(logger, resolvedCoverityInstanceUrl);
                webServiceFactory.connect();
                final ViewService viewService = webServiceFactory.createViewService();
                final ConfigurationServiceWrapper configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();
                final GetIssuesInView getIssuesInView = new GetIssuesInView(logger, configurationServiceWrapper, viewService, resolvedProjectName, resolvedViewName);

                final int defectCount = getIssuesInView.getTotalIssuesInView();

                if (defectCount > 0) {
                    final String defectMessage = String.format("[Coverity] Found %s issues in view.", defectCount);
                    if (null != getReturnIssueCount() && getReturnIssueCount()) {
                        logger.error(defectMessage);
                    } else {
                        throw new CoverityJenkinsException(defectMessage);
                    }
                }

                return defectCount;
            } finally {
                if (null != phoneHomeResponse) {
                    phoneHomeResponse.getImmediateResult();
                }
            }
        }

        private String getRequiredValueOrDie(final String pipelineParamter, final String parameterName, final JenkinsCoverityEnvironmentVariable environmentVariable, final Function<String, String> environmentVariableGetter)
            throws CoverityJenkinsException {
            if (StringUtils.isNotBlank(pipelineParamter)) {
                return pipelineParamter;
            }

            final String valueFromEnvironmentVariable = environmentVariableGetter.apply(environmentVariable.toString());
            if (StringUtils.isNotBlank(valueFromEnvironmentVariable)) {
                return valueFromEnvironmentVariable;
            }

            throw new CoverityJenkinsException(
                "Coverity issue check failed because required parameter " + parameterName + " was not set. Please set " + parameterName + " or populate $" + environmentVariable.toString() + " with the desired value.");
        }

    }
}
