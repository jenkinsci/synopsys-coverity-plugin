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
package com.synopsys.integration.coverity.post;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.ws.WebServiceException;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import com.blackducksoftware.integration.log.LogLevel;
import com.blackducksoftware.integration.log.PrintStreamIntLogger;
import com.blackducksoftware.integration.validator.FieldEnum;
import com.blackducksoftware.integration.validator.ValidationResult;
import com.blackducksoftware.integration.validator.ValidationResults;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.synopsys.integration.coverity.CoverityVersion;
import com.synopsys.integration.coverity.JenkinsCoverityInstance;
import com.synopsys.integration.coverity.Messages;
import com.synopsys.integration.coverity.common.CoverityCommonDescriptor;
import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.config.CoverityServerConfigBuilder;
import com.synopsys.integration.coverity.config.CoverityServerConfigValidator;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.coverity.tools.CoverityToolInstallation;
import com.synopsys.integration.coverity.ws.WebServiceFactory;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

@Extension()
public class CoverityPostBuildStepDescriptor extends BuildStepDescriptor<Publisher> implements Serializable {
    public static final CoverityVersion MINIMUM_SUPPORTED_VERSION = CoverityVersion.VERSION_JASPER;
    private final transient CoverityCommonDescriptor coverityCommonDescriptor;
    private JenkinsCoverityInstance coverityInstance;
    private CoverityToolInstallation[] coverityToolInstallations;

    public CoverityPostBuildStepDescriptor() {
        super(CoverityPostBuildStep.class);
        load();
        coverityCommonDescriptor = new CoverityCommonDescriptor();
    }

    public JenkinsCoverityInstance getCoverityInstance() {
        return coverityInstance;
    }

    public String getUrl() {
        String url = null;
        if (null != coverityInstance) {
            url = coverityInstance.getUrl();
        }
        return url;
    }

    public String getCredentialId() {
        String credentialId = null;
        if (null != coverityInstance) {
            credentialId = coverityInstance.getCredentialId();
        }
        return credentialId;
    }

    public CoverityToolInstallation[] getCoverityToolInstallations() {
        return coverityToolInstallations;
    }

    public void setCoverityToolInstallations(CoverityToolInstallation[] coverityToolInstallations) {
        this.coverityToolInstallations = coverityToolInstallations;
        save();
    }

    @Override
    public String getDisplayName() {
        return Messages.CoverityPostBuildStep_getDisplayName();
    }

