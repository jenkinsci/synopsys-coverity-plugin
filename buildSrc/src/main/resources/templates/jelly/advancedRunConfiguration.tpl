package templates.jelly

'j:jelly'('xmlns:j': 'jelly:core', 'xmlns:f': '/lib/form') {
    'f:entry' {
        'f:repeatable'(field: commandsField, add: commandsAddTitle, minimum: 1) {
            table(style: 'width:100%', id: "${commandsField}Id") {
                'f:entry'(field: commandField, title: commandTitle) {
                    'f:textbox'()
                }
            }
            'f:repeatableDeleteButton'(value: commandsDeleteTitle)
        }
    }
}