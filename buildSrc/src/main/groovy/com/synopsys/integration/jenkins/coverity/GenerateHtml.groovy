package com.synopsys.integration.jenkins.coverity

import groovy.json.JsonSlurper
import groovy.text.Template
import groovy.text.markup.MarkupTemplateEngine
import groovy.text.markup.TemplateConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class GenerateHtml extends DefaultTask {
    Map<String, String> outputPathToJson = [:]
    String pathToExtensionResourcesPackage = ''
    String pathToHelpJson = ''

    @TaskAction
    void generateHtml() {
        def config = new TemplateConfiguration()
        config.setAutoIndent(true)
        config.setAutoNewLine(true)

        def engine = new MarkupTemplateEngine(GenerateHtml.getClassLoader(), config)
        def jsonSlurper = new JsonSlurper()

        outputPathToJson.entrySet().each {
            def outputPath = new File(pathToExtensionResourcesPackage, it.key).canonicalPath
            def json = new File(new File(pathToHelpJson).canonicalPath, it.value)
            def jsonObject = jsonSlurper.parse(json)

            jsonObject.entrySet().each { templateEntry ->
                Template template = engine.createTemplateByPath("templates/html/${templateEntry.key}")
                templateEntry.value.entrySet().each { fieldEntry ->
                    String fieldName = fieldEntry.key
                    Map model = fieldEntry.value
                    GenerationUtils.writeTemplate(template, model, outputPath, "help-${fieldName}.html")
                }
            }
        }
    }

}
