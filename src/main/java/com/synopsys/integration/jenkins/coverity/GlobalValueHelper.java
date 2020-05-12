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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityGlobalConfig;
import com.synopsys.integration.log.IntLogger;

import jenkins.model.GlobalConfiguration;

public class GlobalValueHelper {
    public static Optional<CoverityConnectInstance> getCoverityInstanceWithUrl(final IntLogger logger, final String coverityInstanceUrl) {
        final CoverityGlobalConfig coverityGlobalConfig = getCoverityGlobalConfig();
        if (coverityGlobalConfig == null) {
            return Optional.empty();
        }

        final List<CoverityConnectInstance> coverityInstances = coverityGlobalConfig.getCoverityConnectInstances();
        if (null == coverityInstances || coverityInstances.isEmpty()) {
            logger.error("[ERROR] No Coverity Connect instances are configured in the Jenkins system config.");
        } else {
            return coverityInstances.stream()
                       .filter(coverityInstance -> coverityInstance.getUrl().equals(coverityInstanceUrl))
                       .findFirst();
        }

        return Optional.empty();
    }

    public static CoverityConnectInstance getCoverityInstanceWithUrlOrDie(final IntLogger logger, final String coverityInstanceUrl) throws CoverityIntegrationException {
        return getCoverityInstanceWithUrl(logger, coverityInstanceUrl).orElseThrow(() -> new CoverityIntegrationException("No Coverity Connect instance is configured with the url " + coverityInstanceUrl));
    }

    public static List<CoverityConnectInstance> getGlobalCoverityConnectInstances() {
        return Optional.ofNullable(getCoverityGlobalConfig())
                   .map(CoverityGlobalConfig::getCoverityConnectInstances)
                   .orElseGet(Collections::emptyList);
    }

    private static CoverityGlobalConfig getCoverityGlobalConfig() {
        return GlobalConfiguration.all().get(CoverityGlobalConfig.class);
    }
}
