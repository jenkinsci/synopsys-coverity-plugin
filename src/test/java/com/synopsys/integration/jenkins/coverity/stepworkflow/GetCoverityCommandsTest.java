package com.synopsys.integration.jenkins.coverity.stepworkflow;

import static com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType.COV_ANALYZE;
import static com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType.COV_RUN_DESKTOP;
import static com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType.THRESHOLD;
import static com.synopsys.integration.jenkins.coverity.extensions.CoverityCaptureType.COV_BUILD;
import static com.synopsys.integration.jenkins.coverity.extensions.CoverityCaptureType.COV_CAPTURE_PROJECT;
import static com.synopsys.integration.jenkins.coverity.extensions.CoverityCaptureType.COV_CAPTURE_SCM;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityCaptureType;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.AdvancedCoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CommandArguments;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.RepeatableCommand;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.SimpleCoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.service.CoverityCommandService;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.util.IntEnvironmentVariables;

public class GetCoverityCommandsTest {
    public static final String SOURCE_ARGUMENT = "$SOURCE_ARGUMENT";
    public static final String COV_BUILD_ARGUMENTS = "$ADDITIONAL_COV_BUILD_ARGUMENTS";
    public static final String COV_ANALYZE_ARGUMENTS = "$ADDITIONAL_COV_ANALYZE_ARGUMENTS";
    public static final String COV_RUN_DESKTOP_ARGUMENTS = "$ADDITIONAL_COV_RUN_DESKTOP_ARGUMENTS";
    public static final String COV_COMMIT_DEFECTS_ARGUMENTS = "$ADDITIONAL_COV_COMMIT_DEFECTS_ARGUMENTS";
    public static final String COV_CAPTURE_ARGUMENTS = "$ADDITIONAL_COV_CAPTURE_ARGUMENTS";
    public static final String AUTH_KEY_FILE_PATH = "$AUTH_KEY_FILE_PATH";
    public static int ANALYSIS_THRESHOLD = 5;

    private static Stream<Arguments> testGetSimpleModeCommandsArguments() {
        RepeatableCommand covBuild = RepeatableCommand.COV_BUILD(SOURCE_ARGUMENT, COV_BUILD_ARGUMENTS);
        RepeatableCommand covRunDesktop = RepeatableCommand.COV_RUN_DESKTOP(AUTH_KEY_FILE_PATH, COV_RUN_DESKTOP_ARGUMENTS);
        RepeatableCommand covAnalyze = RepeatableCommand.COV_ANALYZE(COV_ANALYZE_ARGUMENTS);
        RepeatableCommand covCommitDefects = RepeatableCommand.COV_COMMIT_DEFECTS(AUTH_KEY_FILE_PATH, COV_COMMIT_DEFECTS_ARGUMENTS);
        RepeatableCommand covCaptureProject = RepeatableCommand.COV_CAPTURE_PROJECT(SOURCE_ARGUMENT, COV_CAPTURE_ARGUMENTS);
        RepeatableCommand covCaptureScm = RepeatableCommand.COV_CAPTURE_SCM(SOURCE_ARGUMENT, COV_CAPTURE_ARGUMENTS);
        int aboveThreshold = ANALYSIS_THRESHOLD + 1;
        int belowThreshold = ANALYSIS_THRESHOLD - 1;

        return Stream.of(
            Arguments.of(COV_BUILD, COV_ANALYZE, ANALYSIS_THRESHOLD, new RepeatableCommand[] { covBuild, covAnalyze, covCommitDefects }),
            Arguments.of(COV_CAPTURE_PROJECT, COV_ANALYZE, ANALYSIS_THRESHOLD, new RepeatableCommand[] { covCaptureProject, covAnalyze, covCommitDefects }),
            Arguments.of(COV_CAPTURE_SCM, COV_ANALYZE, ANALYSIS_THRESHOLD, new RepeatableCommand[] { covCaptureScm, covAnalyze, covCommitDefects }),
            Arguments.of(COV_BUILD, COV_RUN_DESKTOP, ANALYSIS_THRESHOLD, new RepeatableCommand[] { covBuild, covRunDesktop, covCommitDefects }),
            Arguments.of(COV_BUILD, THRESHOLD, ANALYSIS_THRESHOLD, new RepeatableCommand[] { covBuild, covAnalyze, covCommitDefects }),
            Arguments.of(COV_BUILD, THRESHOLD, belowThreshold, new RepeatableCommand[] { covBuild, covRunDesktop, covCommitDefects }),
            Arguments.of(COV_BUILD, THRESHOLD, aboveThreshold, new RepeatableCommand[] { covBuild, covAnalyze, covCommitDefects }),
            Arguments.of(null, COV_ANALYZE, ANALYSIS_THRESHOLD, new RepeatableCommand[] { covBuild, covAnalyze, covCommitDefects }),
            Arguments.of(COV_BUILD, null, ANALYSIS_THRESHOLD, new RepeatableCommand[0])
        );
    }

