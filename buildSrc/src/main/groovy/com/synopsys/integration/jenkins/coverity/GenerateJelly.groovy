package com.synopsys.integration.jenkins.coverity

import groovy.json.JsonSlurper
import groovy.text.Template
import groovy.text.markup.MarkupTemplateEngine
import groovy.text.markup.TemplateConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class GenerateJelly extends DefaultTask {
    Map<String, String> outputPathToJson = [:]

    @TaskAction
    void generateJelly() {
        final def config = new TemplateConfiguration()
        config.setAutoIndent(true)
        config.setAutoNewLine(true)

        final def engine = new MarkupTemplateEngine(GenerateJelly.getClassLoader(), config)
        final def jsonSlurper = new JsonSlurper()

        outputPathToJson.entrySet().each {
            final def outputDir = it.key
            final def json = new File(it.value)
            final def jsonObject = jsonSlurper.parse(json)
            final Map model = jsonObject.model
            final String templateName = jsonObject.template

            Template template = engine.createTemplateByPath("templates/jelly/${templateName}")
            GenerationUtils.writeTemplate(template, model, "${outputDir}/config.jelly")
        }
    }

}
