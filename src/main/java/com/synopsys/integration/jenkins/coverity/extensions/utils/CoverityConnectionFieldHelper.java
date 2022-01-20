/*
 * synopsys-coverity
 *
 * Copyright (c) 2022 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.stream.Collectors;

import javax.xml.ws.WebServiceException;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.rest.client.ConnectionResult;
import com.synopsys.integration.rest.credentials.Credentials;

import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CoverityConnectionFieldHelper extends FieldHelper {
    public CoverityConnectionFieldHelper(IntLogger logger) {
        super(logger);
    }

    public ListBoxModel doFillCoverityInstanceUrlItems() {
        ListBoxModel listBoxModel = GlobalValueHelper.getGlobalCoverityConnectInstances().stream()
                                              .map(CoverityConnectInstance::getUrl)
                                              .map(this::wrapAsListBoxModelOption)
                                              .collect(Collectors.toCollection(ListBoxModel::new));
        listBoxModel.add("- none -", "");
        return listBoxModel;
    }

    public FormValidation doCheckCoverityInstanceUrl(String coverityInstance, Boolean overrideDefaultCredentials, String credentialsId) {
        if (GlobalValueHelper.getGlobalCoverityConnectInstances().isEmpty()) {
            return FormValidation.error("There are no Coverity instances configured");
        }

        if (StringUtils.isBlank(coverityInstance)) {
            return FormValidation.error("Please choose one of the Coverity instances");
        }

        if (Boolean.TRUE.equals(overrideDefaultCredentials)) {
            return testConnectionIgnoreSuccessMessage(coverityInstance, credentialsId);
        }
        return testConnectionIgnoreSuccessMessage(coverityInstance);
    }

    public FormValidation doCheckCoverityInstanceUrlIgnoreMessage(String coverityInstance, Boolean overrideDefaultCredentials, String credentialsId) {
        FormValidation formValidation = doCheckCoverityInstanceUrl(coverityInstance, overrideDefaultCredentials, credentialsId);

        if (formValidation.kind.equals(FormValidation.Kind.ERROR)) {
            return FormValidation.error("Could not connect to selected Coverity instance.");
        } else {
            return FormValidation.ok();
        }
    }

    public FormValidation testConnectionIgnoreSuccessMessage(String jenkinsCoverityInstanceUrl) {
        return GlobalValueHelper.getCoverityInstanceWithUrl(logger, jenkinsCoverityInstanceUrl)
                   .map(coverityConnectInstance -> this.testConnectionIgnoreSuccessMessage(coverityConnectInstance, coverityConnectInstance.getDefaultCredentialsId()))
                   .orElse(FormValidation.error("There are no Coverity instances configured with the name %s", jenkinsCoverityInstanceUrl));
    }

    public FormValidation testConnectionIgnoreSuccessMessage(String jenkinsCoverityInstanceUrl, String credentialsId) {
        return GlobalValueHelper.getCoverityInstanceWithUrl(logger, jenkinsCoverityInstanceUrl)
                   .map(coverityConnectInstance -> this.testConnectionIgnoreSuccessMessage(coverityConnectInstance, credentialsId))
                   .orElse(FormValidation.error("There are no Coverity instances configured with the name %s", jenkinsCoverityInstanceUrl));
    }

    public FormValidation testConnectionToCoverityInstance(CoverityConnectInstance coverityConnectInstance, String credentialsId) {
        String url = coverityConnectInstance.getCoverityURL().map(URL::toString).orElse(StringUtils.EMPTY);
        Credentials credentials = coverityConnectInstance.getCoverityServerCredentials(logger, credentialsId);

        return testConnectionTo(url, credentials);
    }

    public FormValidation testConnectionTo(String url, Credentials credentials) {
        Thread thread = Thread.currentThread();
        ClassLoader threadClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(this.getClass().getClassLoader());

        try {
            CoverityServerConfig coverityServerConfig = CoverityServerConfig.newBuilder().setUrl(url)
                                                                  .setCredentials(credentials)
                                                                  .build();

            coverityServerConfig.createWebServiceFactory(logger).connect();

            ConnectionResult connectionResult = coverityServerConfig.attemptConnection(logger);

            if (connectionResult.isFailure()) {
                return FormValidation.error(String.format("Could not connect to %s: %s (Status code: %s)", url, connectionResult.getFailureMessage().orElse(StringUtils.EMPTY), connectionResult.getHttpStatusCode()));
            }

            return FormValidation.ok("Successfully connected to " + url);
        } catch (MalformedURLException e) {
            return FormValidation.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (WebServiceException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unauthorized")) {
                return FormValidation.error(e, String.format("Web service error occurred when attempting to connect to %s%s%s: %s", url, System.lineSeparator(), e.getClass().getSimpleName(), e.getMessage()));
            }
            return FormValidation.error(e, String.format("User authentication failed when attempting to connect to %s%s%s: %s", url, System.lineSeparator(), e.getClass().getSimpleName(), e.getMessage()));
        } catch (CoverityIntegrationException | IllegalArgumentException e) {
            return FormValidation.error(e, e.getMessage());
        } catch (Exception e) {
            return FormValidation.error(e, String.format("An unexpected error occurred when attempting to connect to %s%s%s: %s", url, System.lineSeparator(), e.getClass().getSimpleName(), e.getMessage()));
        } finally {
            thread.setContextClassLoader(threadClassLoader);
        }
    }

    private FormValidation testConnectionIgnoreSuccessMessage(CoverityConnectInstance coverityConnectInstance, String credentialsId) {
        FormValidation connectionTest = testConnectionToCoverityInstance(coverityConnectInstance, credentialsId);
        if (FormValidation.Kind.OK.equals(connectionTest.kind)) {
            return FormValidation.ok();
        } else {
            return connectionTest;
        }
    }

}
