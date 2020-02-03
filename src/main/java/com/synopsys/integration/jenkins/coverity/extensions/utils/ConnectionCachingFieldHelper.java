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
