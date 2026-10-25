# Widget default note shortcut

Long-press a note in the home list and choose **Set as widget default** to choose the single note used by the widget shortcut. The setting is stored in default shared preferences under `widget_default_note_id`; it is independent of each widget's current note selection.

Each widget has a small document shortcut at the right end of its header. Tapping it opens the default note in `EditWidget` and updates that widget's `widget_note_<appWidgetId>` preference first, so the widget returns to the same note when refreshed. Other widgets keep their own selection. If the default note was hidden, using the shortcut makes it visible again. If no default is configured or its saved note was deleted, the widget selects a usable visible note or creates **Default Note** when no visible note exists.

The previous/next navigation buttons and default-note shortcut are hidden when exactly one named, non-hidden note is available to the widget. They remain available with no visible notes so the widget can still create its starter note on first interaction, and with multiple notes for navigation or opening the app-wide default.

Long-press a note and choose **Hide from widget** or **Show in widget** to control whether it appears while browsing with the widget's previous and next buttons. Hidden note IDs are stored in `widget_hidden_note_ids`. If every note is hidden, the widget shows instructions to reveal a note from the home list. Note deletion remaps the default, hidden IDs, and each widget's selected note as the database compacts note IDs.

The home note list marks the default note with a small blue dot. A green dot marks notes visible in the widget; hidden notes have no visibility dot. The online-sync cloud icon sits beside these indicators on the same title row, with all indicators aligned at the right edge of each card.

The shortcut uses the widget ID in its activity `PendingIntent`, giving each widget an independent destination while sharing one app-wide default note.

When an empty widget is first used (tapping its content, the default-note shortcut, or previous/next), it automatically gets a usable note. If there is already a visible, named note, that note becomes the app-wide widget default. If there is no visible, named note, the app creates a local note titled **Default Note** and makes it the default. The selected note is also assigned to the widget that initiated the interaction.

The fallback is shared by the widget provider and `EditWidget` so the empty-state tap and the header shortcut both recover without asking the user to configure a default first. Hidden notes and blank placeholder rows are skipped when choosing an existing note.
