/**
 * synopsys-coverity
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
package com.synopsys.integration.coverity.common.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.blackducksoftware.integration.exception.IntegrationException;
import com.blackducksoftware.integration.log.IntLogger;
import com.blackducksoftware.integration.log.LogLevel;
import com.blackducksoftware.integration.log.PrintStreamIntLogger;
import com.synopsys.integration.coverity.JenkinsCoverityInstance;

public abstract class BaseCacheData<T> {
    public static final int CACHE_TIME = 5;
    private boolean retrievingNow = false;
    private Instant lastTimeRetrieved = null;
    private List<T> cachedData = null;

    public List<T> getCachedData() {
        return cachedData;
    }

    public abstract String getDataType();

    public void checkAndWaitForData(final JenkinsCoverityInstance coverityInstance, final Boolean updateNow) throws InterruptedException, IntegrationException {
        checkAndUpdateCachedData(coverityInstance, updateNow);
        Instant startingTime = Instant.now();
        Instant now;
        while (null == cachedData) {
            now = Instant.now();
            Duration timeLapsed = Duration.between(startingTime, now);
            if (timeLapsed.getSeconds() > TimeUnit.MINUTES.toSeconds(5)) {
                throw new IntegrationException(String.format("Validation timed out. Retrieving the %s information took longer than 5 minutes.", getDataType()));
            }
            System.out.println(String.format("Waiting for %s", getDataType()));
            Thread.sleep(500);
        }
    }

    public void checkAndUpdateCachedData(final JenkinsCoverityInstance coverityInstance, final Boolean updateNow) {
        boolean forceUpdate = false;
        if (null != updateNow) {
            forceUpdate = updateNow;
        }
        if (!retrievingNow) {
            retrievingNow = true;
            try {
                IntLogger logger = new PrintStreamIntLogger(System.out, LogLevel.DEBUG);
                Instant now = Instant.now();
                if (null == cachedData || null == lastTimeRetrieved) {
                    List<T> views = retrieveData(coverityInstance);
                    cachedData = views;
                    lastTimeRetrieved = now;
                } else if (null != lastTimeRetrieved) {
                    Duration timeLapsed = Duration.between(lastTimeRetrieved, now);
                    // only update the cached views every 5 minutes
                    if (forceUpdate || timeLapsed.getSeconds() > TimeUnit.MINUTES.toSeconds(CACHE_TIME)) {
                        cachedData = null;
                        List<T> views = retrieveData(coverityInstance);
                        cachedData = views;
                        lastTimeRetrieved = now;
                    }
                }
            } finally {
                retrievingNow = false;
            }
        }
    }

    public abstract List<T> retrieveData(JenkinsCoverityInstance coverityInstance);
}
