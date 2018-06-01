/**
 * synopsys-coverity
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.synopsys.integration.coverity;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.blackducksoftware.integration.rest.proxy.ProxyInfo;
import com.blackducksoftware.integration.rest.proxy.ProxyInfoBuilder;
import com.blackducksoftware.integration.util.proxy.ProxyUtil;
import com.google.common.collect.Lists;

import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;

public class JenkinsProxyHelper {
    public ProxyInfo getProxyInfo(final String url, final String proxyHost, final int proxyPort, final String proxyUsername, final String proxyPassword, final String ignoredProxyHosts, final String ntlmDomain,
            final String ntlmWorkstation) {
        ProxyInfo proxyInfo = ProxyInfo.NO_PROXY_INFO;
        if (shouldUseProxy(ignoredProxyHosts, url)) {
            final ProxyInfoBuilder proxyInfoBuilder = new ProxyInfoBuilder();
            applyJenkinsProxy(proxyInfoBuilder, proxyHost, proxyPort, proxyUsername, proxyPassword, ntlmDomain);
            proxyInfo = proxyInfoBuilder.build();
        }
        return proxyInfo;
    }

    public ProxyInfo getProxyInfoFromJenkins(final String url) {
        ProxyInfo proxyInfo = ProxyInfo.NO_PROXY_INFO;
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            final ProxyConfiguration proxyConfig = jenkins.proxy;
            if (proxyConfig != null) {
                if (shouldUseProxy(proxyConfig.noProxyHost, url)) {
                    final ProxyInfoBuilder proxyInfoBuilder = new ProxyInfoBuilder();
                    applyJenkinsProxy(proxyInfoBuilder, proxyConfig.name, proxyConfig.port, proxyConfig.getUserName(), proxyConfig.getPassword());
                    proxyInfo = proxyInfoBuilder.build();
                }
            }
        }
        return proxyInfo;
    }

    private boolean shouldUseProxy(final String noProxyHosts, final String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        try {
            final URL actualURL = new URL(url);
            if (StringUtils.isBlank(noProxyHosts)) {
                return true;
            }
            final List<Pattern> noProxyHostPatterns = getNoProxyHostPatterns(noProxyHosts);
            return !ProxyUtil.shouldIgnoreHost(actualURL.getHost(), noProxyHostPatterns);
        } catch (final MalformedURLException e) {
            return false;
        }
    }

    private List<Pattern> getNoProxyHostPatterns(final String noProxyHosts) {
        final List<Pattern> noProxyHostPatterns = Lists.newArrayList();
        for (final String currentNoProxyHost : noProxyHosts.split("[ \t\n,|]+")) {
            if (currentNoProxyHost.length() == 0) {
                continue;
            }
            noProxyHostPatterns.add(Pattern.compile(currentNoProxyHost.replace(".", "\\.").replace("*", ".*")));
        }
        return noProxyHostPatterns;
    }

    private void applyJenkinsProxy(final ProxyInfoBuilder proxyInfoBuilder, final String proxyHost, final int proxyPort, final String proxyUsername, final String proxyPassword, final String ntlmDomain) {
        if (StringUtils.isNotBlank(proxyHost) && proxyPort >= 0) {
            proxyInfoBuilder.setHost(proxyHost);
            proxyInfoBuilder.setPort(proxyPort);
            proxyInfoBuilder.setUsername(proxyUsername);
            proxyInfoBuilder.setPassword(proxyPassword);
            proxyInfoBuilder.setNtlmDomain(ntlmDomain);
        }
    }

    private void applyJenkinsProxy(final ProxyInfoBuilder proxyInfoBuilder, final String proxyHost, final int proxyPort, final String proxyUsernameWithDomain, final String proxyPassword) {
        if (StringUtils.isNotBlank(proxyHost) && proxyPort >= 0) {
            proxyInfoBuilder.setHost(proxyHost);
            proxyInfoBuilder.setPort(proxyPort);
            final ProxyUsernameWrapper wrapper = parseProxyUsername(proxyUsernameWithDomain);
            proxyInfoBuilder.setUsername(wrapper.getProxyUsername());
            proxyInfoBuilder.setNtlmDomain(wrapper.getNtlmDomain());
            proxyInfoBuilder.setPassword(proxyPassword);
        }
    }

    private ProxyUsernameWrapper parseProxyUsername(final String proxyUsernameWithDomain) {
        if (StringUtils.isNotBlank(proxyUsernameWithDomain)) {
            if (proxyUsernameWithDomain.indexOf('\\') >= 0) {
                final String domain = proxyUsernameWithDomain.substring(0, proxyUsernameWithDomain.indexOf('\\'));
                final String user = proxyUsernameWithDomain.substring(proxyUsernameWithDomain.indexOf('\\') + 1);
                return new ProxyUsernameWrapper(user, domain);
            } else {
                return new ProxyUsernameWrapper(proxyUsernameWithDomain, null);
            }
        } else {
            return new ProxyUsernameWrapper(null, null);
        }
    }

    private class ProxyUsernameWrapper {
        private final String proxyUsername;
        private final String ntlmDomain;

        public ProxyUsernameWrapper(final String proxyUsername, final String ntlmDomain) {
            this.proxyUsername = proxyUsername;
            this.ntlmDomain = ntlmDomain;
        }

        public String getProxyUsername() {
            return this.proxyUsername;
        }

        public String getNtlmDomain() {
            return this.ntlmDomain;
        }
    }

}
