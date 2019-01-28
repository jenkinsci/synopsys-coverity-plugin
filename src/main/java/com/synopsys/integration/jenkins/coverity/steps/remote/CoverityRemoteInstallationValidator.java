/**
 * synopsys-coverity
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
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
package com.synopsys.integration.jenkins.coverity.steps.remote;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.coverity.CoverityVersion;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;

import hudson.EnvVars;

public class CoverityRemoteInstallationValidator extends CoverityRemoteCallable<String> {
    public static final CoverityVersion MINIMUM_SUPPORTED_VERSION = CoverityVersion.VERSION_JASPER;
    private static final long serialVersionUID = -460886461718309214L;
    private final EnvVars envVars;

    public CoverityRemoteInstallationValidator(final JenkinsCoverityLogger logger, final EnvVars envVars) {
        super(logger);
        this.envVars = envVars;
    }

    public String call() throws CoverityJenkinsException {
        final String coverityToolHome = envVars.get(JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME.toString());
        final Path pathToCoverityToolHome = Paths.get(coverityToolHome);

        if (!Files.exists(pathToCoverityToolHome)) {
            logger.error("The specified Analysis installation directory doesn't exist.");
            return StringUtils.EMPTY;
        }

        final Path pathToAnalysisVersionFile = pathToCoverityToolHome.resolve("VERSION");
        final Path pathToAnalysisVersionXml = pathToCoverityToolHome.resolve("VERSION.xml");
        if (!Files.exists(pathToAnalysisVersionXml) || !Files.exists(pathToAnalysisVersionFile)) {
            logger.error(String.format("%s and %s were not found.", pathToAnalysisVersionFile.toString(), pathToAnalysisVersionXml.toString()));
            return StringUtils.EMPTY;
        }

        // check the version file value and validate it is greater than minimum version
        final CoverityVersion coverityVersion = getVersion(pathToAnalysisVersionFile).orElse(null);
        if (coverityVersion == null) {
            logger.error("Could not determine the version of the Coverity analysis tool.");
            return StringUtils.EMPTY;
        }

        if (coverityVersion.compareTo(MINIMUM_SUPPORTED_VERSION) < 0) {
            logger.error(String.format("Analysis version %s detected. The minimum supported version is %s", coverityVersion.toString(), MINIMUM_SUPPORTED_VERSION.toString()));
            return StringUtils.EMPTY;
        }

        final Path pathToBinDirectory = pathToCoverityToolHome.resolve("bin");
        if (!Files.isDirectory(pathToBinDirectory)) {
            logger.error(String.format("%s was not found", pathToBinDirectory.toString()));
            return StringUtils.EMPTY;
        }

        return pathToBinDirectory.toString();
    }

    /*
     * Gets the {@link CoverityVersion} given a static analysis tools home directory by finding the VERSION file,
     * then reading the version number
     */
    private Optional<CoverityVersion> getVersion(final Path versionFile) throws CoverityJenkinsException {
        final String versionPrefix = "externalVersion=";
        try {
            return Files.lines(versionFile)
                       .filter(str -> str.startsWith(versionPrefix))
                       .map(str -> str.substring(versionPrefix.length()))
                       .map(CoverityVersion::parse)
                       .filter(Optional::isPresent)
                       .map(Optional::get)
                       .findFirst();
        } catch (final IOException e) {
            throw new CoverityJenkinsException("Could not validate the version of the COVERITY_TOOL_HOME", e);
        }
    }

}