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
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.net.MalformedURLException;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
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

    public T getData(CoverityConnectInstance coverityConnectInstance) throws InterruptedException {
        semaphore.acquire();
        semaphore.release();
        refreshIfStale(coverityConnectInstance);
        return cachedData;
    }

    public void refreshIfStale(CoverityConnectInstance coverityConnectInstance) throws InterruptedException {
        long cacheTimeInSeconds = TimeUnit.MINUTES.toSeconds(CACHE_TIME_IN_MINUTES);
        if (Instant.now().minusSeconds(cacheTimeInSeconds).isAfter(lastTimeRetrieved)) {
            refresh(coverityConnectInstance);
        }
    }

    public void refresh(CoverityConnectInstance coverityConnectInstance) throws InterruptedException {
        semaphore.acquire();
        Thread thread = Thread.currentThread();
        ClassLoader threadClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(this.getClass().getClassLoader());

        try {
            logger.info("Refreshing connection to Coverity Connect instance...");

            CoverityServerConfig coverityServerConfig = coverityConnectInstance.getCoverityServerConfig(logger);
            WebServiceFactory webServiceFactory = coverityServerConfig.createWebServiceFactory(logger);
            webServiceFactory.connect();

            this.cachedData = getFreshData(webServiceFactory);

            lastTimeRetrieved = Instant.now();
            logger.info("Connection refreshed successfully.");
        } catch (MalformedURLException | IllegalArgumentException | IllegalStateException | CoverityIntegrationException e) {
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
