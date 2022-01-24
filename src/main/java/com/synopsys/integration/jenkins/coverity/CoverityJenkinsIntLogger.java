/*
 * synopsys-coverity
 *
 * Copyright (c) 2022 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity;

import java.io.Serializable;

import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.synopsys.integration.log.LogLevel;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.model.TaskListener;

public class CoverityJenkinsIntLogger extends JenkinsIntLogger implements Serializable {
    private static final long serialVersionUID = 7672279347652895598L;

    private CoverityJenkinsIntLogger(TaskListener jenkinsTaskListener, LogLevel logLevel) {
        super(jenkinsTaskListener);
        this.setLogLevel(logLevel);
    }

    public static CoverityJenkinsIntLogger initializeLogger(TaskListener jenkinsLogger, IntEnvironmentVariables intEnvironmentVariables) {
        String logLevelString = intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.LOG_LEVEL.toString());
        LogLevel logLevel = LogLevel.fromString(logLevelString);
        return new CoverityJenkinsIntLogger(jenkinsLogger, logLevel);
    }

    public void logInitializationMessage(JenkinsVersionHelper jenkinsVersionHelper) {
        String versionString = jenkinsVersionHelper.getPluginVersion("synopsys-coverity")
                                   .map(version -> String.format("Running Synopsys Coverity version: %s", version))
                                   .orElse("Running Synopsys Coverity");
        this.alwaysLog(versionString);
    }

    @Override
    public void setLogLevel(IntEnvironmentVariables variables) {
        String logLevel = variables.getValue(JenkinsCoverityEnvironmentVariable.LOG_LEVEL.toString(), LogLevel.INFO.toString());
        try {
            setLogLevel(LogLevel.valueOf(logLevel.toUpperCase()));
        } catch (IllegalArgumentException e) {
            setLogLevel(LogLevel.INFO);
        }
    }

}
