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
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.coverity.CoverityVersion;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.stepworkflow.jenkins.RemoteSubStepResponse;

public class ValidateCoverityInstallation extends CoverityRemoteCallable<RemoteSubStepResponse<Serializable>> {
    public static final CoverityVersion MINIMUM_SUPPORTED_VERSION = CoverityVersion.VERSION_PACIFIC;
    private static final long serialVersionUID = -460886461718309214L;
    private final String coverityToolHome;
    private final Boolean validateVersion;

    public ValidateCoverityInstallation(final CoverityJenkinsIntLogger logger, final Boolean validateVersion, final String coverityToolHome) {
        super(logger);
        this.coverityToolHome = coverityToolHome;
        this.validateVersion = validateVersion;
    }

    public RemoteSubStepResponse<Serializable> call() throws CoverityJenkinsException {
        if (StringUtils.isBlank(coverityToolHome)) {
            throw new CoverityJenkinsException(String.format("Cannot find Coverity installation, %s is not set.", JenkinsCoverityEnvironmentVariable.COVERITY_TOOL_HOME.toString()));
        }

        final Path pathToCoverityToolHome = Paths.get(coverityToolHome);

        if (!Files.exists(pathToCoverityToolHome)) {
            throw new CoverityJenkinsException("The specified Analysis installation directory doesn't exist.");
        }

        if (Boolean.TRUE.equals(validateVersion)) {
            final Path pathToAnalysisVersionFile = pathToCoverityToolHome.resolve("VERSION");
            final Path pathToAnalysisVersionXml = pathToCoverityToolHome.resolve("VERSION.xml");
            if (!Files.exists(pathToAnalysisVersionXml) || !Files.exists(pathToAnalysisVersionFile)) {
                throw new CoverityJenkinsException(String.format("%s and %s were not found.", pathToAnalysisVersionFile.toString(), pathToAnalysisVersionXml.toString()));
            }

            // check the version file value and validate it is greater than minimum version
            final CoverityVersion coverityVersion = getVersion(pathToAnalysisVersionFile).orElse(null);
            if (coverityVersion == null) {
                throw new CoverityJenkinsException("Could not determine the version of the Coverity analysis tool.");
            }

            if (coverityVersion.compareTo(MINIMUM_SUPPORTED_VERSION) < 0) {
                throw new CoverityJenkinsException(String.format("Analysis version %s detected. The minimum supported version is %s", coverityVersion.toString(), MINIMUM_SUPPORTED_VERSION.toString()));
            }
        }

        final Path pathToBinDirectory = pathToCoverityToolHome.resolve("bin");
        if (!Files.isDirectory(pathToBinDirectory)) {
            throw new CoverityJenkinsException(String.format("%s was not found", pathToBinDirectory.toString()));
        }

        return RemoteSubStepResponse.SUCCESS();
    }

    /*
     * Gets the {@link CoverityVersion} given a static analysis tools home directory by finding the VERSION file,
     * then reading the version number
     */
    private Optional<CoverityVersion> getVersion(final Path versionFile) throws CoverityJenkinsException {
        final String versionPrefix = "externalVersion=";
        try (final Stream<String> lines = Files.lines(versionFile)) {
            return lines.filter(str -> str.startsWith(versionPrefix))
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