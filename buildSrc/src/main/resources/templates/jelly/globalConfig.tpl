package templates.jelly

'j:jelly'('xmlns:j': 'jelly:core', 'xmlns:f': '/lib/form', 'xmlns:c': '/lib/credentials') {
    'f:section'(title: globalConfigSectionTitle) {
        'f:entry'(title: coverityConnectInstancesTitle, field: coverityConnectInstancesField) {
            'f:repeatable'(var: coverityConnectInstanceVar, name: coverityConnectInstancesField, items: "\${descriptor.${coverityConnectInstancesField}}", add: coverityConnectInstancesAddTitle, minimum: 1) {
                table(style: 'width:100%', id: "${coverityConnectInstancesField}Id") {
                    'f:entry'(field: urlField, title: urlTitle, help: urlHelp) {
                        'f:textbox'()
                    }
                    'f:entry'(field: credentialsField, title: credentialsTitle, help: credentialsHelp) {
                        'c:select'()
                    }
                    'f:validateButton'(method: testConnectionMethod, title: testConnectionTitle, progress: testConnectionProgress, with: "${urlField},${credentialsField}")
                    'f:repeatableDeleteButton'(value: coverityConnectInstancesDeleteTitle)
                }
            }
        }
    }
}
