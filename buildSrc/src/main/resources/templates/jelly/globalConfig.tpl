package templates.jelly

'j:jelly'('xmlns:j': 'jelly:core', 'xmlns:f': '/lib/form', 'xmlns:c': '/lib/credentials') {
    'f:section'(title: globalConfigSectionTitle) {
        'f:entry'(title: coverityConnectInstancesTitle) {
            'f:repeatable'(field: coverityConnectInstancesField, add: coverityConnectInstancesAddTitle, minimum: 1) {
                table(style: 'width:100%') {
                    'f:entry'(field: urlField, title: urlTitle) {
                        'f:textbox'()
                    }
                    'f:entry'(field: credentialsField, title: credentialsTitle) {
                        'c:select'()
                    }
                    'f:entry'(field: desktopAnalysisField, title: desktopAnalysisTitle) {
                        'f:select'()
                    }
                    'f:entry'(field: covManagePathField, title: covManagePathTitle) {
                        'f:textbox'()
                    }
                    'f:validateButton'(method: testConnectionMethod, title: testConnectionTitle, progress: testConnectionProgress, with: "${urlField},${credentialsField}")
                    'f:repeatableDeleteButton'(value: coverityConnectInstancesDeleteTitle)
                }
            }
        }
    }
}
