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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import com.synopsys.integration.coverity.CoverityVersion;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;

public class ValidateCoverityInstallation extends CoverityRemoteCallable<Boolean> {
    public static final CoverityVersion MINIMUM_SUPPORTED_VERSION = CoverityVersion.VERSION_PACIFIC;
    private static final long serialVersionUID = -460886461718309214L;
    private final String coverityToolHome;
    private final Boolean validateVersion;

    public ValidateCoverityInstallation(CoverityJenkinsIntLogger logger, Boolean validateVersion, String coverityToolHome) {
        super(logger);
        this.coverityToolHome = coverityToolHome;
        this.validateVersion = validateVersion;
    }

    public Boolean call() throws CoverityJenkinsException {
        // Previously we would validate the location of coverityToolHome here, but that's too late for CoverityWorkflowStepFactory-- so now we validate it before this class is constructed.
        // If/when we switch to the services pattern that Detect and Polaris Jenkins use, that logic should likely return here.
        // --rotte OCT 2020

        Path pathToCoverityToolHome = Paths.get(coverityToolHome);

        if (!Files.exists(pathToCoverityToolHome)) {
            throw new CoverityJenkinsException("The specified Analysis installation directory doesn't exist.");
        }

        if (Boolean.TRUE.equals(validateVersion)) {
            Path pathToAnalysisVersionFile = pathToCoverityToolHome.resolve("VERSION");
            Path pathToAnalysisVersionXml = pathToCoverityToolHome.resolve("VERSION.xml");
            if (Files.notExists(pathToAnalysisVersionXml) || Files.notExists(pathToAnalysisVersionFile)) {
                throw new CoverityJenkinsException(String.format("%s and %s were not found.", pathToAnalysisVersionFile.toString(), pathToAnalysisVersionXml.toString()));
            }

            // check the version file value and validate it is greater than minimum version
            CoverityVersion coverityVersion = getVersion(pathToAnalysisVersionFile).orElse(null);
            if (coverityVersion == null) {
                throw new CoverityJenkinsException("Could not determine the version of the Coverity analysis tool.");
            }

            if (coverityVersion.compareTo(MINIMUM_SUPPORTED_VERSION) < 0) {
                throw new CoverityJenkinsException(String.format("Analysis version %s detected. The minimum supported version is %s", coverityVersion.toString(), MINIMUM_SUPPORTED_VERSION.toString()));
            }
        }

        Path pathToBinDirectory = pathToCoverityToolHome.resolve("bin");
        if (!Files.isDirectory(pathToBinDirectory)) {
            throw new CoverityJenkinsException(String.format("%s was not found", pathToBinDirectory.toString()));
        }

        return true;
    }

    /*
     * Gets the {@link CoverityVersion} given a static analysis tools home directory by finding the VERSION file,
     * then reading the version number
     */
    private Optional<CoverityVersion> getVersion(Path versionFile) throws CoverityJenkinsException {
        final String versionPrefix = "externalVersion=";
        try (Stream<String> lines = Files.lines(versionFile)) {
            return lines.filter(str -> str.startsWith(versionPrefix))
                       .map(str -> str.substring(versionPrefix.length()))
                       .map(CoverityVersion::parse)
                       .filter(Optional::isPresent)
                       .map(Optional::get)
                       .findFirst();
        } catch (IOException e) {
            throw new CoverityJenkinsException("Could not validate the version of the COVERITY_TOOL_HOME", e);
        }
    }

}