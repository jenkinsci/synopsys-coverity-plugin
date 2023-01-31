/*
 * synopsys-coverity
 *
 * Copyright (c) 2023 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import com.synopsys.integration.coverity.api.rest.ViewType;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.IntLogger;

public class IssueViewCache extends CoverityConnectDataCache<List<String>> {
    public IssueViewCache(IntLogger logger) {
        super(logger);
    }

    @Override
    protected List<String> getFreshData(WebServiceFactory webServiceFactory) {
        List<String> data = Collections.emptyList();

        try {
            logger.info("Attempting retrieval of Coverity Views.");
            ViewService viewService = webServiceFactory.createViewService();
            data = viewService.getAllViewsOfType(ViewType.ISSUES)
                       .stream()
                       .map(view -> view.name)
                       .filter(StringUtils::isNotBlank)
                       .collect(Collectors.toList());
            logger.info("Completed retrieval of Coverity Views.");
        } catch (IOException | IntegrationException e) {
            logger.error(e.getMessage());
            logger.trace("Stack trace:", e);
        }

        return data;
    }

    @Override
    protected List<String> getEmptyData() {
        return Collections.emptyList();
    }
}
