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
package com.synopsys.integration.jenkins.coverity;

import java.net.MalformedURLException;

import com.synopsys.integration.coverity.api.ws.configuration.ConfigurationService;
import com.synopsys.integration.coverity.api.ws.configuration.LicenseDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.VersionDataObj;
import com.synopsys.integration.coverity.config.CoverityHttpClient;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.jenkins.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.phonehome.request.CoverityPhoneHomeRequestFactory;
import com.synopsys.integration.phonehome.request.PhoneHomeRequestBody;
import com.synopsys.integration.phonehome.request.PhoneHomeRequestBodyBuilder;
import com.synopsys.integration.stepworkflow.StepWorkflowResponse;
import com.synopsys.integration.stepworkflow.jenkins.JenkinsStepWorkflow;

import hudson.AbortException;

public abstract class CoverityJenkinsStepWorkflow<T> extends JenkinsStepWorkflow<T> {
    protected final WebServiceFactory webServiceFactory;

    public CoverityJenkinsStepWorkflow(final JenkinsIntLogger logger, final WebServiceFactory webServiceFactory) {
        super(logger);
        this.webServiceFactory = webServiceFactory;
    }

    @Override
    public StepWorkflowResponse<T> runWorkflow() throws AbortException {
        final Thread thread = Thread.currentThread();
        final ClassLoader threadClassLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(this.getClass().getClassLoader());
            return super.runWorkflow();
        } finally {
            thread.setContextClassLoader(threadClassLoader);
        }
    }

    protected PhoneHomeRequestBodyBuilder createPhoneHomeBuilder() {
        final CoverityPhoneHomeRequestFactory coverityPhoneHomeRequestFactory = new CoverityPhoneHomeRequestFactory("synopsys-coverity");
        final CoverityHttpClient coverityHttpClient = webServiceFactory.getCoverityHttpClient();
        String customerName;
        String cimVersion;

        try {
            final ConfigurationService configurationService = webServiceFactory.createConfigurationService();
            try {
                final LicenseDataObj licenseDataObj = configurationService.getLicenseConfiguration();
                customerName = licenseDataObj.getCustomer();
            } catch (final Exception e) {
                logger.trace("Couldn't get the Coverity customer id: " + e.getMessage());
                customerName = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            }

            try {
                final VersionDataObj versionDataObj = configurationService.getVersion();
                cimVersion = versionDataObj.getExternalVersion();
            } catch (final Exception e) {
                logger.trace("Couldn't get the Coverity version: " + e.getMessage());
                cimVersion = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            }
        } catch (final MalformedURLException e) {
            logger.trace("Couldn't get the Coverity customer id: " + e.getMessage());
            logger.trace("Couldn't get the Coverity version: " + e.getMessage());
            cimVersion = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            customerName = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
        }

        final String pluginVersion = JenkinsVersionHelper.getPluginVersion("synopsys-coverity").orElse(PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE);

        return coverityPhoneHomeRequestFactory.create(customerName, coverityHttpClient.getBaseUrl(), pluginVersion, cimVersion);
    }

}
