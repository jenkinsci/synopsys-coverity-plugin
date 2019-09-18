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

    public ConnectionCachingFieldHelper(final IntLogger logger, final Supplier<T> cacheConstructor) {
        super(logger);
        cacheMap = new ConcurrentHashMap<>();
        this.cacheConstructor = cacheConstructor;
    }

    public void updateNow(final String coverityConnectUrl) throws InterruptedException {
        try {
            final CoverityConnectInstance coverityConnectInstance = GlobalValueHelper.getCoverityInstanceWithUrlOrDie(logger, coverityConnectUrl);
            final T cache = getCache(coverityConnectUrl);
            cache.refresh(coverityConnectInstance);
        } catch (final CoverityIntegrationException ignored) {
            // Handled by form validation
        }
    }

    protected T getCache(final String coverityConnectUrl) {
        cacheMap.putIfAbsent(coverityConnectUrl, cacheConstructor.get());
        return cacheMap.get(coverityConnectUrl);
    }

}
