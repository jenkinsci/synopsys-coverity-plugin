package com.synopsys.integration.jenkins.coverity;

import java.io.IOException;

import com.synopsys.integration.coverity.api.ws.configuration.CovRemoteServiceException_Exception;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.service.CoverityEnvironmentService;
import com.synopsys.integration.jenkins.coverity.service.CoverityPhoneHomeService;
import com.synopsys.integration.jenkins.coverity.service.CoverityWorkspaceService;
import com.synopsys.integration.jenkins.coverity.service.ProjectStreamCreationService;
import com.synopsys.integration.jenkins.coverity.service.common.JenkinsWrapperContextService;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.util.IntEnvironmentVariables;

public class CoverityWrapperCommands {
    private final JenkinsIntLogger logger;
    private final CoverityPhoneHomeService coverityPhoneHomeService;
    private final CoverityWorkspaceService coverityWorkspaceService;
    private final CoverityEnvironmentService coverityEnvironmentService;
    private final JenkinsWrapperContextService jenkinsWrapperContextService;
    private final ProjectStreamCreationService projectStreamCreationService;

    public CoverityWrapperCommands(JenkinsIntLogger logger, CoverityPhoneHomeService coverityPhoneHomeService, CoverityWorkspaceService coverityWorkspaceService, CoverityEnvironmentService coverityEnvironmentService,
        JenkinsWrapperContextService jenkinsWrapperContextService, ProjectStreamCreationService projectStreamCreationService){
        this.logger = logger;
        this.coverityPhoneHomeService = coverityPhoneHomeService;
        this.coverityWorkspaceService = coverityWorkspaceService;
        this.coverityEnvironmentService = coverityEnvironmentService;
        this.jenkinsWrapperContextService = jenkinsWrapperContextService;
        this.projectStreamCreationService = projectStreamCreationService;
    }

    public void injectCoverityEnvironment(
        String coverityInstanceUrl,
        String credentialsId,
        String projectName,
        String streamName,
        String viewName,
        ConfigureChangeSetPatterns configureChangeSetPatterns,
        Boolean createMissingProjectsAndStreams
    ) throws IOException, InterruptedException {
        try {
            coverityPhoneHomeService.phoneHome(coverityInstanceUrl, credentialsId);

            String coverityToolHomeBin = coverityWorkspaceService.getValidatedCoverityToolHomeBin(false, coverityEnvironmentService.getCoverityToolHome());
            String authKeyFilePath = coverityWorkspaceService.createAuthenticationKeyFile(coverityInstanceUrl, credentialsId);
            String intermediateDirectoryPath = coverityWorkspaceService.getIntermediateDirectoryPath();

            IntEnvironmentVariables coverityEnvironment = coverityEnvironmentService.createCoverityEnvironment(
                configureChangeSetPatterns,
                coverityInstanceUrl,
                credentialsId,
                projectName,
                streamName,
                viewName,
                intermediateDirectoryPath,
                coverityToolHomeBin,
                authKeyFilePath
            );

            jenkinsWrapperContextService.populateEnvironment(coverityEnvironment);
            if (Boolean.TRUE.equals(createMissingProjectsAndStreams)) {
                projectStreamCreationService.createMissingProjectOrStream(coverityInstanceUrl, credentialsId, projectName, streamName);
            }

            logger.info("Coverity environment injected successfully.");
        } catch (CovRemoteServiceException_Exception | IntegrationException e) {
            throw new IOException(e);
        }
    }


}
