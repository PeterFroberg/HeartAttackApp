package com.example.peter.heartattackapp;

import com.google.android.gms.maps.model.Marker;

import java.security.Key;

public class Event {
    private int ID;
    private String date_time;
    private String Lon;
    private String Lat;
    private int personOnSite;
    private int personsOnTheWay;
    private int activeAlarm;
    private int aedOnSite;
    private String alarmSentToSOS;
    private Marker marker;

    public Event(int ID, String date_time, String Lat, String Lon, int personOnSite,
                 int personsOnTheWay, int activeAlarma, int aedOnSite, String alarmSentToSOS, Marker marker){
        this.ID = ID;
        this.date_time = date_time;
        this.Lon = Lon;
        this.Lat = Lat;
        this.personOnSite = personOnSite;
        this.personsOnTheWay = personsOnTheWay;
        this.activeAlarm = activeAlarma;
        this.aedOnSite = aedOnSite;
        this.alarmSentToSOS = alarmSentToSOS;
        this.marker = marker;
    }

    public int getID() {
        return ID;
    }

    public String getDate_time() {
        return date_time;
    }

    public String getLon() {
        return Lon;
    }

    public String getLat() {
        return Lat;
    }

    public int getPersonsOnSite() {
        return personOnSite;
    }

    public int getPersonsOnTheWay() {
        return personsOnTheWay;
    }

    public int getActiveAlarm() {
        return activeAlarm;
    }

    public int getAedOnSite() {
        return aedOnSite;
    }

    public String getAlarmSentToSOS() {
        return alarmSentToSOS;
    }

    public Marker getMarker(){
        return marker;
    }

    public void setMarker(Marker marker){
        this.marker = marker;
    }

    public void setPersonsOnTheWay(int personsOnTheWay){
        this.personsOnTheWay = personsOnTheWay;
    }

    public void  setPersonOnSite(int personOnSite){
        this.personOnSite = personOnSite;
    }

}


