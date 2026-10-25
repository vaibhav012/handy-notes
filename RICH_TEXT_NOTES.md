# Markdown notes in the Android app

The Android app stores note content as the same Markdown string used by handy-notes-server. It does not wrap content in a new JSON format, so local notes, server sync, widget editing, and web editing share one representation.

`RichNoteEditor` uses one continuous multiline `EditText` for the whole note. Markdown block prefixes are rendered inline: tasks appear as tappable checkbox spans, bullets and ordered markers become markers, quote prefixes become quote bars, and heading prefixes are hidden while their text is styled. Tapping a task toggles `[ ]` / `[x]` in the underlying Markdown string. Enter continues non-empty task, bullet, ordered-list, and quote lines using the server's prefixes and increments ordered-list numbers; Enter on an empty task, bullet, or ordered item exits the list. Headings continue as a normal paragraph, matching the web editor. Backspace at the start of a formatted line removes its prefix. The toolbar toggles the current line's heading, quote, bullet, ordered-list, or task style, removing that prefix when the same style is selected again; it also wraps a selection in bold, italic, or code syntax. Inline delimiters are hidden while their text is styled.

The widget note view formats the content through `RichNoteEditor.renderMarkdown`. Its content row remains a shortcut into `EditWidget`, where the same single-surface interactive Markdown editor is used. The widget itself is a read-oriented surface and does not toggle tasks in place.

`RichNoteEditor.setContent` also imports the earlier local version-1 block JSON format into Markdown on open. The next save converts that content to Markdown, which is the server-compatible format. Note previews use `RichNoteEditor.preview` so JSON and common Markdown markers are never shown in the home list.

Supported block syntax: paragraphs, headings (`#` through `######`), quotes (`>`), unordered lists (`-`, `*`, `+`), ordered lists (`1.` / `1)`), and task lists (`- [ ]` / `- [x]`). Inline syntax: bold (`**` or `__`), italic (`*` or `_`), and code (backticks).
