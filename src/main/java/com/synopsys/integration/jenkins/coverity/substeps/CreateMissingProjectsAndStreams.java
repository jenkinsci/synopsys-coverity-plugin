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
package com.synopsys.integration.jenkins.coverity.substeps;

import java.util.Optional;

import com.synopsys.integration.coverity.api.ws.configuration.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.StreamDataObj;
import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.substeps.AbstractVoidSubStep;
import com.synopsys.integration.jenkins.substeps.SubStepResponse;

public class CreateMissingProjectsAndStreams extends AbstractVoidSubStep {
    private final JenkinsCoverityLogger logger;
    private final ConfigurationServiceWrapper configurationServiceWrapper;
    private final String projectName;
    private final String streamName;

    public CreateMissingProjectsAndStreams(final JenkinsCoverityLogger logger, final ConfigurationServiceWrapper configurationServiceWrapper, final String projectName, final String streamName) {
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
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return SubStepResponse.FAILURE(e);
        } catch (final CovRemoteServiceException_Exception e) {
            return SubStepResponse.FAILURE(e);
        }

        return SubStepResponse.SUCCESS();
    }

}
