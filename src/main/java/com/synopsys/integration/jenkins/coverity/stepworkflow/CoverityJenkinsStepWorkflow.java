/*
 * synopsys-coverity
 *
 * Copyright (c) 2024 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import java.net.MalformedURLException;

import com.synopsys.integration.coverity.api.ws.configuration.ConfigurationService;
import com.synopsys.integration.coverity.api.ws.configuration.LicenseDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.VersionDataObj;
import com.synopsys.integration.coverity.config.CoverityHttpClient;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.function.ThrowingSupplier;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.synopsys.integration.phonehome.request.PhoneHomeRequestBody;
import com.synopsys.integration.phonehome.request.PhoneHomeRequestBodyBuilder;
import com.synopsys.integration.stepworkflow.StepWorkflowResponse;
import com.synopsys.integration.stepworkflow.jenkins.JenkinsStepWorkflow;

import hudson.AbortException;

public abstract class CoverityJenkinsStepWorkflow<T> extends JenkinsStepWorkflow<T> {
    protected final ThrowingSupplier<WebServiceFactory, CoverityJenkinsAbortException> webServiceFactorySupplier;
    protected WebServiceFactory webServiceFactory;

    public CoverityJenkinsStepWorkflow(JenkinsIntLogger logger, JenkinsVersionHelper jenkinsVersionHelper, ThrowingSupplier<WebServiceFactory, CoverityJenkinsAbortException> webServiceFactorySupplier) {
        super(logger, jenkinsVersionHelper);
        // Due to special classloading handling, it's better to get a supplier here that we use to fetch the Factory in our override of runWorkflow(). --rotte MAY 2020
        this.webServiceFactorySupplier = webServiceFactorySupplier;
    }

    @Override
    public StepWorkflowResponse<T> runWorkflow() throws AbortException {
        // Coverity Common uses JAX-WS, which requires special classloading handling for Jenkins instances running Java 9+ --rotte MAY 2020
        Thread thread = Thread.currentThread();
        ClassLoader threadClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(this.getClass().getClassLoader());
            webServiceFactory = webServiceFactorySupplier.get();
            return super.runWorkflow();
        } finally {
            this.cleanUp();
            thread.setContextClassLoader(threadClassLoader);
        }
    }

    protected abstract void cleanUp() throws AbortException;

    @Override
    protected PhoneHomeRequestBodyBuilder createPhoneHomeBuilder() {
        CoverityHttpClient coverityHttpClient = webServiceFactory.getCoverityHttpClient();
        String customerName;
        String cimVersion;

        try {
            ConfigurationService configurationService = webServiceFactory.createConfigurationService();
            try {
                LicenseDataObj licenseDataObj = configurationService.getLicenseConfiguration();
                customerName = licenseDataObj.getCustomer();
            } catch (Exception e) {
                logger.trace("Couldn't get the Coverity customer id: " + e.getMessage());
                customerName = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            }

            try {
                VersionDataObj versionDataObj = configurationService.getVersion();
                cimVersion = versionDataObj.getExternalVersion();
            } catch (Exception e) {
                logger.trace("Couldn't get the Coverity version: " + e.getMessage());
                cimVersion = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            }
        } catch (MalformedURLException e) {
            logger.trace("Couldn't get the Coverity customer id: " + e.getMessage());
            logger.trace("Couldn't get the Coverity version: " + e.getMessage());
            cimVersion = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            customerName = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
        }

        String pluginVersion = jenkinsVersionHelper.getPluginVersion("synopsys-coverity").orElse(PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE);

        return PhoneHomeRequestBodyBuilder.createForCoverity("synopsys-coverity", customerName, coverityHttpClient.getBaseUrl(), pluginVersion, cimVersion);
    }

}
