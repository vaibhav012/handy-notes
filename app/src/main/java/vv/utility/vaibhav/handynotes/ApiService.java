package vv.utility.vaibhav.handynotes;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ApiService {
    private static final String TAG = "ApiService";

    public static class NoteSyncResult {
        public String onlineId;
        public String title;
        public String content;

        public NoteSyncResult(String onlineId, String title, String content) {
            this.onlineId = onlineId;
            this.title = title;
            this.content = content;
        }
    }

    public static class ServerNote {
        public String onlineId;
        public String title;
        public String content;
        public Integer localId;

        public ServerNote(String onlineId, String title, String content, Integer localId) {
            this.onlineId = onlineId;
            this.title = title;
            this.content = content;
            this.localId = localId;
        }
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long MAX_ATTACHMENT_SIZE = 10L * 1024L * 1024L;
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    private static final ExecutorService executorService = Executors.newCachedThreadPool();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        }
        return false;
    }

    private static Request.Builder createAuthenticatedRequestBuilder(Context context, String url) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        builder.addHeader("Content-Type", "application/json");
        builder.addHeader("Accept", "application/json");

        String accessToken = SupabaseAuthManager.getAccessTokenBlocking(context);
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IOException("Sign in to your Supabase account to sync notes.");
        }
        builder.addHeader("Authorization", "Bearer " + accessToken);
        return builder;
    }

    private static Response executeWithSessionRetry(Context context, Request request) throws IOException {
        Response response = executeLogged(request);
        if (response.code() != 401) {
            return response;
        }
        ResponseBody authErrorBody = response.body();
        String authError = authErrorBody != null ? authErrorBody.string() : "";
        response.close();
        String refreshedToken;
        Log.i(TAG, "API returned 401; attempting Supabase session refresh for " + requestLabel(request));
        try {
            refreshedToken = SupabaseAuthManager.refreshAccessTokenBlocking(context);
        } catch (Exception e) {
            String detail = e.getMessage();
            Log.e(TAG, "Session refresh failed: " + e.getClass().getSimpleName()
                    + (detail == null || detail.isEmpty() ? "" : ": " + detail));
            throw new IOException("Server rejected the access token (401"
                    + (authError.isEmpty() ? "" : ": " + authError)
                    + "); could not refresh the Supabase session. Please sign out and sign in again."
                    + (detail == null || detail.isEmpty() ? "" : " (" + detail + ")"), e);
        }
        if (refreshedToken == null || refreshedToken.isEmpty()) {
            Log.e(TAG, "Session refresh completed without an access token.");
            throw new IOException("Server rejected the access token (401"
                    + (authError.isEmpty() ? "" : ": " + authError)
                    + "); could not refresh the Supabase session. Please sign out and sign in again.");
        }

        Request.Builder builder = request.newBuilder()
                .header("Authorization", "Bearer " + refreshedToken);

        Request retry = builder.build();
        Log.i(TAG, "Session refresh succeeded; retrying " + requestLabel(retry));
        return executeLogged(retry);
    }

    public static class NoteAttachment {
        public String id;
        public String fileName;
        public String contentType;
        public long sizeBytes;
        public String thumbnailUrl;
        public String downloadUrl;

        public NoteAttachment(String id, String fileName, String contentType, long sizeBytes,
                              String thumbnailUrl, String downloadUrl) {
            this.id = id;
            this.fileName = fileName;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.thumbnailUrl = thumbnailUrl;
            this.downloadUrl = downloadUrl;
        }
    }

    private static Response executeLogged(Request request) throws IOException {
        String label = requestLabel(request);
        Log.d(TAG, "Request " + label + "; bearer token attached="
                + (request.header("Authorization") != null));
        Response response = client.newCall(request).execute();
        if (response.isSuccessful()) {
            Log.i(TAG, label + " returned HTTP " + response.code());
        } else {
            String error = "";
            ResponseBody body = response.body();
            if (body != null) {
                try {
                    error = response.peekBody(4096).string();
                } catch (IOException ignored) {
                    error = "<could not read error body>";
                }
            }
            Log.e(TAG, label + " returned HTTP " + response.code() + " " + response.message()
                    + (error.isEmpty() ? "" : "; body=" + error));
        }
        return response;
    }

    private static String requestLabel(Request request) {
        String path = request.url().encodedPath().replaceAll("/[0-9a-fA-F-]{36}$", "/{id}");
        return request.method() + " " + request.url().host() + path;
    }

    private static void logFailure(String operation, Exception error) {
        String detail = error.getMessage();
        Log.e(TAG, operation + " failed: " + error.getClass().getSimpleName()
                + (detail == null || detail.isEmpty() ? "" : ": " + detail));
    }

    public static void createOnlineNote(final Context context, final int localNoteId, final String title, final String content, final ApiCallback<NoteSyncResult> callback) {
        if (!isNetworkAvailable(context)) {
            postError(callback, "No internet connection available");
            return;
        }

        executorService.execute(() -> {
            try {
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("localId", localNoteId);
                jsonParam.put("title", title);
                jsonParam.put("content", content);

                RequestBody body = RequestBody.create(jsonParam.toString(), JSON);
                String url = ApiConfig.getNotesEndpointUrl(context);

                Request request = createAuthenticatedRequestBuilder(context, url)
                        .post(body)
                        .build();

                try (Response response = executeWithSessionRetry(context, request)) {
                    if (response.isSuccessful()) {
                        ResponseBody responseBody = response.body();
                        String responseStr = responseBody != null ? responseBody.string().trim() : "";
                        String onlineId = "";
                        String resTitle = title;
                        String resContent = content;

                        if (!responseStr.isEmpty()) {
                            JSONObject jsonResponse;
                            if (responseStr.startsWith("[")) {
                                JSONArray array = new JSONArray(responseStr);
                                jsonResponse = array.length() > 0 ? array.getJSONObject(0) : new JSONObject();
                            } else {
                                jsonResponse = new JSONObject(responseStr);
                            }

                            if (jsonResponse.has("data") && jsonResponse.get("data") instanceof JSONObject) {
                                jsonResponse = jsonResponse.getJSONObject("data");
                            }

                            if (jsonResponse.has("id")) {
                                onlineId = jsonResponse.getString("id");
                            } else if (jsonResponse.has("onlineId")) {
                                onlineId = jsonResponse.getString("onlineId");
                            } else if (jsonResponse.has("_id")) {
                                onlineId = jsonResponse.getString("_id");
                            }

                            if (jsonResponse.has("title")) {
                                resTitle = jsonResponse.getString("title");
                            } else if (jsonResponse.has("noteName")) {
                                resTitle = jsonResponse.getString("noteName");
                            }

                            if (jsonResponse.has("content")) {
                                resContent = jsonResponse.getString("content");
                            } else if (jsonResponse.has("note")) {
                                resContent = jsonResponse.getString("note");
                            }
                        }

                        if (onlineId.isEmpty()) {
                            throw new IOException("The server did not return the created note ID.");
                        }

                        NoteSyncResult result = new NoteSyncResult(onlineId, resTitle, resContent);
                        postSuccess(callback, result);
                    } else {
                        ResponseBody errorBody = response.body();
                        String errStr = errorBody != null ? errorBody.string() : "";
                        postError(callback, "Server returned error " + response.code() + ": " + response.message() + (errStr.isEmpty() ? "" : " (" + errStr + ")"));
                    }
                }
            } catch (Exception e) {
                logFailure("Create online note", e);
                postError(callback, "Failed to create online note: " + e.getMessage());
            }
        });
    }

    public static void updateOnlineNote(final Context context, final String onlineId, final String title, final String content, final ApiCallback<Boolean> callback) {
        if (!isNetworkAvailable(context)) {
            postError(callback, "No internet connection available");
            return;
        }

        executorService.execute(() -> {
            try {
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("title", title);
                jsonParam.put("content", content);

                RequestBody body = RequestBody.create(jsonParam.toString(), JSON);
                String url = ApiConfig.getNoteDetailEndpointUrl(context, onlineId);

                Request request = createAuthenticatedRequestBuilder(context, url)
                        .put(body)
                        .build();

                try (Response response = executeWithSessionRetry(context, request)) {
                    if (response.isSuccessful()) {
                        postSuccess(callback, true);
                    } else {
                        ResponseBody errorBody = response.body();
                        String errStr = errorBody != null ? errorBody.string() : "";
                        postError(callback, "Server update error " + response.code() + ": " + response.message() + (errStr.isEmpty() ? "" : " (" + errStr + ")"));
                    }
                }
            } catch (Exception e) {
                logFailure("Update online note", e);
                postError(callback, "Failed to update online note: " + e.getMessage());
            }
        });
    }

    public static void fetchOnlineNote(final Context context, final String onlineId, final ApiCallback<NoteSyncResult> callback) {
        if (!isNetworkAvailable(context)) {
            postError(callback, "No internet connection available");
            return;
        }

        executorService.execute(() -> {
            try {
                String url = ApiConfig.getNoteDetailEndpointUrl(context, onlineId);

                Request request = createAuthenticatedRequestBuilder(context, url)
                        .get()
                        .build();

                try (Response response = executeWithSessionRetry(context, request)) {
                    if (response.isSuccessful()) {
                        ResponseBody responseBody = response.body();
                        String responseStr = responseBody != null ? responseBody.string().trim() : "";

                        String resTitle = "";
                        String resContent = "";

                        if (!responseStr.isEmpty()) {
                            JSONObject jsonResponse;
                            if (responseStr.startsWith("[")) {
                                JSONArray array = new JSONArray(responseStr);
                                jsonResponse = array.length() > 0 ? array.getJSONObject(0) : new JSONObject();
                            } else {
                                jsonResponse = new JSONObject(responseStr);
                            }

                            if (jsonResponse.has("data") && jsonResponse.get("data") instanceof JSONObject) {
                                jsonResponse = jsonResponse.getJSONObject("data");
                            }

                            if (jsonResponse.has("title")) {
                                resTitle = jsonResponse.getString("title");
                            } else if (jsonResponse.has("noteName")) {
                                resTitle = jsonResponse.getString("noteName");
                            }

                            if (jsonResponse.has("content")) {
                                resContent = jsonResponse.getString("content");
                            } else if (jsonResponse.has("note")) {
                                resContent = jsonResponse.getString("note");
                            }
                        }

                        NoteSyncResult result = new NoteSyncResult(onlineId, resTitle, resContent);
                        postSuccess(callback, result);
                    } else {
                        ResponseBody errorBody = response.body();
                        String error = errorBody != null ? errorBody.string() : "";
                        postError(callback, "Server fetch error: " + response.code() + " " + response.message()
                                + (error.isEmpty() ? "" : " (" + error + ")"));
                    }
                }
            } catch (Exception e) {
                logFailure("Fetch online note", e);
                postError(callback, "Failed to fetch online note: " + e.getMessage());
            }
        });
    }

    public static void fetchOnlineNotes(final Context context, final ApiCallback<List<ServerNote>> callback) {
        if (!isNetworkAvailable(context)) {
            postError(callback, "No internet connection available");
            return;
        }

        executorService.execute(() -> {
            try {
                Request request = createAuthenticatedRequestBuilder(context, ApiConfig.getNotesEndpointUrl(context))
                        .get()
                        .build();
                try (Response response = executeWithSessionRetry(context, request)) {
                    if (!response.isSuccessful()) {
                        ResponseBody errorBody = response.body();
                        String error = errorBody != null ? errorBody.string() : "";
                        postError(callback, "Server list error: " + response.code() + " " + response.message()
                                + (error.isEmpty() ? "" : " (" + error + ")"));
                        return;
                    }
                    ResponseBody responseBody = response.body();
                    String responseStr = responseBody != null ? responseBody.string().trim() : "";
                    JSONArray data = null;
                    if (responseStr.startsWith("[")) {
                        data = new JSONArray(responseStr);
                    } else if (responseStr.startsWith("{")) {
                        JSONObject jsonResponse = new JSONObject(responseStr);
                        data = jsonResponse.optJSONArray("data");
                    }

                    List<ServerNote> notes = new ArrayList<>();
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.optJSONObject(i);
                            if (item == null) continue;
                            String id = item.optString("id", item.optString("onlineId", ""));
                            if (id.isEmpty()) continue;
                            Integer localId = item.isNull("localId") || !item.has("localId")
                                    ? null : item.optInt("localId");
                            String title = item.optString("title", item.optString("noteName", "Untitled Note"));
                            String content = item.optString("content", item.optString("note", ""));
                            notes.add(new ServerNote(id, title, content, localId));
                        }
                    }
                    postSuccess(callback, notes);
                }
            } catch (Exception e) {
                logFailure("List online notes", e);
                postError(callback, "Failed to list server notes: " + e.getMessage());
            }
        });
    }

    public static void fetchNoteAttachments(final Context context, final String noteId,
                                           final ApiCallback<List<NoteAttachment>> callback) {
        if (!isNetworkAvailable(context)) {
            postError(callback, "No internet connection available");
            return;
        }
        executorService.execute(() -> {
            try {
                String url = ApiConfig.getNoteDetailEndpointUrl(context, noteId) + "/images";
                Request request = createAuthenticatedRequestBuilder(context, url).get().build();
                try (Response response = executeWithSessionRetry(context, request)) {
                    if (!response.isSuccessful()) {
                        ResponseBody body = response.body();
                        String error = body != null ? body.string() : "";
                        postError(callback, "Could not load attachments: " + response.code()
                                + (error.isEmpty() ? "" : " (" + error + ")"));
                        return;
                    }
                    ResponseBody body = response.body();
                    JSONObject json = new JSONObject(body != null ? body.string() : "{}");
                    JSONArray data = json.optJSONArray("data");
                    List<NoteAttachment> attachments = new ArrayList<>();
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject item = data.optJSONObject(i);
                            if (item == null) continue;
                            attachments.add(new NoteAttachment(item.optString("id"), item.optString("fileName"),
                                    item.optString("contentType"), item.optLong("sizeBytes"),
                                    item.optString("thumbnailUrl"), item.optString("downloadUrl")));
                        }
                    }
                    postSuccess(callback, attachments);
                }
            } catch (Exception e) {
                logFailure("List note attachments", e);
                postError(callback, "Could not load attachments: " + e.getMessage());
            }
        });
    }

    public static void loadAttachmentPreview(final String previewUrl, final int targetSizePx,
                                             final ApiCallback<Bitmap> callback) {
        if (previewUrl == null || previewUrl.isEmpty()) {
            postError(callback, "Preview is unavailable");
            return;
        }
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(previewUrl).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                    throw new IOException("Image preview request failed");
                }
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_ATTACHMENT_SIZE) throw new IOException("Image preview is too large");
                        output.write(buffer, 0, count);
                    }
                }
                byte[] bytes = output.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                BitmapFactory.Options decode = new BitmapFactory.Options();
                decode.inSampleSize = 1;
                while (bounds.outWidth / decode.inSampleSize > targetSizePx * 2
                        || bounds.outHeight / decode.inSampleSize > targetSizePx * 2) {
                    decode.inSampleSize *= 2;
                }
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decode);
                if (bitmap == null) throw new IOException("Could not decode image preview");
                postSuccess(callback, bitmap);
            } catch (Exception e) {
                logFailure("Load note attachment preview", e);
                postError(callback, "Could not load image preview");
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public static void uploadNoteAttachment(final Context context, final String noteId, final Uri uri,
                                            final ApiCallback<Boolean> callback) {
        if (!isNetworkAvailable(context)) {
            postError(callback, "No internet connection available");
            return;
        }
        executorService.execute(() -> {
            try {
                String fileName = getAttachmentFileName(context, uri);
                byte[] fileBytes;
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("Could not read the selected file.");
                    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_ATTACHMENT_SIZE) throw new IOException("Files must be no larger than 10 MiB.");
                        output.write(buffer, 0, count);
                    }
                    if (total == 0) throw new IOException("The selected file is empty.");
                    fileBytes = output.toByteArray();
                }
                String mime = context.getContentResolver().getType(uri);
                MediaType mediaType = mime != null ? MediaType.parse(mime) : MediaType.parse("application/octet-stream");
                RequestBody fileBody = RequestBody.create(fileBytes, mediaType);
                RequestBody multipart = new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("image", fileName, fileBody).build();
                String url = ApiConfig.getNoteDetailEndpointUrl(context, noteId) + "/images";
                Request.Builder requestBuilder = createAuthenticatedRequestBuilder(context, url);
                requestBuilder.removeHeader("Content-Type");
                Request request = requestBuilder.post(multipart).build();
                try (Response response = executeWithSessionRetry(context, request)) {
                    if (response.isSuccessful()) {
                        postSuccess(callback, true);
                    } else {
                        ResponseBody body = response.body();
                        String error = body != null ? body.string() : "";
                        postError(callback, "Upload failed: " + response.code()
                                + (error.isEmpty() ? "" : " (" + error + ")"));
                    }
                }
            } catch (Exception e) {
                logFailure("Upload note attachment", e);
                postError(callback, "Upload failed: " + e.getMessage());
            }
        });
    }

    private static String getAttachmentFileName(Context context, Uri uri) {
        String fileName = null;
        try (android.database.Cursor cursor = context.getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) fileName = cursor.getString(0);
        } catch (Exception ignored) { }
        if (fileName == null || fileName.trim().isEmpty()) fileName = "attachment";
        return fileName;
    }

    public static void deleteNoteAttachment(final Context context, final String noteId, final String attachmentId,
                                            final ApiCallback<Boolean> callback) {
        if (!isNetworkAvailable(context)) {
            postError(callback, "No internet connection available");
            return;
        }
        executorService.execute(() -> {
            try {
                String url = ApiConfig.getNoteDetailEndpointUrl(context, noteId) + "/images/" + attachmentId;
                Request request = createAuthenticatedRequestBuilder(context, url).delete().build();
                try (Response response = executeWithSessionRetry(context, request)) {
                    if (response.isSuccessful()) postSuccess(callback, true);
                    else {
                        ResponseBody body = response.body();
                        String error = body != null ? body.string() : "";
                        postError(callback, "Delete failed: " + response.code()
                                + (error.isEmpty() ? "" : " (" + error + ")"));
                    }
                }
            } catch (Exception e) {
                logFailure("Delete note attachment", e);
                postError(callback, "Delete failed: " + e.getMessage());
            }
        });
    }

    public static void deleteOnlineNote(final Context context, final String onlineId, final ApiCallback<Boolean> callback) {
        if (!isNetworkAvailable(context)) {
            postError(callback, "No internet connection available");
            return;
        }
        executorService.execute(() -> {
            try {
                Request request = createAuthenticatedRequestBuilder(context,
                        ApiConfig.getNoteDetailEndpointUrl(context, onlineId))
                        .delete()
                        .build();
                try (Response response = executeWithSessionRetry(context, request)) {
                    if (response.isSuccessful()) {
                        postSuccess(callback, true);
                    } else if (response.code() == 404) {
                        postSuccess(callback, true);
                    } else {
                        postError(callback, "Server delete error: " + response.code() + " " + response.message());
                    }
                }
            } catch (Exception e) {
                logFailure("Delete online note", e);
                postError(callback, "Failed to delete server note: " + e.getMessage());
            }
        });
    }

    private static <T> void postSuccess(final ApiCallback<T> callback, final T result) {
        if (callback != null) {
            mainHandler.post(() -> callback.onSuccess(result));
        }
    }

    private static <T> void postError(final ApiCallback<T> callback, final String errorMessage) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(errorMessage));
        }
    }
}
