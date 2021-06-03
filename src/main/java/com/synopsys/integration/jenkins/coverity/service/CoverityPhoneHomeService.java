package com.synopsys.integration.jenkins.coverity.service;

import java.net.MalformedURLException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.impl.client.HttpClientBuilder;

import com.google.gson.Gson;
import com.synopsys.integration.coverity.api.ws.configuration.ConfigurationService;
import com.synopsys.integration.coverity.api.ws.configuration.LicenseDataObj;
import com.synopsys.integration.coverity.api.ws.configuration.VersionDataObj;
import com.synopsys.integration.jenkins.coverity.exception.CoverityJenkinsAbortException;
import com.synopsys.integration.jenkins.wrapper.JenkinsVersionHelper;
import com.synopsys.integration.log.IntLogger;
import com.synopsys.integration.phonehome.PhoneHomeClient;
import com.synopsys.integration.phonehome.PhoneHomeResponse;
import com.synopsys.integration.phonehome.PhoneHomeService;
import com.synopsys.integration.phonehome.request.PhoneHomeRequestBody;
import com.synopsys.integration.phonehome.request.PhoneHomeRequestBodyBuilder;

public class CoverityPhoneHomeService {
    private final IntLogger logger;
    private final JenkinsVersionHelper jenkinsVersionHelper;
    private final CoverityConfigService coverityConfigService;

    public CoverityPhoneHomeService(IntLogger logger, JenkinsVersionHelper jenkinsVersionHelper, CoverityConfigService coverityConfigService) {
        this.logger = logger;
        this.jenkinsVersionHelper = jenkinsVersionHelper;
        this.coverityConfigService = coverityConfigService;
    }

    public Optional<PhoneHomeResponse> phoneHome(String coverityInstanceUrl, String credentialsId) {
        try {
            HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
            Gson gson = new Gson();
            PhoneHomeClient phoneHomeClient = new PhoneHomeClient(logger, httpClientBuilder, gson);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            PhoneHomeService phoneHomeService = PhoneHomeService.createAsynchronousPhoneHomeService(logger, phoneHomeClient, executor);

            PhoneHomeRequestBodyBuilder phoneHomeRequestBodyBuilder = this.createPhoneHomeBuilder(coverityInstanceUrl, credentialsId);

            jenkinsVersionHelper.getJenkinsVersion()
                .ifPresent(jenkinsVersionString -> phoneHomeRequestBodyBuilder.addToMetaData("jenkins.version", jenkinsVersionString));
            PhoneHomeRequestBody phoneHomeRequestBody = phoneHomeRequestBodyBuilder.build();

            return Optional.ofNullable(phoneHomeService.phoneHome(phoneHomeRequestBody));
        } catch (Exception e) {
            logger.trace("Phone home failed due to an unexpected exception:", e);
        }

        return Optional.empty();
    }

    public PhoneHomeRequestBodyBuilder createPhoneHomeBuilder(String coverityInstanceUrl, String credentialsId) {
        String customerName;
        String cimVersion;

        try {
            ConfigurationService configurationService = coverityConfigService.getWebServiceFactoryFromUrl(coverityInstanceUrl, credentialsId).createConfigurationService();
            try {
                LicenseDataObj licenseDataObj = configurationService.getLicenseConfiguration();
                customerName = licenseDataObj.getCustomer();
            } catch (Exception e) {
                logger.trace("Couldn't get the Coverity customer id: " + e.getMessage());
                customerName = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            }

            try {
                VersionDataObj versionDataObj = configurationService.getVersion();
                cimVersion = versionDataObj.getExternalVersion();
            } catch (Exception e) {
                logger.trace("Couldn't get the Coverity version: " + e.getMessage());
                cimVersion = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            }
        } catch (MalformedURLException | CoverityJenkinsAbortException e) {
            logger.trace("Couldn't get the Coverity customer id: " + e.getMessage());
            logger.trace("Couldn't get the Coverity version: " + e.getMessage());
            cimVersion = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
            customerName = PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE;
        }

        String pluginVersion = jenkinsVersionHelper.getPluginVersion("synopsys-coverity").orElse(PhoneHomeRequestBody.UNKNOWN_FIELD_VALUE);

        return PhoneHomeRequestBodyBuilder.createForCoverity("synopsys-coverity", customerName, coverityInstanceUrl, pluginVersion, cimVersion);
    }
}
