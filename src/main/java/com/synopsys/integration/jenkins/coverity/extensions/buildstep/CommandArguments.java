/*
 * synopsys-coverity
 *
 * Copyright (c) 2023 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.jenkins.annotations.HelpMarkdown;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

public class CommandArguments extends AbstractDescribableImpl<CommandArguments> {
    @HelpMarkdown("Specify additional arguments to apply to the invocation of the cov-build command. Affects the **Build** capture type.\r\n"
                      + "\r\n"
                      + "**Note:**  \r\n"
                      + "The following options are automatically provided and should not be specified as an argument here. If you wish to override any of the provided arguments, select the 'Run custom Coverity commands' run configuration.\r\n"
                      + "* --dir ${WORKSPACE}/idir\r\n"
                      + "* *Source Argument (build command)*")
    private final String covBuildArguments;

    @HelpMarkdown("Specify additional arguments to apply to the invocation of the cov-analyze command. Affects the **Full Analysis** analysis type.\r\n"
                      + "\r\n"
                      + "**Note:**  \r\n"
                      + "The following options are automatically provided and should not be specified as an argument here. If you wish to override any of the provided arguments, select the 'Run custom Coverity commands' run configuration.\r\n"
                      + "* --dir ${WORKSPACE}/idir")
    private final String covAnalyzeArguments;

    @HelpMarkdown("Specify additional arguments to apply to the invocation of the cov-run-desktop command. Affects the **Incremental Analysis** analysis type.\r\n"
                      + "\r\n"
                      + "**Note:**  \r\n"
                      + "The following options are automatically provided and should not be specified as an argument here. If you wish to override any of the provided arguments, select the 'Run custom Coverity commands' run configuration.\r\n"
                      + "* --dir ${WORKSPACE}/idir\r\n"
                      + "* --url ${COV_URL}\r\n"
                      + "* --stream ${COV_STREAM}\r\n"
                      + "* ${CHANGE_SET}")
    private final String covRunDesktopArguments;

    @HelpMarkdown("Specify additional arguments to apply to the invocation of the cov-commit-defects command. Affects <strong>all</strong> analysis types.\r\n"
                      + "\r\n"
                      + "**Note:**  \r\n"
                      + "The following options are automatically provided and should not be specified as an argument here. If you wish to override any of the provided arguments, select the 'Run custom Coverity commands' run configuration.\r\n"
                      + "* --dir ${WORKSPACE}/idir\r\n"
                      + "* --url ${COV_URL}\r\n"
                      + "* --stream ${COVERITY_STREAM}\r\n"
    )
    private final String covCommitDefectsArguments;

    @HelpMarkdown("Specify additional arguments to apply to the invocation of the cov-capture command. Affects the **Buildless Capture (Project)** and **Buildless Capture (SCM)** capture types.  \r\n"
                      + "The following options are automatically provided and should not be specified as an argument here. If you wish to override any of the provided arguments, select the 'Run custom Coverity commands' run configuration.\r\n"
                      + "\r\n"
                      + "With the **Buildless Capture (Project)** capture type, the following arguments are automatically provided:\r\n"
                      + "* --project-dir *Source Argument (project directory)*\r\n"
                      + "* --dir ${WORKSPACE}/idir\r\n"
                      + "\r\n"
                      + "With the **Buildless Capture (SCM)** capture type, the following arguments are automatically provided:\r\n"
                      + "* --scm-url *Source Argument (scm url)*\r\n"
                      + "* --dir ${WORKSPACE}/idir")
    private final String covCaptureArguments;

    @DataBoundConstructor
    public CommandArguments(final String covBuildArguments, final String covAnalyzeArguments, final String covRunDesktopArguments, final String covCommitDefectsArguments, final String covCaptureArguments) {
        this.covBuildArguments = covBuildArguments;
        this.covAnalyzeArguments = covAnalyzeArguments;
        this.covRunDesktopArguments = covRunDesktopArguments;
        this.covCommitDefectsArguments = covCommitDefectsArguments;
        this.covCaptureArguments = covCaptureArguments;
    }

    public String getCovBuildArguments() {
        return covBuildArguments;
    }

    public String getCovAnalyzeArguments() {
        return covAnalyzeArguments;
    }

    public String getCovRunDesktopArguments() {
        return covRunDesktopArguments;
    }

    public String getCovCommitDefectsArguments() {
        return covCommitDefectsArguments;
    }

    public String getCovCaptureArguments() {
        return covCaptureArguments;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<CommandArguments> {
        public DescriptorImpl() {
            super(CommandArguments.class);
            load();
        }

        public FormValidation doCheckCovBuildArguments(@QueryParameter("covBuildArguments") final String covBuildArguments) {
            return checkForAlreadyProvidedArguments(covBuildArguments, RepeatableCommand.Argument.DIR);
        }

        public FormValidation doCheckCovAnalyzeArguments(@QueryParameter("covAnalyzeArguments") final String covAnalyzeArguments) {
            return checkForAlreadyProvidedArguments(covAnalyzeArguments, RepeatableCommand.Argument.DIR);
        }

        public FormValidation doCheckCovRunDesktopArguments(@QueryParameter("covRunDesktopArguments") final String covRunDesktopArguments) {
            return checkForAlreadyProvidedArguments(covRunDesktopArguments, RepeatableCommand.Argument.DIR, RepeatableCommand.Argument.URL, RepeatableCommand.Argument.STREAM);
        }

        public FormValidation doCheckCovCommitDefectsArguments(@QueryParameter("covCommitDefectsArguments") final String covCommitDefectsArguments) {
            return checkForAlreadyProvidedArguments(covCommitDefectsArguments, RepeatableCommand.Argument.DIR, RepeatableCommand.Argument.URL, RepeatableCommand.Argument.STREAM);
        }

        private FormValidation checkForAlreadyProvidedArguments(final String command, final RepeatableCommand.Argument... providedArguments) {
            final String alreadyProvidedArguments = Arrays.stream(providedArguments)
                                                        .map(RepeatableCommand.Argument::toString)
                                                        .filter(command::contains)
                                                        .collect(Collectors.joining(", "));

            if (StringUtils.isNotBlank(alreadyProvidedArguments)) {
                return FormValidation.error(String.format("The argument(s) %s are automatically provided in this mode. If you wish to override, configure the 'Run custom Coverity commands' section instead.", alreadyProvidedArguments));
            }
            return FormValidation.ok();
        }

    }

}
