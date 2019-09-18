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

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.log.IntLogger;

import hudson.util.ListBoxModel;

public class ViewFieldHelper extends ConnectionCachingFieldHelper<ViewCache> {
    public ViewFieldHelper(final IntLogger logger) {
        super(logger, () -> new ViewCache(logger));
    }

    public ListBoxModel getViewNamesForListBox(final String coverityConnectUrl) throws InterruptedException {
        try {
            return getViews(coverityConnectUrl).stream()
                       .filter(StringUtils::isNotBlank)
                       .map(this::wrapAsListBoxModelOption)
                       .collect(Collectors.toCollection(ListBoxModel::new));
        } catch (final CoverityIntegrationException ignored) {
            // Form validation will display this exception as an error, so we can safely ignore it
            return new ListBoxModel();
        }
    }

    private List<String> getViews(final String coverityConnectUrl) throws CoverityIntegrationException, InterruptedException {
        final CoverityConnectInstance coverityConnectInstance = GlobalValueHelper.getCoverityInstanceWithUrlOrDie(logger, coverityConnectUrl);
        final ViewCache projectStreamCache = getCache(coverityConnectUrl);
        return projectStreamCache.getData(coverityConnectInstance);
    }

}
