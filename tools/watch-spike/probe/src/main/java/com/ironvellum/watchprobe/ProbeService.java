package com.ironvellum.watchprobe;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the actual RFCOMM link independently of the Activity. Never opens Ironvellum storage. */
public final class ProbeService extends Service {
    public static final String EXTRA_ADDRESS = "peer";
    private static final UUID SERVICE_ID = UUID.fromString("77a1574f-1f7a-4d23-a2ce-4bab504b819f");
    private static final String CHANNEL = "watch-probe";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor reader = executor(1);
    private final ThreadPoolExecutor writer = executor(16);
    private final Semaphore inboundSlots = new Semaphore(16);
    private final ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
    private final LocalBinder binder = new LocalBinder();
    private SharedPreferences identity;
    private Run active;
    private Runnable listener;
    private ProbeProtocol.State state;
    private int status = R.string.disconnected;
    private String error = "", fingerprint = "";
    private boolean awaitingApproval, pending;
    private long nextId = new SecureRandom().nextInt(Integer.MAX_VALUE - 1) + 1L;
    private long localId = -1, pendingId, pendingAt, pingId, pingAt, offset;
    private long lastRtt = -1, uncertainty = -1, bestRtt = Long.MAX_VALUE;

    public final class LocalBinder extends Binder { public ProbeService service() { return ProbeService.this; } }
    private static ThreadPoolExecutor executor(int capacity) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(capacity));
    }
    @Override public void onCreate() {
        super.onCreate();
        identity = getSharedPreferences("probe_identity", MODE_PRIVATE);
        if (!BuildConfig.IS_WATCH) state = authority.state();
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String address = intent == null ? null : intent.getStringExtra(EXTRA_ADDRESS);
        if (address != null) begin(address);
        else stopSelf();
        return START_NOT_STICKY;
    }

    public void observe(Runnable callback) { listener = callback; changed(); }
    public void unobserve() { listener = null; }
    public ProbeProtocol.State state() { return state; }
    public int status() { return status; }
    public String error() { return error; }
    public String fingerprint() { return fingerprint; }
    public boolean awaitingApproval() { return awaitingApproval; }
    public boolean busy() { return active != null; }
    public boolean live() { return active != null && active.live; }
    public boolean pending() { return pending; }
    public long lastRtt() { return lastRtt; }
    public long uncertainty() { return uncertainty; }
    public long lastReplyAge() { return active == null ? 0 : Math.max(0, (SystemClock.elapsedRealtime() - active.lastReply) / 1000); }
    public long estimatedPhoneNow() { return SystemClock.elapsedRealtime() + (BuildConfig.IS_WATCH ? offset : 0); }
    public boolean clockKnown() { return !BuildConfig.IS_WATCH || uncertainty >= 0; }

    private void changed() { if (listener != null) listener.run(); }
    private boolean permissionGranted() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
    private void status(int message) { status = message; changed(); }

    @SuppressLint("MissingPermission") // Central runtime check before every new socket operation.
    private void begin(String address) {
        stopRun(false);
        if (!permissionGranted()) { stopSelf(); status(R.string.permission); return; }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) { stopSelf(); status(R.string.unavailable); return; }
        if (!BluetoothAdapter.checkBluetoothAddress(address)) { fail(null, "Invalid selected address"); return; }
        BluetoothDevice selected = null;
        for (BluetoothDevice device : adapter.getBondedDevices()) if (device.getAddress().equals(address)) selected = device;
        if (selected == null) { fail(null, "Selected device is not bonded"); return; }
        String savedAddress = identity.getString("address", "");
        if (!savedAddress.isEmpty() && !savedAddress.equals(address)) { fail(null, "Forget the previous peer before selecting another device"); return; }
        try {
            foreground();
            Run run = new Run(address);
            active = run;
            pending = false; fingerprint = ""; awaitingApproval = false;
            if (BuildConfig.IS_WATCH) state = null;
            lastRtt = -1; uncertainty = -1; bestRtt = Long.MAX_VALUE;
            pingId = 0;
            authority.newConnection();
            byte[] privateKey = privateKey();
            byte[] pin = decodeIdentity("peer");
            BluetoothDevice peer = selected;
            status(BuildConfig.IS_WATCH ? R.string.connecting : R.string.listening);
            reader.execute(() -> connect(run, adapter, peer, privateKey, pin));
            main.postDelayed(run.deadline, 20_000);
        } catch (RuntimeException | IOException e) { fail(active, e.getMessage()); }
    }

    private byte[] privateKey() throws IOException {
        byte[] existing = decodeIdentity("private");
        if (existing != null) return existing;
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        if (!identity.edit().putString("private", Base64.encodeToString(key, Base64.NO_WRAP)).commit()) {
            Arrays.fill(key, (byte) 0); throw new IOException("Cannot save local identity");
        }
        return key;
    }
    private byte[] decodeIdentity(String key) throws IOException {
        String text = identity.getString(key, null);
        if (text == null) return null;
        try {
            byte[] bytes = Base64.decode(text, Base64.NO_WRAP);
            if (bytes.length != 32) throw new IllegalArgumentException();
            return bytes;
        } catch (IllegalArgumentException e) { throw new IOException("Invalid identity storage; explicitly forget the peer", e); }
    }

    @SuppressLint("MissingPermission") // Permission was checked in begin; revocation fails the run closed.
    private void connect(Run run, BluetoothAdapter adapter, BluetoothDevice peer, byte[] key, byte[] pin) {
        NoiseChannel channel = null;
        try {
            BluetoothSocket socket;
            if (BuildConfig.IS_WATCH) {
                socket = peer.createRfcommSocketToServiceRecord(SERVICE_ID);
                run.attach(socket);
                socket.connect();
            } else {
                BluetoothServerSocket server = adapter.listenUsingRfcommWithServiceRecord("IronvellumProbe", SERVICE_ID);
                run.attach(server);
                socket = server.accept();
                run.attach(socket);
                server.close();
                if (!socket.getRemoteDevice().getAddress().equals(run.address)) throw new IOException("Unselected peer rejected");
            }
            main.post(() -> {
                if (active != run) return;
                status(R.string.handshake);
                main.removeCallbacks(run.deadline); main.postDelayed(run.deadline, 150_000);
            });
            channel = NoiseChannel.open(socket.getInputStream(), socket.getOutputStream(), BuildConfig.IS_WATCH, key, pin,
                    (hash, pinned) -> approve(run, hash, pinned));
            byte[] remote = channel.peerKey();
            synchronized (run) {
                // Forget/cancel must win over an in-flight handshake saving its peer pin.
                if (run.cancelled.get()) throw new IOException("Cancelled");
                if (!identity.edit().putString("peer", Base64.encodeToString(remote, Base64.NO_WRAP))
                        .putString("address", run.address).commit()) throw new IOException("Cannot save peer identity");
                run.channel = channel;
            }
            main.post(() -> {
                if (active != run) return;
                main.removeCallbacks(run.deadline);
                run.live = true; run.lastReply = SystemClock.elapsedRealtime();
                awaitingApproval = false; status(R.string.live);
                if (!BuildConfig.IS_WATCH) send(run, ProbeProtocol.Message.state(0, authority.state(), ProbeProtocol.ACCEPTED, SystemClock.elapsedRealtime()));
                heartbeat(run);
            });
            while (!run.cancelled.get()) {
                ProbeProtocol.Message message = ProbeProtocol.decode(channel.receive());
                try {
                    inboundSlots.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Receive interrupted", e);
                }
                main.post(() -> {
                    try { received(run, message); }
                    finally { inboundSlots.release(); }
                });
            }
        } catch (IOException | RuntimeException e) {
            main.post(() -> fail(run, e.getMessage()));
        } finally {
            Arrays.fill(key, (byte) 0);
            run.cancel();
            if (channel != null) try { channel.close(); } catch (IOException ignored) { /* socket is already closed */ }
        }
    }

    private boolean approve(Run run, String hash, boolean pinned) throws IOException {
        main.post(() -> {
            if (active != run) return;
            fingerprint = hash; awaitingApproval = !pinned;
            changed();
        });
        if (pinned) return !run.cancelled.get();
        try {
            return run.approval.await(120, TimeUnit.SECONDS) && run.approved && !run.cancelled.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException("Pairing interrupted", e);
        }
    }
    public void approve(boolean accepted) {
        Run run = active;
        if (run == null || !awaitingApproval) return;
        run.approved = accepted; awaitingApproval = false; run.approval.countDown();
        if (!accepted) disconnect();
        else changed();
    }

    private void received(Run run, ProbeProtocol.Message message) {
        if (active != run || run.cancelled.get() || !run.live) return;
        long now = SystemClock.elapsedRealtime();
        run.lastReply = now;
        if (!BuildConfig.IS_WATCH && message.type == ProbeProtocol.REQUEST && message.id > 0 && message.action != ProbeProtocol.NEXT_SET) {
            ProbeProtocol.Message reply = authority.apply(message, now);
            state = authority.state(); send(run, reply); changed();
        } else if (!BuildConfig.IS_WATCH && message.type == ProbeProtocol.PING) {
            send(run, ProbeProtocol.Message.pong(message, now));
        } else if (BuildConfig.IS_WATCH && message.type == ProbeProtocol.STATE) {
            if (state == null || message.state.revision >= state.revision) state = message.state;
            if (pending && message.id == pendingId) {
                pending = false; lastRtt = now - pendingAt;
                status = message.outcome == ProbeProtocol.ACCEPTED ? R.string.live : R.string.conflict;
            }
            changed();
        } else if (BuildConfig.IS_WATCH && message.type == ProbeProtocol.PONG && message.id == pingId && message.sentAt == pingAt) {
            long rtt = now - pingAt;
            if (rtt >= 0 && rtt < bestRtt) {
                bestRtt = rtt; uncertainty = (rtt + 1) / 2;
                offset = message.phoneAt - (pingAt + rtt / 2);
            }
            pingId = 0; changed();
        } else fail(run, "Unexpected message or direction");
    }

    public void command(int action, int value) {
        if (state == null || pending) return;
        if (!BuildConfig.IS_WATCH) {
            ProbeProtocol.Message command = ProbeProtocol.Message.command(localId--, authority.state(), action, value);
            ProbeProtocol.Message result = authority.apply(command, SystemClock.elapsedRealtime());
            state = authority.state();
            if (live()) send(active, ProbeProtocol.Message.state(0, state, result.outcome, SystemClock.elapsedRealtime()));
            changed();
        } else if (live()) {
            pendingId = nextId++; pendingAt = SystemClock.elapsedRealtime(); pending = true;
            ProbeProtocol.Message command = ProbeProtocol.Message.command(pendingId, state, action, value);
            status(R.string.pending); send(active, command);
        }
    }

    private void send(Run run, ProbeProtocol.Message message) {
        if (active != run || run.cancelled.get()) return;
        try {
            byte[] payload = ProbeProtocol.encode(message);
            writer.execute(() -> {
                if (run.cancelled.get() || run.channel == null) return;
                try { run.channel.send(payload); }
                catch (IOException | RuntimeException e) { main.post(() -> fail(run, e.getMessage())); }
            });
        } catch (IOException | RuntimeException e) { fail(run, e.getMessage()); }
    }

    private void heartbeat(Run run) {
        if (active != run || run.cancelled.get() || !run.live) return;
        long now = SystemClock.elapsedRealtime();
        if (now - run.lastReply > 15_000 || pending && now - pendingAt > 5_000) {
            fail(run, "Peer reply timed out; action outcome is unconfirmed"); return;
        }
        if (BuildConfig.IS_WATCH && pingId == 0) {
            pingId = nextId++; pingAt = now;
            send(run, ProbeProtocol.Message.ping(pingId, pingAt));
        }
        main.postDelayed(() -> heartbeat(run), 1000);
    }

    public void disconnect() { stopRun(true); }
    private void stopRun(boolean stopService) {
        Run run = active; active = null;
        if (run != null) { main.removeCallbacks(run.deadline); run.cancel(); }
        pending = false; awaitingApproval = false; fingerprint = ""; status = R.string.disconnected;
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (stopService) stopSelf();
        changed();
    }
    public void forget() {
        disconnect();
        if (!identity.edit().clear().commit()) { fail(null, "Cannot clear pairing storage"); return; }
        fingerprint = ""; changed();
    }
    private void fail(Run run, String detail) {
        if (run != null && active != run) return;
        disconnect(); error = detail == null ? "Connection closed" : detail; status(R.string.failed);
    }
    private void foreground() {
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, ProbeActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.notification_text))
                .setContentIntent(open).setOngoing(true).build();
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
    }
    @Override public void onDestroy() {
        disconnect(); listener = null; main.removeCallbacksAndMessages(null);
        reader.shutdownNow(); writer.shutdownNow(); super.onDestroy();
    }

    private final class Run {
        final String address;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final CountDownLatch approval = new CountDownLatch(1);
        final Runnable deadline = () -> fail(this, "Connection or pairing timed out");
        volatile boolean approved;
        boolean live;
        long lastReply;
        volatile NoiseChannel channel;
        private BluetoothSocket socket;
        private BluetoothServerSocket server;
        Run(String address) { this.address = address; }
        synchronized void attach(BluetoothSocket value) throws IOException {
            if (cancelled.get()) { value.close(); throw new IOException("Cancelled"); }
            socket = value;
        }
        synchronized void attach(BluetoothServerSocket value) throws IOException {
            if (cancelled.get()) { value.close(); throw new IOException("Cancelled"); }
            server = value;
        }
        synchronized void cancel() {
            cancelled.set(true); approval.countDown();
            if (server != null) try { server.close(); } catch (IOException ignored) { /* already closed */ }
            if (socket != null) try { socket.close(); } catch (IOException ignored) { /* already closed */ }
        }
    }
}
