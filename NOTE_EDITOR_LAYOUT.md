# Note editor space

The note detail screen reserves only a compact Files row when there are no attachments. The **Add file** button stays available, and the file count and scrollable attachment list appear only when files exist. Loading and error messages appear in the status row as needed.

`activity_note_view.xml` uses a vertical root: toolbar, note header, weighted `noteContentArea`, and bottom actions. The content area and its editor use weighted `0dp` heights, so Android can shrink them when it resizes the activity for the keyboard. `NoteView` requests `SOFT_INPUT_ADJUST_RESIZE`, also declared for the activity in the manifest. The attachment scroll area is capped at 72dp and starts hidden; `NoteView.refreshAttachments` reveals the file count and list only when the server returns files.

The widget editor's `noteRefreshSpinner` is placed in the card's note-title row, alongside the title. Keeping it in that row ensures the refresh indicator stays within the rounded card instead of being anchored to the full-screen root.
