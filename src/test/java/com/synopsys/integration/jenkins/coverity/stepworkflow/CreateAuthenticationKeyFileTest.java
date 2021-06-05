package com.synopsys.integration.jenkins.coverity.stepworkflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.util.StringUtils;
import org.mockito.Mockito;

import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.service.CoverityConfigService;
import com.synopsys.integration.jenkins.coverity.service.CoverityWorkspaceService;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.service.JenkinsRemotingService;

import hudson.FilePath;

public class CreateAuthenticationKeyFileTest {
    private static Stream<Arguments> getAuthenticationKeyFileContents() {
        return Stream.of(
            Arguments.of(Optional.empty()),
            Arguments.of(Optional.of("foo"))
        );
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @ParameterizedTest
    @MethodSource("getAuthenticationKeyFileContents")
    public void testCreateAuthenticationKeyFile(Optional<String> authenticationKeyFileContents) {
        String workspaceRemotePath = System.getProperty("java.io.tmpdir");

        assumeTrue(Files.isWritable(Paths.get(workspaceRemotePath)));

        String coverityServerUrl = "https://example.com/cim";
        String credentialsId = "SOME_CREDENTIALS_ID";

        try {
            CoverityConnectInstance mockedInstance = Mockito.mock(CoverityConnectInstance.class);
            Mockito.when(mockedInstance.getAuthenticationKeyFileContents(Mockito.any(), Mockito.any())).thenReturn(authenticationKeyFileContents);

            CoverityConfigService mockConfigService = Mockito.mock(CoverityConfigService.class);
            Mockito.when(mockConfigService.getCoverityInstanceOrAbort(Mockito.anyString())).thenReturn(mockedInstance);

            JenkinsRemotingService mockRemotingService = Mockito.mock(JenkinsRemotingService.class);
            Mockito.when(mockRemotingService.getRemoteFilePath(workspaceRemotePath)).thenReturn(new FilePath(new File(workspaceRemotePath)));

            CoverityWorkspaceService coverityWorkspaceService = new CoverityWorkspaceService(JenkinsIntLogger.logToStandardOut(), mockRemotingService, mockConfigService);
            String filePath = coverityWorkspaceService.createAuthenticationKeyFile(coverityServerUrl, credentialsId, workspaceRemotePath);

            File authKeyFile = new File(filePath);
            authKeyFile.deleteOnExit();

            if (authenticationKeyFileContents.isPresent()) {
                assertTrue(authKeyFile.exists());

                //Use the nio APIs here because of a bug on Windows -- rotte JUN 2020
                Path authKeyFilePath = authKeyFile.toPath();
                assertTrue(Files.isReadable(authKeyFilePath), "Authentication key file was not readable when it was expected to be");
                assertTrue(Files.isWritable(authKeyFilePath), "Authentication key file was not writeable when it was expected to be");
                assertFalse(Files.isExecutable(authKeyFilePath), "Authentication key file was executable when it was not expected to be");

                try {
                    String keyFileContents = new String(Files.readAllBytes(authKeyFilePath));
                    assertEquals(authenticationKeyFileContents.get(), keyFileContents);
                } catch (IOException e) {
                    fail("Cannot read authentication key file to verify contents", e);
                }
            } else {
                assertTrue(StringUtils.isBlank(filePath));
                assertFalse(authKeyFile.exists());
            }
        } catch (IOException | InterruptedException e) {
            fail("Unexpected exception occurred", e);
        }
    }
}
