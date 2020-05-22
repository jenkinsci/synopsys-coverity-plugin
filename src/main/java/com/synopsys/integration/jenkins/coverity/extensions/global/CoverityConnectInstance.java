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
package com.synopsys.integration.jenkins.coverity.extensions.global;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.jenkins.annotations.HelpMarkdown;
import com.synopsys.integration.jenkins.coverity.SynopsysCoverityCredentialsHelper;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectUrlFieldHelper;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.rest.credentials.Credentials;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class CoverityConnectInstance extends AbstractDescribableImpl<CoverityConnectInstance> {
    @HelpMarkdown("Specify the URL for your Coverity Connect instance.  \r\n"
                      + "Populates the $COV_HOST and $COV_PORT environment variables")
    private final String url;

    @HelpMarkdown("Specify credentials or authentication key file for authenticating with your Coverity Connect instance.  \r\n"
                      + "**Note:** \"Username with password\" and \"Secret File\" are the only kind of credentials supported.")
    private final String credentialId;

    @DataBoundConstructor
    public CoverityConnectInstance(final String url, final String credentialId) {
        this.url = url;
        this.credentialId = credentialId;
    }

    public String getUrl() {
        return url;
    }

    public String getCredentialId() {
        return credentialId;
    }

    public Optional<URL> getCoverityURL() {
        URL coverityUrl = null;
        if (url != null) {
            try {
                coverityUrl = new URL(url);
            } catch (final MalformedURLException ignored) {
                // Handled by form validation in the global configuration
            }
        }

        return Optional.ofNullable(coverityUrl);
    }

    public CoverityServerConfig getCoverityServerConfig(IntLogger logger) throws IllegalArgumentException, IllegalStateException {
        return CoverityServerConfig.newBuilder()
                   .setUrl(url)
                   .setCredentials(getCoverityServerCredentials(logger))
                   .build();
    }

    public Credentials getCoverityServerCredentials(IntLogger logger) {
        SynopsysCoverityCredentialsHelper synopsysCoverityCredentialsHelper = new SynopsysCoverityCredentialsHelper(logger, Jenkins.getInstance());
        return synopsysCoverityCredentialsHelper.getIntegrationCredentialsById(credentialId);
    }

    public Optional<String> getUsername(IntLogger logger) {
        SynopsysCoverityCredentialsHelper synopsysCoverityCredentialsHelper = new SynopsysCoverityCredentialsHelper(logger, Jenkins.getInstance());
        return synopsysCoverityCredentialsHelper.getCoverityUsernameById(credentialId);
    }

    public Optional<String> getPassphrase() {
        SynopsysCoverityCredentialsHelper synopsysCoverityCredentialsHelper = SynopsysCoverityCredentialsHelper.silentHelper(Jenkins.getInstance());
        return synopsysCoverityCredentialsHelper.getCoverityPassphraseById(credentialId);
    }

    public Optional<String> getAuthenticationKeyFileContents(IntLogger logger) throws CoverityJenkinsAbortException {
        SynopsysCoverityCredentialsHelper synopsysCoverityCredentialsHelper = new SynopsysCoverityCredentialsHelper(logger, Jenkins.getInstance());
        Optional<FileCredentialsImpl> authenticationKeyFileCredentials = synopsysCoverityCredentialsHelper.getAuthenticationKeyFileCredentialsById(credentialId);
        String contents = null;

        if (authenticationKeyFileCredentials.isPresent()) {
            try (InputStream inputStream = authenticationKeyFileCredentials.get().getContent()) {
                contents = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new CoverityJenkinsAbortException("Authentication Key File could not be read from the Synopsys Coverity for Jenkins global configuration.");
            }
        }

        return Optional.ofNullable(contents);
    }

    public boolean isEmpty() {
        return null == url && null == credentialId;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CoverityConnectInstance> {
        private final CoverityConnectUrlFieldHelper coverityConnectUrlFieldHelper;

        public DescriptorImpl() {
            super(CoverityConnectInstance.class);
            load();
            final Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
            coverityConnectUrlFieldHelper = new CoverityConnectUrlFieldHelper(slf4jIntLogger);
        }

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
            return SynopsysCoverityCredentialsHelper.silentHelper(Jenkins.getInstance()).listSupportedCredentials();
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

            final CoverityConnectInstance coverityConnectInstance = new CoverityConnectInstance(url, credentialId);
            return coverityConnectUrlFieldHelper.testConnectionToCoverityInstance(coverityConnectInstance);
        }
    }

}
