package com.russellsoftworks.facepunch;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class ForumActivity extends AppCompatActivity {

    private SharedPreferences mPrefs;
    private ProgressDialog mProgressDialog;
    private ArrayList<ThreadListing> mThreads = new ArrayList<ThreadListing>();

    protected class LoadForumTask extends AsyncTask<String, Void, Boolean> {
        protected Boolean doInBackground(String... args) {
            Boolean result = false;
            String forumURL = args[0];

            try {
                URL url = new URL("https://facepunch.com/" + forumURL);

                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setDoInput(true);
                con.setDoOutput(true);
                con.setRequestProperty("Cookie", mPrefs.getString("cookies", ""));

                OutputStream ostream = con.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(ostream, "UTF-8"));
                writer.write("securitytoken=" + mPrefs.getString("securitytoken", ""));
                writer.flush();
                writer.close();

                con.connect();

                CookieManager cookieManager = new CookieManager();

                InputStream istream = con.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(istream));

                String line = "";
                StringBuilder response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line + System.getProperty("line.separator"));

                    if (line.contains("SECURITYTOKEN"))
                        mPrefs.edit().putString("securitytoken", line.toLowerCase().replace("var securitytoken = ", "").replace("\"", "").replace(";", "")).apply();
                }

                Elements titleTags = Jsoup.parse(response.toString()).select("tr.threadbit");
                for (int i=0; i < titleTags.size(); i++) {
                    Element titleTagOuter = titleTags.get(i);
                    Element titleTag = Jsoup.parse(titleTagOuter.html()).select("a.title").first();
                    ThreadListing thread = new ThreadListing();
                    thread.Title = titleTag.text();
                    thread.ThreadURL = titleTag.attr("href");
                    thread.IsSticky = titleTagOuter.hasClass("sticky");
                    thread.IsLocked = titleTagOuter.hasClass("lock");
                    thread.Author = titleTagOuter.select("div.author a").first().text();
                    thread.CurrentlyViewing = titleTagOuter.select("span.viewers").text();

                    if (titleTagOuter.select("a.newposts").first() != null)
                        thread.NewPosts = titleTagOuter.select("a.newposts").first().text().toString();

                    mThreads.add(thread);
                }

                result = true;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return result;
        }

        protected void onPostExecute(Boolean result) {
            if (result) {
                populateThreadList();
            } else {
                Toast.makeText(getApplicationContext(), "Problem loading forum!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forum);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        Intent intent = getIntent();
        if (intent.getStringExtra("forumURL") != null && intent.getStringExtra("forumName") != null) {
            mProgressDialog = new ProgressDialog(ForumActivity.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setMessage("Loading forum..");
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.show();

            new LoadForumTask().execute(intent.getStringExtra("forumURL"));
            setTitle(intent.getStringExtra("forumName"));
        } else {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_forum, menu);
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

    protected void populateThreadList() {
        mProgressDialog.dismiss();
        LinearLayout threadList = (LinearLayout) findViewById(R.id.threadList);

        for (int i=0; i < mThreads.size(); i++) {
            ThreadListing thread = mThreads.get(i);
            View view = getLayoutInflater().inflate(R.layout.forum_title, null);
            view.setTag(R.string.key_threadTitle, thread.Title);
            view.setTag(R.string.key_threadURL, thread.ThreadURL);
            ((TextView) view.findViewById(R.id.threadTitle)).setText(thread.Title);

            String authorText = thread.Author;
            if (thread.CurrentlyViewing.length() > 0)
                authorText += " • " + thread.CurrentlyViewing;

            ((TextView) view.findViewById(R.id.threadAuthor)).setText(authorText);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(getApplicationContext(), ThreadActivity.class);
                    intent.putExtra("threadURL", v.getTag(R.string.key_threadURL).toString());
                    intent.putExtra("threadTitle", v.getTag(R.string.key_threadTitle).toString());
                    startActivity(intent);
                }
            });

            if (thread.IsSticky) {
                view.setBackgroundColor(getResources().getColor(R.color.thread_sticky));
                ((TextView) view.findViewById(R.id.threadTitle)).setTextColor(Color.rgb(146, 146, 75)); // TODO: Define in Colors.xml
                ((TextView) view.findViewById(R.id.threadAuthor)).setTextColor(Color.rgb(146, 146, 75)); // TODO: Define in Colors.xml

                if (!thread.IsLocked)
                    ((TextView) view.findViewById(R.id.threadTitle)).setTypeface(null, Typeface.BOLD);
                    ((TextView) view.findViewById(R.id.threadAuthor)).setTypeface(null, Typeface.BOLD);
            } else if (thread.IsLocked) {
                view.setBackgroundColor(Color.rgb(200, 200, 200));
            }

            if (thread.NewPosts.length() > 0) {
                ((TextView) view.findViewById(R.id.newPostsText)).setText(thread.NewPosts);
                ((TextView) view.findViewById(R.id.newPostsText)).setVisibility(View.VISIBLE);
            } else {
                ((LinearLayout) view.findViewById(R.id.newPostsText).getParent()).removeView(view.findViewById(R.id.newPostsText));
            }

            threadList.addView(view);

            if (i != mThreads.size() - 1) {
                View divider = getLayoutInflater().inflate(R.layout.divider_forum, null);
                threadList.addView(divider);
            }
        }
    }
}
