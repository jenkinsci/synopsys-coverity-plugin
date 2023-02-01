/*
 * synopsys-coverity
 *
 * Copyright (c) 2023 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
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
