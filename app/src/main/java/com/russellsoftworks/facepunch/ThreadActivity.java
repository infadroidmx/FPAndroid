package com.russellsoftworks.facepunch;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;
import com.melnykov.fab.ObservableScrollView;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.kefirsf.bb.BBProcessorFactory;
import org.kefirsf.bb.TextProcessor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class ThreadActivity extends AppCompatActivity {

    private SharedPreferences mPrefs;
    private ProgressDialog mProgressDialog;
    private ArrayList<Post> mPosts = new ArrayList<Post>();
    private String mThreadURL;
    private int mThreadPage = 1;
    private boolean mThreadLocked = false;

    protected class LoadThreadTask extends AsyncTask<String, Void, Boolean> {
        protected Boolean doInBackground(String... args) {
            Boolean result = false;
            String threadURL = args[0];

            try {
                URL url = new URL("https://facepunch.com/" + threadURL);

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

                InputStream istream = con.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(istream));

                String line = "";
                StringBuilder response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line + System.getProperty("line.separator"));

                    if (line.contains("SECURITYTOKEN"))
                        mPrefs.edit().putString("securitytoken", line.toLowerCase().replace("var securitytoken = ", "").replace("\"", "").replace(";", "")).apply();
                }

                Element paginationTag;
                Element pageNumTag;
                if ((paginationTag = Jsoup.parse(response.toString()).select("div.pagination").first()) != null && (pageNumTag = paginationTag.select("span.selected").first()) != null)
                    mThreadPage = Integer.valueOf(pageNumTag.text());

                Element newReplyTag = Jsoup.parse(response.toString()).select("span#reply_button").first();
                if (newReplyTag != null)
                    mThreadLocked = newReplyTag.text().toLowerCase().contains("closed");

                Elements postTags = Jsoup.parse(response.toString()).select("div.postdetails");

                for (int i=0; i < postTags.size(); i++) {
                    Element postTag = postTags.get(i);
                    Post post = new Post();
                    post.Body = postTag.select("div.content").first().select("blockquote").remove().html();//Jsoup.parse(postTag.select("div.content").first().html().replace("<br>", "br2n").replace("<br/>", "br2n").replace("<b>", "[b]")).text().replace("br2n", "\n");
                    post.Author = postTag.select("div.username_container").first().text();
                    mPosts.add(post);
                }

                result = true;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return result;
        }

        protected void onPostExecute(Boolean result) {
            if (result) {
                populateThread();
            } else {
                Toast.makeText(getApplicationContext(), "Problem loading thread!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    protected class SubmitPostTask extends AsyncTask<String, Void, Boolean> {
        protected Boolean doInBackground(String... args) {
            Boolean result = false;
            String threadURL = args[0];

            try {
                String postBody = URLEncoder.encode(args[1], "UTF-8");
                URL url = new URL("https://facepunch.com/" + threadURL);

                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setDoInput(true);
                con.setDoOutput(true);
                con.setRequestProperty("Cookie", mPrefs.getString("cookies", ""));

                OutputStream ostream = con.getOutputStream();
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(ostream, "UTF-8"));
                writer.write("securitytoken=" + mPrefs.getString("securitytoken", "") + "&fromquickreply=1&do=postreply&message=" + postBody);
                writer.flush();
                writer.close();

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

                result = true;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return result;
        }

        protected void onPostExecute(Boolean result) {
            if (result) {
                mProgressDialog.dismiss();
                loadThread(mThreadURL + "&page=" + (mThreadPage + 99999));
            } else {
                Toast.makeText(getApplicationContext(), "Problem submitting post!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thread);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        FloatingActionButton newPostButton = (FloatingActionButton) findViewById(R.id.newPostButton);
        newPostButton.attachToScrollView((ObservableScrollView) findViewById(R.id.postScrollView));

        Intent intent = getIntent();
        if (intent.getStringExtra("threadURL") != null && intent.getStringExtra("threadTitle") != null) {
            mThreadURL = intent.getStringExtra("threadURL");

            loadThread(mThreadURL + "&goto=newpost"); // By default we want to go to new posts in the thread

            setTitle(intent.getStringExtra("threadTitle"));
        } else {
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_thread, menu);
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

    protected void loadThread(String URL) {
        mProgressDialog = new ProgressDialog(ThreadActivity.this);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setMessage("Loading thread..");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.show();

        ((LinearLayout) findViewById(R.id.postList)).removeAllViews();
        mPosts.clear();

        new LoadThreadTask().execute(URL);
    }

    protected void populateThread() {
        LinearLayout postList = (LinearLayout) findViewById(R.id.postList);

        for (int i=0; i < mPosts.size(); i++) {
            Post post = mPosts.get(i);
            View view = getLayoutInflater().inflate(R.layout.thread_post, null);
            ((TextView) view.findViewById(R.id.authorName)).setText(post.Author);
            ((TextView) view.findViewById(R.id.postBody)).setText(Html.fromHtml(post.Body));

            postList.addView(view);

            if (i != mPosts.size() - 1) {
                View divider = getLayoutInflater().inflate(R.layout.divider_forum, null);
                postList.addView(divider);
            }
        }

        ((TextView) findViewById(R.id.pageNumberText)).setText("Page " + mThreadPage);
        mProgressDialog.dismiss();
    }

    public void onFirstPageClicked(View v) {
        if (mThreadPage != 1)
            loadThread(mThreadURL);
    }

    public void onPrevPageClicked(View v) {
        if (mThreadPage != 1)
            loadThread(mThreadURL + "&page=" + Math.max(mThreadPage - 1, 1));
    }

    // TODO: Consider a better, less fucking lazy way of doing next/last page..
    // TODO: Maybe by actually figuring out how many pages are in a thread? It's not that hard. Idiot.
    public void onNextPageClicked(View v) {
        loadThread(mThreadURL + "&page=" + (mThreadPage + 1)); // Facepunch automatically redirects non-existent page numbers to last page
    }

    public void onLastPageClicked(View v) {
        loadThread(mThreadURL + "&page=" + (mThreadPage + 99999)); // Facepunch automatically redirects non-existent page numbers to last page
    }

    public void onNewPostClicked(View v) {
        if (mThreadLocked) {
            Toast.makeText(getApplicationContext(), "This thread is locked!", Toast.LENGTH_SHORT).show();
            return;
        }

        LayoutInflater inflater = getLayoutInflater();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View dialogView = inflater.inflate(R.layout.dialog_newpost, null);

        builder.setView(dialogView);
        builder.setPositiveButton("Submit Post", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                submitPost(((EditText) dialogView.findViewById(R.id.newPostEditText)).getText().toString());
            }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        }).setTitle("New Post");

        builder.create().show();
    }

    public void submitPost(String body) {
        mProgressDialog = new ProgressDialog(ThreadActivity.this);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setMessage("Submitting post..");
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.show();

        new SubmitPostTask().execute(mThreadURL.replace("showthread", "newreply") + "&do=postreply", body);
    }
}
