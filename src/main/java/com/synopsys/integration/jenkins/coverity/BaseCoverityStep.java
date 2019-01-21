/**
 * synopsys-coverity
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
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
package com.synopsys.integration.jenkins.coverity;

import java.net.URL;
import java.util.Map;
import java.util.Optional;

import com.synopsys.integration.coverity.executable.SynopsysEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityToolInstallation;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

public abstract class BaseCoverityStep {
    private final Node node;
    private final TaskListener listener;
    private final EnvVars envVars;
    private final FilePath workspace;
    private final Run run;
    protected JenkinsCoverityLogger logger;

    public BaseCoverityStep(final Node node, final TaskListener listener, final EnvVars envVars, final FilePath workspace, final Run run) {
        this.node = node;
        this.listener = listener;
        this.envVars = envVars;
        this.workspace = workspace;
        this.run = run;
    }

    public Node getNode() {
        return node;
    }

    public TaskListener getListener() {
        return listener;
    }

    public EnvVars getEnvVars() {
        return envVars;
    }

    public String getEnvironmentVariable(final SynopsysEnvironmentVariable synopsysEnvironmentVariable) {
        return envVars.get(synopsysEnvironmentVariable.toString());
    }

    public void setEnvironmentVariable(final SynopsysEnvironmentVariable synopsysEnvironmentVariable, final String value) {
        envVars.put(synopsysEnvironmentVariable.toString(), value);
    }

    public void addToPath(final CoverityToolInstallation coverityToolInstallation) {
        coverityToolInstallation.buildEnvVars(envVars);
    }

    public FilePath getWorkspace() {
        return workspace;
    }

    public Run getRun() {
        return run;
    }

    public Result getResult() {
        return getRun().getResult();
    }

    public void setResult(final Result result) {
        getRun().setResult(result);
    }

    public void initializeJenkinsCoverityLogger() {
        final JenkinsCoverityLogger logger = new JenkinsCoverityLogger(listener);
        final IntEnvironmentVariables variables = createIntEnvironmentVariables();
        logger.setLogLevel(variables);
        this.logger = logger;
    }

    public IntEnvironmentVariables createIntEnvironmentVariables() {
        final IntEnvironmentVariables variables = new IntEnvironmentVariables();
        variables.putAll(envVars);
        return variables;
    }

    protected String handleVariableReplacement(final Map<String, String> variables, final String variableToReplace) throws CoverityJenkinsException {
        if (variableToReplace != null) {
            final String newValue = Util.replaceMacro(variableToReplace, variables);
            if (newValue.contains("$")) {
                throw new CoverityJenkinsException("Variable was not properly replaced. Variable: " + variableToReplace + ", Result: " + newValue + ". Make sure the variable has been properly defined.");
            }
            return newValue;
        }
        return null;
    }

    protected void logGlobalConfiguration(final CoverityConnectInstance coverityInstance) {
        if (null == coverityInstance) {
            logger.warn("No Coverity Connect instance configured.");
        } else {
            final Optional<URL> optionalCoverityURL = coverityInstance.getCoverityURL();
            if (!optionalCoverityURL.isPresent()) {
                logger.warn("No Coverity URL configured.");
            } else {
                logger.alwaysLog("-- Coverity URL: " + optionalCoverityURL.get().toString());
            }
            final Optional<String> optionalCoverityUsername = coverityInstance.getCoverityUsername();
            if (!optionalCoverityUsername.isPresent()) {
                logger.warn("No Coverity Username configured.");
            } else {
                logger.alwaysLog("-- Coverity username: " + optionalCoverityUsername.get());
            }
        }
    }

}
