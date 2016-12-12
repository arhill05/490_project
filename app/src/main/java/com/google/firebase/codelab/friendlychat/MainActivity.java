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
import java.util.Collections;
import java.util.Comparator;

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

        // check if the user is logged in.
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


        // use the adapter provided by Firebase to create a new Listview adapter for the Buzzword class
        final FirebaseListAdapter<Buzzword> myAdapter = new FirebaseListAdapter<Buzzword>(this, Buzzword.class, android.R.layout.simple_list_item_1, mBuzzwordsRef) {
            @Override
            protected void populateView(View v, Buzzword model, int position) {
                // use the Buzzword's "word" to set what will be seen.
                ((TextView) v.findViewById(android.R.id.text1)).setText(model.getWord());
            }
        };

        listView.setAdapter(myAdapter);

        mBuzzwordsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                // access each child, and put it in a local array so we can do comparisons without calling
                // the database again
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

                // once a buzzword is clicked in the list, set the clicked buzzword and it's
                // reference to the Firebase entry to a local variable
                Buzzword buzzwordClicked = (Buzzword)adapterView.getItemAtPosition(position);
                DatabaseReference ref = myAdapter.getRef(position);

                String refKey = ref.getKey();

                // put both the reference to the buzzword and the buzzword itself as an extra on the intent
                //
                // note : Buzzword.class implements Serializable which allows us to serialize it as an extra

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

                        // calculate the weight of each buzzword in the phrase
                        weightTotal = weightTotal + buzzword.getWeight();
                        count++;
                    }
                }

                // hide the keyboard upon clicking the buzz button
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
                            resultsView.setText("Level: Worse than dad jokes. Rating: " + averageWeight);
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
                    incrementButton();
                }
                else {
                    TextView resultsView = (TextView) findViewById(R.id.resultsView);
                    resultsView.setText("Please enter a phrase");
                }


            }
        });
    }


    // change the button every time it is clicked
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

        // if the user is not signed in, direct them to the signIn activity
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

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
    }

}
