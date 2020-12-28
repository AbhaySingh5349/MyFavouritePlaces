package com.example.myfavouriteplaces;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myfavouriteplaces.firebasetree.NodeNames;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Objects;

public class ViewPlaceMapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    private LinearLayout shareLinearLayout;
    private TextView placeAddressTextView, distanceTextView, contactTextView;
    private ImageView locationImageView;

    FirebaseAuth firebaseAuth; // to create object of Firebase Auth class to fetch currently loged in user
    FirebaseUser firebaseUser; // to create object of Firebase User class to get current user to store currently loged in user
    DatabaseReference databaseReference, locationDatabaseReference;

    String currentUserId;

    LocationRequest locationRequest;
    LatLng placeLatLng, currentLatLng;
    Location lastKnownLocation;
    FusedLocationProviderClient fusedLocationProviderClient;
    LocationCallback locationCallback;

    View mapView, myLocationBtn;
    RelativeLayout.LayoutParams layoutParams;

    int accessFineLocationRequestCode = 101, accessCallRequestCode = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_place_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.viewPlaceMap);
        mapFragment.getMapAsync(this);

        shareLinearLayout = findViewById(R.id.shareLinearLayout);
        placeAddressTextView = findViewById(R.id.placeAddressTextView);
        locationImageView = findViewById(R.id.locationImageView);
        distanceTextView = findViewById(R.id.distanceTextView);
        contactTextView = findViewById(R.id.contactTextView);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        currentUserId = Objects.requireNonNull(firebaseUser).getUid();

        databaseReference = FirebaseDatabase.getInstance().getReference();
        locationDatabaseReference = databaseReference.child("Current Location");

        placeLatLng = new LatLng(0,0);

        mapView = mapFragment.getView(); // for adjusting my location button
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ViewPlaceMapsActivity.this); // get last known location of device
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(4000);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (mapView != null && mapView.findViewById(Integer.parseInt("1")) != null) {
            myLocationBtn = ((View) mapView.findViewById(Integer.parseInt("1")).getParent()).findViewById(Integer.parseInt("2"));
            layoutParams = (RelativeLayout.LayoutParams) myLocationBtn.getLayoutParams();  // fetching layout params of Location Button
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0); // removing location button from top right corner
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);  // adding location button to bottom right corner
            layoutParams.setMargins(0, 0, 40, 180);
        }

        Double placeLatitude = getIntent().getDoubleExtra("Place Latitude",0);
        Double placeLongitude = getIntent().getDoubleExtra("Place Longitude",0);
        String address = getIntent().getStringExtra("Place Address");
        String contact = getIntent().getStringExtra("Place Contact");

        placeLatLng = new LatLng(placeLatitude,placeLongitude);
        mMap.addMarker(new MarkerOptions().position(placeLatLng).title(address).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(placeLatLng, 18));  // to zoom in camera to marked location

        addCircle(placeLatLng, 50);

        shareLinearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = "https://www.google.com/maps/search/?api=1&query=" + placeLatitude + "," + placeLongitude;
                Intent shareAddressIntent = new Intent(Intent.ACTION_SEND);
                shareAddressIntent.putExtra(Intent.EXTRA_TEXT, "See Directions for: " + address + ": " + url);
                shareAddressIntent.setType("text/plain");
                startActivity(Intent.createChooser(shareAddressIntent, "Share Via"));
            }
        });

        placeAddressTextView.setText(address);

        locationImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(placeLatLng, 18));
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationUpdates();
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(true);
        } else {
            askLocationPermission();
        }

        contactTextView.setText(contact);

        contactTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(ContextCompat.checkSelfPermission(ViewPlaceMapsActivity.this,Manifest.permission.CALL_PHONE)==PackageManager.PERMISSION_GRANTED){
                    Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + contact));
                    startActivity(intent);
                }else {
                    ActivityCompat.requestPermissions(ViewPlaceMapsActivity.this, new String[]{Manifest.permission.CALL_PHONE}, accessCallRequestCode);
                }
            }
        });

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if(locationResult==null){
                    return;
                }else {
                    GeoFire geoFire = new GeoFire(locationDatabaseReference);

                    for (Location location : locationResult.getLocations()){
                        Log.d("Location Updated","Current location: " + location.toString());

                        geoFire.setLocation(currentUserId,new GeoLocation(location.getLatitude(),location.getLongitude()));
                        currentLatLng = new LatLng(location.getLatitude(),location.getLongitude());

                        Location currentLocation = new Location("");
                        currentLocation.setLatitude(currentLatLng.latitude);
                        currentLocation.setLongitude(currentLatLng.longitude);

                        Location placeLocation = new Location("");
                        placeLocation.setLatitude(placeLatitude);
                        placeLocation.setLongitude(placeLongitude);

                        float distance = currentLocation.distanceTo(placeLocation);

                        if(distance<=1000){
                            distanceTextView.setText(String.valueOf(distance) + " m away");
                        }else {
                            distanceTextView.setText(String.valueOf(distance/1000) + " Km away");
                        }
                    }
                }
            }
        };
    }

    private void locationUpdates() {
        LocationSettingsRequest locationSettingsRequest = new LocationSettingsRequest.Builder().addLocationRequest(locationRequest).build();
        SettingsClient settingsClient = LocationServices.getSettingsClient(this);

        Task<LocationSettingsResponse> locationSettingsResponseTask = settingsClient.checkLocationSettings(locationSettingsRequest);
        locationSettingsResponseTask.addOnSuccessListener(new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                startLocationUpdates();
            }
        });
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest,locationCallback, Looper.getMainLooper());
    }

    private void askLocationPermission() {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_FINE_LOCATION)){
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},accessFineLocationRequestCode);
            }else {
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},accessFineLocationRequestCode);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == accessFineLocationRequestCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationUpdates();
                }
            }
        }

        if (requestCode == accessCallRequestCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(ViewPlaceMapsActivity.this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(ViewPlaceMapsActivity.this, "You can call", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ViewPlaceMapsActivity.this, "Call permission access is necessary", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void addCircle(LatLng latLng,float radius){
        CircleOptions circleOptions = new CircleOptions();
        circleOptions.center(latLng);
        circleOptions.radius(radius);
        circleOptions.fillColor(Color.argb(64,255,0,0));
        circleOptions.strokeColor(Color.argb(255,255,0,0));
        circleOptions.strokeWidth(4);
        mMap.addCircle(circleOptions);
    }

    @Override
    protected void onStop() {
        super.onStop();

        GeoFire geoFire = new GeoFire(locationDatabaseReference);
        geoFire.removeLocation(currentUserId);

        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }
}