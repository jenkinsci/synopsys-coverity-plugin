package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class RepeatableCommandTest {
    public static final String EXTRA_ARGUMENTS = "--foo bar";
    public static final String AUTH_KEY_PATH = "/some/path/auth-key.txt";

    private static Stream<Arguments> getTestConstructCovBuildParameters() {
        String buildCommand = "./gradlew clean build";
        return Stream.of(
            Arguments.of(buildCommand, EXTRA_ARGUMENTS, "cov-build --dir ${COV_DIR} --foo bar ./gradlew clean build"),
            Arguments.of(buildCommand, StringUtils.EMPTY, "cov-build --dir ${COV_DIR} ./gradlew clean build"),
            Arguments.of(StringUtils.EMPTY, EXTRA_ARGUMENTS, "cov-build --dir ${COV_DIR} --foo bar"),
            Arguments.of(StringUtils.EMPTY, StringUtils.EMPTY, "cov-build --dir ${COV_DIR}")
        );
    }

    private static Stream<Arguments> getTestConstructCovAnalyzeParameters() {
        return Stream.of(
            Arguments.of(EXTRA_ARGUMENTS, "cov-analyze --dir ${COV_DIR} --foo bar"),
            Arguments.of(StringUtils.EMPTY, "cov-analyze --dir ${COV_DIR}")
        );
    }

    private static Stream<Arguments> getTestConstructCovRunDesktopParameters() {
        return Stream.of(
            Arguments.of(AUTH_KEY_PATH, EXTRA_ARGUMENTS, "cov-run-desktop --dir ${COV_DIR} --url ${COV_URL} --stream ${COV_STREAM} --auth-key-file /some/path/auth-key.txt --foo bar ${CHANGE_SET}"),
            Arguments.of(AUTH_KEY_PATH, StringUtils.EMPTY, "cov-run-desktop --dir ${COV_DIR} --url ${COV_URL} --stream ${COV_STREAM} --auth-key-file /some/path/auth-key.txt ${CHANGE_SET}"),
            Arguments.of(StringUtils.EMPTY, EXTRA_ARGUMENTS, "cov-run-desktop --dir ${COV_DIR} --url ${COV_URL} --stream ${COV_STREAM} --foo bar ${CHANGE_SET}"),
            Arguments.of(StringUtils.EMPTY, StringUtils.EMPTY, "cov-run-desktop --dir ${COV_DIR} --url ${COV_URL} --stream ${COV_STREAM} ${CHANGE_SET}")
        );
    }

    private static Stream<Arguments> getTestConstructCovCommitDefectsParameters() {
        return Stream.of(
            Arguments.of(AUTH_KEY_PATH, EXTRA_ARGUMENTS, "cov-commit-defects --dir ${COV_DIR} --url ${COV_URL} --stream ${COV_STREAM} --auth-key-file /some/path/auth-key.txt --foo bar"),
            Arguments.of(AUTH_KEY_PATH, StringUtils.EMPTY, "cov-commit-defects --dir ${COV_DIR} --url ${COV_URL} --stream ${COV_STREAM} --auth-key-file /some/path/auth-key.txt"),
            Arguments.of(StringUtils.EMPTY, EXTRA_ARGUMENTS, "cov-commit-defects --dir ${COV_DIR} --url ${COV_URL} --stream ${COV_STREAM} --foo bar"),
            Arguments.of(StringUtils.EMPTY, StringUtils.EMPTY, "cov-commit-defects --dir ${COV_DIR} --url ${COV_URL} --stream ${COV_STREAM}")
        );
    }

    private static Stream<Arguments> getTestConstructCovCaptureScmParameters() {
        String scmUrl = "git@example.com";
        return Stream.of(
            Arguments.of(scmUrl, EXTRA_ARGUMENTS, "cov-capture --scm-url git@example.com --dir ${COV_DIR} --foo bar"),
            Arguments.of(StringUtils.EMPTY, EXTRA_ARGUMENTS, "cov-capture --dir ${COV_DIR} --foo bar"),
            Arguments.of(scmUrl, StringUtils.EMPTY, "cov-capture --scm-url git@example.com --dir ${COV_DIR}"),
            Arguments.of(StringUtils.EMPTY, StringUtils.EMPTY, "cov-capture --dir ${COV_DIR}")
        );
    }

    private static Stream<Arguments> getTestConstructCovCaptureProjectParameters() {
        String projectDirectory = "/some/project/dir";
        return Stream.of(
            Arguments.of(projectDirectory, EXTRA_ARGUMENTS, "cov-capture --project-dir /some/project/dir --dir ${COV_DIR} --foo bar"),
            Arguments.of(StringUtils.EMPTY, EXTRA_ARGUMENTS, "cov-capture --dir ${COV_DIR} --foo bar"),
            Arguments.of(projectDirectory, StringUtils.EMPTY, "cov-capture --project-dir /some/project/dir --dir ${COV_DIR}"),
            Arguments.of(StringUtils.EMPTY, StringUtils.EMPTY, "cov-capture --dir ${COV_DIR}")
        );
    }

    @ParameterizedTest
    @MethodSource("getTestConstructCovBuildParameters")
    public void testConstructCovBuild(String buildCommand, String extraArgs, String expectedResult) {
        RepeatableCommand repeatableCommand = RepeatableCommand.COV_BUILD(buildCommand, extraArgs);

        assertEquals(expectedResult, repeatableCommand.getCommand());
    }

    @ParameterizedTest
    @MethodSource("getTestConstructCovAnalyzeParameters")
    public void testConstructCovAnalyze(String extraArgs, String expectedResult) {
        RepeatableCommand repeatableCommand = RepeatableCommand.COV_ANALYZE(extraArgs);

        assertEquals(expectedResult, repeatableCommand.getCommand());
    }

    @ParameterizedTest
    @MethodSource("getTestConstructCovRunDesktopParameters")
    public void testConstructCovRunDesktop(String authKeyFilePath, String extraArgs, String expectedResult) {
        RepeatableCommand repeatableCommand = RepeatableCommand.COV_RUN_DESKTOP(authKeyFilePath, extraArgs);

        assertEquals(expectedResult, repeatableCommand.getCommand());
    }

    @ParameterizedTest
    @MethodSource("getTestConstructCovCommitDefectsParameters")
    public void testConstructCovCommitDefects(String authKeyFilePath, String extraArgs, String expectedResult) {
        RepeatableCommand repeatableCommand = RepeatableCommand.COV_COMMIT_DEFECTS(authKeyFilePath, extraArgs);

        assertEquals(expectedResult, repeatableCommand.getCommand());
    }

    @ParameterizedTest
    @MethodSource("getTestConstructCovCaptureProjectParameters")
    public void testConstructCovCaptureProject(String projectDir, String extraArgs, String expectedResult) {
        RepeatableCommand repeatableCommand = RepeatableCommand.COV_CAPTURE_PROJECT(projectDir, extraArgs);

        assertEquals(expectedResult, repeatableCommand.getCommand());
    }

    @ParameterizedTest
    @MethodSource("getTestConstructCovCaptureScmParameters")
    public void testConstructCovCaptureScm(String scmUrl, String extraArgs, String expectedResult) {
        RepeatableCommand repeatableCommand = RepeatableCommand.COV_CAPTURE_SCM(scmUrl, extraArgs);

        assertEquals(expectedResult, repeatableCommand.getCommand());
    }

}
