package com.ironvellum.watchprobe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deliberately synthetic transport fixture, not Ironvellum workout/domain logic. */
public final class ProbeProtocol {
    private ProbeProtocol() { }
    public static final int REQUEST = 1, STATE = 2, PING = 3, PONG = 4;
    public static final int REPS = 1, LOAD = 2, DONE = 3, RPE = 4, START_TIMER = 5, STOP_TIMER = 6, NEXT_SET = 7;
    public static final int ACCEPTED = 0, CONFLICT = 1, REJECTED = 2;

    public static final class State {
        public final long revision, timerStart;
        public final int set, reps, loadGrams, rpe, stoppedSeconds;
        public final boolean done;
        public State(long revision, int set, int reps, int loadGrams, int rpe, boolean done,
                     long timerStart, int stoppedSeconds) {
            this.revision = revision; this.set = set; this.reps = reps; this.loadGrams = loadGrams;
            this.rpe = rpe; this.done = done; this.timerStart = timerStart; this.stoppedSeconds = stoppedSeconds;
        }
    }

    public static final class Message {
        public int type, action, value, set, outcome;
        public long id, revision, sentAt, phoneAt;
        public State state;
        public static Message command(long id, State state, int action, int value) {
            Message m = new Message(); m.type = REQUEST; m.id = id; m.revision = state.revision;
            m.set = state.set; m.action = action; m.value = value; return m;
        }
        public static Message state(long id, State state, int outcome, long phoneAt) {
            Message m = new Message(); m.type = STATE; m.id = id; m.state = state;
            m.outcome = outcome; m.phoneAt = phoneAt; return m;
        }
        public static Message ping(long id, long sentAt) {
            Message m = new Message(); m.type = PING; m.id = id; m.sentAt = sentAt; return m;
        }
        public static Message pong(Message ping, long phoneAt) {
            Message m = new Message(); m.type = PONG; m.id = ping.id; m.sentAt = ping.sentAt; m.phoneAt = phoneAt; return m;
        }
    }

