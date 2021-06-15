/*
 * synopsys-coverity
 *
 * Copyright (c) 2021 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
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

public class IssueViewFieldHelper extends ConnectionCachingFieldHelper<IssueViewCache> {
    public IssueViewFieldHelper(IntLogger logger) {
        super(logger, () -> new IssueViewCache(logger));
    }

    public ListBoxModel getViewNamesForListBox(String coverityConnectUrl, Boolean overrideDefaultCredentials, String credentialsId) throws InterruptedException {
        try {
            return getViews(coverityConnectUrl, overrideDefaultCredentials, credentialsId).stream()
                       .filter(StringUtils::isNotBlank)
                       .map(this::wrapAsListBoxModelOption)
                       .collect(Collectors.toCollection(ListBoxModel::new));
        } catch (CoverityIntegrationException ignored) {
            // Form validation will display this exception as an error, so we can safely ignore it
            return new ListBoxModel();
        }
    }

    private List<String> getViews(String coverityConnectUrl, Boolean overrideDefaultCredentials, String credentialsId) throws CoverityIntegrationException, InterruptedException {
        CoverityConnectInstance coverityConnectInstance = GlobalValueHelper.getCoverityInstanceWithUrlOrDie(logger, coverityConnectUrl);
        if (Boolean.TRUE.equals(overrideDefaultCredentials)) {
            IssueViewCache issueViewCache = getCache(coverityConnectUrl, credentialsId);
            return issueViewCache.getData(coverityConnectInstance, credentialsId);
        } else {
            IssueViewCache issueViewCache = getCache(coverityConnectUrl, coverityConnectInstance.getCredentialId());
            return issueViewCache.getData(coverityConnectInstance, coverityConnectInstance.getCredentialId());
        }
    }

}
