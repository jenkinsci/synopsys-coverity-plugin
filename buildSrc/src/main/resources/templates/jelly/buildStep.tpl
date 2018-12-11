package templates.jelly

import static com.synopsys.integration.jenkins.coverity.GenerateJelly.SELECT
import static com.synopsys.integration.jenkins.coverity.GenerateJelly.TEXTBOX

'j:jelly'('xmlns:j': 'jelly:core', 'xmlns:f': '/lib/form') {
    script(src: '${rootURL}/plugin/synopsys-coverity/javascript/CoverityFunctions.js')
    script(type: 'text/javascript', 'setRootURL("${app.rootUrl}");')

    entry(coverityInstanceUrlField, coverityInstanceUrlTitle, SELECT)
    entry(coverityToolNameField, coverityToolNameTitle, SELECT)
    entry(projectNameField, projectNameTitle, SELECT)
    entry(streamNameField, streamNameTitle, SELECT)

    'f:optionalBlock'(field: checkForIssuesInViewField, title: checkForIssuesInViewTitle, inline: true) {
        entry(viewNameField, viewNameTitle, SELECT)
        entry(buildStatusForIssuesField, buildStatusForIssuesTitle, SELECT)
    }

    'f:optionalBlock'(field: configureChangeSetPatternsField, title: configureChangeSetPatternsTitle, inline: true) {
        entry(changeSetInclusionPatternsField, changeSetInclusionPatternsTitle, TEXTBOX)
        entry(changeSetExclusionPatternsField, changeSetExclusionPatternsTitle, TEXTBOX)
    }

    refreshConnectionButton(refreshConnectionButtonValue)

    'f:dropdownDescriptorSelector'(field: coverityRunConfigurationField, title: coverityRunConfigurationTitle, default: "\${descriptor.$coverityRunConfigurationDefault}")

    entry(onCommandFailureField, onCommandFailureTitle, SELECT)
}

def entry(def field, def title, def inputTag) {
    'f:entry'(field: field, title: title) {
        "$inputTag"()
    }
}

def refreshConnectionButton(def buttonValue) {
    'f:entry' {
        div(style: 'float:right') {
            input(type: 'button', value: buttonValue, class: 'yui-button ${attrs.clazz}', onclick: 'loadProjects();loadStreams();loadViews();')
        }
    }
}
