package vv.utility.vaibhav.handynotes;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

public final class AccountDialog {
    private AccountDialog() {}

    public static void show(Context context, Runnable onSessionChanged) {
        Context appContext = context.getApplicationContext();
        SupabaseAuthManager.getSignedInEmail(appContext, new SupabaseAuthManager.Callback() {
            @Override
            public void onSuccess(String email) {
                if (email != null && !email.isEmpty()) {
                    showSignedIn(context, email, onSessionChanged);
                } else {
                    showSignIn(context, onSessionChanged);
                }
            }

            @Override
            public void onError(String message) {
                showSignIn(context, onSessionChanged);
            }
        });
    }

    private static void showSignIn(Context context, Runnable onSessionChanged) {
        LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(context, 24);
        fields.setPadding(horizontalPadding, dp(context, 12), horizontalPadding, 0);

        TextView message = new TextView(context);
        message.setText("Sign in or create an account to sync your notes online across devices.");
        fields.addView(message);

        EditText emailField = new EditText(context);
        emailField.setHint("Email");
        emailField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailField.setSingleLine(true);
        fields.addView(emailField);

        EditText passwordField = new EditText(context);
        passwordField.setHint("Password");
        passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordField.setSingleLine(true);
        fields.addView(passwordField);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Account Login")
                .setView(fields)
                .setPositiveButton("Sign In", null)
                .setNeutralButton("Sign Up", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String email = emailField.getText().toString().trim();
                String password = passwordField.getText().toString();
                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(context, "Enter your email and password", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                SupabaseAuthManager.signIn(context, email, password, new SupabaseAuthManager.Callback() {
                    @Override
                    public void onSuccess(String signedInEmail) {
                        dialog.dismiss();
                        Toast.makeText(context, "Signed in successfully", Toast.LENGTH_SHORT).show();
                        if (onSessionChanged != null) onSessionChanged.run();
                    }

                    @Override
                    public void onError(String error) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                    }
                });
            });

            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String email = emailField.getText().toString().trim();
                String password = passwordField.getText().toString();
                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(context, "Enter your email and password", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
                SupabaseAuthManager.signUp(context, email, password, new SupabaseAuthManager.Callback() {
                    @Override
                    public void onSuccess(String signedInEmail) {
                        dialog.dismiss();
                        Toast.makeText(context, "Account created! Signed in as " + signedInEmail, Toast.LENGTH_LONG).show();
                        if (onSessionChanged != null) onSessionChanged.run();
                    }

                    @Override
                    public void onError(String error) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });

        dialog.show();
    }

    private static void showSignedIn(Context context, String email, Runnable onSessionChanged) {
        new AlertDialog.Builder(context)
                .setTitle("Account")
                .setMessage("Signed in as " + email + "\n\nOnline note sync is active.")
                .setPositiveButton("Sign Out", (dialog, which) -> SupabaseAuthManager.signOut(context,
                        new SupabaseAuthManager.Callback() {
                            @Override
                            public void onSuccess(String value) {
                                Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show();
                                if (onSessionChanged != null) onSessionChanged.run();
                            }

                            @Override
                            public void onError(String error) {
                                Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Close", null)
                .show();
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
