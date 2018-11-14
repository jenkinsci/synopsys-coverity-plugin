package com.synopsys.integration.jenkins.coverity

import groovy.text.Template

class GenerationUtils {
    static void writeTemplate(Template template, Map model, String outputPath) {
        def file = new File(outputPath)
        if (file.exists()) {
            file.delete()
        }
        file.withWriter('UTF-8') {
            template.make(model).writeTo(it)
        }
    }

}
