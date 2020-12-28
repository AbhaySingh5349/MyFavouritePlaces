package com.example.myfavouriteplaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.example.myfavouriteplaces.firebasetree.NodeNames;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.hbb20.CountryCodePicker;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class AddPlaceMapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    /* Adding Home, Work address and favourite places on Long Click with contact number of that place
     and search places in Search Bar */

    SearchView locationSearchView;
    EditText placeNameEditText, contactEditText;
    CountryCodePicker countryCodePicker; // to pick country code which contact number belongs to
    Button savePlaceBtn;

    private GoogleMap mMap;

    LocationManager locationManager; //  to obtain periodic updates of the device's geographical location, or to be notified when the device enters the proximity of a given geographical location
    LocationListener locationListener; // Used for receiving notifications from the LocationManager when the location has changed.
    LatLng lastKnownLatLng;
    Location lastKnownLocation; // A data class representing a geographic location  consisting of a latitude, longitude, timestamp, and other information such as bearing, altitude and velocity
    FusedLocationProviderClient fusedLocationProviderClient; // location APIs in Google Play services that intelligently combines different signals to provide the location information
    Geocoder geocoder; // A class for handling geoCoding and reverse geoCoding
    View mapView, myLocationBtn;
    RelativeLayout.LayoutParams layoutParams;

    int accessFineLocationRequestCode = 101, accessBackgroundLocationRequestCode = 102, maxAddressListResults = 1;

    Marker myLocationMarker, selectedLocationMarker;

    FirebaseAuth firebaseAuth; // to create object of Firebase Auth class to fetch currently logged in user
    FirebaseUser firebaseUser; // to create object of Firebase User class to get current user to store currently logged in user
    DatabaseReference databaseReference, homeDatabaseReference;

    String currentUserId, mobileNumber = "", placeName = "";
    HashMap<String, Object> placeInfoHashMap, referenceHashMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_place_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.addPlaceMap);
        mapFragment.getMapAsync(this);

        mapView = mapFragment.getView(); // for adjusting my location button
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(AddPlaceMapsActivity.this); // get last known location of device

        // get current user

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = firebaseAuth.getCurrentUser();
        currentUserId = Objects.requireNonNull(firebaseUser).getUid();

        // reference to database nodes

        databaseReference = FirebaseDatabase.getInstance().getReference();
        homeDatabaseReference = databaseReference.child(NodeNames.HOME);
    }

    //  method is called when the map is ready to be used

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // adjusting MyLocation button

        if (mapView != null && mapView.findViewById(Integer.parseInt("1")) != null) {
            myLocationBtn = ((View) mapView.findViewById(Integer.parseInt("1")).getParent()).findViewById(Integer.parseInt("2"));
            layoutParams = (RelativeLayout.LayoutParams) myLocationBtn.getLayoutParams();  // fetching layout params of Location Button
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0); // removing location button from top right corner
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);  // adding location button to bottom right corner
            layoutParams.setMargins(0, 0, 40, 180);
        }

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                getLastKnownLocation();
            }
        };

        if (Build.VERSION.SDK_INT < 23) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // checking permission to Access Location of device
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            getLastKnownLocation();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) // GPS Service of our Device is ON
            {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                getLastKnownLocation();
            }
        }

        locationSearchView = findViewById(R.id.locationSearchView);

        // searching places in edit text

        locationSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                List<Address> addressList = null;
                String place = locationSearchView.getQuery().toString();
                if(place!=null || !place.equals("")){
                    Geocoder geocoder = new Geocoder(AddPlaceMapsActivity.this); // generate LatLng from address
                    try {
                        addressList = geocoder.getFromLocationName(place,1); // getting most probable place
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if(addressList.size()!=0){
                        Address address = addressList.get(0);
                        LatLng latLng = new LatLng(address.getLatitude(),address.getLongitude()); // retrieving Latitude & Longitude from address
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18)); // focusing on searched place
                        Toast.makeText(AddPlaceMapsActivity.this,place,Toast.LENGTH_LONG).show();
                    }else {
                        Toast.makeText(AddPlaceMapsActivity.this,"Please enter correct location",Toast.LENGTH_LONG).show();
                    }
                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        placeNameEditText = findViewById(R.id.placeNameEditText);
        contactEditText = findViewById(R.id.contactEditText);
        countryCodePicker = findViewById(R.id.countryCodePicker);
        savePlaceBtn = findViewById(R.id.savePlaceBtn);

    }

    private void getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Task<Location> task = fusedLocationProviderClient.getLastLocation(); // class which returns the best most recent location currently available.
        task.addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    lastKnownLocation = location;
                    lastKnownLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                    myLocationMarker = mMap.addMarker(new MarkerOptions().position(lastKnownLatLng).title("Your Location"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lastKnownLatLng, 18));
                    if (ActivityCompat.checkSelfPermission(AddPlaceMapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(AddPlaceMapsActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    mMap.setMyLocationEnabled(true);
                    mMap.getUiSettings().setMyLocationButtonEnabled(true);
                    mMap.setOnMapLongClickListener(AddPlaceMapsActivity.this);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == accessFineLocationRequestCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                    getLastKnownLocation();
                }
            }
        }
    }

    // adding marker and retrieving address of place on Long Click of map

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onMapLongClick(LatLng latLng) {
        if(selectedLocationMarker!=null){
            selectedLocationMarker.remove(); // removing earlier selected marker
        }

        placeInfoHashMap = new HashMap<>();

        //  Locale object represents a specific geographical, political, or cultural region.

        geocoder = new Geocoder(AddPlaceMapsActivity.this, Locale.getDefault());
        String address = "";
        try {
            List<Address> placesAddress = geocoder.getFromLocation(latLng.latitude,latLng.longitude,maxAddressListResults); // retrieving specific address to place

            if(placesAddress!=null && placesAddress.size()>0) {

                if(placesAddress.get(0).getLocality()!=null) {
                    if (placesAddress.get(0).getSubLocality() != null) {
                        if (placesAddress.get(0).getThoroughfare() != null) {
                            if (placesAddress.get(0).getSubThoroughfare() != null) {
                                if (placesAddress.get(0).getPremises() != null) {
                                    if (placesAddress.get(0).getFeatureName() != null) {
                                        address += placesAddress.get(0).getFeatureName() + ", ";
                                    }
                                    address += placesAddress.get(0).getPremises() + ", ";
                                }
                                address += placesAddress.get(0).getSubThoroughfare() + ", ";
                            }
                            address += placesAddress.get(0).getThoroughfare() + ", ";
                        }
                        address += placesAddress.get(0).getSubLocality() + ", ";
                    }
                    address += placesAddress.get(0).getLocality();
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        selectedLocationMarker = mMap.addMarker(new MarkerOptions().position(latLng).title(address).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        Toast.makeText(AddPlaceMapsActivity.this,"Location: " + address + " Saved",Toast.LENGTH_SHORT).show();

        String finalAddress = address;

        // updating place details to database

        savePlaceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!validateContact() | !validatePlaceName()){
                    validateContact();
                    validatePlaceName();
                }else {
                    placeInfoHashMap.put(NodeNames.ADDRESS, finalAddress);
                    placeInfoHashMap.put(NodeNames.CONTACT,mobileNumber);
                    placeInfoHashMap.put(NodeNames.PLACENAME,placeName);
                    placeInfoHashMap.put(NodeNames.LATITUDE,latLng.latitude);
                    placeInfoHashMap.put(NodeNames.LONGITUDE,latLng.longitude);

                    if(Objects.equals(getIntent().getStringExtra("source"), "Set Home Address")){
                        String reference = NodeNames.HOME + "/" + currentUserId;
                        referenceHashMap = new HashMap<>();
                        referenceHashMap.put(reference,placeInfoHashMap);

                        databaseReference.updateChildren(referenceHashMap, new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                                if(error==null){
                                    Toast.makeText(AddPlaceMapsActivity.this,"Home address saved",Toast.LENGTH_SHORT).show();
                                    finish();
                                }else {
                                    Toast.makeText(AddPlaceMapsActivity.this,"error: " + error.getMessage(),Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }else if(Objects.equals(getIntent().getStringExtra("source"), "Set Work Address")){
                        String reference = NodeNames.WORK + "/" + currentUserId;
                        referenceHashMap = new HashMap<>();
                        referenceHashMap.put(reference,placeInfoHashMap);

                        databaseReference.updateChildren(referenceHashMap, new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                                if(error==null){
                                    Toast.makeText(AddPlaceMapsActivity.this,"Work address saved",Toast.LENGTH_SHORT).show();
                                    finish();
                                }else {
                                    Toast.makeText(AddPlaceMapsActivity.this,"error: " + error.getMessage(),Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }else if(Objects.equals(getIntent().getStringExtra("source"), "Add Favourite Places")){
                        String placeId = databaseReference.child(NodeNames.FAVOURITEPLACES).push().getKey();

                        placeInfoHashMap.put("PlaceId",placeId);
                        String reference = NodeNames.FAVOURITEPLACES + "/" + currentUserId + "/" + placeId;
                        referenceHashMap = new HashMap<>();
                        referenceHashMap.put(reference,placeInfoHashMap);

                        databaseReference.updateChildren(referenceHashMap, new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(@Nullable DatabaseError error, @NonNull DatabaseReference ref) {
                                if(error==null){
                                    Toast.makeText(AddPlaceMapsActivity.this,"Favourite Place saved",Toast.LENGTH_SHORT).show();
                                    placeNameEditText.setText(null);
                                    contactEditText.setText(null);
                                    if(selectedLocationMarker!=null){
                                        selectedLocationMarker.remove(); // removing earlier selected marker
                                    }
                                }else {
                                    Toast.makeText(AddPlaceMapsActivity.this,"error: " + error.getMessage(),Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    // validating required fields

    private boolean validatePlaceName(){
        placeName = placeNameEditText.getText().toString().trim();
        if(placeName.isEmpty()){
            Toast.makeText(AddPlaceMapsActivity.this,"Enter Place",Toast.LENGTH_LONG).show();
            return false;
        }else {
            return true;
        }
    }

    private boolean validateContact(){
        if(contactEditText.length()==10){
            countryCodePicker.registerCarrierNumberEditText(contactEditText);
            mobileNumber = countryCodePicker.getFullNumberWithPlus();
            return true;
        }else {
            Toast.makeText(AddPlaceMapsActivity.this,"Enter Correct Contact Number",Toast.LENGTH_LONG).show();
            return false;
        }
    }
}