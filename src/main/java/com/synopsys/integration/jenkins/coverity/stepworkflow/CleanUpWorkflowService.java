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
