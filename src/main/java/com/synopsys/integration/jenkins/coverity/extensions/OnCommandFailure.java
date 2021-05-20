/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions;

import com.synopsys.integration.jenkins.extensions.JenkinsSelectBoxEnum;

public enum OnCommandFailure implements JenkinsSelectBoxEnum {
    SKIP_REMAINING_COMMANDS("Skip any remaining commands"),
    EXECUTE_REMAINING_COMMANDS("Continue executing any remaining commands");

    private String displayName;

    OnCommandFailure(final String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

}
