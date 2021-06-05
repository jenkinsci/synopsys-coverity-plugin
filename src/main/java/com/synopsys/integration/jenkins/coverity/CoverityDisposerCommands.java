/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.jenkins.coverity.service.CleanUpWorkflowService;
import com.synopsys.integration.jenkins.coverity.service.CoverityEnvironmentService;

public class CoverityDisposerCommands {
    private final CoverityEnvironmentService coverityEnvironmentService;
    private final CleanUpWorkflowService cleanUpWorkflowService;

    public CoverityDisposerCommands(CoverityEnvironmentService coverityEnvironmentService, CleanUpWorkflowService cleanUpWorkflowService) {
        this.coverityEnvironmentService = coverityEnvironmentService;
        this.cleanUpWorkflowService = cleanUpWorkflowService;
    }

    public void cleanUp() {
        String authKeyFilePath = coverityEnvironmentService.getAuthKeyFilePath();

        if (StringUtils.isNotBlank(authKeyFilePath)) {
            cleanUpWorkflowService.cleanUpAuthenticationFile(authKeyFilePath);
        }
    }

}
