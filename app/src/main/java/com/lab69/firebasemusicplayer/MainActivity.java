package com.lab69.firebasemusicplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.jean.jcplayer.model.JcAudio;
import com.example.jean.jcplayer.view.JcPlayerView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private boolean checkPermission = false;
 Uri uri;
 String songname,songurl;
 ListView mylistview;
 ArrayList<String> arrayListSongsName=new ArrayList<>();
 ArrayList<String> arrayListSongsUrl=new ArrayList<>();
 ArrayAdapter<String> arrayAdapter;

 JcPlayerView jcPlayerView;
    ArrayList<JcAudio> jcAudios = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mylistview=findViewById(R.id.mylistview);

        jcPlayerView=findViewById(R.id.jcplayer);
        retriveSongs();

        mylistview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                jcPlayerView.playAudio(jcAudios.get(position));
                jcPlayerView.setVisibility(View.VISIBLE);
                jcPlayerView.createNotification();
            }
        });



    }

    private void retriveSongs() {

        DatabaseReference databaseReference=FirebaseDatabase.getInstance().getReference("Songs");
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds:dataSnapshot.getChildren())
                {
                    Songs songObj=ds.getValue(Songs.class);

                    arrayListSongsName.add(songObj.getSongname());
                    arrayListSongsUrl.add(songObj.getSongurl());
                    jcAudios.add(JcAudio.createFromURL(songObj.getSongname(),songObj.getSongurl()));

                }

                arrayAdapter=new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_list_item_1,arrayListSongsName)
                {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {

                        View view= super.getView(position,convertView,parent);
                        TextView textView=view.findViewById(android.R.id.text1);
                        textView.setSingleLine(true);
                        textView.setMaxLines(1);
                        textView.setTextColor(Color.WHITE);

                        return view;
                    }
                };
                jcPlayerView.initPlaylist(jcAudios,null);
                mylistview.setAdapter(arrayAdapter);




            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });



    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.custom_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.nav_upload) {

            if (validatePermission())
            {
                pickSong();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    private void pickSong() {
        Intent intent_upload=new Intent();
        intent_upload.setType("audio/*");
        intent_upload.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent_upload,1);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
         //selectsongs
        if (requestCode==1)
        {
            if (resultCode==RESULT_OK)
            {
                uri= data.getData();
                Cursor mcursor=getApplicationContext().getContentResolver()
                        .query(uri,null,null,null,null);

                int indexname=mcursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                mcursor.moveToFirst();
                songname=mcursor.getString(indexname);
                mcursor.close();
                //uploadselectedsongs
                uploadsongtoFirebaseStorage();

            }

        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void uploadsongtoFirebaseStorage() {
//uploadselectedsongs
        StorageReference storageReference= FirebaseStorage.getInstance().getReference()
                .child("Songs").child(uri.getLastPathSegment());

        final ProgressDialog progressDialog=new ProgressDialog(this);
        progressDialog.show();

        storageReference.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                Task<Uri> uriTask=taskSnapshot.getStorage().getDownloadUrl();
                while (!uriTask.isComplete());
                Uri urlsong=uriTask.getResult();
                songurl=urlsong.toString();

                uploadDetailstoDatabase();
                progressDialog.dismiss();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, e.getMessage().toString(), Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();
            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(@NonNull UploadTask.TaskSnapshot taskSnapshot) {
                double progress=(100.0*taskSnapshot.getBytesTransferred())/taskSnapshot.getTotalByteCount();
                int currentprogress=(int) progress;
                progressDialog.setMessage("Uploaded: "+currentprogress+"%");
            }
        });

    }

    private void uploadDetailstoDatabase() {

//store songsName and SongsUrl to database


        Songs songsObj=new Songs(songname,songurl);

        FirebaseDatabase.getInstance().getReference("Songs")
                .push().setValue(songsObj).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful())
                {
                    Toast.makeText(MainActivity.this, "Song Upload Successful", Toast.LENGTH_SHORT).show();

                }
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this, e.getMessage().toString(), Toast.LENGTH_SHORT).show();
            }
        });

    }

    private boolean validatePermission() {

        Dexter.withActivity(MainActivity.this)
                .withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        checkPermission = true;
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {

                        checkPermission = false;
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                        permissionToken.continuePermissionRequest();
                    }
                }).check();
        return  checkPermission;
    }
}
