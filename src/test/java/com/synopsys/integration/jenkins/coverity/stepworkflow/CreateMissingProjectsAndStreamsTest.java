package com.synopsys.integration.jenkins.coverity.stepworkflow;

import static org.junit.jupiter.api.Assertions.fail;

import java.net.MalformedURLException;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.AdditionalMatchers;
import org.mockito.Mockito;

import com.synopsys.integration.coverity.api.ws.configuration.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.StreamDataObj;
import com.synopsys.integration.coverity.ws.ConfigurationServiceWrapper;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.service.CoverityConfigService;
import com.synopsys.integration.jenkins.coverity.service.ProjectStreamCreationService;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;

public class CreateMissingProjectsAndStreamsTest {
    private static final String EXISTING_PROJECT = "existingProject";
    private static final String NEW_PROJECT = "newProject";
    private static final String FAILED_PROJECT = "cannotCreateThisProject";
    private static final String EXISTING_STREAM = "existingStream";
    private static final String NEW_STREAM = "newStream";
    private static final String FAILED_STREAM = "cannotCreateThisStream";
    private static ConfigurationServiceWrapper mockConfigurationServiceWrapper;

    @BeforeAll
    public static void setUpMockConfigurationServiceWrapper() throws CovRemoteServiceException_Exception, InterruptedException {
        mockConfigurationServiceWrapper = Mockito.mock(ConfigurationServiceWrapper.class);

        ProjectDataObj mockedProject = Mockito.mock(ProjectDataObj.class);
        Mockito.when(mockConfigurationServiceWrapper.getProjectByExactName(EXISTING_PROJECT))
            .thenReturn(Optional.of(mockedProject));
        Mockito.when(mockConfigurationServiceWrapper.getProjectByExactName(NEW_PROJECT))
            .thenReturn(Optional.empty());
        Mockito.when(mockConfigurationServiceWrapper.getAndWaitForProjectWithExactName(NEW_PROJECT))
            .thenReturn(Optional.of(mockedProject));
        Mockito.when(mockConfigurationServiceWrapper.getProjectByExactName(FAILED_PROJECT))
            .thenReturn(Optional.empty());
        Mockito.when(mockConfigurationServiceWrapper.getAndWaitForProjectWithExactName(FAILED_PROJECT))
            .thenReturn(Optional.empty());

        StreamDataObj mockedStream = Mockito.mock(StreamDataObj.class);
        Mockito.when(mockConfigurationServiceWrapper.getStreamByExactName(EXISTING_STREAM))
            .thenReturn(Optional.of(mockedStream));
        Mockito.when(mockConfigurationServiceWrapper.getStreamByExactName(NEW_STREAM))
            .thenReturn(Optional.empty());
        Mockito.when(mockConfigurationServiceWrapper.getAndWaitForStreamWithExactName(NEW_STREAM))
            .thenReturn(Optional.of(mockedStream));
        Mockito.when(mockConfigurationServiceWrapper.getStreamByExactName(FAILED_STREAM))
            .thenReturn(Optional.empty());
        Mockito.when(mockConfigurationServiceWrapper.getAndWaitForStreamWithExactName(FAILED_STREAM))
            .thenReturn(Optional.empty());
    }

    private static Stream<Arguments> getTestProjectAndStreamNames() {
        return Stream.of(
            Arguments.of(EXISTING_PROJECT, EXISTING_STREAM),
            Arguments.of(EXISTING_PROJECT, NEW_STREAM),
            Arguments.of(EXISTING_PROJECT, FAILED_STREAM),
            Arguments.of(NEW_PROJECT, EXISTING_STREAM),
            Arguments.of(NEW_PROJECT, NEW_STREAM),
            Arguments.of(NEW_PROJECT, FAILED_STREAM),
            Arguments.of(FAILED_PROJECT, EXISTING_STREAM),
            Arguments.of(FAILED_PROJECT, NEW_STREAM),
            Arguments.of(FAILED_PROJECT, FAILED_STREAM)
        );
    }

