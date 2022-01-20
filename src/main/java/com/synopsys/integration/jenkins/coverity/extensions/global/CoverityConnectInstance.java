/*
 * synopsys-coverity
 *
 * Copyright (c) 2022 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
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
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.jenkins.annotations.HelpMarkdown;
import com.synopsys.integration.jenkins.coverity.SynopsysCoverityCredentialsHelper;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CoverityConnectionFieldHelper;
import com.synopsys.integration.jenkins.wrapper.JenkinsWrapper;
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

    @HelpMarkdown("Specify the default credentials or authentication key file for authenticating with your Coverity Connect instance. Credentials can be specified on a per-execution basis in the Freestyle or Pipeline job config.  \r\n"
                      + "The credentials specified here will be used if no other credentials are provided in the job config.  \r\n"
                      + "**Note:** \"Username with password\" and \"Secret File\" are the only kind of credentials supported.")
    private final String defaultCredentialsId;

    // A backwards-compatibility field for defaultCredentialsId.
    // Ideally we could find a way to remove this and use XSTREAM aliasing instead, but previous attempts have not been successful.
    // --rotte JUN 2021
    private transient String credentialId;

    @DataBoundConstructor
    public CoverityConnectInstance(String url, String defaultCredentialsId) {
        this.url = url;
        this.defaultCredentialsId = defaultCredentialsId;
    }

    @DataBoundSetter
    public void setCredentialId(String credentialId){
        this.credentialId = credentialId;
    }

    public String getDefaultCredentialsId() {
        if (credentialId != null) {
            return credentialId;
        }
        return defaultCredentialsId;
    }

    public String getUrl() {
        return url;
    }

    public Optional<URL> getCoverityURL() {
        URL coverityUrl = null;
        if (url != null) {
            try {
                coverityUrl = new URL(url);
            } catch (MalformedURLException ignored) {
                // Handled by form validation in the global configuration
            }
        }

        return Optional.ofNullable(coverityUrl);
    }

    public CoverityServerConfig getCoverityServerConfig(IntLogger logger, String credentialsId) throws IllegalArgumentException, IllegalStateException {
        return CoverityServerConfig.newBuilder()
                   .setUrl(url)
                   .setCredentials(getCoverityServerCredentials(logger, credentialsId))
                   .build();
    }

    public Credentials getCoverityServerCredentials(IntLogger logger, String credentialId) {
        SynopsysCoverityCredentialsHelper synopsysCoverityCredentialsHelper = new SynopsysCoverityCredentialsHelper(logger, JenkinsWrapper.initializeFromJenkinsJVM());
        return synopsysCoverityCredentialsHelper.getIntegrationCredentialsById(credentialId);
    }

    public Optional<String> getUsername(IntLogger logger, String credentialId) {
        SynopsysCoverityCredentialsHelper synopsysCoverityCredentialsHelper = new SynopsysCoverityCredentialsHelper(logger,JenkinsWrapper.initializeFromJenkinsJVM());
        return synopsysCoverityCredentialsHelper.getCoverityUsernameById(credentialId);
    }

    public Optional<String> getPassphrase(String credentialId) {
        SynopsysCoverityCredentialsHelper synopsysCoverityCredentialsHelper = SynopsysCoverityCredentialsHelper.silentHelper(JenkinsWrapper.initializeFromJenkinsJVM());
        return synopsysCoverityCredentialsHelper.getCoverityPassphraseById(credentialId);
    }

    public Optional<String> getAuthenticationKeyFileContents(IntLogger logger, String credentialId) throws CoverityJenkinsAbortException {
        SynopsysCoverityCredentialsHelper synopsysCoverityCredentialsHelper = new SynopsysCoverityCredentialsHelper(logger, JenkinsWrapper.initializeFromJenkinsJVM());
        Optional<FileCredentials> authenticationKeyFileCredentials = synopsysCoverityCredentialsHelper.getAuthenticationKeyFileCredentialsById(credentialId);
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
        return null == url && null == defaultCredentialsId;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CoverityConnectInstance> {
        private final CoverityConnectionFieldHelper coverityConnectionFieldHelper;
        private final SynopsysCoverityCredentialsHelper credentialsHelper;

        public DescriptorImpl() {
            super(CoverityConnectInstance.class);
            load();
            Slf4jIntLogger slf4jIntLogger = new Slf4jIntLogger(LoggerFactory.getLogger(this.getClass()));
            coverityConnectionFieldHelper = new CoverityConnectionFieldHelper(slf4jIntLogger);
            credentialsHelper = new SynopsysCoverityCredentialsHelper(slf4jIntLogger, JenkinsWrapper.initializeFromJenkinsJVM());
        }

        public FormValidation doCheckUrl(@QueryParameter("url") String url) {
            JenkinsWrapper.initializeFromJenkinsJVM().getJenkins().ifPresent(jenkins -> jenkins.checkPermission(Jenkins.ADMINISTER));
            if (StringUtils.isBlank(url)) {
                return FormValidation.error("Please provide a URL for the Coverity Connect instance.");
            }
            try {
                new URL(url);
            } catch (MalformedURLException e) {
                return FormValidation.error(e, String.format("The provided URL for the Coverity Connect instance is not a valid URL. Error: %s", e.getMessage()));
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillDefaultCredentialsIdItems() {
            return credentialsHelper.listSupportedCredentials();
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("url") String url, @QueryParameter("defaultCredentialsId") String defaultCredentialsId) {
            JenkinsWrapper.initializeFromJenkinsJVM().getJenkins().ifPresent(jenkins -> jenkins.checkPermission(Jenkins.ADMINISTER));
            FormValidation urlValidation = doCheckUrl(url);
            if (!FormValidation.Kind.OK.equals(urlValidation.kind)) {
                return urlValidation;
            }

            if (StringUtils.isBlank(defaultCredentialsId)) {
                return FormValidation.error("Please specify the credentials for the Coverity Connect instance.");
            }

            CoverityConnectInstance coverityConnectInstance = new CoverityConnectInstance(url, defaultCredentialsId);
            return coverityConnectionFieldHelper.testConnectionToCoverityInstance(coverityConnectInstance, defaultCredentialsId);
        }
    }

}
