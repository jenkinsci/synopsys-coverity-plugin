/*
 * synopsys-coverity
 *
 * Copyright (c) 2024 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity;

import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.synopsys.integration.coverity.authentication.AuthenticationKeyFile;
import com.synopsys.integration.coverity.authentication.AuthenticationKeyFileUtility;
import com.synopsys.integration.jenkins.wrapper.JenkinsWrapper;
import com.synopsys.integration.jenkins.wrapper.SynopsysCredentialsHelper;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.log.SilentIntLogger;
import com.synopsys.integration.rest.credentials.CredentialsBuilder;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Optional;

public class SynopsysCoverityCredentialsHelper extends SynopsysCredentialsHelper {
    public static final Class<FileCredentials> AUTH_KEY_FILE_CREDENTIALS_CLASS = FileCredentials.class;
    public static final CredentialsMatcher AUTH_KEY_FILE_CREDENTIALS = CredentialsMatchers.instanceOf(AUTH_KEY_FILE_CREDENTIALS_CLASS);
    public static final CredentialsMatcher SUPPORTED_CREDENTIALS_TYPES = CredentialsMatchers.anyOf(AUTH_KEY_FILE_CREDENTIALS, CredentialsMatchers.instanceOf(USERNAME_PASSWORD_CREDENTIALS_CLASS));
    private final IntLogger logger;
    private final JenkinsWrapper jenkinsWrapper;

    public SynopsysCoverityCredentialsHelper(IntLogger logger, JenkinsWrapper jenkinsWrapper) {
        super(jenkinsWrapper);
        this.jenkinsWrapper = jenkinsWrapper;
        this.logger = logger;
    }

    public static SynopsysCoverityCredentialsHelper silentHelper(JenkinsWrapper jenkinsWrapper) {
        return new SynopsysCoverityCredentialsHelper(new SilentIntLogger(), jenkinsWrapper);
    }

    public ListBoxModel listSupportedCredentials() {
        Optional<Jenkins> optionalJenkins = jenkinsWrapper.getJenkins();
        if (optionalJenkins.isPresent()) {
            Jenkins jenkins = optionalJenkins.get();
            return new StandardListBoxModel()
                       .includeEmptyValue()
                       .includeMatchingAs(ACL.SYSTEM, jenkins, StandardCredentials.class, Collections.emptyList(), SUPPORTED_CREDENTIALS_TYPES);
        } else {
            return new StandardListBoxModel()
                .includeEmptyValue();
        }
    }

    public Optional<FileCredentials> getAuthenticationKeyFileCredentialsById(String credentialsId) {
        return getCredentialsById(AUTH_KEY_FILE_CREDENTIALS_CLASS, credentialsId);
    }

    public Optional<String> getCoverityUsernameById(String credentialsId) {
        return getIntegrationCredentialsById(credentialsId)
                   .getUsername();
    }

    public Optional<String> getCoverityPassphraseById(String credentialsId) {
        return getUsernamePasswordCredentialsById(credentialsId)
                   .map(UsernamePasswordCredentialsImpl::getPassword)
                   .map(Secret::getPlainText);
    }

    @Override
    public com.synopsys.integration.rest.credentials.Credentials getIntegrationCredentialsById(String credentialsId) {
        Optional<UsernamePasswordCredentialsImpl> possibleUsernamePasswordCredentials = getUsernamePasswordCredentialsById(credentialsId);
        Optional<FileCredentials> possibleAuthKeyCredentials = getAuthenticationKeyFileCredentialsById(credentialsId);
        CredentialsBuilder credentialsBuilder = com.synopsys.integration.rest.credentials.Credentials.newBuilder();

        if (possibleUsernamePasswordCredentials.isPresent()) {
            UsernamePasswordCredentialsImpl usernamePasswordCredentials = possibleUsernamePasswordCredentials.get();

            credentialsBuilder.setUsernameAndPassword(usernamePasswordCredentials.getUsername(), usernamePasswordCredentials.getPassword().getPlainText());
        }

        if (possibleAuthKeyCredentials.isPresent()) {
            FileCredentials fileCredentials = possibleAuthKeyCredentials.get();
            AuthenticationKeyFileUtility authenticationKeyFileUtility = AuthenticationKeyFileUtility.defaultUtility();

            try (InputStream keyFileInputStream = fileCredentials.getContent()) {
                AuthenticationKeyFile authenticationKeyFile = authenticationKeyFileUtility.readAuthenticationKeyFile(keyFileInputStream);

                credentialsBuilder.setUsernameAndPassword(authenticationKeyFile.username, authenticationKeyFile.key);
            } catch (IOException e) {
                logger.trace("Could not parse authentication key file with credentials id " + credentialsId + " because: ", e);
            }

        }

        return credentialsBuilder.build();
    }

    public void checkPermissionToAccessCredentials(Item item) {
        Jenkins jenkins = getJenkins();
        if (jenkins == null) {
            throw new RuntimeException("Jenkins instance is null");
        }

        if (item == null) {
            jenkins.checkPermission(Jenkins.ADMINISTER);
        } else {
            item.checkPermission(Item.EXTENDED_READ);
            item.checkPermission(CredentialsProvider.USE_ITEM);
        }
    }

    @Nullable
    private Jenkins getJenkins() {
        Optional<Jenkins> optionalJenkins = jenkinsWrapper.getJenkins();
        if (optionalJenkins.isPresent()) {
            return optionalJenkins.get();
        } else {
            return null;
        }
    }
}