/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.stepworkflow;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import com.synopsys.integration.coverity.exception.ExecutableException;
import com.synopsys.integration.coverity.exception.ExecutableRunnerException;
import com.synopsys.integration.coverity.executable.Executable;
import com.synopsys.integration.coverity.executable.ExecutableManager;
import com.synopsys.integration.jenkins.coverity.CoverityJenkinsIntLogger;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;

public class CoverityRemoteToolRunner extends CoverityRemoteCallable<Integer> {
    private static final long serialVersionUID = -1777043273065180425L;
    private final String coverityToolHome;
    private final List<String> arguments;
    private final HashMap<String, String> environmentVariables;

    private final String workingDirectoryPath;

    public CoverityRemoteToolRunner(final CoverityJenkinsIntLogger logger, final String coverityToolHome, final List<String> arguments, final String workingDirectoryPath, final HashMap<String, String> environmentVariables) {
        super(logger);
        this.environmentVariables = environmentVariables;
        this.coverityToolHome = coverityToolHome;
        this.arguments = arguments;
        this.workingDirectoryPath = workingDirectoryPath;
    }

    public Integer call() throws CoverityJenkinsException {
        final File workingDirectory = new File(workingDirectoryPath);
        final Executable executable = new Executable(arguments, workingDirectory, environmentVariables);
        final ExecutableManager executableManager = new ExecutableManager(new File(coverityToolHome));
        final Integer exitCode;
        final ByteArrayOutputStream errorOutputStream = new ByteArrayOutputStream();
        try (final PrintStream errorStream = new PrintStream(errorOutputStream, true, "UTF-8")) {
            final PrintStream jenkinsPrintStream = logger.getTaskListener().getLogger();
            exitCode = executableManager.execute(executable, logger, jenkinsPrintStream, errorStream);
        } catch (final UnsupportedEncodingException | ExecutableException | ExecutableRunnerException e) {
            throw new CoverityJenkinsException(e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoverityJenkinsException(e);
        } finally {
            logger.error(new String(errorOutputStream.toByteArray(), StandardCharsets.UTF_8));
        }
        return exitCode;
    }

}
