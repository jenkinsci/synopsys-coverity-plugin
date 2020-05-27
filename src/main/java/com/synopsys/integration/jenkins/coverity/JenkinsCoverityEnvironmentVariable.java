/**
 * synopsys-coverity
 *
 * Copyright (c) 2020 Synopsys, Inc.
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

import com.synopsys.integration.coverity.executable.SynopsysEnvironmentVariable;

public enum JenkinsCoverityEnvironmentVariable implements SynopsysEnvironmentVariable {
    LOG_LEVEL("COVERITY_LOG_LEVEL"),
    CHANGE_SET("CHANGE_SET"),
    CHANGE_SET_SIZE("CHANGE_SET_SIZE"),
    TEMPORARY_AUTH_KEY_PATH("COV_AUTH_KEY_PATH"),
    COVERITY_URL("COV_URL"),
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
