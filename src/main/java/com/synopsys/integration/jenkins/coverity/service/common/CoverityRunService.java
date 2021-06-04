package com.synopsys.integration.jenkins.coverity.service.common;

import hudson.model.Action;
import hudson.model.Run;

public class CoverityRunService {
    private final Run<?, ?> run;

    public CoverityRunService(Run<?, ?> run) {
        this.run = run;
    }

    public void addAction(Action a) {
        run.addAction(a);
    }

}
