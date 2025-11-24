package org.calibre.gui

import org.calibre.metadata.Library
import org.calibre.metadata.Metadata
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

class DesktopGui(private val library: Library) : JFrame("Calibre Kotlin") {

    private val tableModel = DefaultTableModel(arrayOf("ID", "Title", "Authors", "Series"), 0)
    private val table = JTable(tableModel)

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(800, 600)
        layout = BorderLayout()

        // Toolbar
        val toolbar = JToolBar()
        val btnAdd = JButton("Add Book")
        btnAdd.addActionListener { addBookAction() }
        val btnRemove = JButton("Remove Book")
        btnRemove.addActionListener { removeBookAction() }
        val btnConvert = JButton("Convert")
        btnConvert.addActionListener { convertBookAction() }
        
        toolbar.add(btnAdd)
        toolbar.add(btnRemove)
        toolbar.add(btnConvert)
        add(toolbar, BorderLayout.NORTH)

        // Table
        val scrollPane = JScrollPane(table)
        add(scrollPane, BorderLayout.CENTER)

        // Status Bar
        val statusBar = JLabel("Ready")
        add(statusBar, BorderLayout.SOUTH)

        refreshTable()
    }

    private fun refreshTable() {
        tableModel.rowCount = 0
        val books = library.getAllBooks()
        for (book in books) {
            tableModel.addRow(arrayOf(
                book.id,
                book.title,
                book.authors.joinToString(", "),
                if (book.series != null) "${book.series} #${book.seriesIndex}" else ""
            ))
        }
    }

    private fun addBookAction() {
        val fileChooser = JFileChooser()
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val file = fileChooser.selectedFile
            try {
                val id = library.importBook(file)
                JOptionPane.showMessageDialog(this, "Imported book ID: $id")
                refreshTable()
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, "Error: ${e.message}")
            }
        }
    }

    private fun removeBookAction() {
        val row = table.selectedRow
        if (row != -1) {
            val id = tableModel.getValueAt(row, 0) as Int
            if (library.removeBook(id)) {
                refreshTable()
            } else {
                JOptionPane.showMessageDialog(this, "Failed to remove book")
            }
        }
    }
    
    private fun convertBookAction() {
        val row = table.selectedRow
        if (row == -1) return
        
        val id = tableModel.getValueAt(row, 0) as Int
        val formats = arrayOf("txt", "html")
        val format = JOptionPane.showInputDialog(this, "Select format", "Convert", 
            JOptionPane.QUESTION_MESSAGE, null, formats, formats[0]) as String?
            
        if (format != null) {
            try {
                val bookFile = library.getBookFile(id)
                if (bookFile == null) {
                    JOptionPane.showMessageDialog(this, "Book file not found")
                    return
                }
                
                val metadata = library.getMetadata(id)
                val title = metadata?.title ?: "converted"
                val safeTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                val outFile = java.io.File("${safeTitle}.$format")
                
                val pipeline = org.calibre.conversion.ConversionPipeline()
                pipeline.convert(bookFile, format, outFile)
                
                JOptionPane.showMessageDialog(this, "Converted to ${outFile.absolutePath}")
                
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, "Conversion failed: ${e.message}")
            }
        }
    }
}
