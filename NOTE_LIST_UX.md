# Note list UX

The home screen uses a compact, scannable card for each note. A row shows the note title on the first line and a one-line excerpt of its content below. The local numeric note ID is intentionally hidden because it is an internal identifier rather than useful browsing information. Online notes retain their cloud badge.

Rows have a 68 dp minimum height, moderate horizontal padding, and reduced spacing between cards. Tapping a row still opens the note, and long-pressing still opens note actions.

`Home.refreshArrayList()` reads note IDs, titles, and content in one SQLite query. It normalizes whitespace in excerpts so multi-line notes preview as a single line. `CustomAdapter` binds the excerpt and displays “No additional text” when a note has no content. `one_note_layout.xml` defines the two-line visual hierarchy and compact card spacing.
