package com.synopsys.integration.jenkins.coverity.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.coverity.CoverityVersion;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.service.callable.CoverityRemoteCallable;
import com.synopsys.integration.jenkins.coverity.service.common.CoverityRemotingService;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;

import hudson.FilePath;

public class CoverityWorkspaceService {
    private final JenkinsIntLogger logger;
    private final CoverityRemotingService coverityRemotingService;
    private final CoverityConfigService coverityConfigService;

    public CoverityWorkspaceService(JenkinsIntLogger logger, CoverityRemotingService coverityRemotingService, CoverityConfigService coverityConfigService) {
        this.logger = logger;
        this.coverityRemotingService = coverityRemotingService;
        this.coverityConfigService = coverityConfigService;
    }

    public String getValidatedCoverityToolHomeBin(Boolean validateVersion, String coverityToolHome) throws IntegrationException, IOException, InterruptedException {
        ValidateCoverityInstallation validateCoverityInstallation = new ValidateCoverityInstallation(logger, validateVersion, coverityToolHome);

        return coverityRemotingService.call(validateCoverityInstallation);
    }

    public String createAuthenticationKeyFile(String coverityServerUrl, String credentialsId, String workingDirectory) throws IOException, InterruptedException {
        CoverityConnectInstance coverityConnectInstance = coverityConfigService.getCoverityInstanceOrAbort(coverityServerUrl);
        Optional<String> authKeyContents = coverityConnectInstance.getAuthenticationKeyFileContents(logger, credentialsId);

        FilePath workingFilePath = coverityRemotingService.getRemoteFilePath(workingDirectory);

        if (authKeyContents.isPresent()) {
            FilePath authKeyFile = workingFilePath.createTextTempFile("auth-key", ".txt", authKeyContents.get());
            authKeyFile.chmod(0600);
            return authKeyFile.getRemote();
        }
        return StringUtils.EMPTY;
    }

    public String getIntermediateDirectoryPath(String customWorkspacePath) {
        return coverityRemotingService.getRemoteFilePath(customWorkspacePath).child("idir").getRemote();
    }

    private static class ValidateCoverityInstallation extends CoverityRemoteCallable<String> {
        public static final CoverityVersion MINIMUM_SUPPORTED_VERSION = CoverityVersion.VERSION_PACIFIC;
        private static final long serialVersionUID = -460886461718309214L;
        private final String coverityToolHome;
        private final Boolean validateVersion;

        public ValidateCoverityInstallation(JenkinsIntLogger logger, Boolean validateVersion, String coverityToolHome) {
            super(logger);
            this.coverityToolHome = coverityToolHome;
            this.validateVersion = validateVersion;
        }

        @Override
        public String call() throws CoverityJenkinsException {
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

            return pathToBinDirectory.toString();
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

}
