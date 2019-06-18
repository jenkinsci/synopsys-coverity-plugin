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
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.stream.Collectors;

import javax.xml.ws.WebServiceException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.RepeatableCommand;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.SilentIntLogger;
import com.synopsys.integration.log.Slf4jIntLogger;
import com.synopsys.integration.rest.client.ConnectionResult;

import hudson.util.FormValidation;

public class CommonFieldValidator {
    private final Logger logger = LoggerFactory.getLogger(CommonFieldValidator.class);

    public FormValidation doCheckCoverityInstanceUrl(final String coverityInstance) {
        if (GlobalValueHelper.getGlobalCoverityConnectInstances().isEmpty()) {
            return FormValidation.error("There are no Coverity instances configured");
        }

        if (StringUtils.isBlank(coverityInstance)) {
            return FormValidation.error("Please choose one of the Coverity instances");
        }

        return testConnectionIgnoreSuccessMessage(coverityInstance);
    }

    public FormValidation doCheckCoverityInstanceUrlIgnoreMessage(final String coverityInstance) {
        final FormValidation formValidation = doCheckCoverityInstanceUrl(coverityInstance);

        if (formValidation.kind.equals(FormValidation.Kind.ERROR)) {
            return FormValidation.error("Selected Coverity instance is invalid.");
        } else {
            return FormValidation.ok();
        }
    }

    public FormValidation testConnectionIgnoreSuccessMessage(final String jenkinsCoverityInstanceUrl) {
        return GlobalValueHelper.getCoverityInstanceWithUrl(new SilentIntLogger(), jenkinsCoverityInstanceUrl)
                   .map(this::testConnectionIgnoreSuccessMessage)
                   .orElse(FormValidation.error("There are no Coverity instances configured with the name %s", jenkinsCoverityInstanceUrl));
    }

    public FormValidation checkForAlreadyProvidedArguments(final String command, final RepeatableCommand.Argument... providedArguments) {
        final String alreadyProvidedArguments = Arrays.stream(providedArguments)
                                                    .map(RepeatableCommand.Argument::toString)
                                                    .filter(command::contains)
                                                    .collect(Collectors.joining(", "));

        if (StringUtils.isNotBlank(alreadyProvidedArguments)) {
            return FormValidation.error(String.format("The argument(s) %s are automatically provided in this mode. If you wish to override, configure the 'Run custom Coverity commands' section instead.", alreadyProvidedArguments));
        }
        return FormValidation.ok();
    }

    public FormValidation testConnectionToCoverityInstance(final CoverityConnectInstance coverityConnectInstance) {
        final String url = coverityConnectInstance.getCoverityURL().map(URL::toString).orElse(null);
        final String username = coverityConnectInstance.getCoverityUsername().orElse(null);
        final String password = coverityConnectInstance.getCoverityPassword().orElse(null);

        try {
            final CoverityServerConfig coverityServerConfig = CoverityServerConfig.newBuilder().setUrl(url)
                                                                  .setUsername(username)
                                                                  .setPassword(password)
                                                                  .build();

            coverityServerConfig.createWebServiceFactory(new Slf4jIntLogger(logger)).connect();

            final ConnectionResult connectionResult = coverityServerConfig.attemptConnection(new Slf4jIntLogger(logger));

            if (connectionResult.isFailure()) {
                return FormValidation.error(String.format("Could not connect to %s: %s (Status code: %s)", url, connectionResult.getFailureMessage().orElse(StringUtils.EMPTY), connectionResult.getHttpStatusCode()));
            }

            return FormValidation.ok("Successfully connected to " + url);
        } catch (final MalformedURLException e) {
            return FormValidation.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (final WebServiceException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Unauthorized")) {
                return FormValidation.error(e, String.format("Web service error occurred when attempting to connect to %s%s%s: %s", url, System.lineSeparator(), e.getClass().getSimpleName(), e.getMessage()));
            }
            return FormValidation.error(e, String.format("User authentication failed when attempting to connect to %s%s%s: %s", url, System.lineSeparator(), e.getClass().getSimpleName(), e.getMessage()));
        } catch (final CoverityIntegrationException | IllegalArgumentException e) {
            return FormValidation.error(e, e.getMessage());
        } catch (final Exception e) {
            return FormValidation.error(e, String.format("An unexpected error occurred when attempting to connect to %s%s%s: %s", url, System.lineSeparator(), e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    private FormValidation testConnectionIgnoreSuccessMessage(final CoverityConnectInstance coverityConnectInstance) {
        final FormValidation connectionTest = testConnectionToCoverityInstance(coverityConnectInstance);
        if (FormValidation.Kind.OK.equals(connectionTest.kind)) {
            return FormValidation.ok();
        } else {
            return connectionTest;
        }
    }

}