    @ParameterizedTest
    @MethodSource("testGetSimpleModeCommandsArguments")
    public void testGetSimpleModeCommands(CoverityCaptureType coverityCaptureType, CoverityAnalysisType coverityAnalysisType, int changeSetSize, RepeatableCommand[] expectedCommands) {
        CommandArguments commandArguments = new CommandArguments(COV_BUILD_ARGUMENTS, COV_ANALYZE_ARGUMENTS, COV_RUN_DESKTOP_ARGUMENTS, COV_COMMIT_DEFECTS_ARGUMENTS, COV_CAPTURE_ARGUMENTS);
        SimpleCoverityRunConfiguration coverityRunConfiguration = new SimpleCoverityRunConfiguration(coverityAnalysisType, SOURCE_ARGUMENT, commandArguments);
        coverityRunConfiguration.setCoverityCaptureType(coverityCaptureType);
        coverityRunConfiguration.setChangeSetAnalysisThreshold(ANALYSIS_THRESHOLD);

        CoverityCommandService coverityCommandService = new CoverityCommandService(JenkinsIntLogger.logToStandardOut(),null);
        try {
            RepeatableCommand[] actualCommands = coverityCommandService.getSimpleModeCommands(coverityRunConfiguration, changeSetSize, AUTH_KEY_FILE_PATH);

            assertEquals(expectedCommands.length, actualCommands.length, "Command array was not the expected size: ");
            for (int i = 0; i < expectedCommands.length; i++) {
                assertEquals(expectedCommands[i].getCommand(), actualCommands[i].getCommand());
            }
        } catch (CoverityJenkinsException e) {
            assertNull(coverityAnalysisType, "CoverityJenkinsException should only be thrown when coverityAnalysisType is null.");
        }
    }

    @Test
    public void testGetCoverityCommandsFromSimpleConfig() {
        IntEnvironmentVariables intEnvironmentVariables = IntEnvironmentVariables.empty();
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE.toString(), String.valueOf(ANALYSIS_THRESHOLD));
        String coverityIntermediateDirectory = "/some/path";
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_INTERMEDIATE_DIRECTORY.toString(), coverityIntermediateDirectory);

        CommandArguments commandArguments = new CommandArguments(COV_BUILD_ARGUMENTS, COV_ANALYZE_ARGUMENTS, COV_RUN_DESKTOP_ARGUMENTS, COV_COMMIT_DEFECTS_ARGUMENTS, COV_CAPTURE_ARGUMENTS);
        SimpleCoverityRunConfiguration coverityRunConfiguration = new SimpleCoverityRunConfiguration(COV_ANALYZE, SOURCE_ARGUMENT, commandArguments);
        coverityRunConfiguration.setCoverityCaptureType(COV_BUILD);
        coverityRunConfiguration.setChangeSetAnalysisThreshold(ANALYSIS_THRESHOLD);

        List<String> expectedCovBuild = Arrays.asList("cov-build", "--dir", "/some/path", "$ADDITIONAL_COV_BUILD_ARGUMENTS", "$SOURCE_ARGUMENT");
        List<String> expectedCovAnalyze = Arrays.asList("cov-analyze", "--dir", "/some/path", "$ADDITIONAL_COV_ANALYZE_ARGUMENTS");
        List<String> expectedCovCommitDefects = Arrays.asList("cov-commit-defects", "--dir", "/some/path", "--url", "${COV_URL}", "--stream", "${COV_STREAM}", "$ADDITIONAL_COV_COMMIT_DEFECTS_ARGUMENTS");

        CoverityCommandService coverityCommandService = new CoverityCommandService(JenkinsIntLogger.logToStandardOut(),null);
        try {
            List<List<String>> commandList = coverityCommandService.getCommands(intEnvironmentVariables, coverityRunConfiguration);

            assertEquals(expectedCovBuild, commandList.get(0));
            assertEquals(expectedCovAnalyze, commandList.get(1));
            assertEquals(expectedCovCommitDefects, commandList.get(2));
        } catch (CoverityJenkinsException e) {
            fail("Unexpected exception occurred getting the coverity commands, you may need to fix the test code.", e);
        }
    }

    @Test
    public void testGetCoverityCommandsFromAdvancedConfig() {
        IntEnvironmentVariables intEnvironmentVariables = IntEnvironmentVariables.empty();
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE.toString(), String.valueOf(ANALYSIS_THRESHOLD));
        String coverityIntermediateDirectory = "/some/path";
        intEnvironmentVariables.put(JenkinsCoverityEnvironmentVariable.COVERITY_INTERMEDIATE_DIRECTORY.toString(), coverityIntermediateDirectory);

        RepeatableCommand covBuild = RepeatableCommand.COV_BUILD(EMPTY, EMPTY);
        RepeatableCommand covAnalyze = RepeatableCommand.COV_ANALYZE(EMPTY);
        RepeatableCommand covCommitDefects = RepeatableCommand.COV_COMMIT_DEFECTS(EMPTY, EMPTY);
        AdvancedCoverityRunConfiguration coverityRunConfiguration = new AdvancedCoverityRunConfiguration(new RepeatableCommand[] { covBuild, covAnalyze, covCommitDefects });

        List<String> expectedCovBuild = Arrays.asList("cov-build", "--dir", "/some/path");
        List<String> expectedCovAnalyze = Arrays.asList("cov-analyze", "--dir", "/some/path");
        List<String> expectedCovCommitDefects = Arrays.asList("cov-commit-defects", "--dir", "/some/path", "--url", "${COV_URL}", "--stream", "${COV_STREAM}");

        CoverityCommandService coverityCommandService = new CoverityCommandService(JenkinsIntLogger.logToStandardOut(),null);
        try {
            List<List<String>> commandList = coverityCommandService.getCommands(intEnvironmentVariables, coverityRunConfiguration);

            assertEquals(expectedCovBuild, commandList.get(0));
            assertEquals(expectedCovAnalyze, commandList.get(1));
            assertEquals(expectedCovCommitDefects, commandList.get(2));
        } catch (CoverityJenkinsException e) {
            fail("Unexpected exception occurred getting the coverity commands, you may need to fix the test code.", e);
        }
    }

}
