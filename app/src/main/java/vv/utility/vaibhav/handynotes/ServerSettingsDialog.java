package vv.utility.vaibhav.handynotes;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

public class ServerSettingsDialog {

    public static void show(final Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.dialog_server_settings, null);

        final EditText editServerUrl = view.findViewById(R.id.editServerUrl);
        final EditText editSupabaseUrl = view.findViewById(R.id.editSupabaseUrl);
        final EditText editPublishableKey = view.findViewById(R.id.editPublishableKey);

        editServerUrl.setText(ApiConfig.getBaseUrl(context));
        editSupabaseUrl.setText(ApiConfig.getSupabaseUrl(context));
        editPublishableKey.setText(ApiConfig.getSupabasePublishableKey(context));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Server Settings")
                .setView(view)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            String newUrl = editServerUrl.getText().toString().trim();
            String newSupabaseUrl = editSupabaseUrl.getText().toString().trim();
            String newPublishableKey = editPublishableKey.getText().toString().trim();

            if (newUrl.isEmpty()) newUrl = ApiConfig.DEFAULT_BASE_URL;

            final String serverUrl = newUrl;
            final String projectUrl = newSupabaseUrl;
            final String publishableKey = newPublishableKey;
            boolean supabaseConfigChanged = !projectUrl.equals(ApiConfig.getSupabaseUrl(context))
                    || !publishableKey.equals(ApiConfig.getSupabasePublishableKey(context));

            Runnable persistSettings = () -> {
                ApiConfig.setBaseUrl(context, serverUrl);
                ApiConfig.setSupabaseUrl(context, projectUrl);
                ApiConfig.setSupabasePublishableKey(context, publishableKey);
                Toast.makeText(context, "Server settings saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            };
            if (supabaseConfigChanged) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                SupabaseAuthManager.resetForConfigurationChange(context, new SupabaseAuthManager.Callback() {
                    @Override
                    public void onSuccess(String value) { persistSettings.run(); }

                    @Override
                    public void onError(String message) { persistSettings.run(); }
                });
            } else {
                persistSettings.run();
            }
        }));
        dialog.show();
    }
}
