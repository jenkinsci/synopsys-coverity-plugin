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
