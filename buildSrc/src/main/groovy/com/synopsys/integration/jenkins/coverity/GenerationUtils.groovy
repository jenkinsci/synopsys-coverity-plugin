package com.synopsys.integration.jenkins.coverity

import groovy.text.Template

class GenerationUtils {
    static void writeTemplate(Template template, Map model, String outputFolder, String fileName) {
        def file = new File(outputFolder, fileName)
        file.mkdirs()
        if (file.exists()) {
            file.delete()
        }
        file.withWriter('UTF-8') {
            template.make(model).writeTo(it)
        }
    }

}
