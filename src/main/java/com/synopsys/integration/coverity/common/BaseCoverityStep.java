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

package com.synopsys.integration.coverity.common;

import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.coverity.CoverityConnectInstance;
import com.synopsys.integration.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.coverity.JenkinsProxyHelper;
import com.synopsys.integration.coverity.exception.CoverityJenkinsException;
import com.synopsys.integration.jenkins.coverity.global.CoverityGlobalConfig;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.GlobalConfiguration;

public abstract class BaseCoverityStep {
    private final String coverityInstanceUrl;
    private final Node node;
    private final TaskListener listener;
    private final EnvVars envVars;
    private final FilePath workspace;
    private final Run run;
    protected JenkinsCoverityLogger logger;

    public BaseCoverityStep(final String coverityInstanceUrl, final Node node, final TaskListener listener, final EnvVars envVars, final FilePath workspace, final Run run) {
        this.coverityInstanceUrl = coverityInstanceUrl;
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

    protected CoverityGlobalConfig getCoverityGlobalConfig() {
        return GlobalConfiguration.all().get(CoverityGlobalConfig.class);
    }

    protected Optional<CoverityConnectInstance> verifyAndGetCoverityInstance() {
        final CoverityConnectInstance[] coverityInstances = getCoverityGlobalConfig().getCoverityConnectInstances();
        if (StringUtils.isBlank(coverityInstanceUrl)) {
            logger.error("[ERROR] No Coverity instance configured for this Job.");
        } else if (null == coverityInstances || coverityInstances.length == 0) {
            logger.error("[ERROR] No Coverity instance configured in Jenkins.");
        } else {
            return Stream.of(coverityInstances)
                       .filter(coverityInstance -> coverityInstance.getUrl().equals(coverityInstanceUrl))
                       .findFirst();
        }

        return Optional.empty();
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

    public String handleVariableReplacement(final Map<String, String> variables, final String value) throws CoverityJenkinsException {
        if (value != null) {
            final String newValue = Util.replaceMacro(value, variables);
            if (newValue.contains("$")) {
                throw new CoverityJenkinsException("Variable was not properly replaced. Value : " + value + ", Result : " + newValue + ". Make sure the variable has been properly defined.");
            }
            return newValue;
        }
        return null;
    }

    public JenkinsProxyHelper getJenkinsProxyHelper() {
        return new JenkinsProxyHelper();
    }

    public void logGlobalConfiguration(final CoverityConnectInstance coverityInstance) {
        if (null == coverityInstance) {
            logger.warn("No configured Coverity server was detected in the Jenkins System Configuration.");
        } else {
            final Optional<URL> optionalCoverityURL = coverityInstance.getCoverityURL();
            if (!optionalCoverityURL.isPresent()) {
                logger.warn("No Coverity URL configured.");
            } else {
                logger.alwaysLog("-- Coverity URL : " + optionalCoverityURL.get().toString());
            }
            final Optional<String> optionalCoverityUsername = coverityInstance.getCoverityUsername();
            if (!optionalCoverityUsername.isPresent()) {
                logger.warn("No Coverity Username configured.");
            } else {
                logger.alwaysLog("-- Coverity username : " + optionalCoverityUsername.get());
            }
        }
    }

}
