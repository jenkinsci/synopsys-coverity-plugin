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
    private Semaphore semaphore = new Semaphore(1);
    private Instant lastTimeRetrieved = null;
    private T cachedData = null;

    public CoverityConnectDataCache(final IntLogger logger) {
        this.logger = logger;
    }

    public T getData(final CoverityConnectInstance coverityConnectInstance) throws InterruptedException {
        semaphore.acquire();
        semaphore.release();
        refreshIfStale(coverityConnectInstance);
        return cachedData;
    }

    public void refreshIfStale(final CoverityConnectInstance coverityConnectInstance) throws InterruptedException {
        final long cacheTimeInSeconds = TimeUnit.MINUTES.toSeconds(CACHE_TIME_IN_MINUTES);
        if (lastTimeRetrieved == null || Instant.now().minusSeconds(cacheTimeInSeconds).isAfter(lastTimeRetrieved)) {
            refresh(coverityConnectInstance);
        }
    }

    public void refresh(final CoverityConnectInstance coverityConnectInstance) throws InterruptedException {
        semaphore.acquire();
        final Thread thread = Thread.currentThread();
        final ClassLoader threadClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(this.getClass().getClassLoader());

        try {
            logger.info("Refreshing connection to Coverity Connect instance...");

            final CoverityServerConfig coverityServerConfig = coverityConnectInstance.getCoverityServerConfig();
            final WebServiceFactory webServiceFactory = coverityServerConfig.createWebServiceFactory(logger);
            webServiceFactory.connect();

            this.cachedData = getFreshData(webServiceFactory);

            lastTimeRetrieved = Instant.now();
            logger.info("Connection refreshed successfully.");
        } catch (final MalformedURLException | IllegalArgumentException | IllegalStateException | CoverityIntegrationException e) {
            logger.error("[ERROR] Could not refresh connection to Coverity Connect instance. Please confirm you have a valid URL.");
            logger.trace("Stack trace:", e);
        } finally {
            thread.setContextClassLoader(threadClassLoader);
            semaphore.release();
        }
    }

    protected abstract T getFreshData(final WebServiceFactory webServiceFactory);

}
