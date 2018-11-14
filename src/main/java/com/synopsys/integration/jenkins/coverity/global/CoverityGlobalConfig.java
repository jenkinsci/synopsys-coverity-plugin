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
package com.synopsys.integration.jenkins.coverity.global;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.synopsys.integration.coverity.CoverityVersion;
import com.synopsys.integration.coverity.JenkinsCoverityInstance;
import com.synopsys.integration.coverity.common.CoverityCommonDescriptor;
import com.synopsys.integration.coverity.tools.CoverityToolInstallation;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

@Extension
public class CoverityGlobalConfig extends GlobalConfiguration implements Serializable {
    public static final CoverityVersion MINIMUM_SUPPORTED_VERSION = CoverityVersion.VERSION_JASPER;

    private final CoverityCommonDescriptor coverityCommonDescriptor;
    private JenkinsCoverityInstance coverityInstance;
    private CoverityToolInstallation[] coverityToolInstallations;

    public CoverityGlobalConfig() {
        load();
        coverityCommonDescriptor = new CoverityCommonDescriptor();
    }

    @Override
    public boolean configure(final StaplerRequest req, final JSONObject formData) throws Descriptor.FormException {
        final String url = formData.getString("url");
        final String credentialId = formData.getString("credentialId");
        coverityInstance = new JenkinsCoverityInstance(url, credentialId);
        save();
        return super.configure(req, formData);
    }

    public Optional<JenkinsCoverityInstance> getCoverityInstance() {
        return Optional.ofNullable(coverityInstance);
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

    public void setCoverityToolInstallations(final CoverityToolInstallation[] coverityToolInstallations) {
        this.coverityToolInstallations = coverityToolInstallations;
        save();
    }

    @POST
    public FormValidation doCheckUrl(@QueryParameter("url") final String url) {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        if (StringUtils.isBlank(url)) {
            return FormValidation.error("Please provide a URL for the Coverity Connect instance.");
        }
        try {
            new URL(url);
        } catch (final MalformedURLException e) {
            return FormValidation.error(e, String.format("The provided URL for the Coverity Connect instance is not a valid URL. Error: %s", e.getMessage()));
        }
        return FormValidation.ok();
    }

    public ListBoxModel doFillCredentialIdItems() {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        ListBoxModel boxModel;
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        boolean changed = false;
        try {
            if (this.getClass().getClassLoader() != originalClassLoader) {
                changed = true;
                Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
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
    public FormValidation doTestConnection(@QueryParameter("url") final String url, @QueryParameter("credentialId") final String credentialId) {
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
        final FormValidation urlValidation = doCheckUrl(url);
        if (!FormValidation.Kind.OK.equals(urlValidation.kind)) {
            return urlValidation;
        }

        if (StringUtils.isBlank(credentialId)) {
            return FormValidation.error("Please specify the credentials for the Coverity Connect instance.");
        }

        final JenkinsCoverityInstance jenkinsCoverityInstance = new JenkinsCoverityInstance(url, credentialId);
        return coverityCommonDescriptor.testConnectionToCoverityInstance(jenkinsCoverityInstance);
    }

}
