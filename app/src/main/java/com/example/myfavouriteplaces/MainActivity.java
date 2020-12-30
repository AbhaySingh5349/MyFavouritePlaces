package com.example.myfavouriteplaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.myfavouriteplaces.firebasetree.Constants;
import com.example.myfavouriteplaces.firebasetree.NodeNames;
import com.example.myfavouriteplaces.model.FavouritePlacesModelClass;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Objects;

import butterknife.BindView;
import butterknife.ButterKnife;
import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity implements RewardedVideoAdListener {

    /* adding and displaying address of home, work place and favourite places */

    @BindView(R.id.profileImageView)
    CircleImageView profileImageView;
    @BindView(R.id.addHomeAddressTextView)
    TextView addHomeAddressTextView;
    @BindView(R.id.homeAddressMapImageView)
    ImageView homeAddressMapImageView;
    @BindView(R.id.homeAddressTextView)
    TextView homeAddressTextView;
    @BindView(R.id.addWorkAddressTextView)
    TextView addWorkAddressTextView;
    @BindView(R.id.workAddressMapImageView)
    ImageView workAddressMapImageView;
    @BindView(R.id.workAddressTextView)
    TextView workAddressTextView;
    @BindView(R.id.favoritePlacesCardView)
    CardView favoritePlacesCardView;
    @BindView(R.id.favouriteRecyclerView)
    RecyclerView favouriteRecyclerView;

    FirebaseAuth firebaseAuth; // to create object of Firebase Auth class to fetch currently loged in user
    FirebaseUser firebaseUser; // to create object of Firebase User class to get current user to store currently loged in user
    DatabaseReference databaseReference, homeDatabaseReference, workDatabaseReference, favouritePlacesDatabaseReference;
    StorageReference storageReference;

    String currentUserId;

    LocationManager locationManager;
    boolean gpsProviderEnabled;
    AlertDialog gpsAlertDialog;
    int gpsEnableRequestCode = 101;

    private InterstitialAd interstitialAd;
    private RewardedVideoAd rewardedVideoAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        interstitialAd = new InterstitialAd(this);
        interstitialAd.setAdUnitId("ca-app-pub-3940256099942544/1033173712");
        interstitialAd.loadAd(new AdRequest.Builder().build());

        MobileAds.initialize(this,"ca-app-pub-3940256099942544~3347511713");
        rewardedVideoAd = MobileAds.getRewardedVideoAdInstance(this);
        rewardedVideoAd.setRewardedVideoAdListener(this);
        loadRewardVideoAd();

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(MainActivity.this);
        linearLayoutManager.setStackFromEnd(true); // to show newly added place at top
        linearLayoutManager.setReverseLayout(true);
        favouriteRecyclerView.setLayoutManager(linearLayoutManager);

        // permission for accessing GPS for device

        if(isGPSEnabled()){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);
        }

        // get current user

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        currentUserId = Objects.requireNonNull(firebaseUser).getUid();

        // reference to database nodes

        databaseReference = FirebaseDatabase.getInstance().getReference();
        homeDatabaseReference = databaseReference.child(NodeNames.HOME);
        workDatabaseReference = databaseReference.child(NodeNames.WORK);
        favouritePlacesDatabaseReference = databaseReference.child(NodeNames.FAVOURITEPLACES);
        storageReference = FirebaseStorage.getInstance().getReference(); // give reference to root folder of file storage

        StorageReference profileImageDB = storageReference.child(Constants.IMAGESFOLDER + "/" + currentUserId);
        profileImageDB.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
            @Override
            public void onSuccess(Uri uri) {
                Glide.with(MainActivity.this).load(uri).placeholder(R.drawable.profile).into(profileImageView); // loading profile image
            }
        });

        profileImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(interstitialAd.isLoaded()){
                    interstitialAd.show();
                }else {
                    startActivity(new Intent(MainActivity.this,EditProfileActivity.class));
                }
            }
        });

        interstitialAd.setAdListener(new AdListener()
                                     {
                                         @Override
                                         public void onAdClosed() {
                                             startActivity(new Intent(MainActivity.this,EditProfileActivity.class));
                                             interstitialAd.loadAd(new AdRequest.Builder().build());
                                         }
                                     }
        );

        // retrieving home info from database

        homeDatabaseReference.child(currentUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    if(snapshot.hasChild(NodeNames.ADDRESS)){
                        String address = snapshot.child(NodeNames.ADDRESS).getValue().toString();
                        homeAddressTextView.setText(address);

                        // passing intent to maps activity to display address location with marker and geoFence

                        homeAddressMapImageView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Double latitude = Double.parseDouble(snapshot.child(NodeNames.LATITUDE).getValue().toString());
                                Double longitude = Double.parseDouble(snapshot.child(NodeNames.LONGITUDE).getValue().toString());

                                Intent intent = new Intent(MainActivity.this,ViewPlaceMapsActivity.class);
                                intent.putExtra("Place Latitude",latitude);
                                intent.putExtra("Place Longitude",longitude);
                                intent.putExtra("Place Address",address);
                                intent.putExtra("Place Contact",snapshot.child(NodeNames.CONTACT).getValue().toString());
                                startActivity(intent);
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        // adding info about home like: address, name and contact number

        addHomeAddressTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, AddPlaceMapsActivity.class);
                intent.putExtra("source","Set Home Address");
                startActivity(intent);
            }
        });

        // retrieving work info from database

        workDatabaseReference.child(currentUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    if(snapshot.hasChild(NodeNames.ADDRESS)){
                        String address = snapshot.child(NodeNames.ADDRESS).getValue().toString();
                        workAddressTextView.setText(address);

                        // passing intent to maps activity to display address location with marker and geoFence

                        workAddressMapImageView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Double latitude = Double.parseDouble(snapshot.child(NodeNames.LATITUDE).getValue().toString());
                                Double longitude = Double.parseDouble(snapshot.child(NodeNames.LONGITUDE).getValue().toString());

                                Intent intent = new Intent(MainActivity.this,ViewPlaceMapsActivity.class);
                                intent.putExtra("Place Latitude",latitude);
                                intent.putExtra("Place Longitude",longitude);
                                intent.putExtra("Place Address",address);
                                intent.putExtra("Place Contact",snapshot.child(NodeNames.CONTACT).getValue().toString());
                                startActivity(intent);
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        // adding info about work like: address, name and contact number

        addWorkAddressTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, AddPlaceMapsActivity.class);
                intent.putExtra("source","Set Work Address");
                startActivity(intent);
            }
        });

        // adding info about multiple favourite places like: address, name and contact number and later displaying in Recycler View

        favoritePlacesCardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(rewardedVideoAd.isLoaded()){
                    rewardedVideoAd.show();
                }
                Intent intent = new Intent(MainActivity.this, AddPlaceMapsActivity.class);
                intent.putExtra("source","Add Favourite Places");
                startActivity(intent);
            }
        });
    }

    // checking if GPS is enabled

    private boolean isGPSEnabled(){
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if(gpsProviderEnabled){
            return true;
        }else {
            gpsAlertDialog = new AlertDialog.Builder(this)
                    .setTitle("GPS Enabling Permission").setMessage("GPS is required for tracking location,Please enable Location Services")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent gpsSettingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivityForResult(gpsSettingsIntent,gpsEnableRequestCode);
                        }
                    }).setCancelable(false).show();
        }
        return false;
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==gpsEnableRequestCode){
            gpsProviderEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if(gpsProviderEnabled){
                Toast.makeText(MainActivity.this,"Location Servives Enabled",Toast.LENGTH_SHORT).show();
            }else {
                Toast.makeText(MainActivity.this,"GPS not enabled,Unable to track user location",Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // FirebaseRecyclerOptions is a class provide by the FirebaseUI to make a query in the database to fetch appropriate data

        FirebaseRecyclerOptions<FavouritePlacesModelClass> firebaseRecyclerOptions = new FirebaseRecyclerOptions.Builder<FavouritePlacesModelClass>().setQuery(favouritePlacesDatabaseReference.child(currentUserId),FavouritePlacesModelClass.class).build();

        // FirebaseRecyclerAdapter binds a Query to a RecyclerView and responds to all real-time events included items being added, removed, moved, or changed

        FirebaseRecyclerAdapter<FavouritePlacesModelClass,FavouritePlacesViewHolder> firebaseRecyclerAdapter = new FirebaseRecyclerAdapter<FavouritePlacesModelClass, FavouritePlacesViewHolder>(firebaseRecyclerOptions) {
            @Override
            protected void onBindViewHolder(@NonNull FavouritePlacesViewHolder holder, int position, @NonNull FavouritePlacesModelClass model) {

                String placeId = getRef(position).getKey(); // get database reference key of Recycler View item

                holder.placeTextView.setText(model.getPlaceName());
                holder.addressTextView.setText(model.getAddress());
                holder.placeContactTextView.setText(model.getContact());


                Double latitude = model.getLatitude();
                Double longitude = model.getLongitude();

                // passing intent to maps activity to display address location with marker and geoFence

                holder.locationImageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(MainActivity.this,ViewPlaceMapsActivity.class);
                        intent.putExtra("Place Latitude",latitude);
                        intent.putExtra("Place Longitude",longitude);
                        intent.putExtra("Place Address",model.getAddress());
                        intent.putExtra("Place Contact",model.getContact());
                        startActivity(intent);
                    }
                });

                // allowing user to delete a previously saved favourite place

                holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).setTitle("Remove Favourite Place").setMessage("Are you sure you want to remove Favourite Place")
                                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        favouritePlacesDatabaseReference.child(currentUserId).child(placeId).setValue(null).addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                if(task.isSuccessful()){
                                                    Toast.makeText(MainActivity.this,"Place removed successfully",Toast.LENGTH_SHORT).show();
                                                }else {
                                                    Toast.makeText(MainActivity.this,"error: " + task.getException(),Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        });
                                    }
                                }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                }).setCancelable(false).show();
                        return false;
                    }
                });
            }

            @NonNull
            @Override
            public FavouritePlacesViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.favourite_places_layout,null); // attaching user display layout to Recycler View
                return new FavouritePlacesViewHolder(view);
            }
        };
        favouriteRecyclerView.setAdapter(firebaseRecyclerAdapter); // attaching adapter to Recycler View
        firebaseRecyclerAdapter.startListening(); // an event listener to monitor changes to the Firebase query
    }

    //  ViewHolder describes an item view and metadata about its place within the RecyclerView

    private static class FavouritePlacesViewHolder extends RecyclerView.ViewHolder{

        TextView placeTextView, addressTextView, placeContactTextView;
        ImageView locationImageView;

        public FavouritePlacesViewHolder(@NonNull View itemView) {
            super(itemView);

            placeTextView = itemView.findViewById(R.id.placeTextView);
            addressTextView = itemView.findViewById(R.id.addresTextView);
            locationImageView = itemView.findViewById(R.id.locationImageView);
            placeContactTextView = itemView.findViewById(R.id.placeContactTextView);
        }
    }

    private void loadRewardVideoAd() {
        if(!rewardedVideoAd.isLoaded()){
            rewardedVideoAd.loadAd("ca-app-pub-3940256099942544/5224354917", new AdRequest.Builder().build());
        }
    }

    @Override
    public void onRewardedVideoAdLoaded() {

    }

    @Override
    public void onRewardedVideoAdOpened() {

    }

    @Override
    public void onRewardedVideoStarted() {

    }

    @Override
    public void onRewardedVideoAdClosed() {
        loadRewardVideoAd();
    }

    @Override
    public void onRewarded(RewardItem rewardItem) {

    }

    @Override
    public void onRewardedVideoAdLeftApplication() {

    }

    @Override
    public void onRewardedVideoAdFailedToLoad(int i) {

    }

    @Override
    public void onRewardedVideoCompleted() {

    }

    @Override
    protected void onPause() {
        rewardedVideoAd.pause(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        rewardedVideoAd.resume(this);
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        rewardedVideoAd.destroy(this);
        super.onDestroy();
    }
}