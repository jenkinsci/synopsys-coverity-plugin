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
package com.synopsys.integration.coverity.common;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.blackducksoftware.integration.exception.EncryptionException;
import com.blackducksoftware.integration.log.IntLogger;
import com.blackducksoftware.integration.log.LogLevel;
import com.blackducksoftware.integration.log.PrintStreamIntLogger;
import com.synopsys.integration.coverity.JenkinsCoverityInstance;
import com.synopsys.integration.coverity.Messages;
import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.config.CoverityServerConfigBuilder;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.coverity.post.CoverityPostBuildStepDescriptor;
import com.synopsys.integration.coverity.tools.CoverityToolInstallation;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.v9.ConfigurationService;
import com.synopsys.integration.coverity.ws.v9.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.ws.v9.ProjectDataObj;
import com.synopsys.integration.coverity.ws.v9.ProjectFilterSpecDataObj;
import com.synopsys.integration.coverity.ws.v9.StreamDataObj;

import hudson.model.AutoCompletionCandidates;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class CoverityCommonDescriptor {

    private List<String> cachedStreams = null;
    private Instant lastTimeStreamsRetrieved = null;
    private Boolean retrievingStreamsNow = false;

    public ListBoxModel doFillCoverityToolNameItems(CoverityToolInstallation[] coverityToolInstallations) {
        ListBoxModel boxModel = new ListBoxModel();
        boxModel.add(Messages.CoverityToolInstallation_getNone(), "");
        if (null != coverityToolInstallations && coverityToolInstallations.length > 0) {
            for (CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
                boxModel.add(coverityToolInstallation.getName());
            }
        }
        return boxModel;
    }

    public FormValidation doCheckCoverityToolName(CoverityToolInstallation[] coverityToolInstallations, String coverityToolName) {
        if (null == coverityToolInstallations || coverityToolInstallations.length == 0) {
            return FormValidation.error(Messages.CoverityToolInstallation_getNoToolsConfigured());
        }
        if (StringUtils.isBlank(coverityToolName)) {
            return FormValidation.error(Messages.CoverityToolInstallation_getPleaseChooseATool());
        }
        for (CoverityToolInstallation coverityToolInstallation : coverityToolInstallations) {
            if (coverityToolInstallation.getName().equals(coverityToolName)) {
                return FormValidation.ok();
            }
        }
        return FormValidation.error(Messages.CoverityToolInstallation_getNoToolWithName_0(coverityToolName));
    }

    public ListBoxModel doFillBuildStateOnFailureItems() {
        ListBoxModel boxModel = new ListBoxModel();
        for (BuildState buildState : BuildState.values()) {
            boxModel.add(buildState.getDisplayValue());
        }
        return boxModel;
    }

    public AutoCompletionCandidates doAutoCompleteStreamName(String streamName) {
        AutoCompletionCandidates autoCompletionCandidates = new AutoCompletionCandidates();
        if (StringUtils.isNotBlank(streamName)) {
            checkAndUpdateCachedProjects();
            if (null != cachedStreams) {
                for (String stream : cachedStreams) {
                    if (stream.startsWith(streamName)) {
                        autoCompletionCandidates.add(stream);
                    }
                }
            }
        }
        return autoCompletionCandidates;
    }

    public FormValidation doCheckStreamName(String streamName) {
        if (StringUtils.isBlank(streamName)) {
            return FormValidation.ok();
        }
        checkAndUpdateCachedProjects();
        if (null == cachedStreams) {
            return FormValidation.ok();
        }
        for (String stream : cachedStreams) {
            if (stream.equals(streamName)) {
                return FormValidation.ok();
            }
        }
        if (streamName.contains("$")) {
            return FormValidation.warning(
                    String.format("The stream \"%s\" does not exist or you do not have permission to access it.%s The name appears to contain a variable which can only be resolved at the time of the build.", streamName,
                            System.lineSeparator()));
        }
        return FormValidation.error(String.format("The stream \"%s\" does not exist or you do not have permission to access it.", streamName));
    }

    private void checkAndUpdateCachedProjects() {
        if (!retrievingStreamsNow) {
            retrievingStreamsNow = true;
            IntLogger logger = new PrintStreamIntLogger(System.out, LogLevel.DEBUG);
            Instant now = Instant.now();
            if (null == cachedStreams || null == lastTimeStreamsRetrieved) {
                updateCachedStreams(now);
            } else if (null != lastTimeStreamsRetrieved) {
                Duration timeLapsed = Duration.between(lastTimeStreamsRetrieved, now);
                // only update the cached streams every 5 minutes
                if (timeLapsed.getSeconds() > TimeUnit.MINUTES.toSeconds(5)) {
                    updateCachedStreams(now);
                }
            }
        }
    }

    private void updateCachedStreams(Instant now) {
        IntLogger logger = new PrintStreamIntLogger(System.out, LogLevel.DEBUG);
        try {
            logger.info("Attempting retrieval of Coverity streams.");
            JenkinsCoverityInstance coverityInstance = getCoverityInstance();
            CoverityServerConfigBuilder builder = new CoverityServerConfigBuilder();
            URL coverityURL = coverityInstance.getCoverityURL().get();
            builder.url(coverityURL.toString());
            builder.username(coverityInstance.getCoverityUsername().orElse(null));
            builder.password(coverityInstance.getCoverityPassword().orElse(null));

            CoverityServerConfig coverityServerConfig = builder.build();
            WebServiceFactory webServiceFactory = new WebServiceFactory(coverityServerConfig, logger);
            webServiceFactory.connect();

            cachedStreams = new ArrayList<>();
            ConfigurationService configurationService = webServiceFactory.createConfigurationService();
            List<ProjectDataObj> projects = configurationService.getProjects(new ProjectFilterSpecDataObj());
            for (ProjectDataObj project : projects) {
                if (null != project.getStreams() && !project.getStreams().isEmpty()) {
                    for (StreamDataObj stream : project.getStreams()) {
                        if (null != stream.getId() && null != stream.getId().getName()) {
                            cachedStreams.add(stream.getId().getName());
                        }
                    }
                }
            }
            lastTimeStreamsRetrieved = now;
            logger.info("Updated the cached Coverity streams.");
        } catch (EncryptionException | MalformedURLException | CoverityIntegrationException | CovRemoteServiceException_Exception e) {
            logger.error(e);
        }
    }

    public CoverityPostBuildStepDescriptor getCoverityPostBuildStepDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(CoverityPostBuildStepDescriptor.class);
    }

    public JenkinsCoverityInstance getCoverityInstance() {
        return getCoverityPostBuildStepDescriptor().getCoverityInstance();
    }
}
