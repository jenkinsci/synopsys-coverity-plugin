/*
 * synopsys-coverity
 *
 * Copyright (c) 2022 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import java.io.IOException;

import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;

import hudson.FilePath;

public class CleanUpWorkflowService {
    private final JenkinsIntLogger logger;

    public CleanUpWorkflowService(JenkinsIntLogger logger) {
        this.logger = logger;
    }

    public void cleanUpIntermediateDirectory(FilePath intermediateDirectory) {
        try {
            intermediateDirectory.deleteRecursive();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("WARNING: Synopsys Coverity for Jenkins could not clean up the intermediary directory.");
            logger.trace("Synopsys Coverity for Jenkins could not clean up the intermediary directory because: ", e);
        }
    }

    public void cleanUpAuthenticationFile(FilePath authenticationKeyFile) {
        try {
            if (authenticationKeyFile.delete()) {
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
