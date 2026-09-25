package com.example.bongovpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Base64;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Real Android VpnService + Xray TUN bridge.
 * The Xray core is loaded from the libXray Android AAR dependency.
 */
public class V2RayService extends VpnService {
    public static final String EXTRA_CONFIG = "xray_config";
    private static final String PREFS = "vpn_profiles";
    private static final String KEY_RUNNING = "xray_running";
    private static final int NOTIFICATION_ID = 4107;
    private static final String CHANNEL_ID = "xray_vpn";

    private ParcelFileDescriptor tunInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Xray VPN starting..."));

        String config = intent == null ? null : intent.getStringExtra(EXTRA_CONFIG);
        if (config == null || config.trim().isEmpty()) {
            stopWithError("Xray config is empty");
            return START_NOT_STICKY;
        }

        new Thread(() -> startCore(config), "xray-start").start();
        return START_STICKY;
    }

    private void startCore(String input) {
        try {
            String xrayJson = normalizeToXrayJson(input);
            JSONObject config = new JSONObject(xrayJson);

            // The Android app owns the TUN descriptor. Xray reads its fd from xray.tun.fd.
            JSONObject env = config.optJSONObject("env");
            if (env == null) env = new JSONObject();
            // Placeholder is replaced after the TUN is established.

            // Exclude this app from the VPN so the Xray process can reach its server
            // directly and does not route its own uplink back into the TUN.
            Builder builder = new Builder();
            builder.setSession("Bongo Xray")
                    .setMtu(1500)
                    .addAddress("10.7.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1");
            try { builder.addDisallowedApplication(getPackageName()); } catch (Exception ignored) { }

            tunInterface = builder.establish();
            if (tunInterface == null) throw new IllegalStateException("Android could not establish TUN interface");

            env.put("xray.tun.fd", String.valueOf(tunInterface.getFd()));
            config.put("env", env);

            // Xray's native TUN inbound consumes the Android VpnService fd.
            JSONArray inbounds = new JSONArray();
            JSONObject tun = new JSONObject();
            tun.put("tag", "tun-in");
            tun.put("port", 0);
            tun.put("protocol", "tun");
            JSONObject settings = new JSONObject();
            settings.put("name", "xray0");
            settings.put("mtu", 1500);
            settings.put("gateway", new JSONArray().put("10.7.0.1/30"));
            tun.put("settings", settings);
            inbounds.put(tun);
            config.put("inbounds", inbounds);

            // Validate the outbound/core configuration without constructing the Android TUN.
            JSONObject validation = new JSONObject(config.toString());
            validation.remove("inbounds");
            validation.remove("env");
            String response = invokeCore(new JSONObject()
                    .put("apiVersion", 3)
                    .put("method", "testXray")
                    .put("payload", new JSONObject().put("xrayJson", validation.toString())).toString());
            JSONObject test = new JSONObject(response);
            if (!test.optBoolean("success", false)) throw new IllegalStateException("Xray config test failed: " + test.optString("error", "unknown error"));

            response = invokeCore(new JSONObject()
                    .put("apiVersion", 3)
                    .put("method", "runXray")
                    .put("payload", new JSONObject().put("xrayJson", config.toString())).toString());
            JSONObject run = new JSONObject(response);
            if (!run.optBoolean("success", false)) throw new IllegalStateException("Xray start failed: " + run.optString("error", "unknown error"));

            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, true).apply();
            updateNotification("Xray VPN connected");
        } catch (Exception e) {
            stopWithError("Xray: " + safeMessage(e));
        }
    }

    /** Convert share links with libXray, or accept an existing Xray JSON config. */
    private String normalizeToXrayJson(String input) throws Exception {
        String trimmed = input.trim();
        if (trimmed.startsWith("{")) {
            JSONObject root = new JSONObject(trimmed);
            if (!root.has("outbounds")) throw new IllegalArgumentException("Xray JSON has no outbounds");
            return root.toString();
        }

        String request = new JSONObject()
                .put("apiVersion", 3)
                .put("method", "convertShareLinksToXrayJson")
                .put("payload", new JSONObject().put("text", trimmed))
                .toString();
        JSONObject response = new JSONObject(invokeCore(request));
        if (response.optBoolean("success", false)) {
            Object data = response.opt("data");
            if (data instanceof String) return new JSONObject((String) data).toString();
            if (data instanceof JSONObject) return ((JSONObject) data).toString();
        }

        // Current Xray share-link parsing intentionally does not accept legacy
        // vmess://Base64(JSON). Keep compatibility for common old V2Ray profiles.
        if (trimmed.toLowerCase(java.util.Locale.US).startsWith("vmess://")) {
            String legacy = legacyVmessToXray(trimmed.substring(8));
            if (legacy != null) return legacy;
        }
        throw new IllegalArgumentException("Share link parse failed: " + response.optString("error", "invalid link"));
    }

    private String legacyVmessToXray(String encoded) {
        try {
            String raw = encoded.replace("-", "+").replace("_", "/");
            while (raw.length() % 4 != 0) raw += "=";
            String jsonText = new String(Base64.decode(raw, Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8);
            JSONObject v = new JSONObject(jsonText);
            String address = v.optString("add", "").trim();
            int port = Integer.parseInt(v.optString("port", "0"));
            String uuid = v.optString("id", "").trim();
            if (address.isEmpty() || port <= 0 || uuid.isEmpty()) throw new IllegalArgumentException("Legacy VMess is missing address/port/id");

            JSONObject user = new JSONObject();
            user.put("id", uuid);
            user.put("alterId", parseInt(v.optString("aid", "0"), 0));
            String security = v.optString("scy", "auto");
            user.put("security", security.isEmpty() ? "auto" : security);

            JSONObject server = new JSONObject();
            server.put("address", address);
            server.put("port", port);
            server.put("users", new JSONArray().put(user));

            JSONObject out = new JSONObject();
            out.put("protocol", "vmess");
            out.put("settings", new JSONObject().put("vnext", new JSONArray().put(server)));

            String net = v.optString("net", "tcp").toLowerCase(java.util.Locale.US);
            String tls = v.optString("tls", "").toLowerCase(java.util.Locale.US);
            JSONObject stream = new JSONObject();
            stream.put("network", net.isEmpty() ? "tcp" : net);
            if ("tls".equals(tls)) {
                stream.put("security", "tls");
                JSONObject tlsSettings = new JSONObject();
                String sni = v.optString("sni", v.optString("host", address));
                if (!sni.isEmpty()) tlsSettings.put("serverName", sni);
                if (!v.optString("fp", "").isEmpty()) tlsSettings.put("fingerprint", v.optString("fp"));
                stream.put("tlsSettings", tlsSettings);
            } else {
                stream.put("security", "none");
            }

            if ("ws".equals(net)) {
                JSONObject ws = new JSONObject();
                ws.put("path", v.optString("path", "/"));
                String host = v.optString("host", "");
                if (!host.isEmpty()) ws.put("headers", new JSONObject().put("Host", host));
                stream.put("wsSettings", ws);
            }
            out.put("streamSettings", stream);
            return new JSONObject().put("outbounds", new JSONArray().put(out)).toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; }
    }

    private String invokeCore(String request) throws Exception {
        String[] classes = {
                "libXray.LibXray",
                "io.github.wanliyunyan.libxray.LibXray",
                "com.wanliyunyan.libxray.LibXray",
                "io.github.toolshubofficial.libxray.LibXray",
                "com.toolshubofficial.libxray.LibXray"
        };
        Throwable last = null;
        for (String className : classes) {
            try {
                Class<?> cls = Class.forName(className);
                for (Method m : cls.getMethods()) {
                    if (!(m.getName().equals("invoke") || m.getName().equals("Invoke"))) continue;
                    if (!Modifier.isStatic(m.getModifiers())) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 1 && p[0] == String.class && m.getReturnType() == String.class) {
                        return (String) m.invoke(null, request);
                    }
                }
            } catch (Throwable e) { last = e; }
        }
        throw new IllegalStateException("libXray Android AAR not found or its Invoke API is unavailable", last);
    }

    private void stopCore() {
        try {
            invokeCore(new JSONObject().put("apiVersion", 3).put("method", "stopXray").put("payload", new JSONObject()).toString());
        } catch (Exception ignored) { }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_RUNNING, false).apply();
        if (tunInterface != null) { try { tunInterface.close(); } catch (Exception ignored) { } tunInterface = null; }
    }

    private void stopWithError(String message) {
        updateNotification(message);
        stopCore();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        stopCore();
        super.onDestroy();
    }

    @Override public void onRevoke() {
        stopCore();
        stopSelf();
        super.onRevoke();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return super.onBind(intent); }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Xray VPN", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Sensei Tunnel")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        try { getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(text)); } catch (Exception ignored) { }
    }

    private String safeMessage(Exception e) { Throwable t=e; while(t.getCause()!=null)t=t.getCause(); return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage(); }
}
