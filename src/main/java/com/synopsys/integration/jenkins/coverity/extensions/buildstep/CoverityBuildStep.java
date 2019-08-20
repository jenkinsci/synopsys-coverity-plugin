/**
 * synopsys-coverity
 *
 * Copyright (c) 2019 Synopsys, Inc.
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
package com.synopsys.integration.jenkins.coverity.extensions.buildstep;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.synopsys.integration.coverity.ws.WebServiceFactory;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.jenkins.coverity.GlobalValueHelper;
import com.synopsys.integration.jenkins.coverity.JenkinsCoverityLogger;
import com.synopsys.integration.jenkins.coverity.extensions.CheckForIssuesInView;
import com.synopsys.integration.jenkins.coverity.extensions.CleanUpAction;
import com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType;
import com.synopsys.integration.jenkins.coverity.extensions.CoverityCaptureType;
import com.synopsys.integration.jenkins.coverity.extensions.OnCommandFailure;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance;
import com.synopsys.integration.jenkins.coverity.extensions.global.CoverityGlobalConfig;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValidator;
import com.synopsys.integration.jenkins.coverity.extensions.utils.CommonFieldValueProvider;
import com.synopsys.integration.jenkins.coverity.substeps.GetCoverityCommands;
import com.synopsys.integration.jenkins.coverity.substeps.GetIssuesInView;
import com.synopsys.integration.jenkins.coverity.substeps.ProcessChangeLogSets;
import com.synopsys.integration.jenkins.coverity.substeps.RunCoverityCommands;
import com.synopsys.integration.jenkins.coverity.substeps.SetUpCoverityEnvironment;
import com.synopsys.integration.jenkins.coverity.substeps.remote.CoverityRemoteInstallationValidator;
import com.synopsys.integration.phonehome.PhoneHomeResponse;
import com.synopsys.integration.util.IntEnvironmentVariables;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class CoverityBuildStep extends Builder {
	public static final String FAILURE_MESSAGE = "Unable to perform Synopsys Coverity static analysis: ";

	private final OnCommandFailure onCommandFailure;
	private final CoverityRunConfiguration coverityRunConfiguration;
	private final String projectName;
	private final String streamName;
	private final String createProjectName;
	private final String createStreamName;
	private final CheckForIssuesInView checkForIssuesInView;
	private final ConfigureChangeSetPatterns configureChangeSetPatterns;
	private final String coverityInstanceUrl;
	private CleanUpAction cleanUpAction;
	
	private static final Logger logger = Logger.getLogger(CoverityBuildStep.class.getName());

	@DataBoundConstructor
	public CoverityBuildStep(final String coverityInstanceUrl, final String onCommandFailure, final String projectName, final String streamName, final CheckForIssuesInView checkForIssuesInView,
			final ConfigureChangeSetPatterns configureChangeSetPatterns, final CoverityRunConfiguration coverityRunConfiguration, final String createProjectName, final String createStreamName) {
		this.coverityInstanceUrl = coverityInstanceUrl;
		this.projectName = projectName;
		this.streamName = streamName;
		this.checkForIssuesInView = checkForIssuesInView;
		this.configureChangeSetPatterns = configureChangeSetPatterns;
		this.coverityRunConfiguration = coverityRunConfiguration;
		this.createProjectName = createProjectName;
		this.createStreamName = createStreamName;

		//TODO: Replace constructor string value with enum in 3.0.0
		this.onCommandFailure = OnCommandFailure.valueOf(onCommandFailure);
	}

	public CleanUpAction getCleanUpAction() {
		return cleanUpAction;
	}

	// TODO: Add to constructor in 3.0.0
	@DataBoundSetter
	public void setCleanUpAction(final CleanUpAction cleanUpAction) {
		this.cleanUpAction = cleanUpAction;
	}

	public String getCoverityInstanceUrl() {
		return coverityInstanceUrl;
	}

	public OnCommandFailure getOnCommandFailure() {
		return onCommandFailure;
	}

	public ConfigureChangeSetPatterns getConfigureChangeSetPatterns() {
		return configureChangeSetPatterns;
	}

	public CheckForIssuesInView getCheckForIssuesInView() {
		return checkForIssuesInView;
	}

	public String getProjectName() {
		return projectName;
	}

	public String getStreamName() {
		return streamName;
	}

	public String getCreateProjectName() {
		return createProjectName;
	}

	public String getCreateStreamName() {
		return createStreamName;
	}

	public CoverityRunConfiguration getCoverityRunConfiguration() {
		return coverityRunConfiguration;
	}

	public CoverityRunConfiguration getDefaultCoverityRunConfiguration() {
		final SimpleCoverityRunConfiguration defaultCoverityRunConfiguration = new SimpleCoverityRunConfiguration(CoverityAnalysisType.COV_ANALYZE, "", null);
		defaultCoverityRunConfiguration.setCoverityCaptureType(CoverityCaptureType.COV_BUILD);
		defaultCoverityRunConfiguration.setChangeSetAnalysisThreshold(100);
		return defaultCoverityRunConfiguration;
		// TODO: Uncomment the following in 3.0.0
		// return new SimpleCoverityRunConfiguration(CoverityCaptureType.COV_BUILD, CoverityAnalysisType.COV_ANALYZE, 100, null);
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
		final IntEnvironmentVariables intEnvironmentVariables = new IntEnvironmentVariables();
		intEnvironmentVariables.putAll(build.getEnvironment(listener));
		final JenkinsCoverityLogger logger = JenkinsCoverityLogger.initializeLogger(listener, intEnvironmentVariables);
		final PhoneHomeResponse phoneHomeResponse = GlobalValueHelper.phoneHome(logger, coverityInstanceUrl);

		try {
			final FilePath workingDirectory = getWorkingDirectory(build);

			if (Result.ABORTED.equals(build.getResult())) {
				throw new AbortException(FAILURE_MESSAGE + "The build was aborted.");
			}

			final Node node = build.getBuiltOn();
			if (node == null) {
				throw new AbortException(FAILURE_MESSAGE + "Could not access node.");
			}

			final VirtualChannel virtualChannel = node.getChannel();
			if (virtualChannel == null) {
				throw new AbortException(FAILURE_MESSAGE + "Configured node \"" + node.getDisplayName() + "\" is either not connected or offline.");
			}

			String viewName = StringUtils.EMPTY;
			if (checkForIssuesInView != null && checkForIssuesInView.getViewName() != null) {
				viewName = checkForIssuesInView.getViewName();
			}

			final ProcessChangeLogSets processChangeLogSets = new ProcessChangeLogSets(logger, build.getChangeSets(), configureChangeSetPatterns);
			final List<String> changeSet = processChangeLogSets.computeChangeSet();

			final Boolean isSimpleMode = CoverityRunConfiguration.RunConfigurationType.SIMPLE.equals(coverityRunConfiguration.getRunConFigurationType());
			final CoverityRemoteInstallationValidator coverityRemoteInstallationValidator = new CoverityRemoteInstallationValidator(logger, isSimpleMode, (HashMap<String, String>) intEnvironmentVariables.getVariables());
			final String pathToCoverityToolHome = virtualChannel.call(coverityRemoteInstallationValidator);

			final SetUpCoverityEnvironment setUpCoverityEnvironment = new SetUpCoverityEnvironment(logger, intEnvironmentVariables, pathToCoverityToolHome, coverityInstanceUrl, projectName, streamName, viewName, changeSet);
			setUpCoverityEnvironment.setUpCoverityEnvironment();

			final GetCoverityCommands getCoverityCommands = new GetCoverityCommands(logger, intEnvironmentVariables, coverityRunConfiguration);
			final List<List<String>> commands = getCoverityCommands.getCoverityCommands();

			final RunCoverityCommands runCoverityCommands = new RunCoverityCommands(logger, intEnvironmentVariables, workingDirectory.getRemote(), commands, onCommandFailure, virtualChannel);
			runCoverityCommands.runCoverityCommands();

			if (checkForIssuesInView != null) {
				if (build.getResult() != null && build.getResult().isWorseThan(Result.SUCCESS)) {
					throw new AbortException("Skipping the Synopsys Coverity Check for Issues in View step because the build was not successful.");
				}
				final WebServiceFactory webServiceFactory = GlobalValueHelper.createWebServiceFactoryFromUrl(logger, coverityInstanceUrl);
				final GetIssuesInView getIssuesInView = new GetIssuesInView(logger, webServiceFactory, projectName, viewName);

				logger.alwaysLog("Checking for issues in view");
				logger.alwaysLog("-- Build state for issues in the view: " + checkForIssuesInView.getBuildStatusForIssues().getDisplayName());
				logger.alwaysLog("-- Coverity project name: " + projectName);
				logger.alwaysLog("-- Coverity view name: " + viewName);

				final int defectCount = getIssuesInView.getTotalIssuesInView();

				if (defectCount > 0) {
					logger.alwaysLog(String.format("[Coverity] Found %s issues in view.", defectCount));
					logger.alwaysLog("Setting build status to " + checkForIssuesInView.getBuildStatusForIssues().getResult().toString());
					build.setResult(checkForIssuesInView.getBuildStatusForIssues().getResult());
				}
			}

			if (CleanUpAction.DELETE_INTERMEDIATE_DIRECTORY.equals(cleanUpAction)) {
				final FilePath intermediateDirectory = new FilePath(workingDirectory, "idir");
				intermediateDirectory.deleteRecursive();
			}

		} catch (final InterruptedException e) {
			logger.error("[ERROR] Synopsys Coverity thread was interrupted.", e);
			build.setResult(Result.ABORTED);
			Thread.currentThread().interrupt();
			return false;
		} catch (final IntegrationException e) {
			logger.error("[ERROR] " + e.getMessage());
			logger.debug(e.getMessage(), e);
			build.setResult(Result.FAILURE);
			return false;
		} catch (final Exception e) {
			logger.error("[ERROR] " + e.getMessage());
			logger.debug(e.getMessage(), e);
			build.setResult(Result.UNSTABLE);
			return false;
		} finally {
			if (null != phoneHomeResponse) {
				phoneHomeResponse.getImmediateResult();
			}
		}

		return true;
	}

	private FilePath getWorkingDirectory(final AbstractBuild<?, ?> build) throws AbortException {
		final FilePath workingDirectory;
		if (build.getWorkspace() == null) {
			// might be using custom workspace
			final Node node = build.getBuiltOn();
			if (node != null) {
				workingDirectory = new FilePath(node.getChannel(), build.getProject().getCustomWorkspace());
			} else {
				throw new AbortException(FAILURE_MESSAGE + "Could not determine working directory");
			}
		} else {
			workingDirectory = build.getWorkspace();
		}
		return workingDirectory;
	}

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Builder> implements Serializable {
		private static final long serialVersionUID = -7146909743946288527L;
		private final CommonFieldValueProvider commonFieldValueProvider;
		private final CommonFieldValidator commonFieldValidator;

		public DescriptorImpl() {
			super(CoverityBuildStep.class);
			load();
			commonFieldValidator = new CommonFieldValidator();
			commonFieldValueProvider = new CommonFieldValueProvider();
		}

		@Override
		@Nonnull
		public String getDisplayName() {
			return "Execute Synopsys Coverity static analysis";
		}

		@Override
		public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
			return true;
		}

		public ListBoxModel doFillCoverityInstanceUrlItems() {
			return commonFieldValueProvider.doFillCoverityInstanceUrlItems();
		}

		public FormValidation doCheckCoverityInstanceUrl(@QueryParameter("coverityInstanceUrl") final String coverityInstanceUrl) {
			return commonFieldValidator.doCheckCoverityInstanceUrl(coverityInstanceUrl);
		}

		public ListBoxModel doFillProjectNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("updateNow") boolean updateNow) {
			return commonFieldValueProvider.doFillProjectNameItems(coverityInstanceUrl, updateNow);
		}

		public FormValidation doCheckProjectName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
			return commonFieldValidator.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
		}

		public ListBoxModel doFillStreamNameItems(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl, final @QueryParameter("projectName") String projectName, final @QueryParameter("updateNow") boolean updateNow) {
			return commonFieldValueProvider.doFillStreamNameItems(coverityInstanceUrl, projectName, updateNow);
		}

		public FormValidation doCheckStreamName(final @QueryParameter("coverityInstanceUrl") String coverityInstanceUrl) {
			return commonFieldValidator.doCheckCoverityInstanceUrlIgnoreMessage(coverityInstanceUrl);
		}

		public ListBoxModel doFillOnCommandFailureItems() {
			return CommonFieldValueProvider.getListBoxModelOf(OnCommandFailure.values());
		}

		public ListBoxModel doFillCleanUpActionItems() {
			return CommonFieldValueProvider.getListBoxModelOf(CleanUpAction.values());
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

}
