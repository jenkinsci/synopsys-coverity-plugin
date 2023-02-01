/*
 * synopsys-coverity
 *
 * Copyright (c) 2023 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.IntLogger;

public abstract class CoverityConnectDataCache<T> {
    public static final int CACHE_TIME_IN_MINUTES = 5;
    protected final IntLogger logger;
    private final Semaphore semaphore;
    private Instant lastTimeRetrieved;
    private T cachedData;

    public CoverityConnectDataCache(IntLogger logger) {
        this.logger = logger;
        this.semaphore = new Semaphore(1);
        this.lastTimeRetrieved = Instant.MIN;
        this.cachedData = getEmptyData();
    }

    public T getData(CoverityConnectInstance coverityConnectInstance, String credentialsId) throws InterruptedException {
        semaphore.acquire();
        semaphore.release();
        refreshIfStale(coverityConnectInstance, credentialsId);
        return cachedData;
    }

    public void refreshIfStale(CoverityConnectInstance coverityConnectInstance, String credentialsId) throws InterruptedException {
        long cacheTimeInSeconds = TimeUnit.MINUTES.toSeconds(CACHE_TIME_IN_MINUTES);
        if (Instant.now().minusSeconds(cacheTimeInSeconds).isAfter(lastTimeRetrieved)) {
            refresh(coverityConnectInstance, credentialsId);
        }
    }

    public void refresh(CoverityConnectInstance coverityConnectInstance, String credentialsId) throws InterruptedException {
        semaphore.acquire();
        Thread thread = Thread.currentThread();
        ClassLoader threadClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(this.getClass().getClassLoader());

        try {
            logger.info("Refreshing connection to Coverity Connect instance...");

            CoverityServerConfig coverityServerConfig = coverityConnectInstance.getCoverityServerConfig(logger, credentialsId);
            WebServiceFactory webServiceFactory = coverityServerConfig.createWebServiceFactory(logger);
            webServiceFactory.connect();

            this.cachedData = getFreshData(webServiceFactory);

            lastTimeRetrieved = Instant.now();
            logger.info("Connection refreshed successfully.");
        } catch (Exception e) {
            logger.error("[ERROR] Could not refresh connection to Coverity Connect instance. Please confirm you have a valid URL.");
            logger.trace("Stack trace:", e);
        } finally {
            thread.setContextClassLoader(threadClassLoader);
            semaphore.release();
        }
    }

    protected abstract T getFreshData(WebServiceFactory webServiceFactory);

    protected abstract T getEmptyData();

}
