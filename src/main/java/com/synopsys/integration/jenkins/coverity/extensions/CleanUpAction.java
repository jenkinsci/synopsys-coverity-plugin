/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions;

import com.synopsys.integration.jenkins.extensions.JenkinsSelectBoxEnum;

public enum CleanUpAction implements JenkinsSelectBoxEnum {
    PERSIST_INTERMEDIATE_DIRECTORY("Persist the intermediate directory"),
    DELETE_INTERMEDIATE_DIRECTORY("Clean up the intermediate directory");

    private String displayName;

    CleanUpAction(final String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

}
