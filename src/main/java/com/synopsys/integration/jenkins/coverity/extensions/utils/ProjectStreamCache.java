/*
 * synopsys-coverity
 *
 * Copyright (c) 2022 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;

import com.synopsys.integration.coverity.api.ws.configuration.ConfigurationService;
import com.synopsys.integration.coverity.api.ws.configuration.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.ProjectFilterSpecDataObj;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.log.IntLogger;

public class ProjectStreamCache extends CoverityConnectDataCache<List<ProjectDataObj>> {
    public ProjectStreamCache(IntLogger logger) {
        super(logger);
    }

    @Override
    protected List<ProjectDataObj> getFreshData(WebServiceFactory webServiceFactory) {
        List<ProjectDataObj> projects = Collections.emptyList();
        try {
            logger.info("Attempting retrieval of Coverity Projects.");
            ConfigurationService configurationService = webServiceFactory.createConfigurationService();
            ProjectFilterSpecDataObj projectFilterSpecDataObj = new ProjectFilterSpecDataObj();
            projects = configurationService.getProjects(projectFilterSpecDataObj);
            logger.info("Completed retrieval of Coverity Projects.");
        } catch (MalformedURLException | CovRemoteServiceException_Exception e) {
            logger.error(e.getMessage());
            logger.trace("Stack trace:", e);
        }
        return projects;
    }

    @Override
    protected List<ProjectDataObj> getEmptyData() {
        return Collections.emptyList();
    }
}