    @ParameterizedTest
    @MethodSource("getTestProjectAndStreamNames")
    public void testCreateMissingProjectsAndStreams(String projectName, String streamName) throws CoverityJenkinsAbortException, MalformedURLException {
        String coverityServerUrl = "www.example.com/coverity";
        String credentialsId = "SOME-CREDENTIALS-ID";

        JenkinsIntLogger mockedLogger = Mockito.mock(JenkinsIntLogger.class);

        WebServiceFactory mockedWebServiceFactory = Mockito.mock(WebServiceFactory.class);
        Mockito.when(mockedWebServiceFactory.createConfigurationServiceWrapper()).thenReturn(mockConfigurationServiceWrapper);
        CoverityConfigService mockedCoverityConfigService = Mockito.mock(CoverityConfigService.class);
        Mockito.when(mockedCoverityConfigService.getWebServiceFactoryFromUrl(coverityServerUrl, credentialsId)).thenReturn(mockedWebServiceFactory);

        ProjectStreamCreationService projectStreamCreationService = new ProjectStreamCreationService(mockedLogger, mockedCoverityConfigService);

        try {
            projectStreamCreationService.createMissingProjectOrStream(coverityServerUrl, credentialsId, projectName, streamName);
        } catch (Exception e) {
            fail("Unexpected exception was thrown-- this test should be mocked such that it throws no exceptions. Please fix the test code.", e);
        }

        // The plugin only logs based on whether or not it was able to create given projects or streams, so we have to verify the mocked logger's behavior. --rotte JUN 2020
        if (projectName.equals(EXISTING_PROJECT)) {
            Mockito.verify(mockedLogger, Mockito.never()).info(AdditionalMatchers.and(Mockito.contains("No project"), Mockito.contains(projectName)));
            Mockito.verify(mockedLogger, Mockito.never()).info(AdditionalMatchers.and(Mockito.contains("Successfully created"), Mockito.contains(projectName)));
            Mockito.verify(mockedLogger, Mockito.never()).error(AdditionalMatchers.and(Mockito.contains("Could not create"), Mockito.contains(projectName)));
        } else if (projectName.equals(NEW_PROJECT)) {
            Mockito.verify(mockedLogger).info(AdditionalMatchers.and(Mockito.contains("No project"), Mockito.contains(projectName)));
            Mockito.verify(mockedLogger).info(AdditionalMatchers.and(Mockito.contains("Successfully created"), Mockito.contains(projectName)));
        } else if (projectName.equals(FAILED_PROJECT)) {
            Mockito.verify(mockedLogger).info(AdditionalMatchers.and(Mockito.contains("No project"), Mockito.contains(projectName)));
            Mockito.verify(mockedLogger).error(AdditionalMatchers.and(Mockito.contains("Could not create"), Mockito.contains(projectName)));
        }

        if (projectName.equals(FAILED_PROJECT) || streamName.equals(EXISTING_STREAM)) {
            Mockito.verify(mockedLogger, Mockito.never()).info(AdditionalMatchers.and(Mockito.contains("No stream"), Mockito.contains(streamName)));
            Mockito.verify(mockedLogger, Mockito.never()).info(AdditionalMatchers.and(Mockito.contains("Successfully created"), Mockito.contains(streamName)));
            Mockito.verify(mockedLogger, Mockito.never()).error(AdditionalMatchers.and(Mockito.contains("Could not create"), Mockito.contains(streamName)));
        } else if (streamName.equals(NEW_STREAM)) {
            Mockito.verify(mockedLogger).info(AdditionalMatchers.and(Mockito.contains("No stream"), Mockito.contains(streamName)));
            Mockito.verify(mockedLogger).info(AdditionalMatchers.and(Mockito.contains("Successfully created"), Mockito.contains(streamName)));
        } else if (streamName.equals(FAILED_STREAM)) {
            Mockito.verify(mockedLogger).info(AdditionalMatchers.and(Mockito.contains("No stream"), Mockito.contains(streamName)));
            Mockito.verify(mockedLogger).error(AdditionalMatchers.and(Mockito.contains("Could not create"), Mockito.contains(streamName)));
        }
    }
}
