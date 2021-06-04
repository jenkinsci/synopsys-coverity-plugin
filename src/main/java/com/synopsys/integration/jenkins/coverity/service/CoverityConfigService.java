package com.synopsys.integration.jenkins.coverity.service;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.synopsys.integration.coverity.config.CoverityServerConfig;
import com.synopsys.integration.coverity.exception.CoverityIntegrationException;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityGlobalConfig;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.service.JenkinsConfigService;

import hudson.model.TaskListener;

public class CoverityConfigService {
    private final JenkinsIntLogger logger;
    private final JenkinsConfigService jenkinsConfigService;

    public static CoverityConfigService fromListener(TaskListener listener) {
        JenkinsIntLogger logger = new JenkinsIntLogger(listener);

        // CoverityConfigService doesn't use the API of JenkinsConfigService that requires envVars or the node we're executing on
        JenkinsConfigService jenkinsConfigService = new JenkinsConfigService(null, null, listener);

        return new CoverityConfigService(logger, jenkinsConfigService);
    }

    public CoverityConfigService(JenkinsIntLogger logger,  JenkinsConfigService jenkinsConfigService) {
        this.logger = logger;
        this.jenkinsConfigService = jenkinsConfigService;
    }

    public CoverityConnectInstance getCoverityInstanceOrAbort(String coverityServerUrl) throws CoverityJenkinsAbortException {
        CoverityGlobalConfig coverityGlobalConfig = jenkinsConfigService
                                                        .getGlobalConfiguration(CoverityGlobalConfig.class)
                                                        .orElseThrow(() -> new CoverityJenkinsAbortException("No Coverity global configuration detected in the Jenkins system configuration."));

        List<CoverityConnectInstance> coverityConnectInstances = coverityGlobalConfig.getCoverityConnectInstances();
        if (coverityConnectInstances.isEmpty()) {
            throw new CoverityJenkinsAbortException("No Coverity connect instances are configured in the Jenkins system configuration.");
        }

        return coverityConnectInstances.stream()
                   .filter(instance -> instance.getUrl().equals(coverityServerUrl))
                   .findFirst()
                   .orElseThrow(
                       () -> new CoverityJenkinsAbortException("No Coverity conect instance with the url '" + coverityServerUrl + "' could be  found in the Jenkins system configuration."));
    }

    public WebServiceFactory getWebServiceFactoryFromUrl(String coverityServerUrl, String credentialsId) throws CoverityJenkinsAbortException {
        CoverityConnectInstance coverityConnectInstance = this.getCoverityInstanceOrAbort(coverityServerUrl);

        CoverityServerConfig coverityServerConfig = coverityConnectInstance.getCoverityServerConfig(logger, credentialsId);
        WebServiceFactory webServiceFactory = coverityServerConfig.createWebServiceFactory(logger);
        try {
            webServiceFactory.connect();
        } catch (CoverityIntegrationException e) {
            throw new CoverityJenkinsAbortException("An error occurred when connecting to Coverity Connect. Please ensure that you can connect properly.");
        } catch (MalformedURLException e) {
            throw CoverityJenkinsAbortException.fromMalformedUrlException(coverityServerUrl + WebServiceFactory.CONFIGURATION_SERVICE_V9_WSDL, e);
        }

        return webServiceFactory;
    }

    public Optional<CoverityConnectInstance> getCoverityInstanceWithUrl(String coverityInstanceUrl) {
        Optional<CoverityGlobalConfig> coverityGlobalConfig = jenkinsConfigService.getGlobalConfiguration(CoverityGlobalConfig.class);
        if (!coverityGlobalConfig.isPresent()) {
            return Optional.empty();
        }

        List<CoverityConnectInstance> coverityInstances = coverityGlobalConfig.get().getCoverityConnectInstances();
        if (null == coverityInstances || coverityInstances.isEmpty()) {
            logger.error("[ERROR] No Coverity Connect instances are configured in the Jenkins system config.");
        } else {
            return coverityInstances.stream()
                       .filter(coverityInstance -> coverityInstance.getUrl().equals(coverityInstanceUrl))
                       .findFirst();
        }

        return Optional.empty();
    }

    public CoverityConnectInstance getCoverityInstanceWithUrlOrDie(String coverityInstanceUrl) throws CoverityIntegrationException {
        return getCoverityInstanceWithUrl(coverityInstanceUrl).orElseThrow(() -> new CoverityIntegrationException("No Coverity Connect instance is configured with the url " + coverityInstanceUrl));
    }

    public List<CoverityConnectInstance> getGlobalCoverityConnectInstances() {
        return jenkinsConfigService.getGlobalConfiguration(CoverityGlobalConfig.class)
                   .map(CoverityGlobalConfig::getCoverityConnectInstances)
                   .orElseGet(Collections::emptyList);
    }


}
