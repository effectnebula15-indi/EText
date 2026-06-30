package com.etext.editor

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import com.etext.editor.databinding.ActivityMainBinding
import java.io.BufferedReader
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: android.content.SharedPreferences

    /** Open documents, one per tab. Always at least one. */
    private val documents = mutableListOf<Document>()
    private var currentIndex = -1
    private var untitledCounter = 0

    private var loading = false
    private var theme: EditorTheme = Themes.default
    private var wordWrap: Boolean = true

    // --- Storage Access Framework launchers ---

    private val openLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (!uris.isNullOrEmpty()) openFiles(uris) }

    private val createLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { saveCurrentTo(it, takePersist = true) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("etext", Context.MODE_PRIVATE)
        theme = Themes.byId(prefs.getString("theme", Themes.default.id))
        wordWrap = prefs.getBoolean("wrap", true)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "EText"

        binding.editor.setHorizontallyScrolling(!wordWrap)
        binding.editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, a: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!loading) markCurrentDirty()
                updateStatus()
            }
        })
        binding.editor.setOnClickListener { updateStatus() }

        binding.btnNewTab.setOnClickListener { newDocument() }

        applyTheme(theme)

        handleIncomingIntent(intent)
        if (documents.isEmpty()) newDocument()

        updateTitle()
        updateStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?): Boolean {
        val action = intent?.action ?: return false
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT) {
            val uri = intent.data ?: return false
            openFiles(listOf(uri))
            return true
        }
        return false
    }

    // --- Menu ---

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.action_wrap)?.isChecked = wordWrap
        tintMenu(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_wrap)?.isChecked = wordWrap
        tintMenu(menu)
        return super.onPrepareOptionsMenu(menu)
    }

    private fun tintMenu(menu: Menu) {
        for (i in 0 until menu.size()) {
            menu.getItem(i).icon?.setColorFilter(theme.toolbarText, PorterDuff.Mode.SRC_IN)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_new -> { newDocument(); true }
            R.id.action_open -> { openLauncher.launch(arrayOf("text/*", "*/*")); true }
            R.id.action_save -> { save(); true }
            R.id.action_save_as -> { createLauncher.launch(current().name); true }
            R.id.action_theme -> { showThemePicker(); true }
            R.id.action_wrap -> { toggleWrap(); true }
            R.id.action_about -> { showAbout(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- Document / tab management ---

    private fun current(): Document = documents[currentIndex]

    private fun nextUntitledName(): String {
        untitledCounter++
        return if (untitledCounter == 1) "untitled.txt" else "untitled-$untitledCounter.txt"
    }

    private fun newDocument() {
        addDocument(Document(uri = null, name = nextUntitledName()), select = true)
    }

    private fun addDocument(doc: Document, select: Boolean) {
        saveEditorIntoCurrent()
        documents.add(doc)
        if (select || currentIndex < 0) {
            currentIndex = documents.lastIndex
            bindCurrentToEditor()
        }
        rebuildTabs()
    }

    private fun selectDocument(index: Int) {
        if (index == currentIndex || index !in documents.indices) return
        saveEditorIntoCurrent()
        currentIndex = index
        bindCurrentToEditor()
        rebuildTabs()
        scrollTabIntoView(index)
    }

    private fun closeDocument(index: Int) {
        if (index !in documents.indices) return
        val doc = documents[index]
        val doClose = {
            saveEditorIntoCurrent()
            documents.removeAt(index)
            if (documents.isEmpty()) {
                untitledCounter = 0
                documents.add(Document(uri = null, name = nextUntitledName()))
                currentIndex = 0
            } else {
                currentIndex = when {
                    index < currentIndex -> currentIndex - 1
                    index == currentIndex -> index.coerceAtMost(documents.lastIndex)
                    else -> currentIndex
                }
            }
            bindCurrentToEditor()
            rebuildTabs()
        }
        if (doc.dirty) {
            AlertDialog.Builder(this)
                .setTitle(R.string.close_tab_title)
                .setMessage(getString(R.string.close_tab_message, doc.name))
                .setPositiveButton(R.string.close) { _, _ -> doClose() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            doClose()
        }
    }

    private fun saveEditorIntoCurrent() {
        val d = documents.getOrNull(currentIndex) ?: return
        d.text = binding.editor.text?.toString() ?: ""
        d.selStart = binding.editor.selectionStart.coerceAtLeast(0)
        d.selEnd = binding.editor.selectionEnd.coerceAtLeast(0)
    }

    private fun bindCurrentToEditor() {
        val d = documents.getOrNull(currentIndex) ?: return
        loading = true
        binding.editor.setText(d.text)
        val len = binding.editor.length()
        val a = d.selStart.coerceIn(0, len)
        val b = d.selEnd.coerceIn(0, len)
        binding.editor.setSelection(minOf(a, b), maxOf(a, b))
        loading = false
        updateTitle()
        updateStatus()
    }

    private fun markCurrentDirty() {
        val d = documents.getOrNull(currentIndex) ?: return
        if (!d.dirty) {
            d.dirty = true
            rebuildTabs()
        }
        updateTitle()
    }

    // --- Tab bar rendering ---

    private fun rebuildTabs() {
        val container = binding.tabContainer
        container.removeAllViews()
        binding.tabBar.setBackgroundColor(theme.toolbarBackground)
        container.setBackgroundColor(theme.toolbarBackground)
        binding.btnNewTab.setColorFilter(theme.toolbarText, PorterDuff.Mode.SRC_IN)

        val inflater = LayoutInflater.from(this)
        documents.forEachIndexed { index, doc ->
            val tab = inflater.inflate(R.layout.tab_document, container, false)
            val nameView = tab.findViewById<TextView>(R.id.tabName)
            val closeView = tab.findViewById<ImageView>(R.id.tabClose)
            val active = index == currentIndex

            nameView.text = (if (doc.dirty) "● " else "") + doc.name
            if (active) {
                tab.background = activeTabBackground()
                nameView.setTextColor(theme.toolbarText)
                closeView.setColorFilter(theme.toolbarText, PorterDuff.Mode.SRC_IN)
            } else {
                tab.setBackgroundColor(theme.toolbarBackground)
                nameView.setTextColor(theme.gutterText)
                closeView.setColorFilter(theme.gutterText, PorterDuff.Mode.SRC_IN)
            }

            tab.setOnClickListener { selectDocument(index) }
            closeView.setOnClickListener { closeDocument(index) }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            container.addView(tab, lp)
        }
    }

    /** Active tab: editor-colored fill with a JetBrains-style accent underline. */
    private fun activeTabBackground(): LayerDrawable {
        val accent = ColorDrawable(theme.accent)
        val fill = ColorDrawable(theme.background)
        val layers = LayerDrawable(arrayOf(accent, fill))
        layers.setLayerInset(1, 0, 0, 0, dp(3))
        return layers
    }

    private fun scrollTabIntoView(index: Int) {
        binding.tabScroll.post {
            val child = binding.tabContainer.getChildAt(index) ?: return@post
            val target = child.left - (binding.tabScroll.width - child.width) / 2
            binding.tabScroll.smoothScrollTo(target.coerceAtLeast(0), 0)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // --- File operations ---

    private fun openFiles(uris: List<Uri>) {
        saveEditorIntoCurrent()

        // Drop a pristine, empty "untitled" placeholder when opening real files.
        if (documents.size == 1) {
            val only = documents[0]
            if (only.uri == null && !only.dirty && only.text.isBlank()) {
                documents.clear()
                currentIndex = -1
            }
        }

        var added = 0
        for (uri in uris) {
            try {
                val text = contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(input.reader(StandardCharsets.UTF_8)).readText()
                } ?: ""
                val doc = Document(uri = uri, name = queryName(uri) ?: "document.txt", text = text)
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
                documents.add(doc)
                added++
            } catch (e: Exception) {
                toast(getString(R.string.open_failed))
            }
        }

        if (documents.isEmpty()) {
            newDocument()
            return
        }
        if (added > 0) currentIndex = documents.lastIndex
        currentIndex = currentIndex.coerceIn(0, documents.lastIndex)
        bindCurrentToEditor()
        rebuildTabs()
        scrollTabIntoView(currentIndex)
    }

    private fun save() {
        val doc = current()
        val uri = doc.uri
        if (uri == null) {
            createLauncher.launch(doc.name)
        } else {
            saveCurrentTo(uri, takePersist = false)
        }
    }

    private fun saveCurrentTo(uri: Uri, takePersist: Boolean) {
        val doc = current()
        try {
            // "wt" truncates existing content before writing.
            contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(binding.editor.text.toString().toByteArray(StandardCharsets.UTF_8))
                out.flush()
            }
            if (takePersist) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
            }
            doc.uri = uri
            doc.name = queryName(uri) ?: doc.name
            doc.dirty = false
            updateTitle()
            rebuildTabs()
            toast(getString(R.string.saved))
        } catch (e: Exception) {
            toast(getString(R.string.save_failed))
        }
    }

    private fun queryName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun anyDirty(): Boolean = documents.any { it.dirty }

    override fun onBackPressed() {
        if (anyDirty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.unsaved_title)
                .setMessage(R.string.unsaved_message)
                .setPositiveButton(R.string.discard) { _, _ -> super.onBackPressed() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    // --- Theming ---

    private fun showThemePicker() {
        val names = Themes.all.map { it.displayName }.toTypedArray()
        val checked = Themes.all.indexOfFirst { it.id == theme.id }
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_theme)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                val selected = Themes.all[which]
                theme = selected
                prefs.edit { putString("theme", selected.id) }
                applyTheme(selected)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyTheme(t: EditorTheme) {
        binding.root.setBackgroundColor(t.background)
        binding.editor.applyTheme(t)

        binding.toolbar.setBackgroundColor(t.toolbarBackground)
        binding.toolbar.setTitleTextColor(t.toolbarText)
        binding.toolbar.setSubtitleTextColor(t.gutterText)
        binding.toolbar.overflowIcon?.setColorFilter(t.toolbarText, PorterDuff.Mode.SRC_IN)
        binding.toolbar.navigationIcon?.setColorFilter(t.toolbarText, PorterDuff.Mode.SRC_IN)

        binding.statusBar.setBackgroundColor(t.statusBar)
        binding.statusBar.setTextColor(t.gutterText)

        window.statusBarColor = t.statusBar
        window.navigationBarColor = t.toolbarBackground
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !t.isDark
            isAppearanceLightNavigationBars = !t.isDark
        }

        rebuildTabs()
        invalidateOptionsMenu()
    }

    private fun toggleWrap() {
        wordWrap = !wordWrap
        prefs.edit { putBoolean("wrap", wordWrap) }
        binding.editor.setHorizontallyScrolling(!wordWrap)
        val text = binding.editor.text
        loading = true
        binding.editor.text = text
        loading = false
        invalidateOptionsMenu()
    }

    // --- Title / status ---

    private fun updateTitle() {
        val doc = documents.getOrNull(currentIndex)
        val mark = if (doc?.dirty == true) "● " else ""
        supportActionBar?.title = "EText"
        supportActionBar?.subtitle = mark + (doc?.name ?: "")
    }

    private fun updateStatus() {
        val editor = binding.editor
        val text = editor.text ?: ""
        val sel = editor.selectionStart.coerceAtLeast(0)
        var line = 1
        var col = 1
        var i = 0
        while (i < sel && i < text.length) {
            if (text[i] == '\n') { line++; col = 1 } else col++
            i++
        }
        val lines = if (text.isEmpty()) 1 else text.count { it == '\n' } + 1
        val chars = text.length
        val tabInfo = if (documents.isNotEmpty()) "[${currentIndex + 1}/${documents.size}]  " else ""
        binding.statusBar.text =
            "${tabInfo}Ln $line, Col $col    $lines lines    $chars chars    ${theme.displayName}"
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("EText")
            .setMessage(
                "EText — a simple, contrast-first text editor for .txt, .md and .conf files.\n\n" +
                    "JetBrains-inspired UI with tabbed multi-file editing and seven editor themes.\n\nVersion 1.0"
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
