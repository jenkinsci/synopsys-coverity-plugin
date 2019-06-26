package templates.jelly

import static com.synopsys.integration.jenkins.coverity.GenerateJelly.SELECT

'j:jelly'('xmlns:j': 'jelly:core', 'xmlns:f': '/lib/form') {
    script(src: '${rootURL}/plugin/synopsys-coverity/javascript/CoverityFunctions.js')
    script(type: 'text/javascript', 'setRootURL("${app.rootUrl}");')
    entry(coverityInstanceUrlField, coverityInstanceUrlTitle, SELECT)
    entry(projectNameField, projectNameTitle, SELECT)
    entry(viewNameField, viewNameTitle, SELECT)
    entry(returnDefectCountField, returnDefectCountTitle, 'f:checkbox')

    refreshConnectionButton(refreshConnectionButtonValue)
}

def entry(def field, def title, def inputTag) {
    'f:entry'(field: field, title: title) {
        "$inputTag"(id: "${field}Id")
    }
}

def refreshConnectionButton(def buttonValue) {
    'f:entry' {
        div(style: 'float:right') {
            input(type: 'button', value: buttonValue, class: 'yui-button ${attrs.clazz}', onclick: 'loadProjects();loadViews();')
        }
    }
}
