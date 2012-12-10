package rlewelle.wifidroid.data;

import android.net.wifi.ScanResult;

import java.util.Calendar;

/**
 * Information that would uniquely identify a single access point
 */
public class AccessPoint {
    public String SSID;
    public String BSSID;

    public int level;
    public int frequency;

    public long lastSeen;

    public static AccessPoint getInstance(ScanResult result) {
        AccessPoint ap = new AccessPoint();
        ap.BSSID = result.BSSID;
        ap.SSID = result.SSID;
        ap.level = result.level;
        ap.frequency = result.frequency;
        ap.lastSeen = Calendar.getInstance().getTimeInMillis();

        return ap;
    }

    @Override
    public int hashCode() {
        return BSSID.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof AccessPoint)) return false;
        return ((AccessPoint)o).BSSID.equals(BSSID);
    }
}
