package com.synopsys.integration.jenkins.coverity.extensions.utils;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.coverity.ws.v9.ConfigurationService;
import com.synopsys.integration.coverity.ws.v9.CovRemoteServiceException_Exception;
import com.synopsys.integration.coverity.ws.v9.ProjectDataObj;
import com.synopsys.integration.coverity.ws.v9.ProjectFilterSpecDataObj;
import com.synopsys.integration.log.IntLogger;

public class ProjectStreamCache extends CoverityConnectDataCache<List<ProjectDataObj>> {
    public ProjectStreamCache(final IntLogger logger) {
        super(logger);
    }

    @Override
    protected List<ProjectDataObj> getFreshData(final WebServiceFactory webServiceFactory) {
        List<ProjectDataObj> projects = Collections.emptyList();
        try {
            logger.info("Attempting retrieval of Coverity Projects.");
            final ConfigurationService configurationService = webServiceFactory.createConfigurationService();
            final ProjectFilterSpecDataObj projectFilterSpecDataObj = new ProjectFilterSpecDataObj();
            projects = configurationService.getProjects(projectFilterSpecDataObj);
            logger.info("Completed retrieval of Coverity Projects.");
        } catch (MalformedURLException | CovRemoteServiceException_Exception e) {
            logger.error(e.getMessage());
            logger.trace("Stack trace:", e);
        }
        return projects;
    }
}
