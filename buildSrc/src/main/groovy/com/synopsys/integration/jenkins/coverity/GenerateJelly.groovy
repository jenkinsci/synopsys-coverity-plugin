package com.synopsys.integration.jenkins.coverity

import groovy.json.JsonSlurper
import groovy.text.Template
import groovy.text.markup.MarkupTemplateEngine
import groovy.text.markup.TemplateConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class GenerateJelly extends DefaultTask {
    Map<String, String> outputPathToJson = null
    String pathToExtensionResourcesPackage = null
    String pathToJellyJson = null

    @TaskAction
    void generateJelly() {
        if (!outputPathToJson || !pathToExtensionResourcesPackage || !pathToJellyJson) {
            throw new IllegalStateException('Cannot generate jelly files, ouptutPathToJson, pathToExtensionResourcesPackage, and/or pathToJellyJson not set')
        }

        final def config = new TemplateConfiguration()
        config.setAutoIndent(true)
        config.setAutoNewLine(true)
        config.setUseDoubleQuotes(true)

        final def engine = new MarkupTemplateEngine(GenerateJelly.getClassLoader(), config)
        final def jsonSlurper = new JsonSlurper()

        outputPathToJson.entrySet().each {
            def outputPath = new File(pathToExtensionResourcesPackage, it.key).canonicalPath
            def json = new File(pathToJellyJson, it.value)
            final def jsonObject = jsonSlurper.parse(json)
            final Map model = jsonObject.model
            final String templateName = jsonObject.template

            Template template = engine.createTemplateByPath("templates/jelly/${templateName}")
            GenerationUtils.writeTemplate(template, model, outputPath, 'config.jelly')
        }
    }

    static final String SELECT = 'f:select'
    static final String TEXTBOX = 'f:textbox'

}
