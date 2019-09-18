package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.view.ViewService;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.log.IntLogger;

public class ViewCache extends CoverityConnectDataCache<List<String>> {
    public ViewCache(final IntLogger logger) {
        super(logger);
    }

    @Override
    protected List<String> getFreshData(final WebServiceFactory webServiceFactory) {
        List<String> data = Collections.emptyList();

        try {
            logger.info("Attempting retrieval of Coverity Views.");
            final ViewService viewService = webServiceFactory.createViewService();
            final Map<Long, String> viewMap = viewService.getViews();
            data = new ArrayList<>(viewMap.values());
            logger.info("Completed retrieval of Coverity Views.");
        } catch (IOException | IntegrationException e) {
            logger.error(e.getMessage());
            logger.trace("Stack trace:", e);
        }

        return data;
    }
}
