package com.example.peter.heartattackapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class MapsActivity extends FragmentActivity implements
        OnMapReadyCallback,
        TaskLoadedCallback,
        GoogleMap.OnCameraMoveListener,
        GoogleMap.OnCameraMoveStartedListener,
        GoogleMap.OnMyLocationButtonClickListener,
        GoogleMap.OnMarkerClickListener {


    private GoogleMap mMap;
    private DatabaseHelper heartBreakDB;
    private List<Aed> aedsOnMap = new LinkedList<>();
    private List<Aed> aedsToRemoveFromMap = new LinkedList<>();
    private List<Event> eventsOnMap = new LinkedList<>();
    private List<Event> eventsToRemoveFromMap = new LinkedList<>();
    private int syncFreq;
    private int scanArea;
    private double helpRadius = 0.0177 * scanArea;
    private double aedRadius = helpRadius * 1;
    private boolean updateOn = true;
    private boolean followMyPosition = true;
    private boolean onTask = false;
    private Event currentEvent = null;
    private Aed currentAed = null;
    private boolean arrivedAtEvent = false;
    private LatLng currentMarkerLocation;
    private Polyline currentPolyline;
    private float zoomLevel = 14;
    private boolean guidingUser = false;
    private boolean guidingToEvent = false;
    private boolean guidingToAed = false;
    private boolean eventDialogShowing = false;
    private boolean driving = false;

    private static final double ONSITE_RANGE = 0.0001;
    private static final int HELP_RADIUS_MULTIPLIER = 1;

    private int currentsyncFreq = 6000;

    private Handler eventScheduleHandler = new Handler();
    private TimerTask eventTimerTask = null;
    private Timer eventTimer = new Timer();

    private Button menuButton;
    private Button sosKnapp;

    //FusedLocation
    private FusedLocationProviderClient fusedLocationProviderClient;
    private static final int MY_PERMISSON_REQUEST_FINE_LOCATION = 101;
    private LatLng currentLocation = new LatLng(39.437211, -31.198103); //Default location as emulator don't have last location
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case MY_PERMISSON_REQUEST_FINE_LOCATION:
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast myToast = Toast.makeText(getApplicationContext(), "Appen kräver att få komma åt din platsinfomramtion", Toast.LENGTH_SHORT);
                    myToast.show();
                    finish();
                }
                break;
        }
    }

    @Override
    public void onCameraMove() {

    }

    public void onCameraMoveStarted(int reason) {

        if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
            followMyPosition = false;
        }
    }

    @Override
    public boolean onMyLocationButtonClick() {
        followMyPosition = true;
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, zoomLevel));
        return false;
    }

    @Override
    public boolean onMarkerClick(final Marker marker) {
        for (Event e : eventsOnMap) {
            if (marker.equals(e.getMarker())) {
                showAlertDialogEvent(e);
                return true;
            }
        }
        for (Aed a : aedsOnMap) {
            if (marker.equals(a.getMarker())) {
                showAlertDialogAed(a);
                return true;
            }
        }
        return false;
    }

    public void btnSettings_onClick(View view) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void alarmButtonPressed(View view) {
        Toast.makeText(this, "Larm skickat till databasen",
                Toast.LENGTH_SHORT).show();
        heartBreakDB.insertNewEvent("59.418767", "17.938441");
    }

    private void readUserInterfaceSettings() {
        final Handler settingsScheduleHandler = new Handler();
        Timer settingsTimer = new Timer();
        TimerTask settingsTimerTask = new TimerTask() {
            @Override
            public void run() {
                settingsScheduleHandler.post(new Runnable() {
                    public void run() {
                        new AsyncTask<Integer, Void, String>() {
                            @Override
                            protected String doInBackground(Integer... params) {
                                SharedPreferences pref = PreferenceManager
                                        .getDefaultSharedPreferences(MapsActivity.this);

                                if (syncFreq != Integer.valueOf(pref.getString("sync_frequency", "15000"))) {
                                    String sync_frequency = pref.getString("sync_frequency", "15000");
                                    syncFreq = Integer.valueOf(sync_frequency);
                                    loadFromDatabase();
                                }
                                if (scanArea != Integer.valueOf(pref.getString("scan_area", "100"))) {
                                    String scan_area = pref.getString("scan_area", "100");
                                    scanArea = Integer.valueOf(scan_area);
                                    loadFromDatabase();
                                }

                                driving = pref.getBoolean("walkOrDrive", false);
                                return null;
                            }

                            protected void onPostExecute(String s) {
                                if (currentsyncFreq != syncFreq) {
                                    eventTimerTask.cancel();
                                    eventTimerTask = new TimerTask() {
                                        @Override
                                        public void run() {
                                            eventScheduleHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    asyncLoadEvents();
                                                    asyncLoadAeds();
                                                }
                                            });
                                        }
                                    };
                                    eventTimer.schedule(eventTimerTask, 0, syncFreq);
                                    currentsyncFreq = syncFreq;
                                }
                            }
                        }.execute(1);
                    }
                });
            }
        };
        settingsTimer.schedule(settingsTimerTask, 0, 5000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateOn) {
            startLocationsUpdates();
        }
    }

    private void startLocationsUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSON_REQUEST_FINE_LOCATION);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    private void stopLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    private void showAlertDialogSos() {
        // setup the alert builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("SOS LARM" + "\n");
        builder.setMessage("Du ringer SOS!");

        // add the buttons
        builder.setNegativeButton("OK", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showAlertDialogEvent(final Event event) {
        eventDialogShowing = true;
        // setup the alert builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nytt larm: " + event.getID());
        String aedOnsiteText;
        if (event.getAedOnSite() == 0) {
            aedOnsiteText = "No";
        } else {
            aedOnsiteText = "Yes";
        }
        builder.setMessage("Is aed on site: " + aedOnsiteText + "\n" +
                "when did it occur? " + event.getDate_time() + "\n" +
                "People onsite: " + event.getPersonsOnSite() + "\n" +
                "People on the way: " + event.getPersonsOnTheWay());

        // add the buttons
        builder.setPositiveButton("Guida mig dit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //onTask = true;
                currentMarkerLocation = new LatLng(Double.valueOf(event.getLat()), Double.valueOf(event.getLon()));
                if (currentEvent == null || currentEvent != event) {
                    event.setPersonsOnTheWay(event.getPersonsOnTheWay() + 1);
                    if (guidingToEvent && currentEvent.getPersonsOnTheWay() != 0) {
                        currentEvent.setPersonsOnTheWay(currentEvent.getPersonsOnTheWay() - 1);
                        heartBreakDB.updateEvent(currentEvent);
                    }
                    currentEvent = event;
                    guidingUser = true;
                    guidingToEvent = true;
                    arrivedAtEvent = false;
                    heartBreakDB.updateEvent(event);
                    eventDialogShowing = false;
                    guidePersonToEvent();
                }

            }
        });
        builder.setNegativeButton("Avbryt", (new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                eventDialogShowing = false;
            }
        }));
        if (guidingToEvent && currentEvent.equals(event)) {
            builder.setNeutralButton("Avbryt Guidning", (new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                    currentPolyline.remove();
                    event.setPersonsOnTheWay(event.getPersonsOnTheWay() - 1);
                    heartBreakDB.updateEvent(event);
                    guidingToEvent = false;
                    guidingUser = false;
                    currentEvent = null;
                    currentMarkerLocation = null;
                    eventDialogShowing = false;

                }
            }));
        }

        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showAlertDialogAed(final Aed aed) {

        // setup the alert builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Härtstartare ID: " + aed.getID());
        builder.setMessage("Hjärtstartar namn: " + aed.getName() + "\n");

        // add the buttons
        builder.setPositiveButton("Guida mig dit", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (currentEvent != null) {
                    currentEvent.setPersonsOnTheWay(currentEvent.getPersonsOnTheWay() - 1);
                    heartBreakDB.updateEvent(currentEvent);
                    currentEvent = null;
                    guidingToEvent = false;
                }
                onTask = true;
                currentMarkerLocation = new LatLng(Double.valueOf(aed.getLat()), Double.valueOf(aed.getLon()));
                currentAed = aed;
                guidingUser = true;
                guidingToAed = true;

                guidePersonToEvent();

            }
        });
        builder.setNegativeButton("Avbryt", null);

        if (guidingToAed && currentAed.equals(aed)) {
            builder.setNeutralButton("Avbryt Guidning", (new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    currentPolyline.remove();
                    guidingToAed = false;
                    guidingUser = false;
                    currentAed = null;
                    currentMarkerLocation = null;
                }
            }));

        }

        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void onTaskDone(Object... values) {
        if (currentPolyline != null)
            currentPolyline.remove();
        currentPolyline = mMap.addPolyline((PolylineOptions) values[0]);
    }

    private void guidePersonToEvent() {
        if (driving) {
            new FetchURL(MapsActivity.this).execute(getUrl(currentLocation, currentMarkerLocation, "driving"), "driving");
        } else {
            new FetchURL(MapsActivity.this).execute(getUrl(currentLocation, currentMarkerLocation, "walking"), "walking");
        }
    }

    private void loadFromDatabase() {
        eventTimerTask = new TimerTask() {
            @Override
            public void run() {
                eventScheduleHandler.post(new Runnable() {
                    public void run() {
                        asyncLoadEvents();
                        asyncLoadAeds();
                    }
                });
            }
        };

        eventTimer.schedule(eventTimerTask, 0, syncFreq);
    }

    private void asyncLoadEvents() {
        new AsyncTask<Integer, Void, String>() {
            @Override
            protected String doInBackground(Integer... params) {
                double latCoord;
                double lonCoord;
                helpRadius = 0.0177 * scanArea;
                boolean eventExists = false;

                Cursor eventsFromDatabase = heartBreakDB.getActiveAlarms();
                if (eventsFromDatabase.getCount() != 0) {
                    //Data found
                    while (eventsFromDatabase.moveToNext()) {
                        latCoord = Double.parseDouble(eventsFromDatabase.getString(2));
                        lonCoord = Double.parseDouble(eventsFromDatabase.getString(3));
                        if (checkInRange(latCoord, lonCoord, helpRadius)) {
                            if (eventsOnMap.size() != 0) {
                                for (Event e : eventsOnMap) {
                                    if ((e.getLat().equals(eventsFromDatabase.getString(2))) && (e.getLon().equals(eventsFromDatabase.getString(3)))) {
                                        eventExists = true;
                                        break;
                                    }
                                }
                                if (!eventExists) {
                                    eventsOnMap.add(new Event(eventsFromDatabase.getInt(0),
                                            eventsFromDatabase.getString(1),
                                            eventsFromDatabase.getString(2),
                                            eventsFromDatabase.getString(3),
                                            eventsFromDatabase.getInt(4),
                                            eventsFromDatabase.getInt(5),
                                            eventsFromDatabase.getInt(6),
                                            eventsFromDatabase.getInt(7),
                                            eventsFromDatabase.getString(8),
                                            null));
                                }
                                eventExists = false;
                            } else {
                                eventsOnMap.add(new Event(eventsFromDatabase.getInt(0),
                                        eventsFromDatabase.getString(1),
                                        eventsFromDatabase.getString(2),
                                        eventsFromDatabase.getString(3),
                                        eventsFromDatabase.getInt(4),
                                        eventsFromDatabase.getInt(5),
                                        eventsFromDatabase.getInt(6),
                                        eventsFromDatabase.getInt(7),
                                        eventsFromDatabase.getString(8),
                                        null));
                            }
                        } else {
                            for (Event e : eventsOnMap) {
                                if (e.getLat().equals(eventsFromDatabase.getString(2)) && e.getLon().equals(eventsFromDatabase.getString(3))) {
                                    //eventsOnMap.remove((e));
                                    eventsToRemoveFromMap.add(e);

                                }
                            }
                        }
                    }
                }
                return null;
            }

            protected void onPreExecute() {
                if (eventsOnMap.size() != 0) {
                    for (Event e : eventsOnMap) {
                        if (e.getMarker() != null) {
                            removeMarker(e.getMarker());
                        }
                    }
                    //eventsOnMap.clear();
                }
            }

            protected void onPostExecute(String s) {
                for(Event e : eventsToRemoveFromMap){
                    eventsOnMap.remove(e);
                }
                eventsToRemoveFromMap.clear();
                plotEventOnMap();

            }
        }.

                execute(1);

    }

    private void asyncLoadAeds() {
        new AsyncTask<Integer, Void, String>() {
            @Override
            protected String doInBackground(Integer... params) {
                double latCoord;
                double lonCoord;
                Marker marker = null;
                aedRadius = helpRadius * HELP_RADIUS_MULTIPLIER;
                boolean aedExists = false;

                Cursor aedsFromDatabase = heartBreakDB.getAllData("aed");
                if (aedsFromDatabase.getCount() != 0) {
                    //Data found
                    while (aedsFromDatabase.moveToNext()) {
                        latCoord = Double.parseDouble(aedsFromDatabase.getString(2));
                        lonCoord = Double.parseDouble(aedsFromDatabase.getString(3));

                        if (checkInRange(latCoord, lonCoord, aedRadius)) {
                            if (aedsOnMap.size() != 0) {
                                for (Aed a : aedsOnMap) {
                                    if ((a.getLat().equals(aedsFromDatabase.getString(2)) && a.getLon().equals(aedsFromDatabase.getString(3)))) {
                                        aedExists = true;
                                        break;
                                    }
                                }
                                if (!aedExists) {
                                    aedsOnMap.add(new Aed(aedsFromDatabase.getInt(0),
                                            aedsFromDatabase.getString(1),
                                            aedsFromDatabase.getString(2),
                                            aedsFromDatabase.getString(3),
                                            aedsFromDatabase.getString(4),
                                            aedsFromDatabase.getInt(5),
                                            marker));

                                }
                            } else {
                                aedsOnMap.add(new Aed(aedsFromDatabase.getInt(0),
                                        aedsFromDatabase.getString(1),
                                        aedsFromDatabase.getString(2),
                                        aedsFromDatabase.getString(3),
                                        aedsFromDatabase.getString(4),
                                        aedsFromDatabase.getInt(5),
                                        marker));
                            }
                            aedExists = false;
                        } else {
                            for (Aed a : aedsOnMap) {
                                if ((a.getLat().equals(aedsFromDatabase.getString(2)) && a.getLon().equals(aedsFromDatabase.getString(3)))) {
                                    //aedsOnMap.remove(a);
                                    aedsToRemoveFromMap.add(a);

                                }
                            }
                        }
                    }
                }
                return null;
            }

            protected void onPreExecute() {
                if (aedsOnMap.size() != 0) {
                    for (Aed e : aedsOnMap) {
                        if (e.getMarker() != null) {
                            removeMarker(e.getMarker());
                        }
                    }
                }
            }

            protected void onPostExecute(String s) {
                for (Aed a: aedsToRemoveFromMap){
                    aedsOnMap.remove(a);
                }
                aedsToRemoveFromMap.clear();

                plotAedToMap();
            }
        }.execute(1);
    }

    private void plotAedToMap() {
        double latCoord;
        double lonCoord;
        Marker marker;


        for (Aed a : aedsOnMap) {
            latCoord = Double.parseDouble(a.getLat());
            lonCoord = Double.parseDouble(a.getLon());
            LatLng markerCoord = new LatLng(latCoord, lonCoord);
            marker = mMap.addMarker(new MarkerOptions().position(markerCoord).title(a.getDescription()).
                    title(a.getDescription()).
                    snippet("Lat: " + a.getLat() + " Lon: " + a.getLon()).
                    icon(BitmapDescriptorFactory.fromResource(R.drawable.aed_icon)));
            a.setMarker(marker);
        }
    }

    private void plotEventOnMap() {
        double latCoord;
        double lonCoord;
        Event closestEvent = null;
        Marker marker;

        /*Toast.makeText(this, "Laddat databasen",
                Toast.LENGTH_SHORT).show();*/

        for (Event e : eventsOnMap) {
            latCoord = Double.parseDouble(e.getLat());
            lonCoord = Double.parseDouble(e.getLon());
            LatLng markerCoord = new LatLng(latCoord, lonCoord);
            String snippet = "Lat: " + e.getLat() +
                    " Lon: " + e.getLon() + "\n" +
                    "AED på plats: " + e.getAedOnSite() + "\n" +
                    "Personer på plats: " + e.getPersonsOnSite();

            marker = mMap.addMarker(new MarkerOptions().position(markerCoord).
                    title(e.getDate_time()).
                    snippet(snippet).
                    //snippet("Lat: " + eventsFromDatabase.getString(2) + " Lon: " + eventsFromDatabase.getString(3)).
                            icon(BitmapDescriptorFactory.fromResource(R.drawable.heartfailure)));
            e.setMarker(marker);
        }
        if (!guidingUser && !eventDialogShowing) {
            closestEvent = calculateClosestEvent();
            if (closestEvent != null) {
                showAlertDialogEvent(closestEvent);
            }
        }

    }

    public void removeMarker(Marker marker) {
        marker.remove();
    }

    private boolean checkInRange(double latCoord, double lonCoord, double radius) {
        if (latCoord < currentLocation.latitude + radius &&
                latCoord > currentLocation.latitude - radius &&
                lonCoord < currentLocation.longitude + radius &&
                lonCoord > currentLocation.longitude - radius) {
            return true;
        }
        return false;
    }

    private Event calculateClosestEvent() {
        Event closestEvent = null;
        double closestDistans = 0;
        double latDistans;
        double lonDistans;

        for (Event e : eventsOnMap) {
            latDistans = currentLocation.latitude - Double.parseDouble(e.getLat());
            lonDistans = currentLocation.longitude - Double.parseDouble(e.getLon());
            double distantToEvent = Math.sqrt((latDistans * latDistans) + (lonDistans * lonDistans));
            if (distantToEvent < closestDistans || closestDistans == 0) {
                closestDistans = distantToEvent;
                closestEvent = e;
            }
        }
        return closestEvent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.heartbreak_map);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        preLoadDatabase();

        menuButton = findViewById(R.id.menuButton);
        sosKnapp = findViewById(R.id.sosButton);

        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSettings_onClick(v);
            }
        });

        sosKnapp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAlertDialogSos();
            }
        });

        //FusedLocation GetLast location
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, zoomLevel));
                    } else {
                        //Om man inte får en position
                    }
                }
            });
        } else {
            //Request permissions
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSON_REQUEST_FINE_LOCATION);

        }

        //FusedLocation current location
        locationRequest = new LocationRequest();
        locationRequest.setInterval(7500); //Hur oft man pollar FuesedLOcation services use 15000 for real app
        locationRequest.setFastestInterval(5000); // hur ofta den ska kolla position från andra appar utan att behöva fråga fusedloaction
        locationRequest.setPriority(locationRequest.PRIORITY_HIGH_ACCURACY); // Sätter om den ska prioritera WiFI och Cell info för positionering för att få GPS PRIORITY_HIGH_ACCRACY, för wifi/cell PRIORITY_BALANCED_POWER_ACCURACY

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        currentLocation = new LatLng(location.getLatitude(), location.getLongitude());
                        if (followMyPosition) {
                            zoomLevel = mMap.getCameraPosition().zoom;
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, zoomLevel));
                        }
                        if (guidingUser) {
                            guidePersonToEvent();
                            if (guidingToEvent && !arrivedAtEvent) {
                                if (checkInRange(currentEvent.getMarker().getPosition().latitude, currentEvent.getMarker().getPosition().longitude, ONSITE_RANGE)) {
                                    currentEvent.setPersonOnSite(currentEvent.getPersonsOnSite() + 1);
                                    heartBreakDB.updateEvent(currentEvent);
                                    arrivedAtEvent = true;
                                    currentEvent.setPersonsOnTheWay(currentEvent.getPersonsOnTheWay()-1);
                                    heartBreakDB.updateEvent(currentEvent);
                                    Toast myToast = Toast.makeText(getApplicationContext(), "You have arrived at the event", Toast.LENGTH_LONG);
                                    myToast.show();
                                }
                            }
                        }
                    } else {
                        //om man inte får någon location
                    }
                }
            }
        };

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(MapsActivity.this);
        String sync_frequency = pref.getString("sync_frequency", "15000");
        syncFreq = Integer.valueOf(sync_frequency);

        String scan_area = pref.getString("scan_area", "100");
        scanArea = Integer.valueOf(scan_area);

        driving = pref.getBoolean("walkOrDrive", false);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.setOnCameraMoveStartedListener(this);
        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setOnMarkerClickListener(this);
        // Läsa in ny data från databasen
        loadFromDatabase();
        readUserInterfaceSettings();

        googleMap.getUiSettings().setZoomControlsEnabled(true);
    }

    private String getUrl(LatLng origin, LatLng dest, String directionMode) {

        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;
        String str_destination = "destination=" + dest.latitude + "," + dest.longitude;
        String mode = "mode=" + directionMode;
        String parameters = str_origin + "&" + str_destination + "&" + mode;
        String output = "json";
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters + "&key=" + "AIzaSyBbn1GtJKOrz0a4bwoqhhAOlBAlEqNSTEA";

        return url;
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    public void preLoadDatabase() {

        heartBreakDB = new DatabaseHelper(this);
        heartBreakDB.getWritableDatabase();
        heartBreakDB.deleteAllRowsInTable("aed");
        heartBreakDB.deleteAllRowsInTable("event");

        List<Aed> preloadAeds = new ArrayList<>();
        preloadAeds.add(new Aed(1, "HS_1", "59.428482", "17.949823", "Reception Företag 1", 1, null));
        preloadAeds.add(new Aed(1, "HS_2", "59.410405", "17.927588", "Reception Företag 2", 1, null));
        preloadAeds.add(new Aed(1, "HS_3", "59.421933", "17.923724", "Kassan COOP", 1, null));
        preloadAeds.add(new Aed(1, "HS_4", "59.407347", "17.948781", "Kassan ICA", 1, null));
        preloadAeds.add(new Aed(1, "HS_5", "59.417563", "17.963034", "Reception Företag 3", 1, null));

        preloadAeds.add(new Aed(1, "HS_6", "59.414846", "17.954909", "Reception Företag 4", 1, null));
        preloadAeds.add(new Aed(1, "HS_7", "59.390507", "17.927345", "Reception Företag 5", 1, null));
        preloadAeds.add(new Aed(1, "HS_8", "59.414491", "17.964404", "Kassan Hemköp", 1, null));
        preloadAeds.add(new Aed(1, "HS_9", "59.421844", "17.933002", "Kassan Citygross", 1, null));
        preloadAeds.add(new Aed(1, "HS_10", "59.402940", "17.944864", "Reception Företag 6", 1, null));

        preloadAeds.add(new Aed(1, "HS_6", "59.366434", "17.996110", "Reception Företag 4", 1, null));
        preloadAeds.add(new Aed(1, "HS_7", "59.363825", "18.058771", "Reception Företag 5", 1, null));
        preloadAeds.add(new Aed(1, "HS_8", "59.395046", "18.054065", "Kassan Hemköp", 1, null));
        preloadAeds.add(new Aed(1, "HS_9", "59.420931", "17.846964", "Kassan Citygross", 1, null));
        preloadAeds.add(new Aed(1, "HS_10", "59.432695", "17.922193", "Reception Företag 6", 1, null));

        for (Aed x : preloadAeds) {
            heartBreakDB.insertDataToAed(x.getName(), x.getLat(), x.getLon(), x.getDescription(), x.getAvailableForUse());
        }
        //heartBreakDB.insertNewEvent("59.418732", "17.939394");
        //heartBreakDB.insertNewEvent("59.402702", "17.959634");
    }
}
