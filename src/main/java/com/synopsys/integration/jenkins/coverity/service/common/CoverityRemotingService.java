package com.synopsys.integration.jenkins.coverity.service.common;

import com.synopsys.integration.jenkins.service.JenkinsRemotingService;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;

public class CoverityRemotingService extends JenkinsRemotingService {
    private final Launcher launcher;
    private final FilePath workspace;
    private final TaskListener listener;

    public CoverityRemotingService(Launcher launcher, FilePath workspace, TaskListener listener) {
        super(launcher, workspace, listener);
        this.launcher = launcher;
        this.workspace = workspace;
        this.listener = listener;
    }

    public Launcher getLauncher() {
        return launcher;
    }

    public FilePath getWorkspace() {
        return workspace;
    }

    public FilePath getRemoteFilePath(String filePath) {
        return new FilePath(getVirtualChannel(), filePath);
    }

    private VirtualChannel getVirtualChannel() {
        VirtualChannel virtualChannel = launcher.getChannel();
        if (virtualChannel == null) {
            // It's rare for the launcher's channel to be null, but if it is we can fall back to the workspace. We rely on the launcher first and foremost because that's how we can run on docker agents. --rotte JUL 2020
            virtualChannel = workspace.getChannel();
        }

        return virtualChannel;
    }

}