    @Override
    public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
        return true;
    }

    @Override
    public boolean configure(final StaplerRequest req, final JSONObject formData) throws Descriptor.FormException {
        String url = formData.getString("url");
        String credentialId = formData.getString("credentialId");
        coverityInstance = new JenkinsCoverityInstance(url, credentialId);
        save();
        return super.configure(req, formData);
    }

    ///////// Global configuration methods /////////
    @POST
    public FormValidation doCheckUrl(@QueryParameter("url") String url) {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if (StringUtils.isBlank(url)) {
            return FormValidation.error(Messages.CoverityPostBuildStep_getPleaseSetServerUrl());
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return FormValidation.error(Messages.CoverityPostBuildStep_getUrlError_0(e.getMessage()));
        }
        return FormValidation.ok();
    }

    public ListBoxModel doFillCredentialIdItems() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        ListBoxModel boxModel = null;
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        boolean changed = false;
        try {
            if (CoverityPostBuildStepDescriptor.class.getClassLoader() != originalClassLoader) {
                changed = true;
                Thread.currentThread().setContextClassLoader(CoverityPostBuildStepDescriptor.class.getClassLoader());
            }
            final CredentialsMatcher credentialsMatcher = CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
            // Dont want to limit the search to a particular project for the drop down menu
            boxModel = new StandardListBoxModel().withEmptySelection()
                    .withMatching(credentialsMatcher, CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, Collections.emptyList()));
        } finally {
            if (changed) {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
        return boxModel;
    }

    @POST
    public FormValidation doTestConnection(@QueryParameter("url") String url, @QueryParameter("credentialId") String credentialId) {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if (StringUtils.isBlank(url)) {
            return FormValidation.error(Messages.CoverityPostBuildStep_getPleaseSetServerUrl());
        }
        if (StringUtils.isBlank(credentialId)) {
            return FormValidation.error(Messages.CoverityPostBuildStep_getPleaseSetCoverityCredentials());
        }
        JenkinsCoverityInstance jenkinsCoverityInstance = new JenkinsCoverityInstance(url, credentialId);
        URL actualURL = jenkinsCoverityInstance.getCoverityURL().orElse(null);
        String username = jenkinsCoverityInstance.getCoverityUsername().orElse(null);
        String password = jenkinsCoverityInstance.getCoverityPassword().orElse(null);
        try {
            CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
            builder.url(url).username(username).password(password);
            CoverityServerConfigValidator validator = builder.createValidator();
            ValidationResults results = validator.assertValid();
            if (!results.isEmpty() && (results.hasErrors() || results.hasWarnings())) {
                // Create a nicer more readable string to show the User instead of what the builder exception will provide
                StringBuilder stringBuilder = new StringBuilder();
                for (Map.Entry<FieldEnum, Set<ValidationResult>> entry : results.getResultMap().entrySet()) {
                    stringBuilder.append(entry.getKey().name());
                    stringBuilder.append(": ");
                    Set<ValidationResult> resultSet = entry.getValue();
                    List<String> messages = resultSet.stream().map(thing -> thing.getMessage()).collect(Collectors.toList());
                    String value = String.join(", ", messages);
                    stringBuilder.append(value);
                    stringBuilder.append(System.lineSeparator());
                }
                return FormValidation.error(stringBuilder.toString());
            }

            CoverityServerConfig coverityServerConfig = builder.buildObject();
            WebServiceFactory webServiceFactory = new WebServiceFactory(coverityServerConfig, new PrintStreamIntLogger(System.out, LogLevel.DEBUG));

            try {
                webServiceFactory.connect();
            } catch (CoverityIntegrationException e) {
                e.printStackTrace();
                return FormValidation.error(e.getMessage());
            } catch (MalformedURLException e) {
                return FormValidation.error(e.getClass().getSimpleName() + ": " + e.getMessage());
            }

            return FormValidation.ok("Successfully connected to the instance.");
        } catch (WebServiceException e) {
            if (org.apache.commons.lang.StringUtils.containsIgnoreCase(e.getMessage(), "Unauthorized")) {
                return FormValidation.error("User authentication failed." + System.lineSeparator() + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            return FormValidation.error(e, "Web service error occurred. " + System.lineSeparator() + e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return FormValidation.error(e, "An unexpected error occurred. " + System.lineSeparator() + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    ///////// End of global configuration methods /////////

    public ListBoxModel doFillCoverityToolNameItems() {
        return coverityCommonDescriptor.doFillCoverityToolNameItems(coverityToolInstallations);
    }

    public FormValidation doCheckCoverityToolName(@QueryParameter("coverityToolName") String coverityToolName) {
        return coverityCommonDescriptor.doCheckCoverityToolName(coverityToolInstallations, coverityToolName);
    }

    public ListBoxModel doFillBuildStateForIssuesItems() {
        return coverityCommonDescriptor.doFillBuildStateForIssuesItems();
    }

    // for doAutoComplete the variable will always be named value
    public AutoCompletionCandidates doAutoCompleteProjectName(@QueryParameter("value") String projectName) {
        return coverityCommonDescriptor.doAutoCompleteProjectName(projectName);
    }

    public FormValidation doCheckProjectName(@QueryParameter("projectName") String projectName) {
        return coverityCommonDescriptor.doCheckProjectName(projectName);
    }

    // for doAutoComplete the variable will always be named value
    public AutoCompletionCandidates doAutoCompleteStreamName(@QueryParameter("value") String streamName) {
        return coverityCommonDescriptor.doAutoCompleteStreamName(streamName);
    }

    public FormValidation doCheckStreamName(@QueryParameter("projectName") String projectName, @QueryParameter("streamName") String streamName) {
        return coverityCommonDescriptor.doCheckStreamName(projectName, streamName);
    }

    // for doAutoComplete the variable will always be named value
    public AutoCompletionCandidates doAutoCompleteViewName(@QueryParameter("value") String viewName) {
        return coverityCommonDescriptor.doAutoCompleteViewName(viewName);
    }

    public FormValidation doCheckViewName(@QueryParameter("viewName") String viewName) {
        return coverityCommonDescriptor.doCheckViewName(viewName);
    }
}
