package com.russellsoftworks.facepunch;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.CookieHandler;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class LoginActivity extends AppCompatActivity {

    protected class LoginTask extends AsyncTask<String, Void, Boolean> {
        protected Boolean doInBackground(String... args) {
            String username = args[0];
            String password_md5 = args[1];
            Boolean result = false;

            try {
                URL url = new URL("https://facepunch.com/login.php?do=login");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setDoInput(true);
                con.setDoOutput(true);

                String params = "vb_login_username=" + username + "&vb_login_md5password=" + password_md5 + "&vb_login_md5password_utf=" + password_md5 + "&cookieuser=1&securitytoken=guest&do=login";

                java.net.CookieManager cookieManager = new java.net.CookieManager();

                OutputStream ostream = con.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(ostream, "UTF-8"));
                writer.write(params);
                writer.flush();
                writer.close();

                con.connect();

                InputStream istream = con.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(istream));

                // TODO: You dont need to store the entire response, just look for
                // TODO: success string and break while loop and continue on..
                String line = "";
                String response = "";
                while ((line = reader.readLine()) != null) {
                    response += line;
                }

                if (response.toLowerCase().contains("thank you for logging in")) { // TODO: Crude success checking, look for something more accurate and reliable
                    List<String> cookieList = con.getHeaderFields().get("Set-Cookie");
                    String cookies = "";
                    if (cookieList != null) {
                        for (int i=0; i < cookieList.size(); i++) {
                            cookieManager.getCookieStore().add(null, HttpCookie.parse(cookieList.get(i)).get(0));
                        }
                    }

                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    prefs.edit().putString("cookies", TextUtils.join(";", cookieManager.getCookieStore().getCookies())).apply();

                    result = true;
                } else {
                    // TODO: Error handling
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return result;
        }

        protected void onPostExecute(Boolean result) {
            if (result) {
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
            } else {
                Toast.makeText(getApplicationContext(), "Wrong username or password.", Toast.LENGTH_SHORT).show(); // TODO: Needs better error handling, should use return codes as ints instead of booleans
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        getSupportActionBar().setElevation(0);

        ((EditText) findViewById(R.id.textPassword)).setTransformationMethod(new PasswordTransformationMethod());

        ((Button) findViewById(R.id.buttonLogin)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = ((EditText) findViewById(R.id.textUserName)).getText().toString();
                String password = ((EditText) findViewById(R.id.textPassword)).getText().toString();
                String password_md5 = Util.md5(password);

                new LoginTask().execute(username, password_md5);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        // TODO: Make this actually log you out
        if (id == R.id.action_signout) {
            Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Do nothing, you can't escape without logging in
    }
}
