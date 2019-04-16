import hudson.diagnosis.OldDataMonitor;
import hudson.util.VersionNumber;

start = System.currentTimeMillis()

jenkins = Jenkins.getInstance()
plugin = jenkins.getPluginManager().getPlugins().find { it.getShortName() == 'synopsys-coverity' }

if (plugin == null || !plugin.isActive() || plugin.isOlderThan(new VersionNumber('2.0.0'))) {
  System.err.println('Version 2.0.0 or later of Synopsys Coverity for Jenkins is either not installed or not activated.')
  System.err.println('Please upgrade and activate version 2.0.0 or later before running this script.')
  return
}

coverityGlobalConfig = com.synopsys.integration.jenkins.coverity.GlobalValueHelper.getCoverityGlobalConfig()
oldGlobalConfigXmlPath = new FilePath(jenkins.getRootPath(), 'com.synopsys.integration.coverity.freestyle.CoverityBuildStep.xml')
oldGlobalConfigUrl = null

if (oldGlobalConfigXmlPath && oldGlobalConfigXmlPath.exists()) {
    print('Attempting to migrate Synopsys Coverity global config... ')
    try {
        oldGlobalConfig = new XmlSlurper()
        						.parse(oldGlobalConfigXmlPath.read())

        oldGlobalConfigUrl = oldGlobalConfig.coverityInstance.url.text()
        coverityConnectInstance = new com.synopsys.integration.jenkins.coverity.extensions.global.CoverityConnectInstance(oldGlobalConfigUrl, oldGlobalConfig.coverityInstance.credentialId.text())
        coverityGlobalConfig.getCoverityConnectInstances().add(coverityConnectInstance)
        coverityGlobalConfig.save()
        oldGlobalConfigXmlPath.delete()
        print('migrated successfully.')
    } catch (Exception e) {
        System.err.print("migration failed because ${e.getMessage()}.")
        // Uncomment the following line to debug
        // e.printStackTrace()
    }
    println('')
}

oldDataMonitor = OldDataMonitor.get(jenkins);
items = null
if (oldDataMonitor != null && oldDataMonitor.isActivated()) {
    // If possible, we use the OldDataMonitor so we don't have to iterate through all items (jobs, views, etc.)
    items = oldDataMonitor.getData().keySet()
} else {
    // But if that's not available, we fall back to iterating through all items
    items = jenkins.getItems()
}

// If performance is an issue, you can comment this line out-- this is just to make the migration output prettier
items = items.sort{it.getFullName()}

builder = new StringBuilder()
for (item in items) {
  // Items can be many things-- only FreeStyle jobs are migratable
  if (item instanceof FreeStyleProject) {
	  configXml = item.getConfigFile().getFile();
      oldCoverityConfig = new XmlSlurper()
                        .parse(configXml)
                        .'**'
                        .find { it.name() == 'com.synopsys.integration.coverity.freestyle.CoverityBuildStep' }

      if (oldCoverityConfig) {
        builder.append("Attempting to migrate ${item.getFullName()}... ")
        try {
            onCommandFailure = oldCoverityConfig.onCommandFailure.text()
            projectName = oldCoverityConfig.projectName.text()
            streamName = oldCoverityConfig.streamName.text()

            coverityInstanceUrl = ""
            if (oldGlobalConfigUrl) {
                coverityInstanceUrl = oldGlobalConfigUrl
            } else if (!coverityGlobalConfig.getCoverityConnectInstances().isEmpty()){
                coverityInstanceUrl = coverityGlobalConfig.getCoverityConnectInstances().get(0).getUrl()
            }

            checkForIssuesInViewObj = null
            if (Boolean.valueOf(oldCoverityConfig.checkForIssuesInView.text())) {
                viewName = oldCoverityConfig.viewName.text()
                buildStatusForIssues = oldCoverityConfig.buildStatusForIssues.text()

                checkForIssuesInViewObj = new com.synopsys.integration.jenkins.coverity.extensions.CheckForIssuesInView(viewName, buildStatusForIssues)
            }

            configureChangeSetPatternsObj = null
            if (Boolean.valueOf(oldCoverityConfig.configureChangeSetPatterns.text())) {
                changeSetExclusionPatterns = oldCoverityConfig.changeSetExclusionPatterns.text()
                changeSetInclusionPatterns = oldCoverityConfig.changeSetInclusionPatterns.text()

                configureChangeSetPatternsObj = new com.synopsys.integration.jenkins.coverity.extensions.ConfigureChangeSetPatterns(changeSetExclusionPatterns, changeSetInclusionPatterns)
            }

            coverityRunConfigurationObj = null
            if (oldCoverityConfig.coverityRunConfiguration.text() == 'ADVANCED') {
                commands = []
                oldCoverityConfig.commands.'com.synopsys.integration.coverity.common.RepeatableCommand'.each {
                    commands.add(new com.synopsys.integration.jenkins.coverity.extensions.buildstep.RepeatableCommand(it.command.text()))
                }

                coverityRunConfigurationObj = new com.synopsys.integration.jenkins.coverity.extensions.buildstep.AdvancedCoverityRunConfiguration((com.synopsys.integration.jenkins.coverity.extensions.buildstep.RepeatableCommand[]) commands.toArray())
            } else {
                coverityAnalysisType = com.synopsys.integration.jenkins.coverity.extensions.CoverityAnalysisType.valueOf(oldCoverityConfig.coverityAnalysisType.text())
                buildCommand = oldCoverityConfig.buildCommand.text()

                coverityRunConfigurationObj = new com.synopsys.integration.jenkins.coverity.extensions.buildstep.SimpleCoverityRunConfiguration(coverityAnalysisType, buildCommand, null)
            }

            newCoverityConfig = new com.synopsys.integration.jenkins.coverity.extensions.buildstep.CoverityBuildStep(
                coverityInstanceUrl,
                onCommandFailure,
                projectName,
                streamName,
                checkForIssuesInViewObj,
                configureChangeSetPatternsObj,
                coverityRunConfigurationObj)
            item.getBuildersList().add(newCoverityConfig)
            item.save()
            builder.append('migrated successfully.')
        } catch (Exception e) {
            builder.append("migration failed because ${e.getMessage()}.")
            // Uncomment the following line to debug
            // e.getStackTrace().each { builder.append(it.toString() + "\r\n") }
        }
        builder.append("\r\n")
    }
  }
}
println(builder.toString())

end = System.currentTimeMillis()
println("Migrated in ${end-start}ms")
