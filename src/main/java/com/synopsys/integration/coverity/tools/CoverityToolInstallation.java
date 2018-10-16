/**
 * synopsys-coverity
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.synopsys.integration.coverity.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import com.synopsys.integration.coverity.CoverityVersion;
import com.synopsys.integration.coverity.Messages;
import com.synopsys.integration.coverity.freestyle.CoverityBuildStepDescriptor;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.tools.ToolPropertyDescriptor;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

/**
 * Coverity Static Analysis Tool {@link ToolInstallation}, this represents a named tool configuration with a path to
 * the install directory.
 */
public class CoverityToolInstallation extends ToolInstallation implements NodeSpecific<CoverityToolInstallation>, EnvironmentSpecific<CoverityToolInstallation> {
    @DataBoundConstructor
    public CoverityToolInstallation(final String name, final String home) {
        super(name, home, new DescribableList<ToolProperty<?>, ToolPropertyDescriptor>(Saveable.NOOP));
    }

    @Override
    public CoverityToolInstallation forNode(final Node node, final TaskListener log) throws IOException, InterruptedException {
        return new CoverityToolInstallation(getName(), translateFor(node, log));
    }

    @Override
    public CoverityToolInstallation forEnvironment(final EnvVars environment) {
        return new CoverityToolInstallation(getName(), environment.expand(getHome()));
    }

    /**
     * {@link ToolDescriptor} for {@link CoverityToolInstallation}
     */
    @Extension
    @Symbol("coverity")
    public static final class CoverityToolInstallationDescriptor extends ToolDescriptor<CoverityToolInstallation> {
        @Override
        public String getDisplayName() {
            return Messages.CoverityToolInstallation_getDisplayName();
        }

        /**
         * Override in order to remove the default "install automatically" checkbox for tool installation
         */
        @Override
        public List<ToolPropertyDescriptor> getPropertyDescriptors() {
            return Collections.emptyList();
        }

        @Override
        public DescribableList<ToolProperty<?>, ToolPropertyDescriptor> getDefaultProperties() {
            return new DescribableList<>(NOOP);
        }

        @Override
        public CoverityToolInstallation[] getInstallations() {
            return getCoverityPostBuildStepDescriptor().getCoverityToolInstallations();
        }

        @Override
        public void setInstallations(final CoverityToolInstallation... installations) {
            getCoverityPostBuildStepDescriptor().setCoverityToolInstallations(installations);
        }

        private CoverityBuildStepDescriptor getCoverityPostBuildStepDescriptor() {
            return Jenkins.getInstance().getDescriptorByType(CoverityBuildStepDescriptor.class);
        }

        @Override
        protected FormValidation checkHomeDirectory(final File home) {
            // This validation is only ever run when on master. Jenkins does not use this to validate node overrides
            try {
                if (home == null || !home.exists()) {
                    return FormValidation.error("The specified Analysis installation directory doesn't exist.");
                }

                final File analysisVersionFile = new File(home, "VERSION");
                final File analysisVersionXml = new File(home, "VERSION.xml");

                if (!analysisVersionXml.isFile() || !analysisVersionFile.isFile()) {
                    return FormValidation.error("The specified Analysis installation directory doesn't contain a VERSION and VERSION.xml file.");
                }

                // check the version file value and validate it is greater than minimum version
                final Optional<CoverityVersion> optionalVersion = getVersion(analysisVersionFile);

                if (!optionalVersion.isPresent()) {
                    return FormValidation.error("Could not determine the version of the Coverity analysis tool.");
                }

                final CoverityVersion version = optionalVersion.get();

                if (version.compareTo(CoverityBuildStepDescriptor.MINIMUM_SUPPORTED_VERSION) < 0) {
                    return FormValidation.error(String.format("Analysis version %s detected. The minimum supported version is %s", version.toString(), CoverityBuildStepDescriptor.MINIMUM_SUPPORTED_VERSION.toString()));
                }

                return FormValidation.ok("Analysis installation directory has been verified.");

            } catch (final IOException e) {
                return FormValidation.error("Unable to verify the Analysis installation directory.", e);
            }
        }

        /*
         * Gets the {@link CoverityVersion} given a static analysis tools home directory by finding the VERSION file,
         * then reading the version number
         */
        private Optional<CoverityVersion> getVersion(final File versionFile) throws IOException {
            try (final BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(versionFile), StandardCharsets.UTF_8))) {
                final String prefix = "externalVersion=";
                String line, version = "";
                while ((line = br.readLine()) != null) {
                    if (line.startsWith(prefix)) {
                        version = line.substring(prefix.length(), line.length());
                        break;
                    }
                }
                return CoverityVersion.parse(version);
            }
        }
    }

}
