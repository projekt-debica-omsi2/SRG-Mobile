package pl.srgmobile.classic;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;

/**
 * Offline Android wrapper for SRG CLASSIC HTML.
 * GPS_PROVIDER uses the device's GNSS location provider, not browser geolocation.
 * No INTERNET or background-location permission is requested.
 */
public final class MainActivity extends Activity implements LocationListener {
    private static final int LOCATION_REQUEST = 41;
    private WebView web;
    private LocationManager locationManager;
    private boolean requested = false;
    private boolean listening = false;
    private boolean pageReady = false;
    private int satellitesUsed = -1;
    private long lastFixAt = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GnssStatus.Callback gnssCallback;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        web = new WebView(this);
        web.setBackgroundColor(0xff070a09);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                // All application data is embedded. Do not navigate to third-party pages.
                return !"file".equals(request.getUrl().getScheme()) ||
                    !request.getUrl().toString().startsWith("file:///android_asset/");
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageReady = true;
                sendStatus("READY", "Mostek GPS Androida jest dostępny. Kliknij START GPS.");
                if(requested && !listening) startNativeGps();
            }
        });
        web.addJavascriptInterface(new GpsBridge(), "SRGNativeGPS");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    public final class GpsBridge {
        @JavascriptInterface public void start() {
            runOnUiThread(() -> {
                requested = true;
                if(!hasFinePermission()) {
                    sendStatus("PERMISSION", "Zezwól na DOKŁADNĄ lokalizację w oknie Androida.");
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
                } else startNativeGps();
            });
        }
        @JavascriptInterface public void stop() {
            runOnUiThread(() -> {
                requested = false;
                stopNativeGps();
                sendStatus("STOPPED", "GPS został zatrzymany.");
            });
        }
    }

    private boolean hasFinePermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if(requestCode != LOCATION_REQUEST) return;
        if(hasFinePermission() && requested) startNativeGps();
        else sendStatus("PERMISSION", "W ustawieniach Androida → Aplikacje → SRG Mobile CLASSIC → Uprawnienia → Lokalizacja wybierz „Zezwalaj” i „Dokładna”.");
    }

    private void startNativeGps() {
        if(!requested || !pageReady || listening) return;
        if(!hasFinePermission()) {
            sendStatus("PERMISSION", "Włącz dokładną lokalizację dla tej aplikacji.");return;
        }
        if(locationManager == null || locationManager.getProvider(LocationManager.GPS_PROVIDER) == null) {
            sendStatus("NO_PROVIDER", "Urządzenie nie ma dostępnego dostawcy GPS_PROVIDER.");return;
        }
        if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            sendStatus("DISABLED", "Włącz Lokalizację/GPS w ustawieniach Androida.");return;
        }
        try {
            satellitesUsed = -1;
            lastFixAt = 0;
            if(Build.VERSION.SDK_INT >= 24) {
                gnssCallback = new GnssStatus.Callback() {
                    @Override public void onSatelliteStatusChanged(GnssStatus status) {
                        int count = 0;
                        for(int i=0;i<status.getSatelliteCount();i++) {
                            if(status.usedInFix(i)) count++;
                        }
                        satellitesUsed = count;
                    }
                };
                locationManager.registerGnssStatusCallback(gnssCallback, handler);
            }
            // 1 update/sec while the Activity is visible; only the GNSS GPS_PROVIDER.
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                1000L, 0.0f, this, Looper.getMainLooper());
            listening = true;
            sendStatus("WAITING", "Szukam satelitów GPS. Wyjdź na otwartą przestrzeń, jeśli nie ma sygnału.");
            handler.postDelayed(() -> {
                if(listening && lastFixAt == 0) {
                    sendStatus("WAITING", "Brak pozycji GPS po 30 s. Sprawdź niebo, antenę i ustawienia lokalizacji.");
                }
            }, 30000L);
        } catch(SecurityException ex) {
            listening = false;
            sendStatus("PERMISSION", "Android odmówił dostępu do dokładnej lokalizacji.");
        } catch(Exception ex) {
            listening = false;
            sendStatus("ERROR", "Nie można uruchomić GPS: " + ex.getMessage());
        }
    }

    @Override public void onLocationChanged(Location location) {
        if(!listening || !requested || location == null) return;
        if(!location.hasAccuracy()) return;
        lastFixAt = android.os.SystemClock.elapsedRealtime();
        String speed = location.hasSpeed() ? Float.toString(location.getSpeed()) : "null";
        String bearing = location.hasBearing() ? Float.toString(location.getBearing()) : "null";
        String sats = satellitesUsed >= 0 ? Integer.toString(satellitesUsed) : "null";
        sendJS("window.onNativeGpsFix&&window.onNativeGpsFix("+
            Double.toString(location.getLatitude())+","+
            Double.toString(location.getLongitude())+","+
            Float.toString(location.getAccuracy())+","+speed+","+bearing+","+sats+");");
    }
    @Override public void onProviderDisabled(String provider) {
        if(LocationManager.GPS_PROVIDER.equals(provider)) {
            stopNativeGps();
            sendStatus("DISABLED", "GPS urządzenia wyłączony. Włącz lokalizację w ustawieniach.");
        }
    }
    @Override public void onProviderEnabled(String provider) {
        if(LocationManager.GPS_PROVIDER.equals(provider) && requested) startNativeGps();
    }
    private void stopNativeGps() {
        if(locationManager != null) {
            try { locationManager.removeUpdates(this); } catch(Exception ignored) {}
            if(gnssCallback != null) {
                try {locationManager.unregisterGnssStatusCallback(gnssCallback);} catch(Exception ignored) {}
            }
        }
        gnssCallback = null;
        listening = false;
    }
    private void sendStatus(String code, String detail) {
        sendJS("window.onNativeGpsStatus&&window.onNativeGpsStatus("+
            JSONObject.quote(code)+","+JSONObject.quote(detail)+");");
    }
    private void sendJS(String js) {
        if(web == null || !pageReady) return;
        runOnUiThread(() -> {
            if(web != null && pageReady) web.evaluateJavascript(js, null);
        });
    }
    @Override protected void onPause() {
        super.onPause();
        stopNativeGps(); // No background location tracking.
        if(web != null) web.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if(web != null) web.onResume();
        if(requested && pageReady) startNativeGps();
    }
    @Override protected void onDestroy() {
        stopNativeGps();
        pageReady = false;
        if(web != null) {
            web.removeJavascriptInterface("SRGNativeGPS");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
