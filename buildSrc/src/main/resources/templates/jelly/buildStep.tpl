package templates.jelly

import static com.synopsys.integration.jenkins.coverity.GenerateJelly.SELECT
import static com.synopsys.integration.jenkins.coverity.GenerateJelly.TEXTBOX

'j:jelly'('xmlns:j': 'jelly:core', 'xmlns:f': '/lib/form') {
    script(src: '${rootURL}/plugin/synopsys-coverity/javascript/CoverityFunctions.js')
    script(type: 'text/javascript', 'setRootURL("${app.rootUrl}");')

    entry(coverityInstanceUrlField, coverityInstanceUrlTitle, SELECT)
    entry(projectNameField, projectNameTitle, SELECT)
    entry(streamNameField, streamNameTitle, SELECT)

    'f:optionalBlock'(checked: "\${instance.$checkForIssuesInViewField != null}", field: checkForIssuesInViewField, title: checkForIssuesInViewTitle) {
        'j:scope' {
            'j:set'(var: 'descriptor', value: "\${descriptor.getPropertyType(instance,'${checkForIssuesInViewField}').getApplicableDescriptors().get(0)}")
            'j:set'(var: 'instance', value: "\${instance.$checkForIssuesInViewField}")
            'j:set'(var: 'it', value: "\${it.$checkForIssuesInViewField}")
            entry(viewNameField, viewNameTitle, SELECT)
            entry(buildStatusForIssuesField, buildStatusForIssuesTitle, SELECT)
        }
    }

    'f:optionalBlock'(checked: "\${instance.$configureChangeSetPatternsField != null}", field: configureChangeSetPatternsField, title: configureChangeSetPatternsTitle) {
        'j:scope' {
            'j:set'(var: 'descriptor', value: "\${descriptor.getPropertyType(instance,'${configureChangeSetPatternsField}').getApplicableDescriptors().get(0)}")
            'j:set'(var: 'instance', value: "\${instance.$configureChangeSetPatternsField}")
            'j:set'(var: 'it', value: "\${it.$configureChangeSetPatternsField}")
            entry(changeSetInclusionPatternsField, changeSetInclusionPatternsTitle, TEXTBOX)
            entry(changeSetExclusionPatternsField, changeSetExclusionPatternsTitle, TEXTBOX)
        }
    }

    refreshConnectionButton(refreshConnectionButtonValue)

    'f:dropdownDescriptorSelector'(field: coverityRunConfigurationField, title: coverityRunConfigurationTitle, default: "\${instance.$coverityRunConfigurationDefault}")

    entry(onCommandFailureField, onCommandFailureTitle, SELECT)
}

def entry(def field, def title, def inputTag) {
    'f:entry'(field: field, title: title) {
        "$inputTag"(id: "${field}Id")
    }
}

def refreshConnectionButton(def buttonValue) {
    'f:entry' {
        div(style: 'float:right') {
            input(type: 'button', value: buttonValue, class: 'yui-button ${attrs.clazz}', onclick: 'loadProjects();loadStreams();loadViews();')
        }
    }
}
