/**
 * synopsys-coverity
 *
 * Copyright (c) 2019 Synopsys, Inc.
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
package com.synopsys.integration.jenkins.coverity.substeps;

import com.synopsys.integration.jenkins.coverity.JenkinsCoverityEnvironmentVariable;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityRunConfiguration;
import com.synopsys.integration.jenkins.coverity.extensions.buildstep.SimpleCoverityRunConfiguration;
import com.synopsys.integration.util.IntEnvironmentVariables;

public class IncrementalAnalysisValidation {
    private final IntEnvironmentVariables intEnvironmentVariables;
    private final CoverityRunConfiguration coverityRunConfiguration;

    public IncrementalAnalysisValidation(final IntEnvironmentVariables intEnvironmentVariables, final CoverityRunConfiguration coverityRunConfiguration) {
        this.intEnvironmentVariables = intEnvironmentVariables;
        this.coverityRunConfiguration = coverityRunConfiguration;
    }

    public boolean isIncremental() {
        if (CoverityRunConfiguration.RunConfigurationType.ADVANCED.equals(coverityRunConfiguration.getRunConFigurationType())) {
            return false;
        }

        final SimpleCoverityRunConfiguration simpleCoverityRunConfiguration = (SimpleCoverityRunConfiguration) coverityRunConfiguration;
        final int changeSetSize = Integer.valueOf(intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.CHANGE_SET_SIZE.toString(), "0"));

        switch (simpleCoverityRunConfiguration.getCoverityAnalysisType()) {
            case COV_RUN_DESKTOP:
                return true;
            case THRESHOLD:
                return changeSetSize < simpleCoverityRunConfiguration.getChangeSetAnalysisThreshold();
            default:
                return false;
        }
    }

    public boolean incrementalShouldNotRun() {
        return intEnvironmentVariables.getValue(JenkinsCoverityEnvironmentVariable.CHANGE_SET.toString()).isEmpty();
    }
}
