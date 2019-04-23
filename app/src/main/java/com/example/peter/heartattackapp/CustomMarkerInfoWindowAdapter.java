package com.example.peter.heartattackapp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;

public class CustomMarkerInfoWindowAdapter implements GoogleMap.InfoWindowAdapter{

    private final View markerWindow;
    private Context markerContext;

    public CustomMarkerInfoWindowAdapter(Context markerContext) {
        this.markerContext = markerContext;
        markerWindow = LayoutInflater.from(markerContext).inflate(R.layout.custom_marker_info_window,null);

    }

    private void renderMarkerWindow(Marker marker, View view){
        String title = marker.getTitle();
        TextView tvTitle =  (TextView) view.findViewById(R.id.title);
        if (!title.equals("")){
            tvTitle.setText(title);
        }

        String snippet = marker.getSnippet();
        TextView tvSnippet = (TextView) view.findViewById(R.id.snippet);
        if(!snippet.equals("")){
            tvSnippet.setText(snippet);
        }
    }

    @Override
    public View getInfoWindow(Marker marker) {
        renderMarkerWindow(marker, markerWindow);
        return markerWindow;
    }

    @Override
    public View getInfoContents(Marker marker) {
        renderMarkerWindow(marker,markerWindow);
        return markerWindow;
    }
}
