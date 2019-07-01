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
package com.synopsys.integration.jenkins.coverity.substeps.remote;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import com.synopsys.integration.coverity.exception.ExecutableException;
import com.synopsys.integration.coverity.exception.ExecutableRunnerException;
import com.synopsys.integration.coverity.executable.Executable;
import com.synopsys.integration.coverity.executable.ExecutableManager;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;

public class CoverityRemoteToolRunner extends CoverityRemoteCallable<Integer> {
    private static final long serialVersionUID = -1777043273065180425L;
    private final String coverityToolHome;
    private final List<String> arguments;
    private final HashMap<String, String> environmentVariables;

    private final String workspacePath;

    public CoverityRemoteToolRunner(final JenkinsCoverityLogger logger, final String coverityToolHome, final List<String> arguments, final String workspacePath, final HashMap<String, String> environmentVariables) {
        super(logger);
        this.environmentVariables = environmentVariables;
        this.coverityToolHome = coverityToolHome;
        this.arguments = arguments;
        this.workspacePath = workspacePath;
    }

    public Integer call() throws CoverityJenkinsException {
        final File workspace = new File(workspacePath);
        final Executable executable = new Executable(arguments, workspace, environmentVariables);
        final ExecutableManager executableManager = new ExecutableManager(new File(coverityToolHome));
        final Integer exitCode;
        final ByteArrayOutputStream errorOutputStream = new ByteArrayOutputStream();
        try {
            final PrintStream jenkinsPrintStream = logger.getJenkinsListener().getLogger();
            exitCode = executableManager.execute(executable, logger, jenkinsPrintStream, new PrintStream(errorOutputStream, true, "UTF-8"));
        } catch (final UnsupportedEncodingException | InterruptedException | ExecutableException | ExecutableRunnerException e) {
            throw new CoverityJenkinsException(e);
        } finally {
            logger.error(new String(errorOutputStream.toByteArray(), StandardCharsets.UTF_8));
        }
        return exitCode;
    }

}
