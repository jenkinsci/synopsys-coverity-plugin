/**
 * synopsys-coverity
 *
 * Copyright (c) 2019 Synopsys, Inc.
 * Portions Copyright 2019 Lexmark
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
package com.synopsys.integration.jenkins.coverity.extensions.wrap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.jenkins.PasswordMaskingOutputStream;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityGlobalConfig;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValidator;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValueProvider;
import com.synopsys.integration.jenkins.coverity.steps.CoverityEnvironmentStep;
import com.synopsys.integration.log.SilentIntLogger;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildWrapper;

public class CoverityEnvironmentWrapper extends SimpleBuildWrapper {
	private final String coverityInstanceUrl;
	private final String coverityPassphrase;
	private String projectName;
	private String streamName;
	private String viewName;
	private ConfigureChangeSetPatterns configureChangeSetPatterns;

	private static final Logger logger = Logger.getLogger(CoverityEnvironmentWrapper.class.getName());

	@DataBoundConstructor
	public CoverityEnvironmentWrapper(final String coverityInstanceUrl) {
		this.coverityInstanceUrl = coverityInstanceUrl;
		this.coverityPassphrase = GlobalValueHelper.getCoverityInstanceWithUrl(new SilentIntLogger(), coverityInstanceUrl)
				.flatMap(CoverityConnectInstance::getCoverityPassword)
				.orElse(StringUtils.EMPTY);
	}

	public String getCoverityInstanceUrl() {
		return coverityInstanceUrl;
	}

	public String getProjectName() {
		return projectName;
	}

	@DataBoundSetter
	public void setProjectName(@QueryParameter("projectName") final String projectName) {
		this.projectName = projectName;
	}

	public String getStreamName() {
		return streamName;
	}

	@DataBoundSetter
	public void setStreamName(@QueryParameter("streamName") final String streamName) {
		this.streamName = streamName;
	}

	public String getViewName() {
		return viewName;
	}

	@DataBoundSetter
	public void setViewName(@QueryParameter("viewName") final String viewName) {
		this.viewName = viewName;
	}

	public ConfigureChangeSetPatterns getConfigureChangeSetPatterns() {
		return configureChangeSetPatterns;
	}

	@DataBoundSetter
	public void setConfigureChangeSetPatterns(@QueryParameter("configureChangeSetPatterns") final ConfigureChangeSetPatterns configureChangeSetPatterns) {
		this.configureChangeSetPatterns = configureChangeSetPatterns;
	}

	@Override
	public void setUp(final Context context, final Run<?, ?> build, final FilePath workspace, final Launcher launcher, final TaskListener listener, final EnvVars initialEnvironment) throws IOException {
		final RunWrapper runWrapper = new RunWrapper(build, true);

		final Computer computer = workspace.toComputer();
		if (computer == null) {
			throw new AbortException("Could not access workspace's computer to inject Coverity environment.");
		}

		final Node node = computer.getNode();
		if (node == null) {
			throw new AbortException("Could not access workspace's node to inject Coverity environment.");
		}

		final List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets;
		try {
			changeSets = runWrapper.getChangeSets();
		} catch (Exception e) {
			throw new IOException(e);
		}

		final CoverityEnvironmentStep coverityEnvironmentStep = new CoverityEnvironmentStep(node, listener, initialEnvironment, workspace, build);
		final boolean setUpSuccessful = coverityEnvironmentStep.setUpCoverityEnvironment(changeSets, coverityInstanceUrl, projectName, streamName, viewName, configureChangeSetPatterns);

		if (!setUpSuccessful) {
			throw new AbortException("Could not successfully inject Coverity environment into build process.");
		}

		initialEnvironment.forEach(context::env);
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Override
	public ConsoleLogFilter createLoggerDecorator(@Nonnull final Run<?, ?> build) {
		return new FilterImpl(coverityPassphrase);
	}

	@Symbol("withCoverityEnvironment")
	@Extension
	public static final class DescriptorImpl extends BuildWrapperDescriptor {
		private final transient CommonFieldValidator commonFieldValidator;
		private final transient CommonFieldValueProvider commonFieldValueProvider;

		public DescriptorImpl() {
			super(CoverityEnvironmentWrapper.class);
			load();
			commonFieldValidator = new CommonFieldValidator();
			commonFieldValueProvider = new CommonFieldValueProvider();
		}

		public ListBoxModel doFillCoverityInstanceUrlItems(@QueryParameter("coverityInstanceUrl") final String coverityInstanceUrl) {
			return commonFieldValueProvider.doFillCoverityInstanceUrlItems(coverityInstanceUrl);
		}

		public FormValidation doCheckCoverityInstanceUrlItems(@QueryParameter("coverityInstanceUrl") final String coverityInstanceUrl) {
			return commonFieldValidator.doCheckCoverityInstanceUrl(coverityInstanceUrl);
		}

		public ListBoxModel doFillProjectNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName, final @QueryParameter("updateNow") boolean updateNow) {
			return commonFieldValueProvider.doFillProjectNameItems(coverityInstanceUrl, projectName, updateNow);
		}

		public FormValidation doCheckProjectName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
			return commonFieldValidator.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
		}

		public ListBoxModel doFillStreamNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName, final @QueryParameter("streamName") String streamName,
				final @QueryParameter("updateNow") boolean updateNow) {
			return commonFieldValueProvider.doFillStreamNameItems(coverityInstanceUrl, projectName, streamName, updateNow);
		}

		public FormValidation doCheckStreamName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
			return commonFieldValidator.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
		}

		public ListBoxModel doFillViewNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("viewName") String viewName, final @QueryParameter("updateNow") boolean updateNow) {
			return commonFieldValueProvider.doFillViewNameItems(coverityInstanceUrl, viewName, updateNow);
		}

		public FormValidation doCheckViewName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
			return commonFieldValidator.testConnectionIgnoreSuccessMessage(coverityInstanceUrl);
		}

		@Override
		public boolean isApplicable(final AbstractProject<?, ?> item) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Inject Coverity environment into the build process";
		}

		// A do function launched from config.jelly via a validation button to run commands to create the project and the
		// stream and to associate them together in the Coverity Platform server: 
		public FormValidation doCreateProject(@QueryParameter String createProjectName, @QueryParameter String createStreamName) throws IOException {           
			// Validate input values:
			if (createProjectName == null || createProjectName.equals("")) {
				return FormValidation.error("The Project Name value is missing. Please enter a value and try again.");
			} else if (createStreamName == null || createStreamName.equals("")) {
				return FormValidation.error("The Stream Name value is missing. Please enter a value and try again. Ex: " + createProjectName + "_stream");
			}

			String project = createProjectName.replace(" ", "_");
			String stream = createStreamName.replace(" ", "_");     

			CoverityGlobalConfig coverityGlobalConfig = new CoverityGlobalConfig();
			CoverityConnectInstance coverityConnectInstance = coverityGlobalConfig.getCoverityConnectInstances().get(0);

			String desktopAnalysis = coverityConnectInstance.getDesktopAnalysis();
			String coverityUrl = coverityConnectInstance.getCoverityURL().toString();
			String host = coverityUrl.substring(coverityUrl.indexOf("[") + 1, coverityUrl.indexOf("]"));
			String pattern = "^(http[s]?://www\\\\.|http[s]?://|www\\\\.)";
			host = host.replaceAll(pattern, "");
			String[] hostArray = host.split(":");
			host = hostArray[0];
			String port = hostArray[1];
			String covManagePath = coverityConnectInstance.getCovManagePath();
			if (!covManagePath.substring(covManagePath.length() - 1).equals("/")) {
				covManagePath += "/";
			}
			String user = coverityConnectInstance.getCoverityUsername().get();
			String password = coverityConnectInstance.getCoverityPassword().get();

			// Evaluate exit status from the runCommand and return the appropriate message to the jenkins build config form.
			// The Status from the three commands are combined and returned as a combined status:
			String errorLevel = "0";
			String returnMessage = "";  

			// Run command 1 of 4 - Get list of Coverity projects:
			String projectShowCmd = covManagePath + "cov-manage-im --host " + host + " --ssl --port " + port + " --user " + user + " --password " + password + " --mode projects --show";
			String[] showProjectRet = runCommand(projectShowCmd);

			if (!showProjectRet[1].equals(errorLevel)) {
				return FormValidation.error("An error occurred during the Show Coverity Projects: \n" + showProjectRet[0]);
			}

			//Check if the project already exists:
			String[] projectArray = showProjectRet[0].split(",");
			boolean projectFound = false;
			for (int index = 0; index < projectArray.length; index++){
				String checkValue = projectArray[index];
				checkValue = checkValue.trim();
				checkValue = checkValue.replaceAll("\n", "");
				if (checkValue.equals(project)){
					projectFound = true;
					logger.info("[INFO] Project Name Match Found: " + project);

					break;
				}
			}

			if (projectFound) {
				returnMessage = "Project already exists for " + project + ". No project addition required.";
				logger.info("[INFO] Project already exists for " + project + ". No project addition required.");

			} else {
				// Run command 2 of 4 - Add a new project to the Coverity Connect Instance:
				String createProjectCmd = covManagePath + "cov-manage-im --host " + host + " --ssl --port " + port + " --mode projects --add --set name:" + project + " --user " + user + " --password " + password;
				String[] createProjectRet = runCommand(createProjectCmd);

				if (!createProjectRet[1].equals(errorLevel)) {
					return FormValidation.error("An error occurred during the creation of the Coverity project " + project + "\n" + createProjectRet[0]);
				} 
				logger.info("[INFO] Coverity Project created: " + project);
				returnMessage = createProjectRet[0] + " - " + project;
			}

			String streamShowCmd = covManagePath + "cov-manage-im --host " + host + " --ssl --port " + port + " --user " + user + " --password " + password + " --mode streams --show";
			String[] checkStreamRet = runCommand(streamShowCmd);

			//Check if the stream already exists:
			String[] streamArray = checkStreamRet[0].split("\n");
			boolean streamFound = false;
			for (int index = 0; index < streamArray.length; index++){
				String[] streamData = streamArray[index].split(",");
				String checkValue = streamData[0];
				checkValue = checkValue.trim();
				if (checkValue.equals(stream)){
					streamFound = true;
					logger.info("[INFO] Stream Name Match Found: " + stream);

					break;
				}
			}

			if (streamFound) {
				returnMessage = returnMessage + "\n\nStream already exists for " + project + ". No stream addition required.";
				logger.info("[INFO] Stream already exists for " + project + ". No stream addition required.");

			} else {
				// Run command 3 of 4 - Add a new stream:
				String createStreamCmd = covManagePath + "cov-manage-im --host " + host + " --ssl --port " + port + " --mode streams --add --set desktopAnalysis:" + desktopAnalysis + " --set name:" + stream + " --set triage:\"Default Triage Store\"" + " --set lang:mixed --user " + user + " --password " + password;
				String[] createStreamRet = null;
				createStreamRet = runCommand(createStreamCmd);

				if (!createStreamRet[1].equals(errorLevel)) {
					returnMessage = returnMessage + "\n\nAn error occurred during the creation of the Coverity stream " + stream + "\n" + createStreamRet[0];

					return FormValidation.error(returnMessage);
				} 
				logger.info("[INFO] Coverity Stream created: " + createStreamRet[0]);
				returnMessage = returnMessage + "\n\n" + createStreamRet[0] + " - " + stream;   
			}

			// Run command 4 of 4 - Associate the Stream to the Project:
			String associateCmd = covManagePath + "cov-manage-im --host " + host + " --ssl --port " + port + " --mode projects --update --name " + project + " --insert stream:" + stream + " --user " + user + " --password " + password;          
			String[] associateRet = null;
			associateRet = runCommand(associateCmd);    

			if (!associateRet[1].equals(errorLevel)) {
				returnMessage = returnMessage + "\n\nAn error occurred during the association of the project-" + project + " and Stream-" + stream + "\n" + associateRet[0];

				return FormValidation.error(returnMessage);
			} 
			logger.info("[INFO] Coverity Stream associated to the Project: " + associateRet[0]);
			returnMessage = returnMessage + "\n\n" + associateRet[0] + " - Stream: " + stream + " associated to Project: " + project;

			return FormValidation.ok(returnMessage);
		}

		// Using ProcessBuilder to run the commands in a shell and return output and exit status:
		public static String[] runCommand(String command) {
			String[] returnValues = {"",""};
			String responseString;
			String returnCodeString;
			String shellCmd;
			String argument;
			String osType = osChecker(); 

			// Check the os type of the jenkins system running the command:
			if (osType.equals("win")) {
				shellCmd = ("cmd.exe");
				argument = "/c";
			} else {
				shellCmd = ("/bin/sh");
				argument = "-c";
			}

			ProcessBuilder processBuilder = new ProcessBuilder(shellCmd, argument, command);
			// Merge the error output with the standard output:
			processBuilder.redirectErrorStream(true);

			try {
				Process shell = processBuilder.start();
				// Capture the output from the shell:
				InputStream shellIn = shell.getInputStream();
				int returnCode = shell.waitFor();
				responseString = convertStreamToStr(shellIn);
				shellIn.close();
				// Convert return code to a string so it can be passed back as a string array:
				returnCodeString = Integer.toString(returnCode);
			} catch (IOException e) {
				returnValues[0] = "Error occurred while executing cov command: " + e.getMessage();
				returnValues[1] = "1";
				return returnValues;
			} catch (InterruptedException e) {
				returnValues[0] = "Error occurred while executing cov command: " + e.getMessage();
				returnValues[1] = "1";
				return returnValues;
			}
			returnValues[0] = responseString;
			returnValues[1] = returnCodeString;
			return returnValues;
		}

		// Convert the InputStream to a String. It will iterate until the Reader return equals -1 which means there's no more data to read.
		public static String convertStreamToStr(InputStream is) throws IOException {
			if (is != null) {
				Writer writer = new StringWriter();
				char[] buffer = new char[1024];
				try {
					Reader reader = new BufferedReader(new InputStreamReader(is,"UTF-8"));
					int n;
					while ((n = reader.read(buffer)) != -1) {
						writer.write(buffer, 0, n);
					}
				} finally {
					is.close();
				}

				return writer.toString();
			} else {
				return "no string output";
			}
		}

		// Determine the operating system type so to the proper command can be used:
		public static String osChecker() {
			String os = System.getProperty("os.name").toLowerCase();

			if (os.indexOf("win") >= 0) {
				return "win";
			} else {
				return "unix";
			}
		}
	}

	private static final class FilterImpl extends ConsoleLogFilter implements Serializable {
		private static final long serialVersionUID = 1787519634824445328L;
		private final String passwordToMask;

		public FilterImpl(final String passwordToMask) {
			this.passwordToMask = passwordToMask;
		}

		@Override
		public OutputStream decorateLogger(final Run ignored, final OutputStream logger) {
			return new PasswordMaskingOutputStream(logger, passwordToMask);
		}
	}

}
