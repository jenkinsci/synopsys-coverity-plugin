/**
 * sig-coverity
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
package com.sig.integration.coverity.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.tools.ant.types.Commandline;

import com.blackducksoftware.integration.util.CIEnvironmentVariables;
import com.sig.integration.coverity.CoverityInstance;
import com.sig.integration.coverity.JenkinsCoverityLogger;
import com.sig.integration.coverity.JenkinsProxyHelper;
import com.sig.integration.coverity.PluginHelper;
import com.sig.integration.coverity.exception.CoverityJenkinsException;
import com.sig.integration.coverity.post.CoverityPostBuildStepDescriptor;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

public class CoverityCommonStep {
    private final Node node;
    private final TaskListener listener;
    private final EnvVars envVars;
    private final FilePath workspace;
    private final Run run;

    public CoverityCommonStep(final Node node, final TaskListener listener, final EnvVars envVars, final FilePath workspace, final Run run) {
        this.node = node;
        this.listener = listener;
        this.envVars = envVars;
        this.workspace = workspace;
        this.run = run;
    }

    private CoverityPostBuildStepDescriptor getCoverityPostBuildStepDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(CoverityPostBuildStepDescriptor.class);
    }

    private CoverityInstance getCoverityInstance() {
        return getCoverityPostBuildStepDescriptor().getCoverityInstance();
    }

    public void runCommonDetectStep() {
        final JenkinsCoverityLogger logger = new JenkinsCoverityLogger(listener);
        final CIEnvironmentVariables variables = new CIEnvironmentVariables();
        variables.putAll(envVars);
        logger.setLogLevel(variables);
        try {
            final String pluginVersion = PluginHelper.getPluginVersion();
            logger.info("Running Jenkins Detect version : " + pluginVersion);
            //
            //            final DummyToolInstaller dummyInstaller = new DummyToolInstaller();
            //            final String toolsDirectory = dummyInstaller.getToolDir(new DummyToolInstallation(), node).getRemote();
            //            final String hubUrl = HubServerInfoSingleton.getInstance().getHubUrl();
            //            final String hubUsername = HubServerInfoSingleton.getInstance().getHubUsername();
            //            final String hubPassword = HubServerInfoSingleton.getInstance().getHubPassword();
            //            final String hubApiToken = HubServerInfoSingleton.getInstance().getHubApiToken();
            //            final int hubTimeout = HubServerInfoSingleton.getInstance().getHubTimeout();
            //            final boolean trustSSLCertificates = HubServerInfoSingleton.getInstance().isTrustSSLCertificates();
            //
            //            final DetectRemoteRunner detectRemoteRunner = new DetectRemoteRunner(logger, javaHome, hubUrl, hubUsername, hubPassword, hubApiToken, hubTimeout, trustSSLCertificates, HubServerInfoSingleton.getInstance().getDetectDownloadUrl(),
            //                    toolsDirectory, getCorrectedParameters(detectProperties), envVars);
            //            final JenkinsProxyHelper jenkinsProxyHelper = getJenkinsProxyHelper();
            //            final ProxyInfo proxyInfo = jenkinsProxyHelper.getProxyInfoFromJenkins(hubUrl);
            //            if (ProxyInfo.NO_PROXY_INFO != proxyInfo) {
            //                detectRemoteRunner.setProxyInfo(proxyInfo);
            //            }
            //            final DetectResponse response = node.getChannel().call(detectRemoteRunner);
            //            if (response.getExitCode() > 0) {
            //                logger.error("Detect failed with exit code: " + response.getExitCode());
            //                run.setResult(Result.FAILURE);
            //            } else if (null != response.getException()) {
            //                final Exception exception = response.getException();
            //                if (exception instanceof InterruptedException) {
            //                    run.setResult(Result.ABORTED);
            //                    Thread.currentThread().interrupt();
            //                } else {
            //                    logger.error(exception.getMessage(), exception);
            //                    run.setResult(Result.UNSTABLE);
            //                }
            //            }
            //        } catch (final InterruptedException e) {
            //            logger.error("Detect caller thread was interrupted.", e);
            //            run.setResult(Result.ABORTED);
            //            Thread.currentThread().interrupt();
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            run.setResult(Result.UNSTABLE);
        }
    }

    public List<String> getCorrectedParameters(final String commandLineParameters) throws CoverityJenkinsException {
        final String[] separatedParameters = Commandline.translateCommandline(commandLineParameters);
        final List<String> correctedParameters = new ArrayList<>();
        for (final String parameter : separatedParameters) {
            correctedParameters.add(handleVariableReplacement(envVars, parameter));
        }
        return correctedParameters;
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
}
