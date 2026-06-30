package com.etext.editor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
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

    private var currentUri: Uri? = null
    private var currentName: String = "untitled.txt"
    private var dirty: Boolean = false
    private var loading: Boolean = false

    private var theme: EditorTheme = Themes.default
    private var wordWrap: Boolean = true

    // --- Storage Access Framework launchers ---

    private val openLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { loadFile(it) } }

    private val createLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { saveTo(it, takePersist = true) } }

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
                if (!loading) {
                    dirty = true
                    updateTitle()
                }
                updateStatus()
            }
        })
        binding.editor.setOnClickListener { updateStatus() }

        applyTheme(theme)
        handleIncomingIntent(intent)
        updateTitle()
        updateStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_EDIT) {
            intent.data?.let { loadFile(it) }
        }
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
            R.id.action_new -> { confirmDiscard { newDocument() }; true }
            R.id.action_open -> { confirmDiscard { openLauncher.launch(arrayOf("text/*", "*/*")) }; true }
            R.id.action_save -> { save(); true }
            R.id.action_save_as -> { createLauncher.launch(currentName); true }
            R.id.action_theme -> { showThemePicker(); true }
            R.id.action_wrap -> { toggleWrap(); true }
            R.id.action_about -> { showAbout(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // --- File operations ---

    private fun newDocument() {
        loading = true
        binding.editor.setText("")
        loading = false
        currentUri = null
        currentName = "untitled.txt"
        dirty = false
        updateTitle()
        updateStatus()
    }

    private fun loadFile(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(input.reader(StandardCharsets.UTF_8)).readText()
            } ?: ""
            loading = true
            binding.editor.setText(text)
            binding.editor.setSelection(0)
            loading = false
            currentUri = uri
            currentName = queryName(uri) ?: "document.txt"
            dirty = false
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            updateTitle()
            updateStatus()
        } catch (e: Exception) {
            toast(getString(R.string.open_failed))
        }
    }

    private fun save() {
        val uri = currentUri
        if (uri == null) {
            createLauncher.launch(currentName)
        } else {
            saveTo(uri, takePersist = false)
        }
    }

    private fun saveTo(uri: Uri, takePersist: Boolean) {
        try {
            // "wt" truncates existing content before writing.
            contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(binding.editor.text.toString().toByteArray(StandardCharsets.UTF_8))
                out.flush()
            }
            if (takePersist) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
            }
            currentUri = uri
            currentName = queryName(uri) ?: currentName
            dirty = false
            updateTitle()
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

    private fun confirmDiscard(action: () -> Unit) {
        if (!dirty) {
            action()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_title)
            .setMessage(R.string.unsaved_message)
            .setPositiveButton(R.string.discard) { _, _ -> action() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onBackPressed() {
        confirmDiscard { super.onBackPressed() }
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
        binding.toolbar.overflowIcon?.setColorFilter(t.toolbarText, PorterDuff.Mode.SRC_IN)
        binding.toolbar.navigationIcon?.setColorFilter(t.toolbarText, PorterDuff.Mode.SRC_IN)

        binding.statusBar.setBackgroundColor(t.statusBar)
        binding.statusBar.setTextColor(t.gutterText)

        // System bars.
        window.statusBarColor = t.statusBar
        window.navigationBarColor = t.toolbarBackground
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !t.isDark
            isAppearanceLightNavigationBars = !t.isDark
        }
        invalidateOptionsMenu()
    }

    private fun toggleWrap() {
        wordWrap = !wordWrap
        prefs.edit { putBoolean("wrap", wordWrap) }
        binding.editor.setHorizontallyScrolling(!wordWrap)
        // Force re-layout of the editor.
        val text = binding.editor.text
        loading = true
        binding.editor.text = text
        loading = false
        invalidateOptionsMenu()
    }

    // --- Status bar ---

    private fun updateTitle() {
        val mark = if (dirty) "● " else ""
        supportActionBar?.title = "EText"
        supportActionBar?.subtitle = "$mark$currentName"
        binding.toolbar.setSubtitleTextColor(theme.gutterText)
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
        binding.statusBar.text = "Ln $line, Col $col    $lines lines    $chars chars    ${theme.displayName}"
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("EText")
            .setMessage(
                "EText — a simple, contrast-first text editor for .txt, .md and .conf files.\n\n" +
                    "JetBrains-inspired UI with seven editor themes.\n\nVersion 1.0"
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
