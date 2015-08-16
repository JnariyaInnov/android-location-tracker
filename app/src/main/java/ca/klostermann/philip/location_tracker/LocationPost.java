package ca.klostermann.philip.location_tracker;

import android.location.Location;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class LocationPost {
    private long time;
    private double latitude;
    private double longitude;
    private float speed;
    private double altitude;
    private float accuracy;

    public LocationPost() {}

    public LocationPost(Location location) {
        this.time = location.getTime();
        this.latitude = location.getLatitude();
        this.longitude = location.getLongitude();
        this.speed = location.getSpeed();
        this.altitude = location.getAltitude();
        this.accuracy = location.getAccuracy();
    }

    public static long getDateKey(long time) {
        //Use timestamp of today's date at midnight as key
        GregorianCalendar d = new GregorianCalendar();
        d.setTimeInMillis(time);
        d.set(Calendar.HOUR, 0);
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
        return d.getTimeInMillis();
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    @Override
    public String toString() {
        return "LocationPost{" +
                "time=" + time +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", speed=" + speed +
                ", altitude=" + altitude +
                ", accuracy=" + accuracy +
                '}';
    }
}
