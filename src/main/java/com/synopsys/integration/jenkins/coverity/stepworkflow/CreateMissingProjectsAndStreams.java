/*
 * synopsys-coverity
 *
 * Copyright (c) 2022 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import java.util.Optional;

import com.synopsys.integration.coverity.api.ws.configuration.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.StreamDataObj;
import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.stepworkflow.AbstractExecutingSubStep;
import com.synopsys.integration.stepworkflow.SubStepResponse;

public class CreateMissingProjectsAndStreams extends AbstractExecutingSubStep {
    private final JenkinsIntLogger logger;
    private final ConfigurationServiceWrapper configurationServiceWrapper;
    private final String projectName;
    private final String streamName;

    public CreateMissingProjectsAndStreams(JenkinsIntLogger logger, ConfigurationServiceWrapper configurationServiceWrapper, String projectName, String streamName) {
        this.logger = logger;
        this.configurationServiceWrapper = configurationServiceWrapper;
        this.projectName = projectName;
        this.streamName = streamName;
    }

    @Override
    public SubStepResponse<Object> run() {
        try {
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SubStepResponse.FAILURE(e);
        } catch (CovRemoteServiceException_Exception e) {
            return SubStepResponse.FAILURE(e);
        }

        return SubStepResponse.SUCCESS();
    }

}
