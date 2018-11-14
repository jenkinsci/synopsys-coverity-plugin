package templates.html

div {
    p(overview.join('<br>'))
    table {
        if (columnNames) {
            tr {
                columnNames.each { columnName -> th(columnName) }
            }
        }
        rowContents.each { rowCells ->
            tr {
                rowCells.each { cell -> td(cell) }
            }
        }
    }
    if (notes) p("<strong>NOTE:</strong><br>${notes.join('<br>')}")
}
