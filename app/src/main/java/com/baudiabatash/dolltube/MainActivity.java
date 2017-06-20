package com.baudiabatash.dolltube;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTubeScopes;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.Comment;
import com.google.api.services.youtube.model.CommentSnippet;
import com.google.api.services.youtube.model.CommentThread;
import com.google.api.services.youtube.model.CommentThreadSnippet;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistItem;
import com.google.api.services.youtube.model.PlaylistItemListResponse;
import com.google.api.services.youtube.model.PlaylistListResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements View.OnClickListener
        ,EasyPermissions.PermissionCallbacks{

    GoogleAccountCredential mCredential;
    private TextView mOutputText;
    private Button mCallApiButton,btnPlayList,btnPlayItems;
    ProgressDialog mProgress;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private YouTube youTube;
    Observable<String> myObservable;
    Observable<List<PlaylistItem>> playListObservable;
    private DollUtil dollUtil;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { YouTubeScopes.YOUTUBE_FORCE_SSL,YouTubeScopes.YOUTUBEPARTNER };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dollUtil = new DollUtil(this);

        mOutputText = (TextView) findViewById(R.id.output);
        mCallApiButton = (Button) findViewById(R.id.call_api);
        btnPlayList = (Button) findViewById(R.id.playlist);
        btnPlayItems = (Button) findViewById(R.id.playItems);
        mCallApiButton.setOnClickListener(this);
        btnPlayList.setOnClickListener(this);
        btnPlayItems.setOnClickListener(this);

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling YouTube Data API ...");

        //GoogleClientSecrets clientSecret=

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());



        getResultsFromApi();


        HttpTransport transport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        youTube = new YouTube.Builder(
                transport, jsonFactory, mCredential)
                .setApplicationName("DollTube")
                .build();


    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (! dollUtil.isGooglePlayServicesAvailable()) {
            //acquireGooglePlayServices();
            dollUtil.acquireGooglePlayServices(REQUEST_GOOGLE_PLAY_SERVICES);
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! dollUtil.isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            //new MakeRequestTask(mCredential).execute();
        }
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }



    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }


    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.call_api:
                mOutputText.setText("");
                getResultsFromApi();
                break;

            case R.id.playlist:
                myObservable = Observable.create(new Observable.OnSubscribe<String>() {
                    @Override
                    public void call(Subscriber<? super String> subscriber) {
                        try {
                            String data = test();
                            subscriber.onNext(data); // Emit the contents of the URL
                            subscriber.onCompleted(); // Nothing more to emit
                        } catch (Exception e) {
                            subscriber.onError(e); // In case there are network errors
                        }
                    }
                });

                myObservable.subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Action1<String>() {
                            @Override
                            public void call(String s) {

                            }
                        });
                break;

            case R.id.playItems:
                playListObservable = Observable.create(new Observable.OnSubscribe<List<PlaylistItem>>() {
                    @Override
                    public void call(Subscriber<? super List<PlaylistItem>> subscriber) {
                        try {
                            List<PlaylistItem> data = getPlayListItem(Constant.PLAYLIST_ID);
                            subscriber.onNext(data); // Emit the contents of the URL
                            subscriber.onCompleted(); // Nothing more to emit
                        } catch (Exception e) {
                            subscriber.onError(e); // In case there are network errors
                        }

                    }
                });

                playListObservable.subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Action1<List<PlaylistItem>>() {
                            @Override
                            public void call(List<PlaylistItem> playlistItems) {
                                Log.d("DOOOO",playlistItems.size()+"");
                            }
                        });
                break;
        }


    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {

    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {

    }


    /**
     * An asynchronous task that handles the YouTube Data API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        //private com.google.api.services.youtube.YouTube mService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            /*HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.youtube.YouTube.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("DollTube")
                    .build();*/
        }

        /**
         * Background task to call YouTube Data API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch information about the "GoogleDevelopers" YouTube channel.
         * @return List of Strings containing information about the channel.
         * @throws IOException
         */
        private List<String> getDataFromApi() throws IOException {
            // Get a list of up to 10 files.
            List<String> channelInfo = new ArrayList<String>();
            ChannelListResponse result = youTube.channels().list("snippet,contentDetails,statistics")
                    .setForUsername("GoogleDevelopers")
                    .execute();
            List<Channel> channels = result.getItems();
            if (channels != null) {
                Channel channel = channels.get(0);
                channelInfo.add("This channel's ID is " + channel.getId() + ". " +
                        "Its title is '" + channel.getSnippet().getTitle() + ", " +
                        "and it has " + channel.getStatistics().getViewCount() + " views.");
            }
            return channelInfo;
        }


        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();
        }

        @Override
        protected void onPostExecute(List<String> output) {
            mProgress.hide();
            if (output == null || output.size() == 0) {
                mOutputText.setText("No results returned.");
            } else {
                output.add(0, "Data retrieved using the YouTube Data API:");
                mOutputText.setText(TextUtils.join("\n", output));
            }
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }
    }

    private String getPlayList(){
        String myres="";
        try {
            HashMap<String, String> parameters = new HashMap<>();
            parameters.put("part", "snippet,contentDetails");
            parameters.put("channelId", Constant.CHANNEL_ID);
            parameters.put("maxResults", "25");

            YouTube.Playlists.List playlistsListByChannelIdRequest = youTube.playlists().list(parameters.get("part").toString());
            if (parameters.containsKey("channelId") && parameters.get("channelId") != "") {
                playlistsListByChannelIdRequest.setChannelId(parameters.get("channelId").toString());
            }

            if (parameters.containsKey("maxResults")) {
                playlistsListByChannelIdRequest.setMaxResults(Long.parseLong(parameters.get("maxResults").toString()));
            }

            PlaylistListResponse response = playlistsListByChannelIdRequest.execute();
            /*Log.d("HHHHH",response.getItems().size()+"");
            List<Playlist> myPlayList = response.getItems();*/

            for(Playlist x: response.getItems()){
                Log.d("HHHHH",x.getId());
            }
            myres = response.toString();
            //Log.d("TTTTT",response.toString());
        }catch (IOException e) {
            e.printStackTrace();
        }

        return myres;
    }

    private List<PlaylistItem> getPlayListItem(String playlistId){
        List<PlaylistItem> playlistItems = new ArrayList<>();

        try {
            HashMap<String, String> parameters = new HashMap<>();
            parameters.put("part", "snippet,contentDetails");
            parameters.put("maxResults", "25");
            parameters.put("playlistId", playlistId);

            YouTube.PlaylistItems.List playlistItemsListByPlaylistIdRequest = youTube.playlistItems().list(parameters.get("part").toString());
            if (parameters.containsKey("maxResults")) {
                playlistItemsListByPlaylistIdRequest.setMaxResults(Long.parseLong(parameters.get("maxResults").toString()));
            }

            if (parameters.containsKey("playlistId") && parameters.get("playlistId") != "") {
                playlistItemsListByPlaylistIdRequest.setPlaylistId(parameters.get("playlistId").toString());
            }

            PlaylistItemListResponse response = playlistItemsListByPlaylistIdRequest.execute();
            playlistItems.addAll(response.getItems());
            //System.out.println(response);
            Log.d("DOOOO",response.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }

        return playlistItems;

    }


    private String test(){
        String retStr = "";
        try {
            HashMap<String, String> parameters = new HashMap<>();
            parameters.put("part", "snippet");


            CommentThread commentThread = new CommentThread();
            CommentThreadSnippet snippet = new CommentThreadSnippet();
            Comment topLevelComment = new Comment();
            CommentSnippet commentSnippet = new CommentSnippet();

            topLevelComment.setSnippet(commentSnippet);
            snippet.setTopLevelComment(topLevelComment);
            commentThread.setSnippet(snippet);

            YouTube.CommentThreads.Insert commentThreadsInsertRequest = youTube.commentThreads().insert(parameters.get("part").toString(), commentThread);
            Log.d("TTTT","YOOOO");
            CommentThread response = commentThreadsInsertRequest.execute();
            //Log.d("TTTT","YOOOO");

            System.out.println(response);


        } catch (GoogleJsonResponseException e) {
            Log.d("TTTT",e.getMessage());
            e.printStackTrace();
            System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
        } catch (UserRecoverableAuthIOException e) {
            startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
        }catch (Throwable t) {
            Log.d("TTTT",t.getCause().getMessage());
            t.printStackTrace();
        }


        return retStr;

    }
}
