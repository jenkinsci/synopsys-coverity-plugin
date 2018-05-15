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
package com.sig.integration.coverity.dsl;

import com.sig.integration.coverity.common.RepeatableCommand;
import com.sig.integration.coverity.post.CoverityPostBuildStep;

import hudson.Extension;
import javaposse.jobdsl.dsl.helpers.step.StepContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;

@Extension(optional = true)
public class CoverityDslExtension extends ContextExtensionPoint {
    @DslExtensionMethod(context = StepContext.class)
    public Object coverity(String coverityToolName, Boolean continueOnCommandFailure, RepeatableCommand[] commands, String buildStateOnFailure, Boolean failOnQualityIssues,
            Boolean failOnSecurityIssues, String streamName) {
        return new CoverityPostBuildStep(coverityToolName, continueOnCommandFailure, commands, buildStateOnFailure, failOnQualityIssues, failOnSecurityIssues, streamName);
    }

    @DslExtensionMethod(context = StepContext.class)
    public Object detect(final Runnable closure) {
        final CoverityDslContext context = new CoverityDslContext();
        executeInContext(closure, context);

        return new CoverityPostBuildStep(context.getCoverityToolName(), context.getContinueOnCommandFailure(), context.getCommands(), context.getBuildStateOnFailure(), context.getFailOnQualityIssues(), context.getFailOnSecurityIssues(),
                context.getStreamName());
    }

}
