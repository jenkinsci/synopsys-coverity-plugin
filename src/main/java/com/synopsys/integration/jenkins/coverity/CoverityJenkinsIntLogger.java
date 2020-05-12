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

import java.io.Serializable;

import com.synopsys.integration.jenkins.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.log.LogLevel;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.model.TaskListener;

public class CoverityJenkinsIntLogger extends JenkinsIntLogger implements Serializable {
    private static final long serialVersionUID = 7672279347652895598L;

    private CoverityJenkinsIntLogger(final TaskListener jenkinsTaskListener, final LogLevel logLevel) {
        super(jenkinsTaskListener);
        this.setLogLevel(logLevel);
    }

    public static CoverityJenkinsIntLogger initializeLogger(final TaskListener jenkinsLogger, final IntEnvironmentVariables intEnvironmentVariables) {
        final String logLevelString = intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.LOG_LEVEL.toString());
        final LogLevel logLevel = LogLevel.fromString(logLevelString);
        final CoverityJenkinsIntLogger logger = new CoverityJenkinsIntLogger(jenkinsLogger, logLevel);

        final String versionString = JenkinsVersionHelper.getPluginVersion("synopsys-coverity")
                                         .map(version -> String.format("Running Synopsys Coverity version: %s", version))
                                         .orElse("Running Synopsys Coverity");
        logger.alwaysLog(versionString);
        return logger;
    }

    @Override
    public void setLogLevel(final IntEnvironmentVariables variables) {
        final String logLevel = variables.getValue(JenkinsCoverityEnvironmentVariable.LOG_LEVEL.toString(), LogLevel.INFO.toString());
        try {
            setLogLevel(LogLevel.valueOf(logLevel.toUpperCase()));
        } catch (final IllegalArgumentException e) {
            setLogLevel(LogLevel.INFO);
        }
    }

}
