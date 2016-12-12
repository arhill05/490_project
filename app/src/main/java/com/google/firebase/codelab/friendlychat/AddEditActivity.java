package com.google.firebase.codelab.friendlychat;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class AddEditActivity extends AppCompatActivity {


    boolean isNew = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit);
        final EditText wordEditText = (EditText) findViewById(R.id.addEditWordEditText);
        final Bundle extras = getIntent().getExtras();
        final EditText weightEditText = (EditText) findViewById(R.id.addEditWeightEditText);

        // if extras is not null and has the key to the reference in it, set a local variable to it. otherwise, set it to null
        final String refKey = (extras != null && extras.containsKey("ItemRefKey")) ? (String) getIntent().getExtras().getString("ItemRefKey") : null;

        // if extras is not null and has a buzzword in it, getSerializableExtra on the clicked buzzword to get the buzzword object and assign it to
        // a local variable. otherwise, set it to null.
        Buzzword buzzword = (extras != null && extras.containsKey("ClickedBuzzword")) ? (Buzzword)getIntent().getSerializableExtra("ClickedBuzzword") : null;
        final DatabaseReference mDatabaseReference = FirebaseDatabase.getInstance().getReference("buzzWords");

        // if the local buzzword object is null, that means it was not passed to this activity in extras and therefore it is new and
        // the EditTexts should be left blank. otherwise, set the EditTexts to the properties of the buzzwords to be able to edit them.
        if(buzzword != null) {
            wordEditText.setText(buzzword.getWord(), TextView.BufferType.EDITABLE);
            weightEditText.setText(buzzword.getWeight().toString(), TextView.BufferType.EDITABLE);
        }
        else { isNew = true; }

        Button submitBtn = (Button) findViewById(R.id.buzzwordSubmitBtn);
        submitBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // if the EditTexts aren't empty
                if(!wordEditText.getText().toString().isEmpty() && !weightEditText.getText().toString().isEmpty()){

                    // if it's not a new object, we want to set the word and weight of the current object and not create a new one
                    if(!isNew) {
                        mDatabaseReference.child(refKey).child("word").setValue(wordEditText.getText().toString());
                        mDatabaseReference.child(refKey).child("weight").setValue(Integer.parseInt(weightEditText.getText().toString()));
                    }

                    // otherwise, create a new value
                    else {
                        Buzzword buzz = new Buzzword(wordEditText.getText().toString(), Integer.parseInt(weightEditText.getText().toString()));
                        mDatabaseReference.push().setValue(buzz);
                    }
                    finish();
                }
                else {
                    Toast toast = Toast.makeText(getApplicationContext(), "You must enter a value for both fields!", Toast.LENGTH_SHORT);
                    toast.show();
                }
            }
        });
    }
}
