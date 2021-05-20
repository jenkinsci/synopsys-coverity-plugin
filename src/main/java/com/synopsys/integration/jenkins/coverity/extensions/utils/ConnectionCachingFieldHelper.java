/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.IntLogger;

public abstract class ConnectionCachingFieldHelper<T extends CoverityConnectDataCache> extends FieldHelper {
    private final ConcurrentHashMap<String, T> cacheMap;
    private final Supplier<T> cacheConstructor;

    public ConnectionCachingFieldHelper(IntLogger logger, Supplier<T> cacheConstructor) {
        super(logger);
        cacheMap = new ConcurrentHashMap<>();
        this.cacheConstructor = cacheConstructor;
    }

    public void updateNow(String credentialId, String coverityConnectUrl) throws InterruptedException {
        try {
            CoverityConnectInstance coverityConnectInstance = GlobalValueHelper.getCoverityInstanceWithUrlOrDie(logger, coverityConnectUrl);
            T cache = getCache(coverityConnectUrl);
            cache.refresh(credentialId, coverityConnectInstance);
        } catch (CoverityIntegrationException ignored) {
            // Handled by form validation
        }
    }

    protected T getCache(String coverityConnectUrl) {
        cacheMap.putIfAbsent(coverityConnectUrl, cacheConstructor.get());
        return cacheMap.get(coverityConnectUrl);
    }

}
