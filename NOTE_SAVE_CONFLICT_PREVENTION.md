# Note save conflict prevention

## Behavior

- For an online note, the app and widget editor lock note editing and save/browse controls while the initial server fetch is running. When the fetch completes or fails, controls are re-enabled; fetch errors leave the current local draft available.
- Saving an online note sends the update to the Handy Notes API first. Only after a successful response does the client write the edited title and content to SQLite and close the editor.
- If an online update fails, the editor remains open with the user's draft in its fields, does not commit that draft locally, and allows a retry. The app shows a status message and error toast; the widget editor shows an error toast.
- Offline-only notes are still saved directly to SQLite. Creating a new note also remains local-first because it has no existing server record to update.
- Widget browsing persists the note being left locally and queues online updates when the browse gesture ends. Those queued requests remain asynchronous and do not yet have a durable retry queue.

## Implementation notes

- `NoteView` tracks the initial fetch and explicit update in progress, disables the editor and Save button during the fetch, and disables Save during the update.
- `EditWidget` disables the text editor, Update button, and note browse bar during the initial fetch. Explicit online Update disables Update and browsing until the API callback.
- The server remains authoritative for the linked UUID when the client fetches. These UI locks prevent overlapping edits during the active fetch/update, but they do not provide cross-device locking or conflict resolution for changes made after a fetch completes.

