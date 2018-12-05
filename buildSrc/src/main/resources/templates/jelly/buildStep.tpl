package templates.jelly

final String SELECT = 'f:select'
final String TEXTBOX = 'f:textbox'


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

    'f:dropdownList'(name: coverityRunConfigurationField, title: coverityRunConfigurationTitle) {
        'f:dropdownListBlock'(value: simpleCoverityRunConfigurationValue, title: simpleCoverityRunConfigurationTitle) {
            entry(coverityAnalysisTypeField, coverityAnalysisTypeTitle, SELECT)
            entry(buildCommandField, buildCommandTitle, TEXTBOX)
            'f:optionalBlock'(field: commandArgumentsField, title: commandArgumentsTitle, inline: true) {
                entry(covBuildArgumentsField, covBuildArgumentsTitle, TEXTBOX)
                entry(covAnalyzeArgumentsField, covAnalyzeArgumentsTitle, TEXTBOX)
                entry(covRunDesktopArgumentsField, covRunDesktopArgumentsTitle, TEXTBOX)
                entry(covCommitDefectsArgumentsField, covCommitDefectsArgumentsTitle, TEXTBOX)
            }

        }

        'f:dropdownListBlock'(value: advancedCoverityRunConfigurationValue, title: advancedCoverityRunConfigurationTitle) {
            commands(commandsTitle, commandsField, commandField, commandTitle, commandsAddTitle, commandsDeleteTitle)
        }
    }

    entry(onCommandFailureField, onCommandFailureTitle, SELECT)
}

def entry(def field, def title, def inputTag) {
    'f:entry'(field: field, title: title) {
        fragment "\'${inputTag}\'()", ignored: it
    }
}

def refreshConnectionButton(def buttonValue) {
    'f:entry' {
        div(style: 'float:right') {
            input(type: 'button', value: buttonValue, class: 'yui-button ${attrs.clazz}', onclick: 'loadProjects();loadStreams();loadViews();')
        }
    }
}

def commands(def groupTitle, def groupField, def individualField, def individualTitle, def addTitle, def deleteTitle) {
    'f:entry'(title: groupTitle) {
        'f:repeatable'(field: groupField, add: addTitle, minimum: 1) {
            table(style: 'width:100%', id: "${groupField}Id") {
                'f:entry'(field: individualField, title: individualTitle, help: '/plugin/synopsys-coverity/help/help-command.html') {
                    'f:textbox'()
                }
            }
            'f:repeatableDeleteButton'(value: deleteTitle)
        }
    }
}