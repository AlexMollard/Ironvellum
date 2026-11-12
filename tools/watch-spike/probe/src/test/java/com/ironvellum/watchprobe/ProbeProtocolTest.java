package com.ironvellum.watchprobe;

import static org.junit.Assert.*;
import java.io.IOException;
import org.junit.Test;

public class ProbeProtocolTest {
    @Test public void acceptedEditAndDuplicateChangeOneFixtureRevision() {
        ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
        ProbeProtocol.Message command = ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.REPS, 10);
        assertEquals(ProbeProtocol.ACCEPTED, authority.apply(command, 100).outcome);
        assertEquals(ProbeProtocol.ACCEPTED, authority.apply(command, 200).outcome);
        assertEquals(1, authority.state().revision);
        assertEquals(10, authority.state().reps);
        assertEquals(60_000, authority.state().loadGrams);
    }
    @Test public void reusedCommandIdentityWithDifferentPayloadIsRejected() {
        ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
        ProbeProtocol.Message first = ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.REPS, 10);
        authority.apply(first, 100);
        ProbeProtocol.Message changed = ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.LOAD, 80_000);
        assertEquals(ProbeProtocol.REJECTED, authority.apply(changed, 200).outcome);
        assertEquals(60_000, authority.state().loadGrams);
    }
    @Test public void concurrentStaleEditCannotOverwritePhoneFigure() {
        ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
        ProbeProtocol.Message watch = ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.REPS, 12);
        authority.apply(ProbeProtocol.Message.command(-1, authority.state(), ProbeProtocol.LOAD, 65_000), 100);
        assertEquals(ProbeProtocol.CONFLICT, authority.apply(watch, 101).outcome);
        assertEquals(8, authority.state().reps);
        assertEquals(65_000, authority.state().loadGrams);
    }
    @Test public void oldSetCannotTargetNextFixture() {
        ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
        ProbeProtocol.Message old = ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.DONE, 1);
        authority.apply(ProbeProtocol.Message.command(-1, authority.state(), ProbeProtocol.NEXT_SET, 0), 100);
        assertEquals(ProbeProtocol.CONFLICT, authority.apply(old, 101).outcome);
        assertEquals(2, authority.state().set);
        assertFalse(authority.state().done);
    }
    @Test public void currentRevisionDoesNotAuthorizeOldSetIdentity() {
        ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
        authority.apply(ProbeProtocol.Message.command(-1, authority.state(), ProbeProtocol.NEXT_SET, 0), 100);
        ProbeProtocol.Message wrongSet = ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.DONE, 1);
        wrongSet.set = 1;
        assertEquals(ProbeProtocol.CONFLICT, authority.apply(wrongSet, 101).outcome);
        assertFalse(authority.state().done);
    }
    @Test public void undoClearsRatingAndUndoneSetCannotBeRated() {
        ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
        assertEquals(ProbeProtocol.REJECTED, authority.apply(ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.RPE, 8), 1).outcome);
        authority.apply(ProbeProtocol.Message.command(2, authority.state(), ProbeProtocol.DONE, 1), 2);
        authority.apply(ProbeProtocol.Message.command(3, authority.state(), ProbeProtocol.RPE, 8), 3);
        assertEquals(8, authority.state().rpe);
        authority.apply(ProbeProtocol.Message.command(4, authority.state(), ProbeProtocol.DONE, 0), 4);
        assertEquals(0, authority.state().rpe);
        assertFalse(authority.state().done);
    }
    @Test public void invalidNumericInputLeavesFixtureUnchanged() {
        ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
        assertEquals(ProbeProtocol.REJECTED, authority.apply(ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.REPS, -1), 0).outcome);
        assertEquals(ProbeProtocol.REJECTED, authority.apply(ProbeProtocol.Message.command(2, authority.state(), ProbeProtocol.LOAD, Integer.MAX_VALUE), 0).outcome);
        assertEquals(0, authority.state().revision);
    }
    @Test public void timerStopExcludesReadinessAndDoesNotCompleteSet() {
        ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
        authority.apply(ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.START_TIMER, 0), 1000);
        authority.apply(ProbeProtocol.Message.command(2, authority.state(), ProbeProtocol.STOP_TIMER, 0), 12_900);
        assertEquals(6, authority.state().stoppedSeconds);
        assertFalse(authority.state().done);
    }
    @Test public void stoppingDuringReadinessRecordsNoDuration() {
        ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
        authority.apply(ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.START_TIMER, 0), 1000);
        authority.apply(ProbeProtocol.Message.command(2, authority.state(), ProbeProtocol.STOP_TIMER, 0), 4000);
        assertEquals(-1, authority.state().stoppedSeconds);
        assertEquals(-1, authority.state().timerStart);
        assertFalse(authority.state().done);
    }
    @Test public void roundTripPreservesSetFiguresAndTiming() throws IOException {
        ProbeProtocol.State state = new ProbeProtocol.State(12, 4, 11, 62_500, 8, true, -1, 47);
        ProbeProtocol.Message decoded = ProbeProtocol.decode(ProbeProtocol.encode(ProbeProtocol.Message.state(123, state, ProbeProtocol.ACCEPTED, 5000)));
        assertEquals(123, decoded.id); assertEquals(5000, decoded.phoneAt);
        assertEquals(62_500, decoded.state.loadGrams); assertEquals(47, decoded.state.stoppedSeconds);
        assertTrue(decoded.state.done); assertEquals(8, decoded.state.rpe);
    }
    @Test public void trailingOrIncompatiblePayloadIsRejected() throws IOException {
        byte[] encoded = ProbeProtocol.encode(ProbeProtocol.Message.ping(2, 300));
        byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(IOException.class, () -> ProbeProtocol.decode(trailing));
        encoded[0] = 42;
        assertThrows(IOException.class, () -> ProbeProtocol.decode(encoded));
    }
    @Test public void boundedReceiptsCannotReplayOldAcceptedEdit() {
        ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
        ProbeProtocol.Message original = ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.REPS, 9);
        authority.apply(original, 1);
        for (int i = 2; i < 140; i++) authority.apply(ProbeProtocol.Message.command(i, authority.state(), ProbeProtocol.REPS, i), i);
        long revision = authority.state().revision;
        assertEquals(ProbeProtocol.REJECTED, authority.apply(original, 150).outcome);
        assertEquals(revision, authority.state().revision);
        assertEquals(139, authority.state().reps);
    }
    @Test public void evictedIdentityCannotBeRepurposedWithCurrentRevision() {
        ProbeProtocol.Authority authority = new ProbeProtocol.Authority();
        for (int i = 1; i <= 130; i++) authority.apply(ProbeProtocol.Message.command(i, authority.state(), ProbeProtocol.REPS, i), i);
        long revision = authority.state().revision;
        ProbeProtocol.Message reused = ProbeProtocol.Message.command(1, authority.state(), ProbeProtocol.LOAD, 80_000);
        assertEquals(ProbeProtocol.REJECTED, authority.apply(reused, 200).outcome);
        assertEquals(revision, authority.state().revision);
        assertEquals(60_000, authority.state().loadGrams);
    }
}
