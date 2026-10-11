# Local note sorting

The Home note list supports manual ordering. Drag a note by its grip icon; its card becomes a drag shadow and the row under the pointer shrinks slightly and fades to show the current drop target. Release to move the note. The order is saved on this device and restored when Home refreshes or opens again. Newly discovered notes are appended after notes with a saved position.

The order is stored as a comma-separated sequence of local note IDs in the `note_sort` SharedPreferences file. It is device-local and is not sent to the notes API or included in note exports. Since the existing database deletion flow compacts note IDs, Home updates saved positions to match that compaction after a deletion.

Drag handling starts from the grip in `CustomAdapter`; `Home` resolves the drop position, reorders the displayed IDs, titles, and previews, and persists the updated sequence. The list still uses the existing SQLite note order when no local preference exists.

The add-note action appears as an inline card after shorter lists. With five or more notes, it moves to a compact bottom dock button so it stays reachable without scrolling. Both entry points open NoteView directly in new-note mode. The note is inserted into SQLite only when Save is pressed; Cancel or Back leaves no blank note behind. New notes start local-only and become eligible for online sync only after they are saved and opened as an existing note.
