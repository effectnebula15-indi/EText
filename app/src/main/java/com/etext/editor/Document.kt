package com.etext.editor

import android.net.Uri

/**
 * One open file (or unsaved buffer) backing a single tab.
 *
 * The active document's live text lives in the shared editor; switching tabs
 * snapshots the editor back into [text] / [selStart] / [selEnd] and reloads the
 * incoming document.
 */
class Document(
    var uri: Uri?,
    var name: String,
    var text: CharSequence = "",
    var dirty: Boolean = false,
    var selStart: Int = 0,
    var selEnd: Int = 0,
)
