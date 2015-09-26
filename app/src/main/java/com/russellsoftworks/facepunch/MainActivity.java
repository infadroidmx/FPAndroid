package com.russellsoftworks.facepunch;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.client.HttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.CookieHandler;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences mPrefs;
    private String mUsername;
    private ProgressDialog mProgressDialog;
    private ArrayList<Forum> mForums = new ArrayList<Forum>();

    protected class LoadFrontPageTask extends AsyncTask<String, Void, Boolean> {
        protected Boolean doInBackground(String... args) {
            Boolean result = false;

            try {
                URL url = new URL("https://facepunch.com/forum.php");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setDoOutput(true);
                con.setRequestProperty("Cookie", mPrefs.getString("cookies", ""));
                con.connect();

                InputStream istream = con.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(istream));

                String line = "";
                StringBuilder response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line + System.getProperty("line.separator"));

                    if (line.contains("SECURITYTOKEN"))
                        mPrefs.edit().putString("securitytoken", line.toLowerCase().replace("var securitytoken = ", "").replace("\"", "").replace(";", "")).apply();
                }

                mUsername = Jsoup.parse(response.toString()).select("div#navbar-login").first().select("strong").first().text();

                Elements sectionTags = Jsoup.parse(response.toString()).select("table.forums");
                for (int i=0; i < sectionTags.size(); i++) {
                    Element section = sectionTags.get(i);
                    Elements forumTags = Jsoup.parse(section.toString()).select("tr.forumhead.foruminfo.L1");
                    for (int j=0; j < forumTags.size(); j++) {
                        Element forumTag = forumTags.get(j);
                        Forum forum = new Forum();
                        forum.Name = forumTag.text();
                        forum.ForumURL = Jsoup.parse(forumTag.toString()).select("a").first().attr("href");

                        Elements subForumTags = Jsoup.parse(section.toString()).select("h2.forumtitle");
                        for (int k=0; k < subForumTags.size(); k++) {
                            Element subForumTag = subForumTags.get(k);
                            SubForum sub = new SubForum();
                            sub.Name = subForumTag.text();
                            sub.ForumURL = Jsoup.parse(subForumTag.toString()).select("a").first().attr("href");

                            forum.SubForums.add(sub);
                        }

                        mForums.add(forum);
                    }
                }

                result = true;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return result;
        }

        protected void onPostExecute(Boolean result) {
            if (result) {
                populateFrontPage();
            } else {
                Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (mPrefs.getString("cookies", "").length() <= 0) {
            Intent intent = new Intent(getApplicationContext(), LoginActivity.class);
            startActivity(intent);
            finish();
        } else {
            mProgressDialog = new ProgressDialog(MainActivity.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setMessage("Loading front page..");
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.show();
            new LoadFrontPageTask().execute();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    public void populateFrontPage() {
        mProgressDialog.dismiss();
        LinearLayout forumList = (LinearLayout) findViewById(R.id.forumList);

        for (int i=0; i < mForums.size(); i++) {
            Forum forum = mForums.get(i);
            View forumView = getLayoutInflater().inflate(R.layout.front_forum, null);
            ((TextView) forumView.findViewById(R.id.forumName)).setText(forum.Name);

            for (int j=0; j < forum.SubForums.size(); j++) {
                SubForum sub = forum.SubForums.get(j);
                View subView = getLayoutInflater().inflate(R.layout.front_subforum, null);
                subView.setTag(R.string.key_forumURL, sub.ForumURL);
                subView.setTag(R.string.key_forumName, sub.Name);
                ((TextView) subView.findViewById(R.id.subForumName)).setText(sub.Name);
                subView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(getApplicationContext(), ForumActivity.class);
                        intent.putExtra("forumURL", v.getTag(R.string.key_forumURL).toString());
                        intent.putExtra("forumName", v.getTag(R.string.key_forumName).toString());
                        startActivity(intent);
                    }
                });

                ((LinearLayout) forumView.findViewById(R.id.subForumList)).addView(subView);

                if (j != forum.SubForums.size() - 1) {
                    View divider = getLayoutInflater().inflate(R.layout.divider_forum, null);
                    ((LinearLayout) forumView.findViewById(R.id.subForumList)).addView(divider);
                }
            }

            forumList.addView(forumView);
        }

        Toast.makeText(getApplicationContext(), "Welcome back, " + mUsername + "!", Toast.LENGTH_SHORT).show();
    }
}
