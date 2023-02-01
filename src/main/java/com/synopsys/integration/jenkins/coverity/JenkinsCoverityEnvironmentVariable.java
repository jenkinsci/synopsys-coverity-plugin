/*
 * synopsys-coverity
 *
 * Copyright (c) 2023 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity;

import com.synopsys.integration.coverity.executable.SynopsysEnvironmentVariable;

public enum JenkinsCoverityEnvironmentVariable implements SynopsysEnvironmentVariable {
    LOG_LEVEL("COVERITY_LOG_LEVEL"),
    CHANGE_SET("CHANGE_SET"),
    CHANGE_SET_SIZE("CHANGE_SET_SIZE"),
    TEMPORARY_AUTH_KEY_PATH("COV_AUTH_KEY_PATH"),
    COVERITY_URL("COV_URL"),
    CREDENTIALS_ID("COV_CREDENTIALS_ID"),
    COVERITY_PROJECT("COV_PROJECT"),
    COVERITY_STREAM("COV_STREAM"),
    COVERITY_VIEW("COV_VIEW"),
    COVERITY_TOOL_HOME("COVERITY_TOOL_HOME"),
    COVERITY_INTERMEDIATE_DIRECTORY("COV_DIR");

    private final String name;

    JenkinsCoverityEnvironmentVariable(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    public String expansionString() {
        return String.format("${%s}", this.name);
    }
}
