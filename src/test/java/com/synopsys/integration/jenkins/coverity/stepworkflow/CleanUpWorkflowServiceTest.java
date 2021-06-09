package com.synopsys.integration.jenkins.coverity.stepworkflow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Mockito;

import com.synopsys.integration.jenkins.coverity.service.CleanUpWorkflowService;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.service.JenkinsRemotingService;

import hudson.FilePath;

public class CleanUpWorkflowServiceTest {
    public static Stream<Arguments> provideExceptions() {
        return Stream.of(
            Arguments.of(new IOException()),
            Arguments.of(new InterruptedException())
        );
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("provideExceptions")
    public void testCleanUpIntermediateDirectory(Exception e) throws IOException, InterruptedException {
        String intermediateDirectoryPath = "/some/file/path/idir/";

        JenkinsIntLogger mockedLogger = Mockito.mock(JenkinsIntLogger.class);
        FilePath mockedIntermediateDirectory = Mockito.mock(FilePath.class);
        if (e != null) {
            Mockito.doThrow(e).when(mockedIntermediateDirectory).deleteRecursive();
        }

        JenkinsRemotingService mockedRemotingService = Mockito.mock(JenkinsRemotingService.class);
        Mockito.when(mockedRemotingService.getRemoteFilePath(Mockito.any())).thenReturn(mockedIntermediateDirectory);

        CleanUpWorkflowService cleanUpWorkflowService = new CleanUpWorkflowService(mockedLogger, mockedRemotingService);
        cleanUpWorkflowService.cleanUpIntermediateDirectory(intermediateDirectoryPath);

        Mockito.verify(mockedIntermediateDirectory).deleteRecursive();
        if (e != null) {
            if (e instanceof InterruptedException) {
                assertTrue(Thread.currentThread().isInterrupted());
            }
            Mockito.verify(mockedLogger).warn(Mockito.anyString());
            Mockito.verify(mockedLogger).trace(Mockito.anyString(), Mockito.eq(e));
        }
    }

    @ParameterizedTest
    @NullSource
    @MethodSource("provideExceptions")
    public void testCleanUpAuthKeyFile(Exception e) throws IOException, InterruptedException {
        String authKeyFilePath = "/some/file/path/authkey";

        FilePath mockedAuthKeyFile = Mockito.mock(FilePath.class);
        if (e != null) {
            Mockito.when(mockedAuthKeyFile.delete()).thenThrow(e);
        } else {
            Mockito.when(mockedAuthKeyFile.delete()).thenReturn(true);
        }

        JenkinsIntLogger mockedLogger = Mockito.mock(JenkinsIntLogger.class);

        JenkinsRemotingService mockedRemotingService = Mockito.mock(JenkinsRemotingService.class);
        Mockito.when(mockedRemotingService.getRemoteFilePath(Mockito.any())).thenReturn(mockedAuthKeyFile);

        CleanUpWorkflowService cleanUpWorkflowService = new CleanUpWorkflowService(mockedLogger, mockedRemotingService);

        cleanUpWorkflowService.cleanUpAuthenticationFile(authKeyFilePath);

        Mockito.verify(mockedAuthKeyFile).delete();
        if (e != null) {
            if (e instanceof InterruptedException) {
                assertTrue(Thread.currentThread().isInterrupted());
            }
            Mockito.verify(mockedLogger).error(Mockito.anyString(), Mockito.eq(e));
        }
    }

}
