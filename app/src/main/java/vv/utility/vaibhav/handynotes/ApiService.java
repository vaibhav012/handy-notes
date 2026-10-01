package vv.utility.vaibhav.handynotes;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ApiService {

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
        builder.addHeader("Prefer", "return=representation");

        String supabaseKey = ApiConfig.getSupabasePublishableKey(context);
        if (supabaseKey != null && !supabaseKey.isEmpty()) {
            builder.addHeader("apikey", supabaseKey);
        }

        String accessToken = SupabaseAuthManager.getAccessTokenBlocking(context);
        if (accessToken == null || accessToken.isEmpty()) {
            throw new IOException("Sign in to your Supabase account to sync notes.");
        }
        builder.addHeader("Authorization", "Bearer " + accessToken);
        return builder;
    }

    private static Response executeWithSessionRetry(Context context, Request request) throws IOException {
        Response response = client.newCall(request).execute();
        if (response.code() != 401) {
            return response;
        }
        response.close();
        String refreshedToken;
        try {
            refreshedToken = SupabaseAuthManager.refreshAccessTokenBlocking(context);
        } catch (Exception e) {
            throw new IOException("Your session expired. Please sign in again.", e);
        }
        if (refreshedToken == null || refreshedToken.isEmpty()) {
            throw new IOException("Your session expired. Please sign in again.");
        }

        Request.Builder builder = request.newBuilder()
                .header("Authorization", "Bearer " + refreshedToken);

        String supabaseKey = ApiConfig.getSupabasePublishableKey(context);
        if (supabaseKey != null && !supabaseKey.isEmpty()) {
            builder.header("apikey", supabaseKey);
        }

        Request retry = builder.build();
        return client.newCall(retry).execute();
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
                jsonParam.put("noteName", title);
                jsonParam.put("note", content);

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
                jsonParam.put("id", onlineId);
                jsonParam.put("title", title);
                jsonParam.put("content", content);
                jsonParam.put("noteName", title);
                jsonParam.put("note", content);

                RequestBody body = RequestBody.create(jsonParam.toString(), JSON);
                String url = ApiConfig.getNoteDetailEndpointUrl(context, onlineId);

                Request.Builder builder = createAuthenticatedRequestBuilder(context, url);
                Request request;
                if (url.contains("supabase.co")) {
                    request = builder.patch(body).build();
                } else {
                    request = builder.put(body).build();
                }

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
                        postError(callback, "Server fetch error: " + response.code() + " " + response.message());
                    }
                }
            } catch (Exception e) {
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
                        postError(callback, "Server list error: " + response.code() + " " + response.message());
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
                postError(callback, "Failed to list server notes: " + e.getMessage());
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
