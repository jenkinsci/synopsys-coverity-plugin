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

package com.synopsys.integration.coverity.dsl;

import java.util.List;

import com.synopsys.integration.coverity.common.CoverityAnalysisType;
import com.synopsys.integration.coverity.common.CoverityRunConfiguration;
import com.synopsys.integration.coverity.common.OnCommandFailure;
import com.synopsys.integration.coverity.common.RepeatableCommand;
import com.synopsys.integration.coverity.freestyle.CoverityPostBuildStep;

import hudson.Extension;
import javaposse.jobdsl.dsl.helpers.step.StepContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;

@Extension(optional = true)
public class CoverityDslExtension extends ContextExtensionPoint {
    @DslExtensionMethod(context = StepContext.class)
    public Object coverity(final String coverityToolName, final String onCommandFailure, final List<String> commands, final String buildStateForIssues, final String projectName, final String streamName,
        final String viewName, final String coverityRunConfiguration, final String coverityAnalysisType, final String buildCommand, final String changeSetNameExcludePatterns, final String changeSetNameIncludePatterns,
        final Boolean buildStatusForIssuesConfigured, final Boolean changeSetPatternsConfigured) {
        return new CoverityPostBuildStep(coverityToolName, stringToOnCommandFailure(onCommandFailure), stringsToCommands(commands), buildStateForIssues, projectName, streamName,
            stringToCoverityRunConfiguration(coverityRunConfiguration),
            stringToCoverityAnalysisType(coverityAnalysisType), buildCommand, viewName, changeSetNameExcludePatterns, changeSetNameIncludePatterns, buildStatusForIssuesConfigured, changeSetPatternsConfigured);
    }

    private RepeatableCommand[] stringsToCommands(final List<String> commands) {
        if (null == commands) {
            return null;
        }
        return (RepeatableCommand[]) commands.stream().map(RepeatableCommand::new).toArray();
    }

    private OnCommandFailure stringToOnCommandFailure(final String commandFailureAction) {
        return OnCommandFailure.valueOf(commandFailureAction);
    }

    private CoverityAnalysisType stringToCoverityAnalysisType(final String coverityAnalysisType) {
        return CoverityAnalysisType.valueOf(coverityAnalysisType);
    }

    private CoverityRunConfiguration stringToCoverityRunConfiguration(final String coverityRunConfiguration) {
        return CoverityRunConfiguration.valueOf(coverityRunConfiguration);
    }

}
