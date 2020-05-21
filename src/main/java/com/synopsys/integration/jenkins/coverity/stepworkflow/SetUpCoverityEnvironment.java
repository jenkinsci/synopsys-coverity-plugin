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

import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import com.synopsys.integration.coverity.executable.CoverityToolEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.stepworkflow.AbstractConsumingSubStep;
import com.synopsys.integration.stepworkflow.SubStepResponse;
import com.synopsys.integration.util.IntEnvironmentVariables;

public class SetUpCoverityEnvironment extends AbstractConsumingSubStep<List<String>> {
    private final IntLogger logger;
    private final IntEnvironmentVariables intEnvironmentVariables;
    private final String coverityInstanceUrl;
    private final String coverityUsername;
    private final String coverityPassword;
    private final String projectName;
    private final String streamName;
    private final String viewName;
    private final String intermediateDirectoryPath;

    public SetUpCoverityEnvironment(final IntLogger logger, final IntEnvironmentVariables intEnvironmentVariables, final String coverityInstanceUrl, final String coverityUsername, final String coverityPassword, final String projectName,
        final String streamName, final String viewName, final String intermediateDirectoryPath) {
        this.logger = logger;
        this.intEnvironmentVariables = intEnvironmentVariables;
        this.coverityInstanceUrl = coverityInstanceUrl;
        this.coverityUsername = coverityUsername;
        this.coverityPassword = coverityPassword;
        this.projectName = projectName;
        this.streamName = streamName;
        this.viewName = viewName;
        this.intermediateDirectoryPath = intermediateDirectoryPath;
    }

    @Override
    public SubStepResponse<Object> run(final List<String> changeSet) {
        final String coverityToolHomeBin;
        try {
            coverityToolHomeBin = Paths.get(intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME.toString())).resolve("bin").toString();
        } catch (final InvalidPathException e) {
            return SubStepResponse.FAILURE(new CoverityJenkinsException("Could not get path to Coverity tool home or the path provided is invalid.", e));
        }

        intEnvironmentVariables.put("PATH+COVERITYTOOLBIN", coverityToolHomeBin);
        intEnvironmentVariables.put(CoverityToolEnvironmentVariable.USER.toString(), coverityUsername);
        intEnvironmentVariables.put(CoverityToolEnvironmentVariable.PASSPHRASE.toString(), coverityPassword);
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_URL.toString(), coverityInstanceUrl);
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_PROJECT.toString(), projectName);
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_STREAM.toString(), streamName);
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_VIEW.toString(), viewName);
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.CHANGE_SET.toString(), String.join(" ", changeSet));
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE.toString(), String.valueOf(changeSet.size()));
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_INTERMEDIATE_DIRECTORY.toString(), intermediateDirectoryPath);

        logger.alwaysLog("Synopsys Coverity environment:");
        logger.alwaysLog("-- Synopsys Coverity static analysis tool home: " + coverityToolHomeBin);
        logger.alwaysLog("-- Synopsys Coverity username: " + coverityUsername);
        Arrays.stream(JenkinsCoverityEnvironmentVariable.values())
            .map(JenkinsCoverityEnvironmentVariable::toString)
            .map(environmentVariable -> String.format("-- $%s=%s", environmentVariable, intEnvironmentVariables.getValue(environmentVariable)))
            .forEach(logger::alwaysLog);

        return SubStepResponse.SUCCESS();
    }

}
