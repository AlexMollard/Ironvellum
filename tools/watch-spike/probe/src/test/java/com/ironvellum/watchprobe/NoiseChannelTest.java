package com.ironvellum.watchprobe;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class NoiseChannelTest {
    @Test public void twoPeersVerifySameTranscriptAndExchangeEncryptedPayloads() throws Exception {
        try (Pair pair = new Pair()) {
            pair.open(null, null);
            assertEquals(64, pair.client.fingerprint().length());
            assertEquals(pair.client.fingerprint(), pair.server.fingerprint());
            byte[] message = new byte[] {10, 20, 30};
            pair.client.send(message);
            assertArrayEquals(message, pair.server.receive());
            pair.server.send(new byte[] {40, 50});
            assertArrayEquals(new byte[] {40, 50}, pair.client.receive());
            assertFalse(Arrays.equals(pair.client.peerKey(), pair.server.peerKey()));
        }
    }
    @Test public void reconnectAuthenticatesPinnedStaticIdentities() throws Exception {
        byte[] clientPin, serverPin, clientIdentity, serverIdentity;
        try (Pair first = new Pair()) {
            first.open(null, null);
            clientPin = first.client.peerKey(); serverPin = first.server.peerKey();
            clientIdentity = first.clientKey.clone(); serverIdentity = first.serverKey.clone();
        }
        try (Pair next = new Pair(clientIdentity, serverIdentity)) {
            next.open(clientPin, serverPin);
            next.client.send(new byte[] {99});
            assertArrayEquals(new byte[] {99}, next.server.receive());
        }
    }
    @Test public void changedPinnedIdentityFailsBeforeApproval() throws Exception {
        try (Pair pair = new Pair()) {
            Future<NoiseChannel> client = pair.pool.submit(() -> NoiseChannel.open(pair.clientInput, pair.clientOutput,
                    true, pair.clientKey, randomKey(), (hash, pinned) -> { fail("Changed pin must not prompt"); return true; }));
            Future<NoiseChannel> server = pair.pool.submit(() -> NoiseChannel.open(pair.serverInput, pair.serverOutput,
                    false, pair.serverKey, null, (hash, pinned) -> true));
            java.util.concurrent.ExecutionException failure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> client.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IOException);
            server.cancel(true);
        }
    }
    @Test public void rejectedFingerprintNeverReturnsAnApplicationChannel() throws Exception {
        try (Pair pair = new Pair()) {
            Future<NoiseChannel> client = pair.pool.submit(() -> NoiseChannel.open(pair.clientInput, pair.clientOutput,
                    true, pair.clientKey, null, (hash, pinned) -> false));
            Future<NoiseChannel> server = pair.pool.submit(() -> NoiseChannel.open(pair.serverInput, pair.serverOutput,
                    false, pair.serverKey, null, (hash, pinned) -> false));
            assertThrows(java.util.concurrent.ExecutionException.class, () -> client.get(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.ExecutionException.class, () -> server.get(5, TimeUnit.SECONDS));
        }
    }
    @Test public void oneApprovalCannotOpenChannelUntilSecondApproves() throws Exception {
        assertSecondApprovalRequired(true);
    }
    @Test public void secondPeerRejectingClosesAlreadyApprovedSide() throws Exception {
        assertSecondApprovalRequired(false);
    }
    private void assertSecondApprovalRequired(boolean acceptSecond) throws Exception {
        try (Pair pair = new Pair()) {
            CountDownLatch entered = new CountDownLatch(2), releaseSecond = new CountDownLatch(1);
            NoiseChannel.Approval first = (hash, pinned) -> { entered.countDown(); return true; };
            NoiseChannel.Approval second = (hash, pinned) -> {
                entered.countDown();
                try { return releaseSecond.await(5, TimeUnit.SECONDS) && acceptSecond; }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            };
            Future<NoiseChannel> client = pair.pool.submit(() -> NoiseChannel.open(pair.clientInput, pair.clientOutput, true, pair.clientKey, null, first));
            Future<NoiseChannel> server = pair.pool.submit(() -> NoiseChannel.open(pair.serverInput, pair.serverOutput, false, pair.serverKey, null, second));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> client.get(200, TimeUnit.MILLISECONDS));
            releaseSecond.countDown();
            if (acceptSecond) {
                pair.client = client.get(5, TimeUnit.SECONDS); pair.server = server.get(5, TimeUnit.SECONDS);
            } else {
                assertThrows(java.util.concurrent.ExecutionException.class, () -> server.get(5, TimeUnit.SECONDS));
                assertThrows(java.util.concurrent.ExecutionException.class, () -> client.get(5, TimeUnit.SECONDS));
            }
        }
    }
    @Test public void alteredEncryptedPayloadIsRejected() throws Exception {
        try (Pair pair = new Pair()) {
            pair.open(null, null);
            pair.clientOutput.tamper = true;
            pair.client.send(new byte[] {1, 2, 3});
            assertThrows(IOException.class, () -> pair.server.receive());
        }
    }
    @Test public void replayedCiphertextIsRejected() throws Exception {
        try (Pair pair = new Pair()) {
            pair.open(null, null);
            pair.clientOutput.record.reset();
            pair.client.send(new byte[] {1, 2, 3});
            byte[] replay = pair.clientOutput.record.toByteArray();
            assertArrayEquals(new byte[] {1, 2, 3}, pair.server.receive());
            pair.rawClientOutput.write(replay); pair.rawClientOutput.flush();
            assertThrows(IOException.class, () -> pair.server.receive());
        }
    }
    @Test public void invalidLengthsAndTruncationAreRejectedBeforeAllocation() throws Exception {
        for (int size : new int[] {-1, 0, NoiseChannel.MAX_FRAME + 1, Integer.MAX_VALUE}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            new DataOutputStream(bytes).writeInt(size);
            assertThrows(IOException.class, () -> NoiseChannel.readFrame(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
        assertThrows(IOException.class, () -> NoiseChannel.readFrame(new DataInputStream(new ByteArrayInputStream(new byte[] {0, 0, 0, 5, 1}))));
    }
    @Test public void oversizedPlaintextCannotBeSent() throws Exception {
        try (Pair pair = new Pair()) {
            pair.open(null, null);
            assertThrows(IOException.class, () -> pair.client.send(new byte[NoiseChannel.MAX_FRAME]));
        }
    }
    private static byte[] randomKey() { byte[] key = new byte[32]; new SecureRandom().nextBytes(key); return key; }

    private static final class Tap extends FilterOutputStream {
        final ByteArrayOutputStream record = new ByteArrayOutputStream();
        boolean tamper;
        Tap(OutputStream output) { super(output); }
        @Override public synchronized void write(int value) throws IOException { out.write(value); record.write(value); }
        @Override public synchronized void write(byte[] bytes, int offset, int count) throws IOException {
            byte[] copy = Arrays.copyOfRange(bytes, offset, offset + count);
            if (tamper && count > 4) copy[count - 1] ^= 1;
            out.write(copy); record.write(copy);
        }
    }
    private static final class Pair implements AutoCloseable {
        final ExecutorService pool = Executors.newFixedThreadPool(2);
        final PipedInputStream clientInput = new PipedInputStream(16_384), serverInput = new PipedInputStream(16_384);
        final PipedOutputStream rawClientOutput, serverOutput;
        final Tap clientOutput;
        final byte[] clientKey, serverKey;
        NoiseChannel client, server;
        Pair() throws IOException { this(randomKey(), randomKey()); }
        Pair(byte[] clientKey, byte[] serverKey) throws IOException {
            this.clientKey = clientKey; this.serverKey = serverKey;
            rawClientOutput = new PipedOutputStream(serverInput); serverOutput = new PipedOutputStream(clientInput);
            clientOutput = new Tap(rawClientOutput);
        }
        void open(byte[] clientPin, byte[] serverPin) throws Exception {
            AtomicReference<String> firstHash = new AtomicReference<>();
            CyclicBarrier barrier = new CyclicBarrier(2);
            NoiseChannel.Approval approval = (hash, pinned) -> {
                firstHash.compareAndSet(null, hash);
                try { barrier.await(5, TimeUnit.SECONDS); }
                catch (Exception e) { throw new IOException(e); }
                return firstHash.get().equals(hash);
            };
            Future<NoiseChannel> one = pool.submit(() -> NoiseChannel.open(clientInput, clientOutput, true, clientKey, clientPin, approval));
            Future<NoiseChannel> two = pool.submit(() -> NoiseChannel.open(serverInput, serverOutput, false, serverKey, serverPin, approval));
            client = one.get(5, TimeUnit.SECONDS); server = two.get(5, TimeUnit.SECONDS);
        }
        @Override public void close() throws IOException {
            clientInput.close(); serverInput.close(); rawClientOutput.close(); serverOutput.close();
            pool.shutdownNow();
            if (client != null) client.close(); if (server != null) server.close();
        }
    }
}
