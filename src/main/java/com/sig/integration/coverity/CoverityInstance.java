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
package com.sig.integration.coverity;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;

import hudson.model.AbstractProject;
import hudson.security.ACL;

public class CoverityInstance {
    private final String url;
    private final String credentialId;

    public CoverityInstance(String url, String credentialId) {
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
        try {
            return Optional.of(new URL(url));
        } catch (MalformedURLException e) {
            // problems with the URL will be shown in the global configuration
        }
        return Optional.empty();
    }

    public Optional<String> getCoverityUsername() {
        Optional<BaseStandardCredentials> optionalCredential = getCredential();
        if (optionalCredential.isPresent()) {
            BaseStandardCredentials credential = optionalCredential.get();
            if (credential instanceof UsernamePasswordCredentialsImpl) {
                final UsernamePasswordCredentialsImpl credentials = (UsernamePasswordCredentialsImpl) credential;
                return Optional.of(credentials.getUsername());
            }
        }
        return Optional.empty();
    }

    public Optional<String> getCoverityPassword() {
        Optional<BaseStandardCredentials> optionalCredential = getCredential();
        if (optionalCredential.isPresent()) {
            BaseStandardCredentials credential = optionalCredential.get();
            if (credential instanceof UsernamePasswordCredentialsImpl) {
                final UsernamePasswordCredentialsImpl credentials = (UsernamePasswordCredentialsImpl) credential;
                return Optional.of(credentials.getPassword().getPlainText());
            }
        }
        return Optional.empty();
    }

    public Optional<BaseStandardCredentials> getCredential() {
        Optional<BaseStandardCredentials> optionalCredential = Optional.empty();
        if (StringUtils.isNotBlank(credentialId)) {
            final AbstractProject<?, ?> project = null;
            final List<BaseStandardCredentials> credentials = CredentialsProvider.lookupCredentials(BaseStandardCredentials.class, project, ACL.SYSTEM, Collections.<DomainRequirement>emptyList());
            final IdMatcher matcher = new IdMatcher(credentialId);
            for (final BaseStandardCredentials c : credentials) {
                if (matcher.matches(c)) {
                    optionalCredential = Optional.of(c);
                }
            }
        }
        return optionalCredential;
    }
}
