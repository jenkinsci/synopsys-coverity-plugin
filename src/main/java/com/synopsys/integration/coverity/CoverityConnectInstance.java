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
package com.synopsys.integration.coverity;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;

import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.model.Jenkins;

public class CoverityConnectInstance implements Serializable {
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

    private Optional<StandardUsernamePasswordCredentials> getCredentials() {
        if (StringUtils.isBlank(credentialId)) {
            return Optional.empty();
        }

        return findUsernamePasswordCredentialsWithId(credentialId);
    }

    private Optional<StandardUsernamePasswordCredentials> findUsernamePasswordCredentialsWithId(final String credentialId) {
        final IdMatcher idMatcher = new IdMatcher(credentialId);
        return CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, Collections.emptyList()).stream()
                   .filter(idMatcher::matches)
                   .findAny();
    }

    public boolean isEmpty() {
        return null == url && null == credentialId;
    }

}
