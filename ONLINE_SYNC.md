# Online notes and Supabase authentication

## Behavior

- Online note sync uses the Handy Notes server API at `/api/note`; Android does not query the Supabase database directly.
- Existing Supabase users sign in with email and password from the **Supabase Account** menu. The app does not create accounts.
- Server Settings stores the API host, Supabase project URL, and Supabase publishable key. The publishable key initializes the Auth SDK; API requests use the signed-in user's access token as `Authorization: Bearer …`.
- The Supabase Kotlin Auth SDK owns session persistence and automatic refresh. Session JSON is encrypted with AES-GCM using a non-exportable Android Keystore key. If a note API call returns `401`, the client asks the SDK to refresh the current session and retries that request once.
- When Home resumes with a configured, signed-in account, it requests `GET /api/note` and upserts the returned notes into SQLite using the server UUID as the deduplication key. Existing local-only notes remain in SQLite.
- Notes explicitly marked online are created with their local ID and retain the server UUID. Updates continue using `PUT /api/note/{id}`.
- Deleting a locally stored note marked online also sends `DELETE /api/note/{id}`. Local deletion proceeds immediately; if the server request fails, the app reports that the server copy may remain.

## Implementation notes

- `SupabaseAuthManager.kt` is a Java-callable bridge around `supabase-kt` Auth. It supplies an Android Keystore-backed encrypted `SessionManager` to the SDK. The Kotlin module uses the Android Ktor client engine and lets the SDK persist, restore, refresh, and sign out sessions.
- `ApiService.java` keeps note REST calls on its executor, adds only the user access token as Bearer auth, lists notes, and retries one request after SDK refresh on `401`.
- `DBHelper.upsertOnlineNote` appends unseen server notes and updates already-linked server notes. Its UUID key prevents repeated list fetches from making duplicates.
- Changing the Supabase URL or publishable key signs out and discards the current SDK client before saving the new configuration, preventing a session from being reused across projects.
- The default API host is `https://notes.craftedalcove.in`. Supabase URL and publishable key must be entered in Server Settings before signing in.

## Server contract

The server must implement `MOBILE_INTEGRATION.md`: authenticated `GET`, `POST`, `PUT`, and `DELETE` on `/api/note`; list responses use `{ "data": [...] }`; created note responses include an `id`; and note IDs are UUID strings. The native sign-in flow requires existing accounts in the configured Supabase Auth project.

## Current scope

There is no background retry queue or conflict-resolution policy yet. The server list operation treats the server copy as authoritative for an already-linked UUID. Image attachment endpoints are not used by this Android client.

## SDK references

- [Supabase Kotlin installation](https://supabase.com/docs/reference/kotlin/installing)
- [Password sign-in](https://supabase.com/docs/reference/kotlin/auth-signinwithpassword)
- [Retrieve and refresh sessions](https://supabase.com/docs/reference/kotlin/auth-getsession), [refresh session](https://supabase.com/docs/reference/kotlin/auth-refreshsession)
- [Auth session status](https://supabase.com/docs/reference/kotlin/auth-onauthstatechange)
