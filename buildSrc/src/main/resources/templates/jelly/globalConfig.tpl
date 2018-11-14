package templates.jelly

'j:jelly'('xmlns:j': 'jelly:core', 'xmlns:f': '/lib/form', 'xmlns:c': '/lib/credentials') {
    'f:section'(title: globalConfigSectionTitle) {
        'f:entry'(field: urlField, title: urlTitle, description: urlDescription) {
            'f:textbox'()
        }
        'f:entry'(field: credentialsField, title: credentialsTitle) {
            'c:select'()
        }
        'f:validateButton'(method: testConnectionMethod, title: testConnectionTitle, progress: testConnectionProgress, with: "${urlField},${credentialsField}")
    }
}
