package com.example.peter.heartattackapp;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;


public class DatabaseHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "heartbreak.db";
    public static final String EVENT_TABLE = "event";
    public static final String AED_TABLE = "aed";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, 1);

    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        //Create AED table
        String aedSqlQuery = "create table " + AED_TABLE +
                " (ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "Name TEXT, " +
                "Lat TEXT, " +
                "Lon TEXT, " +
                "Description TEXT, " +
                "AvailableForUse INTEGER)";
        db.execSQL(aedSqlQuery);

        //Create Event tale
        String EventSqlQuery = "create table " + EVENT_TABLE +
                "(ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "CreateDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "Lat TEXT, " +
                "Lon TEXT, " +
                "personOnSite INTEGER," +
                "personsOnTheWay INTEGER, " +
                "activeAlarm INTEGER, " +
                "aedOnSite INTEGER, " +
                "alarmSentToSOS DATETIME)";
        db.execSQL(EventSqlQuery);
    }

   @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //String sqlUpgradeQuery = "DROP TABLE IF EXISTS " + AED_TABLE;
        db.execSQL("DROP TABLE IF EXISTS " + AED_TABLE);
        db.execSQL("DROP TABLE IF EXISTS " + EVENT_TABLE);
        onCreate(db);
    }

    public boolean insertDataToAed(String Name, String LAT, String LON, String Description, int availableForUse){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentvalues = new ContentValues();
        contentvalues.put("Name", Name);
        contentvalues.put("Lat",LAT);
        contentvalues.put("Lon", LON);
        contentvalues.put("Description", Description);
        contentvalues.put("AvailableForUse", availableForUse);
        long result = db.insert(AED_TABLE, null, contentvalues);
        if (result == -1)
            return false;
        else
            return true;
    }

    public boolean insertNewEvent(String lat, String lon){
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("Lat",lat);
        contentValues.put("Lon", lon);
        contentValues.put("personOnSite", 0);
        contentValues.put("activeAlarm", 1);
        contentValues.put("aedOnSite", 0);
        contentValues.put("alarmSentToSOS", 0);
        long result = db.insert(EVENT_TABLE, null, contentValues);
        if (result == -1)
            return false;
        else
            return true;
    }

    public void updateEvent(Event event){
        SQLiteDatabase db = this.getWritableDatabase();
        String sqlQuery =  "UPDATE " + EVENT_TABLE + " SET " +
                "personOnSite=" + event.getPersonsOnSite() +
                " ,personsOnTheWay=" + event.getPersonsOnTheWay() +
                " ,activeAlarm=" + event.getActiveAlarm() +
                " ,aedOnSite=" + event.getAedOnSite() +
                " ,alarmSentToSOS=" + event.getAlarmSentToSOS() +
                " WHERE ID=" + event.getID();
        db.execSQL(sqlQuery);
    }

    public void deleteAllRowsInTable(String table){
       SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("delete from " + table);
    }

    public Cursor getAllData(String table){
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor result = db.rawQuery("select * from " + table, null);
        return result;
    }

    public Cursor getActiveAlarms (){
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor result = db.rawQuery("select * from event where activeAlarm=1", null);
        return result;
    }
}
