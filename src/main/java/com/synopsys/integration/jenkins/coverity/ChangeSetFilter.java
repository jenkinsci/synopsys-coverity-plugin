/*
 * synopsys-coverity
 *
 * Copyright (c) 2024 Synopsys, Inc.
 *
 * Use subject to the terms and conditions of the Synopsys End User Software License and Maintenance Agreement. All rights reserved worldwide.
 */
package com.synopsys.integration.jenkins.coverity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Predicate;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hudson.scm.ChangeLogSet;

public class ChangeSetFilter {
    private final Logger logger = LoggerFactory.getLogger(ChangeSetFilter.class);
    private final Set<String> excludedSet;
    private final Set<String> includedSet;

    /**
     * Provide a comma-separated list of names to exclude and/or a comma-separated list of names to include. Exclusion rules always win.
     */
    public ChangeSetFilter(final String toExclude, final String toInclude) {
        this(createSetFromString(toExclude), createSetFromString(toInclude));
    }

    private ChangeSetFilter(final Set<String> excludedSet, final Set<String> includedSet) {
        this.excludedSet = excludedSet;
        this.includedSet = includedSet;
    }

    public static ChangeSetFilter createAcceptAllFilter() {
        return new ChangeSetFilter(Collections.emptySet(), Collections.emptySet());
    }

    private static Set<String> createSetFromString(final String s) {
        final Set<String> set = new HashSet<>();
        final StringTokenizer stringTokenizer = new StringTokenizer(StringUtils.trimToEmpty(s), ",");
        while (stringTokenizer.hasMoreTokens()) {
            set.add(StringUtils.trimToEmpty(stringTokenizer.nextToken()));
        }
        return set;
    }

    public boolean shouldInclude(final ChangeLogSet.AffectedFile affectedFile) {
        final String affectedFilePath = affectedFile.getPath();
        final String affectedEditType = affectedFile.getEditType().getName();

        final boolean shouldInclude = shouldInclude(affectedFilePath);
        if (shouldInclude) {
            logger.debug(String.format("Type: %s File Path: %s Included in change set", affectedEditType, affectedFilePath));
        } else {
            logger.debug(String.format("Type: %s File Path: %s Excluded from change set", affectedEditType, affectedFilePath));
        }

        return shouldInclude;
    }

    private boolean shouldInclude(final String filePath) {
        // ChangeLogSet.AffectedFile getPath is normalized to use the / separator
        final String fileName;
        if (filePath.contains("/")) {
            fileName = filePath.substring(filePath.lastIndexOf("/"));
        } else {
            fileName = filePath;
        }

        final Predicate<String> caseInsensitiveWildcardMatch = pattern -> FilenameUtils.wildcardMatch(fileName, pattern, IOCase.INSENSITIVE);

        final boolean excluded = excludedSet.stream().anyMatch(caseInsensitiveWildcardMatch);
        final boolean included = includedSet.isEmpty() || includedSet.stream().anyMatch(caseInsensitiveWildcardMatch);

        return included && !excluded;
    }

}
