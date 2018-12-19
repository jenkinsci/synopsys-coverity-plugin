package templates.jelly

import static com.synopsys.integration.jenkins.coverity.GenerateJelly.SELECT
import static com.synopsys.integration.jenkins.coverity.GenerateJelly.TEXTBOX

'j:jelly'('xmlns:j': 'jelly:core', 'xmlns:f': '/lib/form') {
    entry(coverityAnalysisTypeField, coverityAnalysisTypeTitle, SELECT)
    entry(buildCommandField, buildCommandTitle, TEXTBOX)
    'f:optionalBlock'(checked: "\${instance.$commandArgumentsField != null}", field: commandArgumentsField, title: commandArgumentsTitle) {
        'j:scope' {
            'j:set'(var: 'instance', value: "\${instance.$commandArgumentsField}")
            'j:set'(var: 'descriptor', value: "\${instance.descriptor}")
            'j:set'(var: 'it', value: "\${it.$commandArgumentsField}")
            entry(covBuildArgumentsField, covBuildArgumentsTitle, TEXTBOX)
            entry(covAnalyzeArgumentsField, covAnalyzeArgumentsTitle, TEXTBOX)
            entry(covRunDesktopArgumentsField, covRunDesktopArgumentsTitle, TEXTBOX)
            entry(covCommitDefectsArgumentsField, covCommitDefectsArgumentsTitle, TEXTBOX)
        }
    }
}

def entry(def field, def title, def inputTag) {
    'f:entry'(field: field, title: title) {
        "$inputTag"()
    }
}