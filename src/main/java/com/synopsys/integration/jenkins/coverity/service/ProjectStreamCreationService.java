/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.service;

import java.net.MalformedURLException;
import java.util.Optional;

import com.synopsys.integration.coverity.api.ws.configuration.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.StreamDataObj;
import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;

public class ProjectStreamCreationService {
    private final JenkinsIntLogger logger;
    private final CoverityConfigService coverityConfigService;

    public ProjectStreamCreationService(JenkinsIntLogger logger, CoverityConfigService coverityConfigService) {
        this.logger = logger;
        this.coverityConfigService = coverityConfigService;
    }

    public void createMissingProjectOrStream(String coverityServerUrl, String credentialsId, String projectName, String streamName) throws CovRemoteServiceException_Exception, InterruptedException, CoverityJenkinsAbortException {
        WebServiceFactory webServiceFactory = coverityConfigService.getWebServiceFactoryFromUrl(coverityServerUrl, credentialsId);
        ConfigurationServiceWrapper configurationServiceWrapper;
        try {
            configurationServiceWrapper = webServiceFactory.createConfigurationServiceWrapper();
        } catch (MalformedURLException malformedURLException) {
            throw CoverityJenkinsAbortException.fromMalformedUrlException(coverityServerUrl + WebServiceFactory.CONFIGURATION_SERVICE_V9_WSDL, malformedURLException);
        }

        Optional<ProjectDataObj> matchingProject = configurationServiceWrapper.getProjectByExactName(projectName);
        if (!matchingProject.isPresent()) {
            logger.info(String.format("No project with the name '%s' was found, attempting creation...", projectName));
            configurationServiceWrapper.createSimpleProject(projectName);
            matchingProject = configurationServiceWrapper.getAndWaitForProjectWithExactName(projectName);

            if (matchingProject.isPresent()) {
                logger.info(String.format("Successfully created project '%s'", projectName));
            } else {
                logger.error(String.format("Could not create project '%s'", projectName));
            }
        }

        Optional<StreamDataObj> matchingStream = configurationServiceWrapper.getStreamByExactName(streamName);
        if (!matchingStream.isPresent() && matchingProject.isPresent()) {
            logger.info(String.format("No stream with the name '%s' was found, attempting creation as an Any language stream with the Default Triage Store in project '%s'...", streamName, projectName));
            configurationServiceWrapper.createSimpleStreamInProject(matchingProject.get().getId(), streamName);
            matchingStream = configurationServiceWrapper.getAndWaitForStreamWithExactName(streamName);

            if (matchingStream.isPresent()) {
                logger.info(String.format("Successfully created stream '%s'", streamName));
            } else {
                logger.error(String.format("Could not create stream '%s'", streamName));
            }
        }
    }

}