    public static byte[] encode(Message m) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(1); out.writeByte(m.type); out.writeLong(m.id);
        switch (m.type) {
            case REQUEST:
                out.writeLong(m.revision); out.writeInt(m.set); out.writeInt(m.action); out.writeInt(m.value); break;
            case STATE:
                out.writeInt(m.outcome); out.writeLong(m.phoneAt); writeState(out, m.state); break;
            case PING: out.writeLong(m.sentAt); break;
            case PONG: out.writeLong(m.sentAt); out.writeLong(m.phoneAt); break;
            default: throw new IOException("Unknown message type");
        }
        return bytes.toByteArray();
    }

    public static Message decode(byte[] payload) throws IOException {
        if (payload.length > 128) throw new IOException("Oversized fixture message");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        if (in.readUnsignedByte() != 1) throw new IOException("Incompatible fixture protocol");
        Message m = new Message(); m.type = in.readUnsignedByte(); m.id = in.readLong();
        switch (m.type) {
            case REQUEST:
                m.revision = in.readLong(); m.set = in.readInt(); m.action = in.readInt(); m.value = in.readInt(); break;
            case STATE:
                m.outcome = in.readInt(); m.phoneAt = in.readLong(); m.state = readState(in);
                if (m.outcome < ACCEPTED || m.outcome > REJECTED) throw new IOException("Invalid result");
                break;
            case PING: m.sentAt = in.readLong(); break;
            case PONG: m.sentAt = in.readLong(); m.phoneAt = in.readLong(); break;
            default: throw new IOException("Unknown message type");
        }
        if (in.available() != 0) throw new IOException("Trailing fixture payload");
        return m;
    }

    private static void writeState(DataOutputStream out, State s) throws IOException {
        out.writeLong(s.revision); out.writeInt(s.set); out.writeInt(s.reps); out.writeInt(s.loadGrams);
        out.writeInt(s.rpe); out.writeBoolean(s.done); out.writeLong(s.timerStart); out.writeInt(s.stoppedSeconds);
    }
    private static State readState(DataInputStream in) throws IOException {
        State s = new State(in.readLong(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                in.readBoolean(), in.readLong(), in.readInt());
        if (s.revision < 0 || s.set < 1 || s.reps < 0 || s.reps > 999 || s.loadGrams < 0 || s.loadGrams > 1_000_000
                || s.rpe < 0 || s.rpe > 10 || (s.rpe > 0 && s.rpe < 5) || (!s.done && s.rpe != 0)
                || s.timerStart < -1 || s.stoppedSeconds < -1 || s.stoppedSeconds > 86_400) {
            throw new IOException("Invalid fixture state");
        }
        return s;
    }

    public static final class Authority {
        private State state = new State(0, 1, 8, 60_000, 0, false, -1, -1);
        private final Map<Long, Receipt> receipts = new LinkedHashMap<>();
        private long remoteHighWatermark, localLowWatermark;
        public State state() { return state; }
        public void newConnection() { receipts.clear(); remoteHighWatermark = 0; localLowWatermark = 0; }
        public Message apply(Message c, long now) {
            if (c.type != REQUEST) throw new IllegalArgumentException("Not a command");
            Receipt previous = receipts.get(c.id);
            if (previous != null) {
                int result = previous.matches(c) ? previous.outcome : REJECTED;
                return Message.state(c.id, state, result, now);
            }
            // Eviction must not turn a previously used identity into a new command.
            if (c.id == 0 || c.id > 0 && c.id <= remoteHighWatermark || c.id < 0 && c.id >= localLowWatermark) {
                return Message.state(c.id, state, REJECTED, now);
            }
            if (c.id > 0) remoteHighWatermark = c.id; else localLowWatermark = c.id;
            int outcome = REJECTED;
            if (c.revision != state.revision || c.set != state.set) outcome = CONFLICT;
            else if (state.revision < Long.MAX_VALUE) {
                int reps = state.reps, load = state.loadGrams, rpe = state.rpe, stopped = state.stoppedSeconds, set = state.set;
                long start = state.timerStart;
                boolean done = state.done, valid = true;
                switch (c.action) {
                    case REPS: valid = c.value >= 0 && c.value <= 999; reps = c.value; break;
                    case LOAD: valid = c.value >= 0 && c.value <= 1_000_000; load = c.value; break;
                    case DONE:
                        valid = c.value == 0 || c.value == 1;
                        done = c.value == 1; if (!done) rpe = 0; break;
                    case RPE: valid = done && (c.value == 0 || c.value >= 5 && c.value <= 10); rpe = c.value; break;
                    case START_TIMER: valid = !done && start < 0 && stopped < 0; start = now; break;
                    case STOP_TIMER:
                        valid = start >= 0;
                        long elapsed = Math.max(0, now - start - 5000);
                        stopped = now - start < 5000 ? -1 : (int) Math.min(86_400, Math.max(1, elapsed / 1000));
                        start = -1; break;
                    case NEXT_SET:
                        valid = state.set < Integer.MAX_VALUE;
                        set++; done = false; rpe = 0; start = -1; stopped = -1; break;
                    default: valid = false;
                }
                if (valid) {
                    state = new State(state.revision + 1, set, reps, load, rpe, done, start, stopped);
                    outcome = ACCEPTED;
                }
            }
            receipts.put(c.id, new Receipt(c, outcome));
            if (receipts.size() > 128) receipts.remove(receipts.keySet().iterator().next());
            return Message.state(c.id, state, outcome, now);
        }
    }

    private static final class Receipt {
        final long revision;
        final int set, action, value, outcome;
        Receipt(Message m, int outcome) {
            revision = m.revision; set = m.set; action = m.action; value = m.value; this.outcome = outcome;
        }
        boolean matches(Message m) { return revision == m.revision && set == m.set && action == m.action && value == m.value; }
    }
}
