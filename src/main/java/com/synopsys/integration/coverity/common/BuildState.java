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

import java.util.Optional;

import org.apache.commons.lang.StringUtils;

import hudson.model.Result;

public enum BuildState {
    SUCCESS("Success", Result.SUCCESS), FAILURE("Failure", Result.FAILURE), UNSTABLE("Unstable", Result.UNSTABLE);

    private final String displayValue;
    private final Result result;

    BuildState(String displayValue, Result result) {
        this.displayValue = displayValue;
        this.result = result;
    }

    public static Optional<BuildState> getBuildStateFromDisplayValue(final String displayValue) {
        if (StringUtils.isBlank(displayValue)) {
            return Optional.empty();
        }
        for (final BuildState currentEnum : BuildState.values()) {
            if (currentEnum.getDisplayValue().equalsIgnoreCase(displayValue)) {
                return Optional.of(currentEnum);
            }
        }
        return Optional.empty();
    }

    public String getDisplayValue() {
        return displayValue;
    }

    public Result getResult() {
        return result;
    }

}
