# Online notes and Supabase authentication

## Behavior

- Online note sync uses the Handy Notes server API at `/api/note`; Android does not query the Supabase database directly.
- Existing Supabase users sign in with email and password from the **Supabase Account** menu. The app does not create accounts.
- The Handy Notes API host, Supabase project URL, and Supabase publishable key are fixed in `ApiConfig` constants; the app has no settings form or persisted override for them. The Supabase URL and publishable key initialize the Auth SDK; note API requests go only to the Handy Notes server and use the signed-in user's access token as `Authorization: Bearer …`. The Android app never writes to Supabase PostgREST directly.
- The Supabase Kotlin Auth SDK owns session persistence and automatic refresh. Session JSON is encrypted with AES-GCM using a non-exportable Android Keystore key. If a note API call returns `401`, the client asks the SDK to refresh the current session and retries that request once.
- When Home resumes with a configured, signed-in account, it requests `GET /api/note` and upserts the returned notes into SQLite using the server UUID as the deduplication key. Existing local-only notes remain in SQLite.
- Notes explicitly marked online are created with their local ID and retain the server UUID. Updates continue using `PUT /api/note/{id}`.
- Deleting a locally stored note marked online also sends `DELETE /api/note/{id}`. Local deletion proceeds immediately; if the server request fails, the app reports that the server copy may remain.

## Implementation notes

- `SupabaseAuthManager.kt` is a Java-callable bridge around `supabase-kt` Auth. It supplies an Android Keystore-backed encrypted `SessionManager` to the SDK. The Kotlin module uses the Android Ktor client engine and lets the SDK persist, restore, refresh, and sign out sessions.
- `ApiService.java` keeps note REST calls on its executor, sends the canonical API fields to `/api/note` and `/api/note/{id}`, adds the user access token as Bearer auth, lists notes, and retries one request after SDK refresh on `401`.
- Update failures show the server's response, including its JSON error message, so invalid IDs, auth failures, and server-side validation errors can be distinguished while local edits remain saved.
- List and single-note fetch failures include the API response body. If the API returns `401` and SDK refresh fails, the app reports both the API's 401 body and the SDK refresh error, then tells the user to sign out and sign in again.
- Logcat tags `ApiService` and `SupabaseAuthManager` record request method/host/path, status and error body, token-presence (never token values), and session-refresh outcomes. Filter Logcat by either tag when diagnosing sync.
- `DBHelper.upsertOnlineNote` appends unseen server notes and updates already-linked server notes. Its UUID key prevents repeated list fetches from making duplicates.
- API and Supabase configuration is compile-time only; changing projects or hosts requires updating `ApiConfig` and rebuilding the app. Legacy values in SharedPreferences are ignored.
- The fixed API host is `https://notes.craftedalcove.com`.

## Server contract

The server must implement `MOBILE_INTEGRATION.md`: authenticated `GET`, `POST`, `PUT`, and `DELETE` on `/api/note`; list responses use `{ "data": [...] }`; created note responses include an `id`; and note IDs are UUID strings. The native sign-in flow requires existing accounts in the configured Supabase Auth project.

## Current scope

There is no background retry queue or conflict-resolution policy yet. The server list operation treats the server copy as authoritative for an already-linked UUID.

## Note attachments

- The note detail screen lists attachments for online notes, shows inline previews for supported image files, and supports opening, downloading, or removing each file. **Add file** opens Android's document picker; one file is uploaded at a time.
- Uploads use authenticated multipart `POST /api/note/{id}/images` with the `image` form field. The server accepts non-empty files up to 10 MiB and rejects executables. The Android client enforces the same size and empty-file limits before sending.
- Attachment metadata and one-hour signed download URLs come from `GET /api/note/{id}/images`. The client fetches a fresh list after upload or deletion; it does not persist signed URLs. Opening a file hands its signed URL to Android's registered viewer or browser.
- Removing a file calls `DELETE /api/note/{id}/images/{imageId}`. Attachments require a linked online note; local-only notes cannot upload or list server attachments.

## SDK references

- [Supabase Kotlin installation](https://supabase.com/docs/reference/kotlin/installing)
- [Password sign-in](https://supabase.com/docs/reference/kotlin/auth-signinwithpassword)
- [Retrieve and refresh sessions](https://supabase.com/docs/reference/kotlin/auth-getsession), [refresh session](https://supabase.com/docs/reference/kotlin/auth-refreshsession)
- [Auth session status](https://supabase.com/docs/reference/kotlin/auth-onauthstatechange)

- Opening the widget editor immediately shows the local note, then fetches the full server note list in the background. A small spinner appears in the editor's top-right while the request is active. Successful fetches reconcile online notes in local storage and refresh the widget; the editor field is refreshed only if the user has not started typing. If the note being edited was deleted on the server, the editor closes and reports the deletion. Fetch errors keep local content and hide the spinner. Tapping the widget's note name or the editor popup's ellipsized title opens that note in NoteView with Home behind it in the back stack.

- After a successful full server note-list fetch, Home upserts every returned note and removes locally stored notes marked online whose server UUID is absent from that response. Local-only notes and notes explicitly switched to offline mode are retained. A failed fetch does not trigger deletion; an empty successful list removes all online-linked local copies.

- The online cloud badge in the note list uses the theme's on-surface variant color, and the 24dp account toolbar icon uses the header title color, keeping both legible in light and dark modes.
