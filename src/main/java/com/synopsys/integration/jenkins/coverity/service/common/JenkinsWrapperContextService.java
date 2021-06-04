package com.synopsys.integration.jenkins.coverity.service.common;

import com.synopsys.integration.util.IntEnvironmentVariables;

import jenkins.tasks.SimpleBuildWrapper;

public class JenkinsWrapperContextService {
    private final SimpleBuildWrapper.Context context;

    public JenkinsWrapperContextService(SimpleBuildWrapper.Context context) {
        this.context = context;
    }

    public void populateEnvironment(IntEnvironmentVariables intEnvironmentVariables) {
        intEnvironmentVariables
            .getVariables()
            .forEach(context::env);
    }
}
