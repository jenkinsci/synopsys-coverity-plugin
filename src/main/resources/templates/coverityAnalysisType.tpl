package templates

div {
    p(overview.join('<br>'))
    table {
        commands.each { command ->
            tr {
                td(command)
            }
        }
    }
    if (notes) p("<strong>NOTE:</strong><br>${notes.join('<br>')}")
}
