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
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import com.synopsys.integration.jenkins.coverity.extensions.CoveritySelectBoxEnum;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

public abstract class CoverityRunConfiguration extends AbstractDescribableImpl<CoverityRunConfiguration> {
    public abstract RunConfigurationType getRunConFigurationType();

    @Override
    public RunConfigurationDescriptor getDescriptor() {
        return (RunConfigurationDescriptor) super.getDescriptor();
    }

    public enum RunConfigurationType implements CoveritySelectBoxEnum {
        SIMPLE("Run default Coverity workflow"),
        ADVANCED("Run custom Coverity commands");

        private final String displayName;

        RunConfigurationType(final String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }
    }

    public static abstract class RunConfigurationDescriptor extends Descriptor<CoverityRunConfiguration> {
        public RunConfigurationDescriptor(final Class<? extends CoverityRunConfiguration> clazz) {
            super(clazz);
        }
    }

}
