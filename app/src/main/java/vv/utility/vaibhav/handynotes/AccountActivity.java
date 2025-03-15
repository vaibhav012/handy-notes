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
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AccountActivity extends AppCompatActivity {

    Button logoutButton, backupButton, restoreButton;
    TextView name, username, email, phone;
    String uname;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        logoutButton = (Button) findViewById(R.id.logoutButton);
        backupButton = (Button) findViewById(R.id.backupButton);
        restoreButton = (Button) findViewById(R.id.restoreButton);
        name = (TextView) findViewById(R.id.name);
        username = (TextView) findViewById(R.id.username);
        email = (TextView) findViewById(R.id.email);
        phone = (TextView) findViewById(R.id.phone);

        if(!isConnectedToInternet()){
            Toast.makeText(getBaseContext(),"No Internet Connection Detected", Toast.LENGTH_SHORT).show();
            finish();
        }
        else if(!checkLogin()){
            Intent i = new Intent(getBaseContext(), RegisterActivity.class);
            startActivity(i);
        }
        else if(checkLogin()) {
            getDetails();
        }

        logoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logout();
            }
        });

        backupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getBaseContext(), "feature not yet active", Toast.LENGTH_SHORT).show();
            }
        });

        restoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getBaseContext(), "feature not yet active", Toast.LENGTH_SHORT).show();
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
        switch (item.getItemId()) {
            case R.id.action_add_note:
                Toast.makeText(getBaseContext(), "Add From Home Page", Toast.LENGTH_LONG).show();
                return true;

            case R.id.action_account:
                return true;

            case R.id.action_about_us:
                Toast.makeText(getBaseContext(),"Robogenia",Toast.LENGTH_LONG).show();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void getDetails(){
        String urlSuffix = "?u="+uname;
        String mainUrl = "http://www.vaibhavtayal.com/handyNotes/getDetails.php";
        VVInternetQuery ru = new VVInternetQuery(AccountActivity.this, getApplicationContext());
        ru.execute(mainUrl, urlSuffix);
    }

    public boolean isConnectedToInternet() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Network[] networks = connectivityManager.getAllNetworks();
            NetworkInfo networkInfo;
            for (Network mNetwork : networks) {
                networkInfo = connectivityManager.getNetworkInfo(mNetwork);
                if (networkInfo.getState().equals(NetworkInfo.State.CONNECTED)) {
                    return true;
                }
            }
        }else {
            if (connectivityManager != null) {
                //noinspection deprecation
                NetworkInfo[] info = connectivityManager.getAllNetworkInfo();
                if (info != null) {
                    for (NetworkInfo anInfo : info) {
                        if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public Boolean checkLogin(){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        if(sharedPreferences.getString("handyNotesLIn","NULL").equals("NULL")) {
            return false;
        }
        else {
            uname = sharedPreferences.getString("handyNotesLIn", "NULL");
            String urlSuffix = "?u="+uname+"&device="+ Settings.Secure.getString(getBaseContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            String mainUrl = "http://www.vaibhavtayal.com/handyNotes/checkValidUserDevice.php";
            VVInternetQuery ru = new VVInternetQuery(AccountActivity.this, getApplicationContext());
            ru.execute(mainUrl, urlSuffix);
            return true;
        }
    }

    public void logout(){
        SharedPreferences sharedpreferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        SharedPreferences.Editor editor = sharedpreferences.edit();
        editor.putString("handyNotesLIn", "NULL");
        editor.commit();
        String urlSuffix = "?u="+uname+"&device="+ Settings.Secure.getString(getBaseContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        String mainUrl = "http://www.vaibhavtayal.com/handyNotes/logout.php";
        VVInternetQuery ru = new VVInternetQuery(AccountActivity.this, getApplicationContext());
        ru.execute(mainUrl, urlSuffix);
        recreate();
    }

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
            if(s.equals("validUserDevice")){

            }
            else if(s.equals("invalidUserDevice")){
                Toast.makeText(getBaseContext(), "Invalid Session Login Again", Toast.LENGTH_LONG).show();
                logout();
            }
            else if(s.equals("loggedOut")){
                Toast.makeText(getBaseContext(), "Logged Out", Toast.LENGTH_LONG).show();
            }
            else if(!s.equals("0") || !s.equals("")) {
                username.setText("Username : ");
                name.setText("Name : ");
                email.setText("Email : ");
                phone.setText("Phone : ");
                char [] a = s.toCharArray();
                int count = 0;
                for(int i = 0; i < a.length; i++){
                    if(a[i] == '&' && a[i-1] != '&'){
                        count ++;
                    }
                    else {
                        switch (count) {
                            case 0:
                                username.setText(username.getText().toString() + a[i]);
                                break;
                            case 1:
                                name.setText(name.getText().toString() + a[i]);
                                break;
                            case 2:
                                email.setText(email.getText().toString() + a[i]);
                                break;
                            case 3:
                                phone.setText(phone.getText().toString() + a[i]);
                                break;
                        }
                    }
                }
            }
            else {
                Toast.makeText(applicationContext, "Network error : try again later", Toast.LENGTH_LONG).show();
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
