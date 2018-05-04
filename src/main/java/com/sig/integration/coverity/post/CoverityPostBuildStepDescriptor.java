/**
 * sig-coverity
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
package com.sig.integration.coverity.post;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.sig.integration.coverity.CoverityInstance;
import com.sig.integration.coverity.Messages;
import com.sig.integration.coverity.tools.CoverityToolInstallation;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

@Extension()
public class CoverityPostBuildStepDescriptor extends BuildStepDescriptor<Publisher> implements Serializable {
    private CoverityInstance coverityInstance;
    private CoverityToolInstallation[] coverityToolInstallations;

    public CoverityPostBuildStepDescriptor() {
        super(CoverityPostBuildStep.class);
        load();
    }

    public CoverityInstance getCoverityInstance() {
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
        coverityInstance = new CoverityInstance(url, credentialId);
        save();
        return super.configure(req, formData);
    }

    public FormValidation doCheckUrl(@QueryParameter("url") String url) {
        if (StringUtils.isBlank(url)) {
            return FormValidation.error(Messages.CoverityPostBuildStep_getPleaseSetServerUrl());
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            final StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            System.err.println(sw.toString());

            return FormValidation.error(Messages.CoverityPostBuildStep_getUrlError_0(e.getMessage()));
        }
        return FormValidation.ok();
    }

    public ListBoxModel doFillCredentialIdItems() {
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
            final AbstractProject<?, ?> project = null;
            boxModel = new StandardListBoxModel().withEmptySelection()
                               .withMatching(credentialsMatcher, CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, project, ACL.SYSTEM, Collections.emptyList()));
        } finally {
            if (changed) {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
        }
        return boxModel;
    }

    public FormValidation doTestConnection(@QueryParameter("url") String url, @QueryParameter("credentialId") String credentialId) {
        if (StringUtils.isBlank(url)) {
            return FormValidation.error(Messages.CoverityPostBuildStep_getPleaseSetServerUrl());
        }
        if (StringUtils.isBlank(credentialId)) {
            return FormValidation.error(Messages.CoverityPostBuildStep_getPleaseSetCoverityCredentials());
        }
        return FormValidation.ok(Messages.CoverityPostBuildStep_getConnectionSuccessful());
    }

    public ListBoxModel doFillCoverityToolNameItems() {
        ListBoxModel boxModel = new ListBoxModel();
        boxModel.add(Messages.CoverityToolInstallation_getNone(), "");
        if (null != coverityToolInstallations && coverityToolInstallations.length > 0) {
            for (CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
                boxModel.add(coverityToolInstallation.getName());
            }
        }
        return boxModel;
    }

    public FormValidation doCheckCoverityToolName(@QueryParameter("coverityToolName") String coverityToolName) {
        if (null == coverityToolInstallations || coverityToolInstallations.length == 0) {
            return FormValidation.error(Messages.CoverityToolInstallation_getNoToolsConfigured());
        }
        if (StringUtils.isBlank(coverityToolName)) {
            return FormValidation.error(Messages.CoverityToolInstallation_getPleaseChooseATool());
        }
        for (CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
            if (coverityToolInstallation.getName().equals(coverityToolName)) {
                return FormValidation.ok();
            }
        }
        return FormValidation.error(Messages.CoverityToolInstallation_getNoToolWithName_0(coverityToolName));
    }
}
