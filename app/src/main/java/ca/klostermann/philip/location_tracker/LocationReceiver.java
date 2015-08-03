package ca.klostermann.philip.location_tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;

import com.google.android.gms.location.FusedLocationProviderApi;

public class LocationReceiver extends BroadcastReceiver{
    @Override
    public void onReceive(Context context, Intent intent) {
        Location location = (Location) intent.getExtras().get(
                FusedLocationProviderApi.KEY_LOCATION_CHANGED);

        if(TrackerService.isRunning()) {
            TrackerService.service.sendLocation(location);
        }
    }
}
