/**
 * synopsys-coverity
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
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
package com.synopsys.integration.coverity.remote;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.remoting.Role;
import org.jenkinsci.remoting.RoleChecker;

import com.synopsys.integration.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.coverity.executable.Executable;
import com.synopsys.integration.coverity.executable.ExecutableManager;
import com.synopsys.integration.exception.IntegrationException;

import hudson.EnvVars;
import hudson.remoting.Callable;

public class CoverityRemoteRunner implements Callable<CoverityRemoteResponse, IntegrationException> {
    private final JenkinsCoverityLogger logger;

    private final String coverityUsername;
    private final String coverityPassword;

    private final String coverityStaticAnalysisDirectory;
    private final List<String> arguments;

    private final String workspacePath;

    private final EnvVars envVars;

    public CoverityRemoteRunner(final JenkinsCoverityLogger logger, final String coverityUsername, final String coverityPassword,
            final String coverityStaticAnalysisDirectory, final List<String> arguments, final String workspacePath, final EnvVars envVars) {
        this.logger = logger;

        this.coverityUsername = coverityUsername;
        this.coverityPassword = coverityPassword;
        this.coverityStaticAnalysisDirectory = coverityStaticAnalysisDirectory;
        this.arguments = arguments;
        this.workspacePath = workspacePath;
        this.envVars = envVars;
    }

    @Override
    public CoverityRemoteResponse call() throws IntegrationException {
        final File workspace = new File(workspacePath);
        final Map<String, String> environment = new HashMap<>();
        environment.putAll(envVars);
        if (null != coverityUsername) {
            setEnvironmentVariableString(environment, Executable.COVERITY_USER_ENVIRONMENT_VARIABLE, coverityUsername);
        }
        if (null != coverityPassword) {
            setEnvironmentVariableString(environment, Executable.COVERITY_PASSWORD_ENVIRONMENT_VARIABLE, coverityPassword);
        }
        final Executable executable = new Executable(arguments, workspace, environment);
        final ExecutableManager executableManager = new ExecutableManager(new File(coverityStaticAnalysisDirectory));
        int exitCode = -1;
        final ByteArrayOutputStream errorOutputStream = new ByteArrayOutputStream();
        try {
            final PrintStream jenkinsPrintStream = logger.getJenkinsListener().getLogger();
            exitCode = executableManager.execute(executable, logger, jenkinsPrintStream, new PrintStream(errorOutputStream, true, "UTF-8"));
        } catch (final InterruptedException e) {
            logger.error("Coverity remote thread was interrupted.", e);
            return new CoverityRemoteResponse(e);
        } catch (final IntegrationException e) {
            return new CoverityRemoteResponse(e);
        } catch (final UnsupportedEncodingException e) {
            return new CoverityRemoteResponse(e);
        } finally {
            logger.error(new String(errorOutputStream.toByteArray(), StandardCharsets.UTF_8));
        }
        return new CoverityRemoteResponse(exitCode);
    }

    private void setEnvironmentVariableString(final Map<String, String> environment, final String key, final String value) {
        if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
            environment.put(key, value);
        }
    }

    @Override
    public void checkRoles(final RoleChecker checker) throws SecurityException {
        checker.check(this, new Role(CoverityRemoteRunner.class));
    }
}
