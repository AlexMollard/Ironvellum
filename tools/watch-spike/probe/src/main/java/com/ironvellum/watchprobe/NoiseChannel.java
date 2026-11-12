package com.ironvellum.watchprobe;

import com.southernstorm.noise.protocol.CipherStatePair;
import com.southernstorm.noise.protocol.HandshakeState;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

/** Stream-independent Noise XX transport. First pairing requires full transcript comparison. */
public final class NoiseChannel implements Closeable {
    public static final int MAX_FRAME = 4096;
    private static final byte[] PROLOGUE = "Ironvellum disposable watch probe v1".getBytes(StandardCharsets.US_ASCII);
    private final DataInputStream input;
    private final DataOutputStream output;
    private final CipherStatePair ciphers;
    private final byte[] peerKey;
    private final String fingerprint;
    private volatile boolean closed;

    public interface Approval {
        boolean approve(String fingerprint, boolean alreadyPinned) throws IOException;
    }

    private NoiseChannel(DataInputStream input, DataOutputStream output, CipherStatePair ciphers,
                         byte[] peerKey, String fingerprint) {
        this.input = input;
        this.output = output;
        this.ciphers = ciphers;
        this.peerKey = peerKey;
        this.fingerprint = fingerprint;
    }

    public static NoiseChannel open(InputStream in, OutputStream out, boolean initiator,
                                    byte[] privateKey, byte[] pinnedPeer, Approval approval) throws IOException {
        if (privateKey.length != 32 || (pinnedPeer != null && pinnedPeer.length != 32)) {
            throw new IOException("Invalid identity key length");
        }
        DataInputStream input = new DataInputStream(in);
        DataOutputStream output = new DataOutputStream(out);
        HandshakeState handshake = null;
        NoiseChannel channel = null;
        CipherStatePair pair = null;
        boolean established = false;
        try {
            handshake = new HandshakeState("Noise_XX_25519_AESGCM_SHA256",
                    initiator ? HandshakeState.INITIATOR : HandshakeState.RESPONDER);
            handshake.setPrologue(PROLOGUE, 0, PROLOGUE.length);
            handshake.getLocalKeyPair().setPrivateKey(privateKey, 0);
            handshake.start();
            byte[] buffer = new byte[MAX_FRAME];
            while (handshake.getAction() != HandshakeState.SPLIT) {
                if (handshake.getAction() == HandshakeState.WRITE_MESSAGE) {
                    int count = handshake.writeMessage(buffer, 0, null, 0, 0);
                    writeFrame(output, Arrays.copyOf(buffer, count));
                } else if (handshake.getAction() == HandshakeState.READ_MESSAGE) {
                    byte[] frame = readFrame(input);
                    int payloadLength = handshake.readMessage(frame, 0, frame.length, buffer, 0);
                    if (payloadLength != 0) throw new IOException("Unexpected handshake payload");
                } else {
                    throw new IOException("Noise handshake failed");
                }
            }
            byte[] peer = new byte[32];
            handshake.getRemotePublicKey().getPublicKey(peer, 0);
            boolean pinned = pinnedPeer != null;
            if (pinned && !MessageDigest.isEqual(peer, pinnedPeer)) throw new IOException("Peer identity changed; forget and pair explicitly");
            String fingerprint = hex(handshake.getHandshakeHash());
            pair = handshake.split();
            channel = new NoiseChannel(input, output, pair, peer, fingerprint);
            // No fixture payload may leave until both users have accepted this authenticated transcript.
            if (!approval.approve(fingerprint, pinned)) throw new IOException("Pairing rejected");
            channel.send(new byte[] {1});
            if (!Arrays.equals(channel.receive(), new byte[] {1})) throw new IOException("Peer did not confirm pairing");
            established = true;
            return channel;
        } catch (GeneralSecurityException | IllegalArgumentException | IllegalStateException e) {
            throw new IOException("Secure channel rejected", e);
        } finally {
            if (handshake != null) handshake.destroy();
            if (!established) {
                if (pair != null) pair.destroy();
                try { input.close(); } catch (IOException ignored) { /* preserve handshake failure */ }
                try { output.close(); } catch (IOException ignored) { /* preserve handshake failure */ }
            }
        }
    }

    public byte[] peerKey() { return peerKey.clone(); }
    public String fingerprint() { return fingerprint; }

    public synchronized void send(byte[] payload) throws IOException {
        if (closed || payload.length > MAX_FRAME - 16) throw new IOException("Closed channel or oversized payload");
        byte[] encrypted = new byte[payload.length + 16];
        try {
            int length = ciphers.getSender().encryptWithAd(null, payload, 0, encrypted, 0, payload.length);
            writeFrame(output, Arrays.copyOf(encrypted, length));
        } catch (GeneralSecurityException | IllegalStateException e) {
            throw new IOException("Encrypt failed", e);
        }
    }

    /** Exactly one reader per channel; Noise rejects reordered or replayed ciphertext. */
    public byte[] receive() throws IOException {
        if (closed) throw new IOException("Closed channel");
        byte[] encrypted = readFrame(input);
        if (encrypted.length < 16 || closed) throw new IOException("Invalid encrypted frame");
        byte[] payload = new byte[encrypted.length - 16];
        try {
            int length = ciphers.getReceiver().decryptWithAd(null, encrypted, 0, payload, 0, encrypted.length);
            return Arrays.copyOf(payload, length);
        } catch (GeneralSecurityException | IllegalStateException e) {
            throw new IOException("Unauthenticated or replayed frame", e);
        }
    }

    static byte[] readFrame(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > MAX_FRAME) throw new IOException("Invalid frame length");
        byte[] frame = new byte[size];
        input.readFully(frame);
        return frame;
    }

    private static void writeFrame(DataOutputStream output, byte[] frame) throws IOException {
        if (frame.length <= 0 || frame.length > MAX_FRAME) throw new IOException("Invalid frame length");
        output.writeInt(frame.length);
        output.write(frame);
        output.flush();
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }

    /** Called by the sole reader after its socket has been closed to interrupt any pending read. */
    @Override public synchronized void close() throws IOException {
        closed = true;
        ciphers.destroy();
        try { input.close(); } finally { output.close(); }
    }
}
