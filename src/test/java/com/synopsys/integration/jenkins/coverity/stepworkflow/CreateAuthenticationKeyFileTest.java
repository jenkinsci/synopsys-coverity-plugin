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

import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.stepworkflow.SubStep;
import com.synopsys.integration.stepworkflow.SubStepResponse;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.LocalChannel;

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
    public void testCreateAuthenticationKeyFile(Optional<String> authenticationKeyFileContents) throws CoverityJenkinsAbortException {
        String workspaceRemotePath = System.getProperty("java.io.tmpdir");

        assumeTrue(Files.isWritable(Paths.get(workspaceRemotePath)));

        String coverityServerUrl = "https://example.com/cim";
        String credentialsId = "SOME_CREDENTIALS_ID";

        Launcher mockedLauncher = Mockito.mock(Launcher.class);
        Mockito.when(mockedLauncher.getChannel()).thenReturn(Mockito.mock(LocalChannel.class));

        TaskListener mockedListener = Mockito.mock(TaskListener.class);
        Mockito.when(mockedListener.getLogger()).thenReturn(System.out);

        CoverityConnectInstance mockedInstance = Mockito.mock(CoverityConnectInstance.class);
        Mockito.when(mockedInstance.getAuthenticationKeyFileContents(Mockito.any(), Mockito.any())).thenReturn(authenticationKeyFileContents);

        CoverityWorkflowStepFactory realFactory = new CoverityWorkflowStepFactory(Mockito.mock(EnvVars.class), Mockito.mock(Node.class), mockedLauncher, mockedListener);
        // Because we do not want to do GlobalConfiguration.all(), we have to use Mockito::spy-- maybe this is a good reason to refactor? -- rotte JUN 2020
        CoverityWorkflowStepFactory spiedFactory = Mockito.spy(realFactory);
        Mockito.doReturn(mockedInstance).when(spiedFactory).getCoverityConnectInstanceFromUrl(coverityServerUrl);

        SubStep<Object, String> createAuthenticationKeyFile = spiedFactory.createStepCreateAuthenticationKeyFile(workspaceRemotePath, coverityServerUrl, credentialsId);
        SubStepResponse<String> subStepResponse = createAuthenticationKeyFile.run(SubStepResponse.SUCCESS());

        assertTrue(subStepResponse.isSuccess());
        assertTrue(subStepResponse.hasData());
        String filePath = subStepResponse.getData();
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
    }
}
