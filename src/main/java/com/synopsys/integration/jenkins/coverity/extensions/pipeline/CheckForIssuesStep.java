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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValidator;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValueProvider;
import com.synopsys.integration.jenkins.coverity.steps.GetDefectsInViewStep;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CheckForIssuesStep extends Step {
    public static final String DISPLAY_NAME = "Check for Issues in Coverity View";
    public static final String PIPELINE_NAME = "checkCoverityViewForDefects";

    private String coverityInstanceUrl;
    private String projectName;
    private String viewName;
    private Boolean returnDefectCount;

    @DataBoundConstructor
    public CheckForIssuesStep() {}

    public Boolean getReturnDefectCount() {
        return returnDefectCount;
    }

    @DataBoundSetter
    public void setReturnDefectCount(final Boolean returnDefectCount) {
        this.returnDefectCount = returnDefectCount;
    }

    public String getCoverityInstanceUrl() {
        return coverityInstanceUrl;
    }

    @DataBoundSetter
    public void setCoverityInstanceUrl(final String coverityInstanceUrl) {
        this.coverityInstanceUrl = coverityInstanceUrl;
    }

    public String getProjectName() {
        return projectName;
    }

    @DataBoundSetter
    public void setProjectName(final String projectName) {
        this.projectName = projectName;
    }

    public String getViewName() {
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

    @Extension(optional = true)
    public static final class DescriptorImpl extends StepDescriptor {
        private CommonFieldValueProvider commonFieldValueProvider;
        private CommonFieldValidator commonFieldValidator;

        public DescriptorImpl() {
            this.commonFieldValueProvider = new CommonFieldValueProvider();
            this.commonFieldValidator = new CommonFieldValidator();
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
            return commonFieldValueProvider.doFillCoverityInstanceUrlItems();
        }

        public FormValidation doCheckCoverityInstanceUrl(@QueryParameter("coverityInstanceUrl") final String coverityInstanceUrl) {
            return commonFieldValidator.doCheckCoverityInstanceUrl(coverityInstanceUrl);
        }

        public ListBoxModel doFillProjectNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("updateNow") boolean updateNow) {
            return commonFieldValueProvider.doFillProjectNameItems(coverityInstanceUrl, updateNow);
        }

        public FormValidation doCheckProjectName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return commonFieldValidator.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
        }

        public ListBoxModel doFillViewNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, @QueryParameter("updateNow") final boolean updateNow) {
            return commonFieldValueProvider.doFillViewNameItems(coverityInstanceUrl, updateNow);
        }

        public FormValidation doCheckViewName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
            return commonFieldValidator.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
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
            final JenkinsCoverityLogger logger = new JenkinsCoverityLogger(listener);
            final IntEnvironmentVariables intEnvironmentVariables = new IntEnvironmentVariables();
            intEnvironmentVariables.putAll(envVars);

            final WebServiceFactory webServiceFactory;
            try {
                webServiceFactory = GlobalValueHelper.createWebServiceFactoryFromUrl(logger, coverityInstanceUrl).orElseThrow(() -> new CoverityJenkinsException("Could not create WebServiceFactory from url " + coverityInstanceUrl));
            } catch (final IllegalArgumentException e) {
                throw new CoverityJenkinsException("There was an error connecting to the Coverity Connect instance at url " + coverityInstanceUrl, e);
            }

            final GetDefectsInViewStep getDefectsInViewStep = new GetDefectsInViewStep(logger, intEnvironmentVariables, webServiceFactory, projectName, viewName);

            final int defectCount = getDefectsInViewStep.getTotalDefectsInView();

            if (defectCount > 0) {
                final String defectMessage = String.format("[Coverity] Found %s defects in view.", defectCount);
                if (null != getReturnDefectCount() && getReturnDefectCount()) {
                    logger.error(defectMessage);
                } else {
                    throw new CoverityJenkinsException(defectMessage);
                }
            }

            return defectCount;
        }

    }
}
