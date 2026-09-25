package com.example.bongovpn;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.net.VpnService;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import ai.bongotech.bongovpn.BongoVpn;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_OVPN_FILE = 7001;
    private static final int REQUEST_V2RAY_FILE = 7002;
    private static final int REQUEST_VPN_PERMISSION = 7003;
    private static final String PREFS = "vpn_profiles";
    private static final String KEY_OVPN_PROFILES = "ovpn_profiles";
    private static final String KEY_V2RAY_PROFILES = "v2ray_profiles";
    private static final String KEY_HISTORY = "connection_history";

    private MaterialButton btnConnect, btnImportConfig, btnManualConfig, btnClearConfig, btnMenu, btnAddConfig, btnReload, btnOpenVpn, btnV2Ray, btnSettings, btnSpeedTest;
    private Spinner spinnerProtocol, spinnerProfile;
    private TextView tvDownloadSpeed, tvUploadSpeed, tvSessionUsage, tvProfileInfo, tvImportedFiles;
    private LinearLayout configList;

    private BongoVpn bongoVpn;
    private SharedPreferences prefs;
    private boolean isOpenVpnSelected = true;
    private int selectedProfile = -1;
    private String pendingV2RayConfig = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 8100);
        }
        bongoVpn = new BongoVpn(this);

        btnConnect = findViewById(R.id.btnConnect);
        btnImportConfig = findViewById(R.id.btnImportConfig);
        btnManualConfig = findViewById(R.id.btnManualConfig);
        btnClearConfig = findViewById(R.id.btnClearConfig);
        btnMenu = findViewById(R.id.btnMenu);
        btnAddConfig = findViewById(R.id.btnAddConfig);
        btnReload = findViewById(R.id.btnReload);
        btnOpenVpn = findViewById(R.id.btnOpenVpn);
        btnV2Ray = findViewById(R.id.btnV2Ray);
        btnSettings = findViewById(R.id.btnSettings);
        btnSpeedTest = findViewById(R.id.btnSpeedTest);
        configList = findViewById(R.id.configList);
        spinnerProtocol = findViewById(R.id.spinnerProtocol);
        spinnerProfile = findViewById(R.id.spinnerProfile);
        tvDownloadSpeed = findViewById(R.id.tvDownloadSpeed);
        tvUploadSpeed = findViewById(R.id.tvUploadSpeed);
        tvSessionUsage = findViewById(R.id.tvSessionUsage);
        tvProfileInfo = findViewById(R.id.tvProfileInfo);
        tvImportedFiles = findViewById(R.id.tvImportedFiles);

        setupProtocolSpinner();
        setupProfileSpinner();
        setupOpenVpnListener();
        refreshProfileUi();
        showStartupFileToast();

        btnImportConfig.setOnClickListener(v -> {
            if (isOpenVpnSelected) importOpenVpnFile(); else importV2RayFile();
        });
        btnManualConfig.setOnClickListener(v -> showManualConfigDialog());
        btnClearConfig.setOnClickListener(v -> clearSelectedProfile());
        btnConnect.setOnClickListener(v -> {
            if (isOpenVpnSelected) handleOpenVpnConnection();
            else handleV2RayConnection();
        });
        btnOpenVpn.setOnClickListener(v -> spinnerProtocol.setSelection(0));
        btnV2Ray.setOnClickListener(v -> spinnerProtocol.setSelection(1));
        btnAddConfig.setOnClickListener(v -> showAddConfigurationChooser());
        btnReload.setOnClickListener(v -> refreshProfileUi());
        btnMenu.setOnClickListener(v -> showMenuPopup());
        btnSettings.setOnClickListener(v -> showInfoDialog("Settings", "VPN profiles are stored locally. Select OpenVPN or V2Ray, then import a file or use Manual configuration."));
        btnSpeedTest.setOnClickListener(v -> Toast.makeText(this, "Connect a profile first, then use your preferred speed-test app/site.", Toast.LENGTH_LONG).show());
    }

    private void setupProtocolSpinner() {
        String[] protocols = {"OpenVPN", "V2Ray / Xray"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, protocols);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProtocol.setAdapter(adapter);
        spinnerProtocol.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                isOpenVpnSelected = position == 0;
                selectedProfile = -1;
                updateProtocolToggleUi();
                refreshProfileUi();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void updateProtocolToggleUi() {
        if (btnOpenVpn == null || btnV2Ray == null) return;
        if (isOpenVpnSelected) {
            btnOpenVpn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(0,169,92)));
            btnOpenVpn.setTextColor(Color.WHITE);
            btnV2Ray.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(234,244,238)));
            btnV2Ray.setTextColor(Color.rgb(80,98,90));
        } else {
            btnV2Ray.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(0,169,92)));
            btnV2Ray.setTextColor(Color.WHITE);
            btnOpenVpn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(234,244,238)));
            btnOpenVpn.setTextColor(Color.rgb(80,98,90));
        }
    }

    private void showAddConfigurationChooser() {
        LinearLayout box = verticalBox();
        TextView title = new TextView(this);
        title.setText("Add Configuration");
        title.setTextSize(24); title.setTextColor(Color.rgb(16,24,21)); title.setPadding(0,0,0,dp(8));
        box.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Import a file or build a configuration manually. Saved profiles appear in the Configs list.");
        hint.setTextSize(14); hint.setTextColor(Color.rgb(90,105,98)); hint.setPadding(0,0,0,dp(12)); box.addView(hint);

        MaterialButton clipboard = actionButton("▣  Import from Clipboard", true);
        MaterialButton importFile = actionButton(isOpenVpnSelected ? "▣  Pick .ovpn file" : "▣  Pick V2Ray/Xray file", false);
        MaterialButton manual = actionButton("☷  Manual", false);
        box.addView(clipboard); box.addView(importFile); box.addView(manual);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();
        clipboard.setOnClickListener(v -> { dialog.dismiss(); importFromClipboard(); });
        importFile.setOnClickListener(v -> { dialog.dismiss(); if (isOpenVpnSelected) importOpenVpnFile(); else importV2RayFile(); });
        manual.setOnClickListener(v -> { dialog.dismiss(); showManualConfigDialog(); });
        dialog.show();
    }

    private MaterialButton actionButton(String text, boolean primary) {
        MaterialButton b = new MaterialButton(this);
        b.setText(text); b.setTextSize(15); b.setAllCaps(false); b.setMinHeight(dp(54));
        b.setTextColor(primary ? Color.WHITE : Color.rgb(0,169,92));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(primary ? Color.rgb(0,169,92) : Color.WHITE));
        b.setCornerRadius(dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.bottomMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private void importFromClipboard() {
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) throw new IllegalArgumentException("Clipboard is empty");
            CharSequence text = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            String config = text == null ? "" : text.toString().trim();
            if (config.isEmpty()) throw new IllegalArgumentException("Clipboard is empty");
            if (isOpenVpnSelected) showSaveOvpnProfileDialog("clipboard.ovpn", config);
            else showSaveV2RayProfileDialog("clipboard-v2ray.txt", config);
        } catch (Exception e) { Toast.makeText(this, "Clipboard: " + safeMessage(e), Toast.LENGTH_LONG).show(); }
    }

    private void showMenuPopup() {
        LinearLayout box = verticalBox();
        box.setPadding(dp(22), dp(18), dp(22), dp(18));
        TextView brand = new TextView(this);
        brand.setText("🛡  Sensei Tunnel");
        brand.setTextSize(22);
        brand.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        brand.setTextColor(Color.rgb(16,24,21));
        box.addView(brand);

        String[] items = {
                "⚙  Settings", "◔  Speed Test", "◉  Diagnostics", "◷  Connection History",
                "●  Account", "▣  APN Settings", "↕  4G/5G Switcher", "▣  Battery Optimization",
                "↻  Reload Configs", "?  Help Center", "ⓘ  About"
        };
        for (String item : items) {
            MaterialButton b = actionButton(item, false);
            b.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            box.addView(b);
            if (item.contains("APN")) b.setOnClickListener(v -> openSystemSettings(Settings.ACTION_APN_SETTINGS, "APN settings are controlled by Android."));
            else if (item.contains("Settings")) b.setOnClickListener(v -> showSettingsDialog());
            else if (item.contains("Speed Test")) b.setOnClickListener(v -> showSpeedTestDialog());
            else if (item.contains("Diagnostics")) b.setOnClickListener(v -> showDiagnosticsDialog());
            else if (item.contains("Connection History")) b.setOnClickListener(v -> showHistoryDialog());
            else if (item.contains("Account")) b.setOnClickListener(v -> showAccountDialog());
            else if (item.contains("4G/5G")) b.setOnClickListener(v -> openSystemSettings(Settings.ACTION_WIRELESS_SETTINGS, "Android controls the preferred mobile network mode."));
            else if (item.contains("Battery")) b.setOnClickListener(v -> openBatteryOptimization());
            else if (item.contains("Reload")) b.setOnClickListener(v -> { refreshProfileUi(); Toast.makeText(this,"Configs reloaded",Toast.LENGTH_SHORT).show(); });
            else if (item.contains("Help")) b.setOnClickListener(v -> showInfoDialog("Help Center", "1. Select OpenVPN or V2Ray/Xray.\n2. Import a file or use Manual.\n3. Select the saved profile.\n4. Tap Connect.\n\nOpenVPN profiles containing auth-user-pass can store username/password per profile. V2Ray/Xray accepts share links or Xray JSON."));
            else if (item.contains("About")) b.setOnClickListener(v -> showInfoDialog("About", "Sensei Tunnel • OpenVPN + V2Ray/Xray\nProfiles are stored locally on this device."));
        }
        final android.app.Dialog d = new AlertDialog.Builder(this).setView(box).create();
        d.show();
    }

    private void showSettingsDialog() {
        LinearLayout box = verticalBox();
        TextView info = new TextView(this);
        info.setText("Local profile storage\n• OpenVPN profiles: " + profiles(KEY_OVPN_PROFILES).length() +
                "\n• V2Ray/Xray profiles: " + profiles(KEY_V2RAY_PROFILES).length() +
                "\n\nThe app does not upload your profiles to a server.");
        info.setTextSize(15); info.setTextColor(Color.rgb(60,75,68));
        box.addView(info);
        new AlertDialog.Builder(this).setTitle("Settings").setView(box).setPositiveButton("OK", null).show();
    }

    private void showDiagnosticsDialog() {
        StringBuilder s = new StringBuilder();
        s.append("Current protocol: ").append(isOpenVpnSelected ? "OpenVPN" : "V2Ray/Xray").append('\n');
        s.append("OpenVPN connected: ").append(bongoVpn != null && bongoVpn.isConnected()).append('\n');
        s.append("Xray running: ").append(prefs.getBoolean("xray_running", false)).append('\n');
        s.append("OVPN profiles: ").append(profiles(KEY_OVPN_PROFILES).length()).append('\n');
        s.append("V2Ray profiles: ").append(profiles(KEY_V2RAY_PROFILES).length()).append('\n');
        s.append("Status: ").append(tvSessionUsage.getText());
        showInfoDialog("Diagnostics", s.toString());
    }

    private void showHistoryDialog() {
        String history = prefs.getString(KEY_HISTORY, "");
        if (history.trim().isEmpty()) history = "No connection history yet.";
        showInfoDialog("Connection History", history);
    }

    private void showAccountDialog() {
        showInfoDialog("Account", "No online account is required.\nProfiles and credentials are stored locally on this device.");
    }

    private void openSystemSettings(String action, String fallback) {
        try { startActivity(new Intent(action)); }
        catch (Exception e) { showInfoDialog("System Settings", fallback); }
    }

    private void openBatteryOptimization() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                    return;
                }
            }
            showInfoDialog("Battery Optimization", "Battery optimization is already disabled for this app, or Android does not expose this option on this device.");
        } catch (Exception e) { showInfoDialog("Battery Optimization", "Open Android Settings → Battery → Battery optimization and allow Sensei Tunnel to run without optimization."); }
    }

    private void showSpeedTestDialog() {
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Speed Test").setMessage("Testing download and upload speed…").setNegativeButton("Close", null).create();
        dialog.show();
        new Thread(() -> {
            String result;
            try {
                long start = System.nanoTime();
                HttpURLConnection down = (HttpURLConnection) new URL("https://speed.cloudflare.com/__down?bytes=2000000").openConnection();
                down.setConnectTimeout(8000); down.setReadTimeout(15000);
                long bytes = 0; byte[] buf = new byte[8192]; int n;
                try (java.io.InputStream in = down.getInputStream()) { while ((n = in.read(buf)) != -1) bytes += n; } finally { down.disconnect(); }
                double seconds = Math.max(0.001, (System.nanoTime() - start) / 1_000_000_000.0);
                double downMbps = (bytes * 8.0 / seconds) / 1_000_000.0;

                byte[] upload = new byte[512 * 1024];
                new java.security.SecureRandom().nextBytes(upload);
                start = System.nanoTime();
                HttpURLConnection up = (HttpURLConnection) new URL("https://speed.cloudflare.com/__up").openConnection();
                up.setRequestMethod("POST"); up.setDoOutput(true); up.setFixedLengthStreamingMode(upload.length);
                up.setConnectTimeout(8000); up.setReadTimeout(15000);
                try (OutputStream os = up.getOutputStream()) { os.write(upload); }
                int code = up.getResponseCode();
                seconds = Math.max(0.001, (System.nanoTime() - start) / 1_000_000_000.0);
                double upMbps = (upload.length * 8.0 / seconds) / 1_000_000.0;
                up.disconnect();
                result = String.format(java.util.Locale.US, "Download: %.2f Mbps\nUpload: %.2f Mbps\nHTTP upload response: %d", downMbps, upMbps, code);
            } catch (Exception e) { result = "Speed test failed: " + safeMessage(e); }
            final String out = result;
            runOnUiThread(() -> { if (!isFinishing() && dialog.isShowing()) dialog.setMessage(out); });
        }, "speed-test").start();
    }

    private void addHistory(String event) {
        String old = prefs.getString(KEY_HISTORY, "");
        String line = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()) + " — " + event;
        String all = line + (old.isEmpty() ? "" : "\n" + old);
        String[] lines = all.split("\n");
        StringBuilder limited = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 30); i++) { if (i > 0) limited.append('\n'); limited.append(lines[i]); }
        prefs.edit().putString(KEY_HISTORY, limited.toString()).apply();
    }

    private void showInfoDialog(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
    }

    private void setupProfileSpinner() {
        spinnerProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedProfile = position - 1;
                updateProfileInfo();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { selectedProfile = -1; }
        });
    }

    private void setupOpenVpnListener() {
        if (!bongoVpn.hasNotificationPermission()) bongoVpn.requestNotificationPermission();
        bongoVpn.setVpnListener(new BongoVpn.VpnListener() {
            @Override public void onVpnConnected() {
                if (isOpenVpnSelected) {
                    btnConnect.setEnabled(true); btnConnect.setText("Disconnect OpenVPN"); btnConnect.setTextColor(Color.RED);
                    tvSessionUsage.setText("OpenVPN connected");
                    addHistory("OpenVPN connected");
                }
            }
            @Override public void onVpnStopped() {
                if (isOpenVpnSelected) {
                    btnConnect.setEnabled(true); btnConnect.setText("Connect OpenVPN"); btnConnect.setTextColor(Color.BLACK);
                    tvSessionUsage.setText("OpenVPN disconnected");
                    addHistory("OpenVPN disconnected");
                }
            }
            @Override public void onStatusUpdate(String status) { if (isOpenVpnSelected) tvSessionUsage.setText(status); }
            @Override public void onError(String errorMessage) {
                if (isOpenVpnSelected) {
                    btnConnect.setEnabled(true); btnConnect.setText("Connect OpenVPN");
                    tvSessionUsage.setText(errorMessage == null ? "OpenVPN error" : errorMessage);
                }
            }
            @Override public void onSpeedUpdate(long downloadBytes, long uploadBytes, long downloadSpeed, long uploadSpeed) {
                if (isOpenVpnSelected) {
                    tvDownloadSpeed.setText(bongoVpn.formatSpeed(downloadSpeed));
                    tvUploadSpeed.setText(bongoVpn.formatSpeed(uploadSpeed));
                    tvSessionUsage.setText("Down: " + BongoVpn.formatBytes(downloadBytes) + " | Up: " + BongoVpn.formatBytes(uploadBytes));
                }
            }
        });
    }

    private void handleOpenVpnConnection() {
        btnConnect.setEnabled(false);
        if (bongoVpn.isConnected()) {
            btnConnect.setText("Disconnecting..."); bongoVpn.stopVpn(); return;
        }

        try {
            JSONObject profile = getSelectedProfile(KEY_OVPN_PROFILES);
            String config = profile == null ? readAsset("japan.ovpn") : profile.optString("config", "");
            String username = profile == null ? "" : profile.optString("username", "");
            String password = profile == null ? "" : profile.optString("password", "");

            if (config.trim().isEmpty()) throw new IllegalArgumentException("OVPN configuration is empty");
            boolean needsAuth = containsAuthUserPass(config);
            if (needsAuth && (username.trim().isEmpty() || password.isEmpty())) {
                btnConnect.setEnabled(true);
                showCredentialsDialog(profile, config);
                return;
            }

            if (profile == null) bongoVpn.attachFromAsset("japan.ovpn", username, password);
            else if (!attachFromStringCompat(config, username, password))
                throw new IllegalStateException("BongoVPN attachFromString API not available");

            btnConnect.setText("Connecting...");
            addHistory("OpenVPN connecting: " + (profile == null ? "assets/japan.ovpn" : profile.optString("name", "Unnamed")));
            if (bongoVpn.hasVpnPermission()) bongoVpn.startVpn();
            else { bongoVpn.requestVpnPermission(); btnConnect.setEnabled(true); btnConnect.setText("Connect OpenVPN"); }
        } catch (Exception e) {
            btnConnect.setEnabled(true); tvSessionUsage.setText("OpenVPN error: " + safeMessage(e));
            Toast.makeText(this, "OpenVPN: " + safeMessage(e), Toast.LENGTH_LONG).show();
        }
    }

    private boolean attachFromStringCompat(String config, String username, String password) throws Exception {
        for (Method method : bongoVpn.getClass().getMethods()) {
            if (!method.getName().equals("attachFromString") || !Modifier.isPublic(method.getModifiers())) continue;
            Class<?>[] p = method.getParameterTypes();
            if (p.length == 3 && p[0] == String.class && p[1] == String.class && p[2] == String.class) {
                method.invoke(bongoVpn, config, username, password); return true;
            }
            if (p.length == 1 && p[0] == String.class) { method.invoke(bongoVpn, config); return true; }
        }
        return false;
    }

    private void importOpenVpnFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/x-openvpn-profile", "text/plain", "application/octet-stream"});
        startActivityForResult(intent, REQUEST_OVPN_FILE);
    }

    private void importV2RayFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        startActivityForResult(intent, REQUEST_V2RAY_FILE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VPN_PERMISSION) {
            if (resultCode == RESULT_OK && pendingV2RayConfig != null) startV2RayService(pendingV2RayConfig);
            else tvSessionUsage.setText("VPN permission denied");
            pendingV2RayConfig = null; return;
        }
        if ((requestCode != REQUEST_OVPN_FILE && requestCode != REQUEST_V2RAY_FILE) || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            Uri uri = data.getData();
            String config = readTextFromUri(uri).trim();
            String name = getDisplayName(uri);
            if (config.isEmpty()) throw new IllegalArgumentException("File is empty");
            if (requestCode == REQUEST_OVPN_FILE) {
                showSaveOvpnProfileDialog(name, config);
            } else {
                showSaveV2RayProfileDialog(name, config);
            }
        } catch (Exception e) { Toast.makeText(this, "Could not read file: " + safeMessage(e), Toast.LENGTH_LONG).show(); }
    }

    private void showSaveOvpnProfileDialog(String fileName, String config) {
        LinearLayout box = verticalBox();
        EditText name = edit("Profile/file name", fileName);
        EditText user = edit("Username (only if auth-user-pass is required)", "");
        EditText pass = edit("Password", ""); pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(name); box.addView(user); box.addView(pass);
        boolean needsAuth = containsAuthUserPass(config);
        TextView note = new TextView(this); note.setText(needsAuth ? "This OVPN contains auth-user-pass. Enter credentials if your server requires them." : "No auth-user-pass directive detected. Credentials are optional."); note.setPadding(0, dp(8), 0, 0); box.addView(note);
        new AlertDialog.Builder(this).setTitle("Save OVPN file")
                .setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d,w) -> {
                    try { addProfile(KEY_OVPN_PROFILES, name.getText().toString().trim(), config, user.getText().toString(), pass.getText().toString()); refreshProfileUi(); Toast.makeText(this, "OVPN file saved: " + name.getText(), Toast.LENGTH_SHORT).show(); }
                    catch (Exception e) { Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show(); }
                }).show();
    }

    private void showSaveV2RayProfileDialog(String fileName, String config) {
        LinearLayout box = verticalBox(); EditText name = edit("Profile/file name", fileName); box.addView(name);
        new AlertDialog.Builder(this).setTitle("Save V2Ray/Xray file").setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d,w) -> { try { addProfile(KEY_V2RAY_PROFILES, name.getText().toString().trim(), config, "", ""); refreshProfileUi(); Toast.makeText(this, "V2Ray/Xray file saved: " + name.getText(), Toast.LENGTH_SHORT).show(); } catch (Exception e) { Toast.makeText(this, safeMessage(e), Toast.LENGTH_LONG).show(); } }).show();
    }

    private void showManualConfigDialog() {
        if (!isOpenVpnSelected) { showManualV2RayDialog(); return; }
        LinearLayout box = verticalBox();
        EditText name = edit("Profile name", "Manual OVPN");
        EditText config = edit("Paste complete .ovpn configuration", ""); config.setGravity(Gravity.TOP | Gravity.START); config.setMinLines(10); config.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        EditText user = edit("Username (optional)", "");
        EditText pass = edit("Password (optional)", ""); pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(name); box.addView(config); box.addView(user); box.addView(pass);
        new AlertDialog.Builder(this).setTitle("Manual OpenVPN Config").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", (d,w) -> {
            String c = config.getText().toString().trim(); if (c.isEmpty()) { Toast.makeText(this,"OVPN config is required",Toast.LENGTH_LONG).show(); return; }
            try { addProfile(KEY_OVPN_PROFILES, name.getText().toString().trim(), c, user.getText().toString(), pass.getText().toString()); refreshProfileUi(); } catch (Exception e) { Toast.makeText(this,safeMessage(e),Toast.LENGTH_LONG).show(); }
        }).show();
    }

    private void showManualV2RayDialog() {
        LinearLayout box = verticalBox(); EditText name = edit("Profile name", "Manual V2Ray"); EditText input = edit("Paste vless://, vmess://, trojan://, or Xray JSON", "");
        input.setGravity(Gravity.TOP | Gravity.START); input.setMinLines(10); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE); box.addView(name); box.addView(input);
        new AlertDialog.Builder(this).setTitle("Manual V2Ray / Xray").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save", (d,w) -> {
            String c=input.getText().toString().trim(); if(c.isEmpty()){Toast.makeText(this,"V2Ray config is required",Toast.LENGTH_LONG).show();return;} try{addProfile(KEY_V2RAY_PROFILES,name.getText().toString().trim(),c,"","");refreshProfileUi();}catch(Exception e){Toast.makeText(this,safeMessage(e),Toast.LENGTH_LONG).show();}
        }).show();
    }

    private void showCredentialsDialog(JSONObject profile, String config) {
        LinearLayout box = verticalBox(); EditText user = edit("Username", profile == null ? "" : profile.optString("username","")); EditText pass = edit("Password", profile == null ? "" : profile.optString("password","")); pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); box.addView(user); box.addView(pass);
        new AlertDialog.Builder(this).setTitle("OpenVPN credentials required").setView(box).setNegativeButton("Cancel", null).setPositiveButton("Save & Connect", (d,w)->{
            if(profile == null){ Toast.makeText(this,"Select/import an OVPN profile first",Toast.LENGTH_LONG).show(); return; }
            try { profile.put("username",user.getText().toString()); profile.put("password",pass.getText().toString()); updateProfile(KEY_OVPN_PROFILES,selectedProfile,profile); refreshProfileUi(); handleOpenVpnConnection(); } catch(Exception e){Toast.makeText(this,safeMessage(e),Toast.LENGTH_LONG).show();}
        }).show();
    }

    private void handleV2RayConnection() {
        if (prefs.getBoolean("xray_running", false)) {
            stopService(new Intent(this, V2RayService.class));
            prefs.edit().putBoolean("xray_running", false).apply();
            btnConnect.setText("Connect V2Ray / Xray");
            tvSessionUsage.setText("V2Ray/Xray stopped");
            addHistory("V2Ray/Xray stopped");
            return;
        }
        JSONObject profile = getSelectedProfile(KEY_V2RAY_PROFILES);
        if (profile == null) { Toast.makeText(this, "Select or import a V2Ray/Xray profile first", Toast.LENGTH_LONG).show(); return; }
        String config = profile.optString("config", "").trim();
        if (config.isEmpty()) { Toast.makeText(this,"V2Ray config is empty",Toast.LENGTH_LONG).show(); return; }
        pendingV2RayConfig = config;
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) startActivityForResult(prepare, REQUEST_VPN_PERMISSION); else startV2RayService(config);
    }

    private void startV2RayService(String config) {
        Intent i = new Intent(this, V2RayService.class); i.putExtra(V2RayService.EXTRA_CONFIG, config);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        btnConnect.setText("Disconnect V2Ray"); tvSessionUsage.setText("V2Ray/Xray starting...");
        addHistory("V2Ray/Xray connecting: " + profileNameForConfig(config));
    }

    private String profileNameForConfig(String config) {
        try {
            JSONObject p = getSelectedProfile(KEY_V2RAY_PROFILES);
            return p == null ? "Unnamed" : p.optString("name", "Unnamed");
        } catch (Exception e) { return "Unnamed"; }
    }

    private void clearSelectedProfile() {
        String key = isOpenVpnSelected ? KEY_OVPN_PROFILES : KEY_V2RAY_PROFILES;
        if (selectedProfile < 0) { Toast.makeText(this,"Select a profile first",Toast.LENGTH_SHORT).show(); return; }
        try { JSONArray arr = profiles(key); String removedName = arr.getJSONObject(selectedProfile).optString("name", "Unnamed"); arr.remove(selectedProfile); saveProfiles(key,arr); addHistory("Deleted " + (isOpenVpnSelected ? "OVPN: " : "V2Ray: ") + removedName); selectedProfile=-1; refreshProfileUi(); } catch(Exception e){Toast.makeText(this,safeMessage(e),Toast.LENGTH_LONG).show();}
    }

    private void refreshProfileUi() {
        final int desiredProfile = selectedProfile;
        List<String> names = new ArrayList<>(); names.add("Use bundled asset / select profile");
        try { JSONArray arr=profiles(isOpenVpnSelected?KEY_OVPN_PROFILES:KEY_V2RAY_PROFILES); for(int i=0;i<arr.length();i++) names.add(arr.getJSONObject(i).optString("name","Unnamed")); } catch(Exception ignored){}
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names); a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerProfile.setAdapter(a);
        updateProtocolToggleUi();
        updateUiState(); updateImportedFilesText();
        renderConfigList();
        spinnerProfile.post(() -> spinnerProfile.setSelection(desiredProfile>=0?desiredProfile+1:0));
    }

    private void renderConfigList() {
        if (configList == null) return;
        configList.removeAllViews();
        JSONArray all = profiles(isOpenVpnSelected ? KEY_OVPN_PROFILES : KEY_V2RAY_PROFILES);
        if (all.length() == 0) {
            TextView empty = new TextView(this); empty.setText("No saved profiles yet.\nTap ＋ to import or add one manually.");
            empty.setTextSize(15); empty.setTextColor(Color.rgb(115,128,122)); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(12),dp(22),dp(12),dp(22)); configList.addView(empty); return;
        }
        for (int i=0;i<all.length();i++) {
            final int index=i;
            try {
                JSONObject p=all.getJSONObject(i);
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(13),dp(12),dp(13));
                android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(18)); bg.setStroke(dp(1), Color.rgb(215,230,222)); card.setBackground(bg);
                LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
                TextView icon=new TextView(this); icon.setText(isOpenVpnSelected?"◉":"⚡"); icon.setTextSize(24); icon.setTextColor(Color.rgb(0,169,92)); icon.setPadding(0,0,dp(12),0); top.addView(icon);
                TextView name=new TextView(this); name.setText(p.optString("name","Unnamed")); name.setTextSize(17); name.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD); name.setTextColor(Color.rgb(16,24,21)); top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
                TextView protocol=new TextView(this); protocol.setText(isOpenVpnSelected?"OVPN":"VLESS / VMess / Trojan / Xray"); protocol.setTextSize(12); protocol.setTextColor(Color.rgb(80,98,90)); top.addView(protocol);
                card.addView(top);
                TextView details=new TextView(this); details.setText("Tap to select  •  " + (p.optString("username","").isEmpty()?"No saved credentials":"Credentials saved")); details.setTextSize(12); details.setTextColor(Color.rgb(120,133,127)); details.setPadding(dp(38),dp(4),0,dp(4)); card.addView(details);
                LinearLayout actions=new LinearLayout(this); actions.setGravity(Gravity.END); MaterialButton select=new MaterialButton(this); select.setText("Use"); select.setAllCaps(false); select.setTextColor(Color.WHITE); select.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(0,169,92))); select.setCornerRadius(dp(14));
                MaterialButton del=new MaterialButton(this); del.setText("Delete"); del.setAllCaps(false); del.setTextColor(Color.rgb(211,47,47)); del.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(255,245,245))); del.setCornerRadius(dp(14)); del.setLayoutParams(new LinearLayout.LayoutParams(-2,dp(44))); actions.addView(del); actions.addView(select); card.addView(actions);
                View.OnClickListener choose=v->{ selectedProfile=index; spinnerProfile.setSelection(index+1); updateProfileInfo(); updateUiState(); Toast.makeText(this,"Selected: "+p.optString("name","Unnamed"),Toast.LENGTH_SHORT).show(); };
                card.setOnClickListener(choose); select.setOnClickListener(choose);
                del.setOnClickListener(v->{ JSONArray a=profiles(isOpenVpnSelected?KEY_OVPN_PROFILES:KEY_V2RAY_PROFILES); a.remove(index); saveProfiles(isOpenVpnSelected?KEY_OVPN_PROFILES:KEY_V2RAY_PROFILES,a); selectedProfile=-1; refreshProfileUi(); });
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(150)); lp.bottomMargin=dp(10); configList.addView(card,lp);
            } catch(Exception ignored) {}
        }
    }

    private void updateUiState() {
        if(isOpenVpnSelected){btnImportConfig.setText("Import .ovpn File");btnManualConfig.setText("Manual OVPN Config");btnClearConfig.setText("Delete Selected OVPN");updateOpenVpnButtonState();}
        else{btnImportConfig.setText("Import V2Ray/Xray File");btnManualConfig.setText("Manual V2Ray / Xray");btnClearConfig.setText("Delete Selected V2Ray");boolean running=prefs.getBoolean("xray_running",false);btnConnect.setText(running?"Disconnect V2Ray":"Connect V2Ray / Xray");btnConnect.setTextColor(running?Color.RED:Color.BLACK);btnConnect.setEnabled(true);tvSessionUsage.setText(running?"V2Ray/Xray connected":"Ready for Xray core");}
        updateProfileInfo();
    }

    private void updateOpenVpnButtonState(){if(bongoVpn!=null&&bongoVpn.isConnected()){btnConnect.setText("Disconnect OpenVPN");btnConnect.setTextColor(Color.RED);}else{btnConnect.setText("Connect OpenVPN");btnConnect.setTextColor(Color.BLACK);}btnConnect.setEnabled(true);}

    private void updateProfileInfo(){
        if(tvProfileInfo==null)return; try{JSONArray arr=profiles(isOpenVpnSelected?KEY_OVPN_PROFILES:KEY_V2RAY_PROFILES); if(selectedProfile>=0&&selectedProfile<arr.length()){JSONObject p=arr.getJSONObject(selectedProfile);tvProfileInfo.setText("Selected: "+p.optString("name","Unnamed"));}else tvProfileInfo.setText(isOpenVpnSelected?"Selected: bundled assets/japan.ovpn":"Select a saved V2Ray/Xray profile");}catch(Exception e){tvProfileInfo.setText("Profile error");}
    }

    private void updateImportedFilesText(){
        StringBuilder sb=new StringBuilder("Imported files/profiles: "); int count=0;
        try{JSONArray o=profiles(KEY_OVPN_PROFILES);JSONArray v=profiles(KEY_V2RAY_PROFILES);count=o.length()+v.length();sb.append(count); if(count>0){sb.append("\n");for(int i=0;i<o.length();i++)sb.append("• OVPN: ").append(o.getJSONObject(i).optString("name","Unnamed")).append("\n");for(int i=0;i<v.length();i++)sb.append("• V2Ray: ").append(v.getJSONObject(i).optString("name","Unnamed")).append("\n");}}catch(Exception ignored){}
        tvImportedFiles.setText(sb.toString());
    }

    private void showStartupFileToast(){
        try{JSONArray o=profiles(KEY_OVPN_PROFILES),v=profiles(KEY_V2RAY_PROFILES);int n=o.length()+v.length();StringBuilder s=new StringBuilder("Saved/imported files: ").append(n);for(int i=0;i<o.length();i++)s.append("\nOVPN: ").append(o.getJSONObject(i).optString("name","Unnamed"));for(int i=0;i<v.length();i++)s.append("\nV2Ray: ").append(v.getJSONObject(i).optString("name","Unnamed"));Toast.makeText(this,s.toString(),Toast.LENGTH_LONG).show();}catch(Exception ignored){}
    }

    private JSONArray profiles(String key){try{return new JSONArray(prefs.getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    private void saveProfiles(String key,JSONArray a){prefs.edit().putString(key,a.toString()).apply();}
    private void addProfile(String key,String name,String config,String user,String pass)throws Exception{if(name==null||name.trim().isEmpty())name="Imported";JSONArray a=profiles(key);JSONObject p=new JSONObject();p.put("name",name);p.put("config",config);p.put("username",user==null?"":user);p.put("password",pass==null?"":pass);a.put(p);saveProfiles(key,a);selectedProfile=a.length()-1;}
    private void updateProfile(String key,int index,JSONObject obj)throws Exception{JSONArray a=profiles(key);if(index>=0&&index<a.length()){a.put(index,obj);saveProfiles(key,a);}}
    private JSONObject getSelectedProfile(String key){try{JSONArray a=profiles(key);return selectedProfile>=0&&selectedProfile<a.length()?a.getJSONObject(selectedProfile):null;}catch(Exception e){return null;}}

    private LinearLayout verticalBox(){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(18),dp(8),dp(18),0);return b;}
    private EditText edit(String hint,String text){EditText e=new EditText(this);e.setHint(hint);e.setText(text==null?"":text);e.setSingleLine(false);e.setPadding(0,dp(6),0,dp(6));return e;}
    private String readTextFromUri(Uri uri)throws Exception{StringBuilder r=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri)))){String line;while((line=br.readLine())!=null)r.append(line).append('\n');}return r.toString();}
    private String getDisplayName(Uri uri){
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst()){String n=c.getString(0);if(n!=null&&!n.trim().isEmpty())return n;}
        }catch(Exception ignored){}
        String n=uri.getLastPathSegment();if(n==null)n="Imported file";int slash=n.lastIndexOf('/');if(slash>=0)n=n.substring(slash+1);return n;
    }
    private String readAsset(String name)throws Exception{StringBuilder r=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(getAssets().open(name)))){String l;while((l=br.readLine())!=null)r.append(l).append('\n');}return r.toString();}
    private boolean containsAuthUserPass(String c){for(String line:c.split("\\r?\\n")){String s=line.trim();if(s.startsWith("auth-user-pass")&&!s.startsWith("#")&&!s.startsWith(";"))return true;}return false;}
    private String safeMessage(Exception e){Throwable t=e;while(t.getCause()!=null)t=t.getCause();return t.getMessage()==null?t.getClass().getSimpleName():t.getMessage();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    @Override protected void onDestroy(){super.onDestroy();if(bongoVpn!=null)bongoVpn.release();}
}
