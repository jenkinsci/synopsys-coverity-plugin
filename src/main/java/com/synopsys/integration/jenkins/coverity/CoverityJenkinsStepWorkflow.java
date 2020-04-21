package com.synopsys.integration.jenkins.coverity;

import java.net.MalformedURLException;

import com.synopsys.integration.coverity.api.ws.configuration.ConfigurationService;
import com.synopsys.integration.coverity.api.ws.configuration.LicenseDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.VersionDataObj;
import com.synopsys.integration.coverity.config.CoverityHttpClient;
import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.jenkins.JenkinsVersionHelper;
import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.phonehome.request.CoverityPhoneHomeRequestFactory;
import com.synopsys.integration.phonehome.request.PhoneHomeRequestBody;
import com.synopsys.integration.phonehome.request.PhoneHomeRequestBodyBuilder;
import com.synopsys.integration.stepworkflow.jenkins.JenkinsStepWorkflow;

public abstract class CoverityJenkinsStepWorkflow<T> extends JenkinsStepWorkflow<T> {
    protected final WebServiceFactory webServiceFactory;

    public CoverityJenkinsStepWorkflow(final JenkinsIntLogger jenkinsIntLogger, final WebServiceFactory webServiceFactory) {
        super(jenkinsIntLogger);
        this.webServiceFactory = webServiceFactory;
    }

    protected PhoneHomeRequestBodyBuilder createPhoneHomeBuilder() {
        final CoverityPhoneHomeRequestFactory coverityPhoneHomeRequestFactory = new CoverityPhoneHomeRequestFactory("synopsys-coverity");
        final CoverityHttpClient coverityHttpClient = webServiceFactory.getCoverityHttpClient();
        String customerName;
        String cimVersion;

        try {
            final ConfigurationService configurationService = webServiceFactory.createConfigurationService();
            try {
                final LicenseDataObj licenseDataObj = configurationService.getLicenseConfiguration();
                customerName = licenseDataObj.getCustomer();
            } catch (final Exception e) {
                logger.trace("Couldn't get the Coverity customer id: " + e.getMessage());
                customerName = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            }

            try {
                final VersionDataObj versionDataObj = configurationService.getVersion();
                cimVersion = versionDataObj.getExternalVersion();
            } catch (final Exception e) {
                logger.trace("Couldn't get the Coverity version: " + e.getMessage());
                cimVersion = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            }
        } catch (final MalformedURLException e) {
            logger.trace("Couldn't get the Coverity customer id: " + e.getMessage());
            logger.trace("Couldn't get the Coverity version: " + e.getMessage());
            cimVersion = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            customerName = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
        }

        final String pluginVersion = JenkinsVersionHelper.getPluginVersion("synopsys-coverity").orElse(PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE);

        return coverityPhoneHomeRequestFactory.create(customerName, coverityHttpClient.getBaseUrl(), pluginVersion, cimVersion);
    }

}
