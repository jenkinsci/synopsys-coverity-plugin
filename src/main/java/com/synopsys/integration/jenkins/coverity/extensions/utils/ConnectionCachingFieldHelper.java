/*
 * synopsys-coverity
 *
 * Copyright (c) 2023 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.IntLogger;

public abstract class ConnectionCachingFieldHelper<T extends CoverityConnectDataCache> extends FieldHelper {
    private final ConcurrentHashMap<List<String>, T> cacheMap;
    private final Supplier<T> cacheConstructor;

    public ConnectionCachingFieldHelper(IntLogger logger, Supplier<T> cacheConstructor) {
        super(logger);
        cacheMap = new ConcurrentHashMap<>();
        this.cacheConstructor = cacheConstructor;
    }

    public void updateNow(String coverityConnectUrl, Boolean overrideDefaultCredentials, String credentialsId) throws InterruptedException {
        try {
            CoverityConnectInstance coverityConnectInstance = GlobalValueHelper.getCoverityInstanceWithUrlOrDie(logger, coverityConnectUrl);
            if (Boolean.TRUE.equals(overrideDefaultCredentials)) {
                T cache = getCache(coverityConnectUrl, credentialsId);
                cache.refresh(coverityConnectInstance, credentialsId);
            } else {
                T cache = getCache(coverityConnectUrl, coverityConnectInstance.getDefaultCredentialsId());
                cache.refresh(coverityConnectInstance, coverityConnectInstance.getDefaultCredentialsId());
            }
        } catch (CoverityIntegrationException ignored) {
            // Handled by form validation
        }
    }

    protected T getCache(String coverityConnectUrl, String credentialsId) {
        List<String> urlAndCredentialsId = Collections.unmodifiableList(Arrays.asList(coverityConnectUrl, credentialsId));
        cacheMap.putIfAbsent(urlAndCredentialsId, cacheConstructor.get());
        return cacheMap.get(urlAndCredentialsId);
    }

}
