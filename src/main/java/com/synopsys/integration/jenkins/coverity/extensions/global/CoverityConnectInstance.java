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
package com.synopsys.integration.jenkins.coverity.extensions.global;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.slf4j.LoggerFactory;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;
import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.config.CoverityServerConfigBuilder;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectUrlFieldHelper;
import com.synopsys.integration.log.Slf4jIntLogger;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;

public class CoverityConnectInstance extends AbstractDescribableImpl<CoverityConnectInstance> implements Serializable {
    private static final long serialVersionUID = -7638734141012629078L;
    private final String url;
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

    public Optional<String> getCoverityUsername() {
        return getCredentials().map(StandardUsernamePasswordCredentials::getUsername);
    }

    public Optional<String> getCoverityPassword() {
        return getCredentials()
                   .map(StandardUsernamePasswordCredentials::getPassword)
                   .map(Secret::getPlainText);
    }

    public CoverityServerConfig getCoverityServerConfig() throws IllegalArgumentException, IllegalStateException {
        final CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder()
                                                        .setUrl(url);

        getCoverityUsername().ifPresent(builder::setUsername);
        getCoverityPassword().ifPresent(builder::setPassword);

        return builder.build();
    }

    private Optional<StandardUsernamePasswordCredentials> getCredentials() {
        if (StringUtils.isBlank(credentialId)) {
            return Optional.empty();
        }

        final IdMatcher idMatcher = new IdMatcher(credentialId);

        return CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, Collections.emptyList()).stream()
                   .filter(idMatcher::matches)
                   .findAny();
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
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            return new StandardListBoxModel()
                       .includeEmptyValue()
                       .includeMatchingAs(ACL.SYSTEM, Jenkins.getInstance(), StandardUsernamePasswordCredentials.class, Collections.emptyList(), CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));

            // There was classloader logic here that has been removed for brevity. -- rotte 11-16-2018 (with advice from jrichard)
            // Previous code can be found at 6c4432a8347d80a6fa01e3f28846c612862b61a6
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
