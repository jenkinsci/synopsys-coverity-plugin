package templates.jelly

import static com.synopsys.integration.jenkins.coverity.GenerateJelly.SELECT
import static com.synopsys.integration.jenkins.coverity.GenerateJelly.TEXTBOX

'j:jelly'('xmlns:j': 'jelly:core', 'xmlns:f': '/lib/form') {
    entry(coverityCaptureTypeField, coverityCaptureTypeTitle, coverityCaptureTypeDefault, SELECT)
    entry(sourceArgumentField, sourceArgumentTitle, TEXTBOX)
    entry(coverityAnalysisTypeField, coverityAnalysisTypeTitle, coverityAnalysisTypeDefault, SELECT)
    'f:entry'(field: changeSetAnalysisThresholdField, title: changeSetAnalysisThresholdTitle) {
        'f:textbox'(clazz: 'required number', checkmethod: 'post')
    }
    'f:optionalBlock'(checked: "\${instance.$commandArgumentsField != null}", field: commandArgumentsField, title: commandArgumentsTitle) {
        'j:scope' {
            'j:set'(var: 'descriptor', value: "\${descriptor.getPropertyType(instance,'${commandArgumentsField}').getApplicableDescriptors().get(0)}")
            'j:set'(var: 'instance', value: "\${instance.$commandArgumentsField}")
            'j:set'(var: 'it', value: "\${it.$commandArgumentsField}")
            entry(covBuildArgumentsField, covBuildArgumentsTitle, TEXTBOX)
            entry(covCaptureArgumentsField, covCaptureArgumentsTitle, TEXTBOX)
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

def entry(def field, def title, def defaultValueMethod, def inputTag) {
    'f:entry'(field: field, title: title) {
        "$inputTag"(default: "\${instance.$defaultValueMethod}")
    }
}
