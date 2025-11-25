package org.calibre.gui

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import org.calibre.metadata.Library
import org.calibre.metadata.Metadata
import org.calibre.utils.Strings
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel

class DesktopGui(private val library: Library) : JFrame() {

    private val tableModel = DefaultTableModel()
    private val table = JTable(tableModel)
    private val statusBar = JLabel()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(800, 600)
        layout = BorderLayout()
        
        updateTexts()

        // Toolbar
        val toolbar = JToolBar()
        val btnAdd = JButton(Strings["menu.add"])
        btnAdd.addActionListener { addBookAction() }
        val btnRemove = JButton(Strings["menu.remove"])
        btnRemove.addActionListener { removeBookAction() }
        val btnConvert = JButton(Strings["menu.convert"])
        btnConvert.addActionListener { convertBookAction() }
        val btnRead = JButton(Strings["menu.read"])
        btnRead.addActionListener { readBookAction() }
        val btnMeta = JButton("Metadata") // Needs i18n
        btnMeta.addActionListener { downloadMetadataAction() }
        
        toolbar.add(btnAdd)
        toolbar.add(btnRemove)
        toolbar.add(btnConvert)
        toolbar.add(btnRead)
        toolbar.add(btnMeta)
        add(toolbar, BorderLayout.NORTH)

        // Table
        val scrollPane = JScrollPane(table)
        add(scrollPane, BorderLayout.CENTER)

        add(statusBar, BorderLayout.SOUTH)

        refreshTable()
        
        // Initialize JavaFX toolkit
        JFXPanel()
    }
    
    private fun updateTexts() {
        title = Strings["app.title"]
        tableModel.setColumnIdentifiers(arrayOf(
            Strings["col.id"], Strings["col.title"], Strings["col.authors"], Strings["col.series"]
        ))
        statusBar.text = Strings["status.ready"]
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
                JOptionPane.showMessageDialog(this, Strings.format("msg.imported", id))
                refreshTable()
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, Strings.format("msg.error", e.message ?: ""))
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
                JOptionPane.showMessageDialog(this, "Failed")
            }
        }
    }
    
    private fun convertBookAction() {
        val row = table.selectedRow
        if (row == -1) return
        
        val id = tableModel.getValueAt(row, 0) as Int
        val formats = arrayOf("txt", "html")
        val format = JOptionPane.showInputDialog(this, "Select format", Strings["menu.convert"], 
            JOptionPane.QUESTION_MESSAGE, null, formats, formats[0]) as String?
            
        if (format != null) {
            try {
                val bookFile = library.getBookFile(id)
                if (bookFile == null) {
                    JOptionPane.showMessageDialog(this, "File not found")
                    return
                }
                
                val metadata = library.getMetadata(id)
                val title = metadata?.title ?: "converted"
                val safeTitle = title.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                val outFile = java.io.File("${safeTitle}.$format")
                
                val pipeline = org.calibre.conversion.ConversionPipeline()
                pipeline.convert(bookFile, format, outFile)
                
                JOptionPane.showMessageDialog(this, Strings.format("msg.convert_success", outFile.absolutePath))
                
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, Strings.format("msg.convert_fail", e.message ?: ""))
            }
        }
    }
    
    private fun readBookAction() {
        val row = table.selectedRow
        if (row == -1) return
        val id = tableModel.getValueAt(row, 0) as Int
        
        try {
            val bookFile = library.getBookFile(id)
            if (bookFile == null) return
            
            val tempFile = java.io.File.createTempFile("calibre_view", ".html")
            val pipeline = org.calibre.conversion.ConversionPipeline()
            pipeline.convert(bookFile, "html", tempFile)
            
            ViewerFrame(tempFile).isVisible = true
            
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(this, e.message)
        }
    }
    
    private fun downloadMetadataAction() {
        val query = JOptionPane.showInputDialog(this, "Enter title/author:", "Download Metadata", JOptionPane.QUESTION_MESSAGE)
        if (query != null && query.isNotBlank()) {
            try {
                val client = org.calibre.metadata.sources.GoogleBooksClient()
                val results = client.search(query)
                
                if (results.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "No results found.")
                    return
                }
                
                val options = results.map { "${it.title} by ${it.authors.joinToString(", ")}" }.toTypedArray()
                val selection = JOptionPane.showInputDialog(this, "Select book:", "Results", 
                    JOptionPane.QUESTION_MESSAGE, null, options, options[0]) as String?
                    
                if (selection != null) {
                    val index = options.indexOf(selection)
                    val meta = results[index]
                    val id = library.addBook(meta)
                    JOptionPane.showMessageDialog(this, "Added book ID: $id")
                    refreshTable()
                }
                
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, "Error: ${e.message}")
            }
        }
    }
}

class ViewerFrame(file: File) : JFrame("Viewer") {
    init {
        size = Dimension(800, 800)
        val jfxPanel = JFXPanel()
        add(jfxPanel)
        
        Platform.runLater {
            val webView = WebView()
            val webEngine = webView.engine
            try {
                webEngine.load(file.toURI().toURL().toExternalForm())
            } catch (e: Exception) {
                println("Error loading page: ${e.message}")
            }
            val scene = Scene(webView)
            jfxPanel.scene = scene
        }
    }
}
