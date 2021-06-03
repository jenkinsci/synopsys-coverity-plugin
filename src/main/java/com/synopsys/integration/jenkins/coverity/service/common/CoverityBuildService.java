package com.synopsys.integration.jenkins.coverity.service.common;

import java.util.List;

import com.synopsys.integration.jenkins.extensions.JenkinsIntLogger;
import com.synopsys.integration.jenkins.service.JenkinsBuildService;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.scm.ChangeLogSet;

public class CoverityBuildService extends JenkinsBuildService {
    private final AbstractBuild<?, ?> build;

    public CoverityBuildService(JenkinsIntLogger logger, AbstractBuild<?, ?> build) {
        super(logger, build);
        this.build = build;
    }

    public AbstractBuild getBuild() {
        return build;
    }

    public List<ChangeLogSet<? extends ChangeLogSet.Entry>> getChangeLogSets() {
        return build.getChangeSets();
    }

    public void addAction(Action a) {
        build.addAction(a);
    }
}
