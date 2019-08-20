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
package com.synopsys.integration.jenkins.coverity;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

import com.synopsys.integration.coverity.CoverityIntLogger;
import com.synopsys.integration.log.LogLevel;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.model.TaskListener;

public class JenkinsCoverityLogger extends CoverityIntLogger implements Serializable {
    private static final long serialVersionUID = -3861734697709150463L;

    private final TaskListener jenkinsLogger;

    private LogLevel level;

    public JenkinsCoverityLogger(final TaskListener jenkinsLogger, final LogLevel logLevel, final String pluginVersion) {
        this.jenkinsLogger = jenkinsLogger;
        this.level = logLevel;
        this.alwaysLog("Running Synopsys Coverity version: " + pluginVersion);
    }

    public static JenkinsCoverityLogger initializeLogger(final TaskListener jenkinsLogger, final IntEnvironmentVariables intEnvironmentVariables) {
        final String logLevelString = intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.LOG_LEVEL.toString());
        final LogLevel logLevel = LogLevel.fromString(logLevelString);
        final String pluginVersion = GlobalValueHelper.getPluginVersion();
        return new JenkinsCoverityLogger(jenkinsLogger, logLevel, pluginVersion);
    }

    public TaskListener getJenkinsListener() {
        return jenkinsLogger;
    }

    @Override
    public LogLevel getLogLevel() {
        return level;
    }

    @Override
    public void setLogLevel(final LogLevel level) {
        this.level = level;
    }

    /**
     * Prints the message regardless of the log level
     */
    @Override
    public void alwaysLog(final String txt) {
        printLog(txt, null);
    }

    @Override
    public void debug(final String txt) {
        if (level.isLoggable(LogLevel.DEBUG)) {
            printLog(txt, null);
        }
    }

    @Override
    public void debug(final String txt, final Throwable e) {
        if (level.isLoggable(LogLevel.DEBUG)) {
            printLog(txt, e);
        }
    }

    @Override
    public void error(final Throwable e) {
        if (level.isLoggable(LogLevel.ERROR)) {
            printLog(null, e);
        }
    }

    @Override
    public void error(final String txt) {
        if (level.isLoggable(LogLevel.ERROR)) {
            printLog(txt, null);
        }
    }

    @Override
    public void error(final String txt, final Throwable e) {
        if (level.isLoggable(LogLevel.ERROR)) {
            printLog(txt, e);
        }
    }

    @Override
    public void info(final String txt) {
        if (level.isLoggable(LogLevel.INFO)) {
            printLog(txt, null);
        }
    }

    @Override
    public void trace(final String txt) {
        if (level.isLoggable(LogLevel.TRACE)) {
            printLog(txt, null);
        }
    }

    @Override
    public void trace(final String txt, final Throwable e) {
        if (level.isLoggable(LogLevel.TRACE)) {
            printLog(txt, e);
        }
    }

    @Override
    public void warn(final String txt) {
        if (level.isLoggable(LogLevel.WARN)) {
            printLog(txt, null);
        }
    }

    private void printLog(final String txt, final Throwable e) {
        if (txt != null) {
            if (jenkinsLogger != null) {
                jenkinsLogger.getLogger().println(txt);
            } else {
                System.out.println(txt);
            }
        }
        if (e != null) {
            final StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            if (jenkinsLogger != null) {
                jenkinsLogger.getLogger().println(sw.toString());
            } else {
                System.out.println(sw.toString());
            }
        }
    }

}
