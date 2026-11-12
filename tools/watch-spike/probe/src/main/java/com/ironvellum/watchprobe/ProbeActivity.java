package com.ironvellum.watchprobe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Diagnostic UI only. The approved production watch designs are intentionally not implemented here. */
public final class ProbeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BluetoothDevice> peers = new ArrayList<>();
    private final List<Button> fixtureButtons = new ArrayList<>();
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((ProbeService.LocalBinder) binder).service();
            service.observe(ProbeActivity.this::render);
        }
        @Override public void onServiceDisconnected(ComponentName name) { service = null; render(); }
    };
    private final Runnable tick = this::render;
    private ProbeService service;
    private boolean bound, visible;
    private Spinner devices;
    private TextView status, peerStatus, figure, timing, fingerprint;
    private Button begin, confirm, deny;
    private LinearLayout content;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (getResources().getDisplayMetrics().density * 24);
        content.setPadding(padding, padding, padding, padding);
        scroll.setFillViewport(true); scroll.addView(content); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            content.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom);
            return insets;
        });
        scroll.requestApplyInsets();
        text(BuildConfig.IS_WATCH ? R.string.title_wear : R.string.title_phone, 22);
        text(R.string.warning, 14);
        status = text(R.string.disconnected, 16);
        devices = new Spinner(this); devices.setContentDescription(getString(R.string.select_peer)); content.addView(devices);
        peerStatus = text(R.string.permission, 14);
        button(R.string.refresh, this::permissionsAndPeers);
        begin = button(BuildConfig.IS_WATCH ? R.string.connect : R.string.listen, this::begin);
        button(R.string.disconnect, () -> { if (service != null) service.disconnect(); });
        button(R.string.forget, () -> { if (service != null) service.forget(); });
        fingerprint = text(0, 14); fingerprint.setTextIsSelectable(true);
        confirm = button(R.string.confirm, () -> { if (service != null) service.approve(true); });
        deny = button(R.string.deny, () -> { if (service != null) service.approve(false); });
        figure = text(0, 18);
        fixture(R.string.reps_minus, ProbeProtocol.REPS, () -> state().reps - 1);
        fixture(R.string.reps_plus, ProbeProtocol.REPS, () -> state().reps + 1);
        fixture(R.string.load_minus, ProbeProtocol.LOAD, () -> state().loadGrams - 2500);
        fixture(R.string.load_plus, ProbeProtocol.LOAD, () -> state().loadGrams + 2500);
        fixture(R.string.done, ProbeProtocol.DONE, () -> 1);
        fixture(R.string.undo, ProbeProtocol.DONE, () -> 0);
        fixture(R.string.rpe, ProbeProtocol.RPE, () -> 8);
        fixture(R.string.timer_start, ProbeProtocol.START_TIMER, () -> 0);
        fixture(R.string.timer_stop, ProbeProtocol.STOP_TIMER, () -> 0);
        if (!BuildConfig.IS_WATCH) fixture(R.string.new_set, ProbeProtocol.NEXT_SET, () -> 0);
        timing = text(R.string.not_measured, 14);
        render();
    }

    @Override public void onStart() {
        super.onStart();
        visible = true;
        bound = bindService(new Intent(this, ProbeService.class), connection, BIND_AUTO_CREATE);
        handler.post(tick);
        if (permissionGranted()) loadPeers();
    }
    @Override public void onStop() {
        visible = false;
        handler.removeCallbacks(tick);
        if (service != null) service.unobserve();
        if (bound) unbindService(connection);
        service = null; bound = false;
        super.onStop();
    }
    private boolean permissionGranted() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
    private void permissionsAndPeers() {
        List<String> permissions = new ArrayList<>();
        if (!permissionGranted()) permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (permissions.isEmpty()) loadPeers();
        else requestPermissions(permissions.toArray(new String[0]), 1);
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (permissionGranted()) loadPeers();
        else peerStatus.setText(R.string.permission);
    }
    @SuppressLint("MissingPermission") // Calls require the runtime permission and only list existing OS bonds.
    private void loadPeers() {
        peers.clear();
        if (!permissionGranted()) { peerStatus.setText(R.string.permission); return; }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { peerStatus.setText(R.string.unavailable); return; }
        try {
            peers.addAll(adapter.getBondedDevices());
            List<String> labels = new ArrayList<>();
            for (BluetoothDevice peer : peers) {
                String name = peer.getName();
                labels.add((name == null ? "" : name + " · ") + peer.getAddress());
            }
            devices.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
            peerStatus.setText(peers.isEmpty() ? getString(R.string.no_devices) : "");
        } catch (SecurityException e) { peerStatus.setText(R.string.permission); }
    }
    @SuppressLint("MissingPermission") // Address comes only from the selected OS-bonded peer after permission check.
    private void begin() {
        if (!permissionGranted()) { permissionsAndPeers(); return; }
        int selected = devices.getSelectedItemPosition();
        if (selected < 0 || selected >= peers.size()) { peerStatus.setText(R.string.select_peer); return; }
        startForegroundService(new Intent(this, ProbeService.class)
                .putExtra(ProbeService.EXTRA_ADDRESS, peers.get(selected).getAddress()));
    }
    private TextView text(int resource, int size) {
        TextView view = new TextView(this); view.setTextSize(size); view.setPadding(0, 8, 0, 8);
        if (resource != 0) view.setText(resource);
        content.addView(view); return view;
    }
    private Button button(int resource, Runnable action) {
        Button button = new Button(this); button.setText(resource); button.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
        button.setOnClickListener(v -> action.run()); content.addView(button); return button;
    }
    private interface Value { int get(); }
    private void fixture(int resource, int action, Value value) {
        Button button = button(resource, () -> {
            if (service != null && state() != null) service.command(action, value.get());
        });
        fixtureButtons.add(button);
    }
    private ProbeProtocol.State state() { return service == null ? null : service.state(); }
    private void render() {
        if (status == null) return;
        boolean live = service != null && service.live();
        boolean enabled = service != null && state() != null && (!BuildConfig.IS_WATCH || live) && !service.pending();
        for (Button button : fixtureButtons) button.setEnabled(enabled);
        begin.setEnabled(service != null && !service.busy());
        if (service == null) { status.setText(R.string.disconnected); confirm.setVisibility(View.GONE); deny.setVisibility(View.GONE); return; }
        int message = service.status();
        if (!BuildConfig.IS_WATCH && !service.busy() && message == R.string.disconnected) message = R.string.local;
        status.setText(message == R.string.failed ? getString(R.string.failed, service.error()) : getString(message));
        boolean approval = service.awaitingApproval();
        confirm.setVisibility(approval ? View.VISIBLE : View.GONE); deny.setVisibility(approval ? View.VISIBLE : View.GONE);
        String hash = service.fingerprint();
        fingerprint.setVisibility(hash.isEmpty() ? View.GONE : View.VISIBLE);
        if (!hash.isEmpty()) {
            String grouped = hash.replaceAll("(.{16})(?!$)", "$1\n");
            int caption = approval ? R.string.pairing : live ? R.string.fingerprint : R.string.fingerprint_pending;
            fingerprint.setText(getString(caption, grouped));
        }
        ProbeProtocol.State s = state();
        if (s == null) figure.setText(R.string.awaiting_snapshot);
        else {
            String timer = getString(R.string.timer_idle);
            if (BuildConfig.IS_WATCH && !live && s.timerStart >= 0) timer = getString(R.string.timer_stale);
            else if (s.timerStart >= 0 && service.clockKnown()) {
                long counted = Math.max(0, service.estimatedPhoneNow() - s.timerStart);
                timer = counted < 5000 ? getString(R.string.timer_ready, (5000 - counted + 999) / 1000)
                        : getString(R.string.timer_running, (counted - 5000) / 1000);
            } else if (s.timerStart >= 0) timer = getString(R.string.not_measured);
            else if (s.stoppedSeconds >= 0) timer = getString(R.string.timer_stopped, s.stoppedSeconds);
            figure.setText(getString(R.string.fixture, s.set, s.revision,
                    getResources().getQuantityString(R.plurals.reps_count, s.reps, s.reps),
                    String.format(Locale.ROOT, "%.1f", s.loadGrams / 1000.0),
                    getString(s.done ? R.string.yes : R.string.no), s.rpe == 0 ? getString(R.string.unset) : Integer.toString(s.rpe), timer));
        }
        timing.setText(!live || service.lastRtt() < 0 || service.uncertainty() < 0 ? getString(R.string.not_measured)
                : getString(R.string.timing, service.lastRtt(), service.uncertainty(), service.lastReplyAge()));
        handler.removeCallbacks(tick);
        if (visible && (live || s != null && s.timerStart >= 0)) handler.postDelayed(tick, 1000);
    }
}
