package vv.utility.vaibhav.handynotes;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class RegisterActivity extends AppCompatActivity {

    Button loginButton, registerButton, showRegister, showLogin;
    TextView name, username, email, phone, password, confirmPassword;

    String uname;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        loginButton = (Button) findViewById(R.id.loginButton);
        registerButton = (Button) findViewById(R.id.registerButton);
        showRegister = (Button) findViewById(R.id.showRegister);
        showLogin = (Button) findViewById(R.id.showLogin);
        name = (TextView) findViewById(R.id.name);
        username = (TextView) findViewById(R.id.username);
        email = (TextView) findViewById(R.id.email);
        phone = (TextView) findViewById(R.id.phone);
        password = (TextView) findViewById(R.id.password);
        confirmPassword = (TextView) findViewById(R.id.confirmPassword);

        showRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                name.setVisibility(View.VISIBLE);
                email.setVisibility(View.VISIBLE);
                phone.setVisibility(View.VISIBLE);
                confirmPassword.setVisibility(View.VISIBLE);
                showRegister.setVisibility(View.GONE);
                showLogin.setVisibility(View.VISIBLE);
                registerButton.setVisibility(View.VISIBLE);
                loginButton.setVisibility(View.GONE);
            }
        });

        showLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                name.setVisibility(View.GONE);
                email.setVisibility(View.GONE);
                phone.setVisibility(View.GONE);
                confirmPassword.setVisibility(View.GONE);
                showRegister.setVisibility(View.VISIBLE);
                showLogin.setVisibility(View.GONE);
                registerButton.setVisibility(View.GONE);
                loginButton.setVisibility(View.VISIBLE);
            }
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }
        });

        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                register();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.action_bar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()) {
//            case R.id.action_add_note:
//                Toast.makeText(getBaseContext(), "Add From Home Page", Toast.LENGTH_LONG).show();
//                return true;
//
//            case R.id.action_account:
//                Toast.makeText(getBaseContext(),"Please Register or Login", Toast.LENGTH_SHORT).show();
//                return true;
//
//            case R.id.action_about_us:
//                Toast.makeText(getBaseContext(),"Robogenia",Toast.LENGTH_SHORT).show();
//                return true;

//            default:
//                return super.onOptionsItemSelected(item);
//        }
        return super.onOptionsItemSelected(item);
    }

    public void login() {
        uname = this.username.getText().toString().trim();
        String pass = this.password.getText().toString();
        String urlSuffix = "?u="+uname+"&k="+pass;
        String mainUrl = "http://www.vaibhavtayal.com/handyNotes/login.php";
        VVInternetQuery ru = new VVInternetQuery(RegisterActivity.this, getApplicationContext());
        ru.execute(mainUrl, urlSuffix);
    }

    public void register(){
        uname = this.username.getText().toString().trim();
        String pass = this.password.getText().toString();
        String cpass = this.confirmPassword.getText().toString();
        String nam = this.name.getText().toString().trim();
        String phon = this.phone.getText().toString().trim();
        String emai = this.email.getText().toString().trim();
        if(cpass.equals(pass)) {
            String urlSuffix = "?u=" + uname + "&k=" + pass + "&n=" + nam + "&p=" + phon + "&e=" + emai;
            String mainUrl = "http://www.vaibhavtayal.com/handyNotes/signup.php";
            VVInternetQuery ru = new VVInternetQuery(RegisterActivity.this, getApplicationContext());
            ru.execute(mainUrl, urlSuffix);
        }
        else{
            Toast.makeText(getBaseContext(),"Passwords do not match", Toast.LENGTH_LONG).show();
        }
    }

    public void registerSession(){
        String urlSuffix = "?u="+uname+"&device="+ Settings.Secure.getString(getBaseContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        String mainUrl = "http://www.vaibhavtayal.com/handyNotes/addSession.php";
        VVInternetQuery ru = new VVInternetQuery(RegisterActivity.this, getApplicationContext());
        ru.execute(mainUrl, urlSuffix);
    };

    public class VVInternetQuery extends AsyncTask<String, Void, String> {

        ProgressDialog loading;
        Context activityContext;
        Context applicationContext;

        public VVInternetQuery(Context c1, Context c2){
            activityContext = c1;
            applicationContext = c2;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loading = ProgressDialog.show(activityContext, "Please Wait", null, true, true);
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            loading.dismiss();
            if(s.equals("sessionRegistered")) {
                Toast.makeText(applicationContext, "Successful", Toast.LENGTH_LONG).show();
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("handyNotesLIn", uname);
                editor.commit();
                Intent intent = new Intent(getBaseContext(), AccountActivity.class);
                startActivity(intent);
                finish();
            }
            else if(s.equals("1")) {
                registerSession();
            }
            else if(s.equals("2") || s.equals("0")){
                Toast.makeText(getBaseContext(),"Incorrect Username or Password",Toast.LENGTH_LONG).show();
            }
            else if(s.equals("emailInvalid")){
                Toast.makeText(getBaseContext(),"Please Enter a valid Email",Toast.LENGTH_LONG).show();
            }
            else if(s.equals("emailRegistered")){
                Toast.makeText(getBaseContext(),"Email Already Registered, try Logging in",Toast.LENGTH_LONG).show();
            }
            else if(s.equals("usernameExists")){
                Toast.makeText(getBaseContext(),"Username Already Used, try a different one",Toast.LENGTH_LONG).show();
            }
            else{
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("handyNotesLIn", "NULL");
                editor.commit();
                Toast.makeText(getBaseContext(),"Network Error : Try again Later",Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected String doInBackground(String... params) {
            String queryUrl = params[0];
            String s = params[1];
            s = queryUrl+s;
            ReformatStringToUrl r = new ReformatStringToUrl();
            s = r.reformatString(s);
            BufferedReader bufferedReader = null;
            try {
                URL url = new URL(s);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(7000);
                bufferedReader = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String result;
                result = bufferedReader.readLine();
                return result;
            }catch(Exception e){
                return "Network Error : Check your internet connection or try later";
            }
        }
    }
}
