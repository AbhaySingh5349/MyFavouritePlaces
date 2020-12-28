package com.example.myfavouriteplaces.model;

public class FavouritePlacesModelClass {

    String Address, Contact, PlaceName;
    Double Latitude, Longitude;

    public String getAddress() {
        return Address;
    }

    public void setAddress(String address) {
        Address = address;
    }

    public String getContact() {
        return Contact;
    }

    public void setContact(String contact) {
        Contact = contact;
    }

    public String getPlaceName() {
        return PlaceName;
    }

    public void setPlaceName(String placeName) {
        PlaceName = placeName;
    }

    public Double getLatitude() {
        return Latitude;
    }

    public void setLatitude(Double latitude) {
        Latitude = latitude;
    }

    public Double getLongitude() {
        return Longitude;
    }

    public void setLongitude(Double longitude) {
        Longitude = longitude;
    }

    public FavouritePlacesModelClass(String address, String contact, String placeName, Double latitude, Double longitude) {
        Address = address;
        Contact = contact;
        PlaceName = placeName;
        Latitude = latitude;
        Longitude = longitude;
    }

    public FavouritePlacesModelClass() {
    }
}
