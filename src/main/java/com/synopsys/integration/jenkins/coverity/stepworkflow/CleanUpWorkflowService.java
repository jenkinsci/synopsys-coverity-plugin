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

import static com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable.TEMPORARY_AUTH_KEY_PATH;

import java.io.IOException;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;

public class CleanUpWorkflowService {
    private final JenkinsIntLogger logger;
    private final VirtualChannel virtualChannel;
    private final String workspaceRemotePath;
    private final IntEnvironmentVariables intEnvironmentVariables;

    public CleanUpWorkflowService(JenkinsIntLogger logger, VirtualChannel virtualChannel, String workspaceRemotePath, IntEnvironmentVariables intEnvironmentVariables) {
        this.logger = logger;
        this.virtualChannel = virtualChannel;
        this.workspaceRemotePath = workspaceRemotePath;
        this.intEnvironmentVariables = intEnvironmentVariables;
    }

    public void cleanUpIntermediateDirectory() {
        try {
            FilePath intermediateDirectory = new FilePath(virtualChannel, workspaceRemotePath).child("idir");
            intermediateDirectory.deleteRecursive();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("WARNING: Synopsys Coverity for Jenkins could not clean up the intermediary directory.");
            logger.trace("Synopsys Coverity for Jenkins could not clean up the intermediary directory because: ", e);
        }
    }

    public boolean cleanUpAuthenticationFile() {
        String authKeyPath = intEnvironmentVariables.getValue(TEMPORARY_AUTH_KEY_PATH.toString());
        if (StringUtils.isNotBlank(authKeyPath)) {
            try {
                return new FilePath(virtualChannel, authKeyPath).delete();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                logger.error("FATAL: Synopsys Coverity for Jenkins could not clean up authentication file because: ", e);
            }
        }
        return true;
    }
}
