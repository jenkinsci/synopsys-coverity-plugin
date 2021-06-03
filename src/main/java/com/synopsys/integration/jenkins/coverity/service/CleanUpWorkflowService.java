/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.service;

import java.io.IOException;

import com.synopsys.integration.jenkins.coverity.service.common.CoverityRemotingService;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;

public class CleanUpWorkflowService {
    private final JenkinsIntLogger logger;
    private final CoverityRemotingService coverityRemotingService;

    public CleanUpWorkflowService(JenkinsIntLogger logger, CoverityRemotingService coverityRemotingService) {
        this.logger = logger;
        this.coverityRemotingService = coverityRemotingService;
    }

    public void cleanUpIntermediateDirectory(String intermediateDirectory) {
        try {
            coverityRemotingService.getRemoteFilePath(intermediateDirectory).deleteRecursive();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("WARNING: Synopsys Coverity for Jenkins could not clean up the intermediary directory.");
            logger.trace("Synopsys Coverity for Jenkins could not clean up the intermediary directory because: ", e);
        }
    }

    public void cleanUpAuthenticationFile(String authKeyFilePath) {
        try {
            if (coverityRemotingService.getRemoteFilePath(authKeyFilePath).delete()) {
                logger.debug("Authentication keyfile deleted successfully");
            } else {
                logger.warn("WARNING: Synopsys Coverity for Jenkins could not clean up the authentication key file. It may have been cleaned up by something else.");
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("ERROR: Synopsys Coverity for Jenkins could not clean up authentication file because: ", e);
        }
    }
}
