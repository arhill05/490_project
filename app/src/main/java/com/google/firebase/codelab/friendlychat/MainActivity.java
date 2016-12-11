/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.codelab.friendlychat;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.database.FirebaseListAdapter;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.OnConnectionFailedListener {




    public static class BuzzwordViewHolder extends RecyclerView.ViewHolder {
        public TextView wordTextView;
        public TextView weightTextView;

        public BuzzwordViewHolder(View v) {
            super(v);
            wordTextView = (TextView) itemView.findViewById(R.id.wordTextView);
            weightTextView = (TextView) itemView.findViewById(R.id.weightTextView);
        }
    }

    private static final String TAG = "MainActivity";
    public static final String MESSAGES_CHILD = "messages";
    private static final int REQUEST_INVITE = 1;
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 50;
    public static final String ANONYMOUS = "anonymous";
    private static final String MESSAGE_SENT_EVENT = "message_sent";
    private String authenticatedUser = "";
    private String mUsername;
    private String mPhotoUrl;
    private SharedPreferences mSharedPreferences;

    private Button mSendButton;
    private RecyclerView mMessageRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private FirebaseRecyclerAdapter<Buzzword, BuzzwordViewHolder> mFirebaseAdapter;
    private ProgressBar mProgressBar;
    private DatabaseReference mFirebaseDatabaseReference;
    private DatabaseReference mBuzzwordsRef;
    private FirebaseAuth mFirebaseAuth;
    private FirebaseUser mFirebaseUser;
    private FirebaseAnalytics mFirebaseAnalytics;
    private EditText mMessageEditText;
    private AdView mAdView;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;
    private GoogleApiClient mGoogleApiClient;
    private int buttonCounter = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkAuth(getIntent());

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mUsername = ANONYMOUS;
        mFirebaseAuth = FirebaseAuth.getInstance();

        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);
        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().getReference();
        mBuzzwordsRef = FirebaseDatabase.getInstance().getReference("buzzWords");

        final ListView listView = (ListView) findViewById(R.id.buzzList);
        final ArrayList<Buzzword> buzzwords = new ArrayList<Buzzword>();
        final ArrayList<String> buzzwordValues = new ArrayList<String>();

        final FirebaseListAdapter<Buzzword> myAdapter = new FirebaseListAdapter<Buzzword>(this, Buzzword.class, android.R.layout.simple_list_item_1, mBuzzwordsRef) {

            @Override
            protected void populateView(View v, Buzzword model, int position) {
                ((TextView) v.findViewById(android.R.id.text1)).setText(model.getWord());
            }
        };

        listView.setAdapter(myAdapter);

        mBuzzwordsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // access each child
                for(DataSnapshot postSnapshot: snapshot.getChildren()) {
                    Buzzword buzzword = postSnapshot.getValue(Buzzword.class);
                    buzzwords.add(buzzword);
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Log.d(TAG, error.toString());
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                Buzzword buzzwordClicked = (Buzzword)adapterView.getItemAtPosition(position);
                DatabaseReference ref = myAdapter.getRef(position);

                String refKey = ref.getKey();

                Intent intent = new Intent(MainActivity.this, AddEditActivity.class);
                intent.putExtra("ItemRefKey", refKey);
                intent.putExtra("ClickedBuzzword", buzzwordClicked);
                startActivity(intent);

            }
        });

        mSendButton = (Button) findViewById(R.id.buzzButton);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView messageEdit = (TextView)findViewById(R.id.messageEditText);
                String text = messageEdit.getText().toString();
                Integer weightTotal = 0;
                Integer count = 0;

                for(Buzzword buzzword: buzzwords){
                    if(text.contains(buzzword.getWord())){
                        weightTotal = weightTotal + buzzword.getWeight();
                        count++;
                    }
                }

                hideSoftKeyboard(MainActivity.this);
                if(!messageEdit.getText().toString().isEmpty()) {
                    int averageWeight = weightTotal / count;
                    TextView resultsView = (TextView) findViewById(R.id.resultsView);
                    switch(averageWeight){
                        case 0:
                            resultsView.setText("No BS here. Rating: " + averageWeight);
                            break;
                        case 1:
                            resultsView.setText("Beginner BS. Rating: " + averageWeight);
                            break;
                        case 2:
                            resultsView.setText("Beginner BS. Rating: " + averageWeight);
                            break;
                        case 3:
                            resultsView.setText("Still believable. Rating: " + averageWeight);
                            break;
                        case 4:
                            resultsView.setText("Still believable. Rating: " + averageWeight);
                            break;
                        case 5:
                            resultsView.setText("The same level as \"are you with me?\". Rating: " + averageWeight);
                            break;
                        case 6:
                            resultsView.setText("Call the farm. They could use some fertilizer. Rating: " + averageWeight);
                            break;
                        case 7:
                            resultsView.setText("Level: MirZed. Rating: " + averageWeight);
                            break;
                        case 8:
                            resultsView.setText("Level: Pink Hair. Rating: " + averageWeight);
                            break;
                        case 9:
                            resultsView.setText("Level: BSBA in BS. Rating: " + averageWeight);
                            break;
                        case 10:
                            resultsView.setText("Level: Master of zero day vulnerabilities. Rating: " + averageWeight);
                            break;
                    }

                    if(averageWeight > 10) {
                        Toast.makeText(getApplicationContext(), "Your BS levels have exceeded anything known to man. You've broken the application", Toast.LENGTH_SHORT);
                        try {
                            Thread.sleep(5000);
                            throw new RuntimeException("Bullshit limits exceeded");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    incrementButton();
                }
                else {
                    TextView resultsView = (TextView) findViewById(R.id.resultsView);
                    resultsView.setText("Please enter a phrase");
                }


            }
        });
    }

    private void incrementButton() {
        final MediaPlayer mp = MediaPlayer.create(this, R.raw.digress);
        switch(buttonCounter){
            case 1:
                mSendButton.setText("BUZZDAT");
                mp.start();
                break;
            case 2:
                mSendButton.setText("SYNERGIZE");
                break;
            case 3:
                mSendButton.setText("MOVE FORWARD");
                break;
            case 4:
                mSendButton.setText("MINDMAP");
                break;
            case 5:
                mSendButton.setText("SUMMON BARKER");
                buttonCounter = 0;

                break;
        }
        buttonCounter++;
    }

    private void checkAuth(Intent reqIntent) {

        authenticatedUser = reqIntent.getStringExtra(Constants.AUTH_VAL);
        if(TextUtils.isEmpty(authenticatedUser)){
            Intent intent = new Intent(this, SignInActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onPause() {
        if (mAdView != null) {
            mAdView.pause();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdView != null) {
            mAdView.resume();
        }
    }

    @Override
    public void onDestroy() {
        if (mAdView != null) {
            mAdView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.add_item_menu:
                Intent jimmyBob = new Intent(MainActivity.this, AddEditActivity.class);
                startActivity(jimmyBob);
                return true;
            case R.id.sign_out_menu:
                mFirebaseAuth.signOut();
                Intent jimbob = new Intent(MainActivity.this, SignInActivity.class);
                startActivity(jimbob);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    };

    public static void hideSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager =
                (InputMethodManager) activity.getSystemService(
                        Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(
                activity.getCurrentFocus().getWindowToken(), 0);
    }



    // Fetch the config to determine the allowed length of messages.
    /*public void fetchConfig() {
        long cacheExpiration = 3600; // 1 hour in seconds
        // If developer mode is enabled reduce cacheExpiration to 0 so that each fetch goes to the
        // server. This should not be used in release builds.
        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        // Make the fetched config available via FirebaseRemoteConfig get<type> calls.
                        mFirebaseRemoteConfig.activateFetched();
                        applyRetrievedLengthLimit();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // There has been an error fetching the config
                        Log.w(TAG, "Error fetching config: " + e.getMessage());
                        applyRetrievedLengthLimit();
                    }
                });
    }*/

    /* @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_INVITE) {
            if (resultCode == RESULT_OK) {
                // Use Firebase Measurement to log that invitation was sent.
                Bundle payload = new Bundle();
                payload.putString(FirebaseAnalytics.Param.VALUE, "inv_sent");

                // Check how many invitations were sent and log.
                String[] ids = AppInviteInvitation.getInvitationIds(resultCode, data);
                Log.d(TAG, "Invitations sent: " + ids.length);
            } else {
                // Use Firebase Measurement to log that invitation was not sent
                Bundle payload = new Bundle();
                payload.putString(FirebaseAnalytics.Param.VALUE, "inv_not_sent");
                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SHARE, payload);

                // Sending failed or it was canceled, show failure message to the user
                Log.d(TAG, "Failed to send invitation.");
            }
        }
    }*/

    /**
     * Apply retrieved length limit to edit text field. This result may be fresh from the server or it may be from
     * cached values.
     */
    /*private void applyRetrievedLengthLimit() {
        Long friendly_msg_length = mFirebaseRemoteConfig.getLong("friendly_msg_length");
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(friendly_msg_length.intValue())});
        Log.d(TAG, "FML is: " + friendly_msg_length);
    }*/

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
    }

}
