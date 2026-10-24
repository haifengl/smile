/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.cluster;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.torch.Native;

/**
 * The control side of a multi-node model: the leader process owns the server and the engine, and every call that changes
 * model state (bind, prefill, decode step, finish...) is mirrored to the follower processes, which hold the other ranks
 * and execute the identical call so that the NCCL collectives of all ranks line up.
 *
 * <p>Wire format: leader to follower {@code [int length][byte op][payload]}; follower to leader
 * {@code [byte status][long seq][int value]} (status 0 ok, otherwise followed by a UTF error message). Messages of one
 * connection are numbered 1, 2, ... in send order; the follower acknowledges each after executing it.
 *
 * <p>If a follower fails or disconnects, the collectives of the other ranks would hang forever, so by default the leader
 * halts the JVM ({@code SMILE_CLUSTER_FAIL_FAST=0} only logs).
 *
 * @author Haifeng Li
 */
public final class ClusterLeader implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClusterLeader.class);

    /** Magic number of the handshake. */
    static final int MAGIC = 0x534d4c31; // "SML1"
    /** Op code of the shutdown message. */
    public static final int OP_SHUTDOWN = 0;

    /** One connected follower. */
    private final class Peer {
        final int nodeIndex;
        final int ranks;
        final String host;
        final Socket socket;
        final DataOutputStream out;
        final DataInputStream in;
        /** Highest message number acknowledged. */
        volatile long acked;
        /** Value of the acknowledgement of each message that returns one (bind ids), by message number. */
        final Map<Long, Integer> values = new java.util.concurrent.ConcurrentHashMap<>();

        Peer(int nodeIndex, int ranks, String host, Socket socket, DataInputStream in) throws IOException {
            this.nodeIndex = nodeIndex;
            this.ranks = ranks;
            this.host = host;
            this.socket = socket;
            this.in = in;
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 1 << 16));
        }
    }

    private final List<Peer> peers = new ArrayList<>();
    private final int worldSize;
    private final byte[] uniqueId;
    private final Object ackLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile String failure;
    private long seq;
    private static volatile boolean failFast = !"0".equals(System.getenv("SMILE_CLUSTER_FAIL_FAST"));

    /**
     * Whether a failed follower halts the JVM (the default: the other ranks would hang in a collective forever).
     *
     * @param enabled {@code false} only logs, for tests.
     */
    public static void setFailFast(boolean enabled) {
        failFast = enabled;
    }

    private ClusterLeader(int worldSize, byte[] uniqueId) {
        this.worldSize = worldSize;
        this.uniqueId = uniqueId;
    }

    /**
     * Waits for the followers to connect, assigns ranks (the leader owns ranks {@code 0 .. leaderRanks - 1}, then the
     * followers in {@code nodeIndex} order) and sends each follower its welcome: world size, first rank, the NCCL unique
     * id and {@code params}.
     *
     * @param port        TCP port to listen on.
     * @param followers   number of follower processes to wait for.
     * @param leaderRanks ranks (GPUs) of the leader process.
     * @param timeoutMs   how long to wait for all followers.
     * @param params      settings every process must share (batch size, memory fraction...).
     * @return the connected cluster.
     * @throws IOException if a follower does not show up or the handshake fails.
     */
    public static ClusterLeader start(int port, int followers, int leaderRanks, long timeoutMs,
                                      Map<String, String> params) throws IOException {
        return start(port, followers, leaderRanks, timeoutMs, params, null);
    }

    /**
     * As {@link #start(int, int, int, long, Map)} with an explicit NCCL unique id (tests, or a launcher that made it).
     *
     * @param port        TCP port to listen on.
     * @param followers   number of follower processes to wait for.
     * @param leaderRanks ranks (GPUs) of the leader process.
     * @param timeoutMs   how long to wait for all followers.
     * @param params      settings every process must share.
     * @param uniqueId    the 128-byte NCCL unique id, or {@code null} to create one.
     * @return the connected cluster.
     * @throws IOException if a follower does not show up or the handshake fails.
     */
    public static ClusterLeader start(int port, int followers, int leaderRanks, long timeoutMs,
                                      Map<String, String> params, byte[] uniqueId) throws IOException {
        List<Socket> sockets = new ArrayList<>();
        List<DataInputStream> inputs = new ArrayList<>();
        List<int[]> hellos = new ArrayList<>();
        List<String> hosts = new ArrayList<>();
        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
            long deadline = System.currentTimeMillis() + timeoutMs;
            logger.info("cluster: waiting for {} follower(s) on port {}", followers, port);
            while (sockets.size() < followers) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    throw new IOException("only " + sockets.size() + " of " + followers
                            + " cluster followers connected within " + timeoutMs + " ms");
                }
                server.setSoTimeout((int) Math.min(left, Integer.MAX_VALUE));
                Socket s = server.accept();
                s.setTcpNoDelay(true);
                s.setKeepAlive(true);
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 1 << 16));
                if (in.readInt() != MAGIC) {
                    s.close();
                    throw new IOException("unexpected client on the cluster port: " + s.getRemoteSocketAddress());
                }
                int node = in.readInt();
                int ranks = in.readInt();
                String host = in.readUTF();
                logger.info("cluster: follower node {} ({}) connected with {} rank(s)", node, host, ranks);
                sockets.add(s);
                inputs.add(in);
                hellos.add(new int[]{node, ranks});
                hosts.add(host);
            }
        } catch (IOException | RuntimeException e) {
            for (Socket s : sockets) {
                try {
                    s.close();
                } catch (IOException ignored) {
                    // best effort
                }
            }
            throw e;
        }

        Integer[] order = new Integer[sockets.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, Comparator.comparingInt(i -> hellos.get(i)[0]));
        int world = leaderRanks;
        for (int i : order) {
            world += hellos.get(i)[1];
        }
        byte[] id = uniqueId != null ? uniqueId.clone() : Native.ncclUniqueId();
        ClusterLeader leader = new ClusterLeader(world, id);
        int offset = leaderRanks;
        for (int i : order) {
            Peer p = leader.new Peer(hellos.get(i)[0], hellos.get(i)[1], hosts.get(i), sockets.get(i), inputs.get(i));
            p.out.writeInt(MAGIC);
            p.out.writeInt(world);
            p.out.writeInt(offset);
            p.out.write(id);
            p.out.writeInt(params.size());
            for (Map.Entry<String, String> e : params.entrySet()) {
                p.out.writeUTF(e.getKey());
                p.out.writeUTF(e.getValue());
            }
            p.out.flush();
            logger.info("cluster: node {} owns world ranks {}..{}", p.nodeIndex, offset, offset + p.ranks - 1);
            offset += p.ranks;
            leader.peers.add(p);
        }
        for (Peer p : leader.peers) {
            Thread t = new Thread(() -> leader.readAcks(p), "cluster-acks-node" + p.nodeIndex);
            t.setDaemon(true);
            t.start();
        }
        return leader;
    }

    /**
     * Returns the ranks of the whole communicator.
     *
     * @return ranks of the whole communicator.
     */
    public int worldSize() {
        return worldSize;
    }

    /**
     * Returns the NCCL unique id shared with the followers.
     *
     * @return the NCCL unique id shared with the followers.
     */
    public byte[] uniqueId() {
        return uniqueId.clone();
    }

    /**
     * Returns the number of followers.
     *
     * @return the number of followers.
     */
    public int followers() {
        return peers.size();
    }

    /**
     * Sends one message to every follower (they execute it concurrently with the leader).
     *
     * @param op      op code ({@code > 0}); the follower's handler decides what it means.
     * @param payload writes the arguments, or {@code null} for none.
     * @return the message number, for {@link #awaitAck} and {@link #valueOf}.
     */
    public synchronized long send(int op, PayloadWriter payload) {
        checkHealthy();
        ByteArrayOutputStream buf = new ByteArrayOutputStream(64);
        try {
            DataOutputStream d = new DataOutputStream(buf);
            d.writeByte(op);
            if (payload != null) {
                payload.write(d);
            }
            d.flush();
            byte[] body = buf.toByteArray();
            long n = ++seq;
            for (Peer p : peers) {
                p.out.writeInt(body.length);
                p.out.write(body);
                p.out.flush();
            }
            return n;
        } catch (IOException e) {
            fail("lost a follower while sending op " + op + ": " + e);
            throw new IllegalStateException(failure, e);
        }
    }

    /** Writes the arguments of a message. */
    @FunctionalInterface
    public interface PayloadWriter {
        /**
         * Writes the payload.
         *
         * @param out the destination.
         * @throws IOException on a write failure.
         */
        void write(DataOutputStream out) throws IOException;
    }

    /**
     * Blocks until every follower has executed message {@code n}; throws if one failed.
     *
     * @param n the message number from {@link #send}.
     */
    public void awaitAck(long n) {
        synchronized (ackLock) {
            while (!allAcked(n)) {
                if (failure != null) {
                    throw new IllegalStateException(failure);
                }
                try {
                    ackLock.wait(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for the cluster", e);
                }
            }
        }
        checkHealthy();
    }

    /**
     * The value follower {@code index} returned with message {@code n} (call after {@link #awaitAck}).
     *
     * @param index follower, in rank order.
     * @param n     message number.
     * @return the value, or {@code -1} when it returned none.
     */
    public int valueOf(int index, long n) {
        Integer v = peers.get(index).values.remove(n);
        return v == null ? -1 : v;
    }

    private boolean allAcked(long n) {
        for (Peer p : peers) {
            if (p.acked < n) {
                return false;
            }
        }
        return true;
    }

    /** Throws if a follower has failed. */
    public void checkHealthy() {
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
    }

    private void readAcks(Peer p) {
        try {
            while (!closed.get()) {
                int status = p.in.readUnsignedByte();
                long n = p.in.readLong();
                int value = p.in.readInt();
                if (status != 0) {
                    String msg = p.in.readUTF();
                    fail("follower node " + p.nodeIndex + " (" + p.host + ") failed message " + n + ": " + msg);
                    return;
                }
                if (value != -1) {
                    p.values.put(n, value);
                }
                synchronized (ackLock) {
                    p.acked = n;
                    ackLock.notifyAll();
                }
            }
        } catch (IOException e) {
            if (!closed.get()) {
                fail("lost follower node " + p.nodeIndex + " (" + p.host + "): " + e);
            }
        }
    }

    private void fail(String message) {
        if (failure == null) {
            failure = message;
            logger.error("cluster failure: {}", message);
            synchronized (ackLock) {
                ackLock.notifyAll();
            }
            if (!closed.get() && failFast) {
                // the other ranks would wait in a collective forever
                logger.error("halting: the cluster cannot continue (SMILE_CLUSTER_FAIL_FAST=0 only logs)");
                Runtime.getRuntime().halt(3);
            }
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            for (Peer p : peers) {
                p.out.writeInt(1);
                p.out.write(OP_SHUTDOWN);
                p.out.flush();
            }
        } catch (IOException ignored) {
            // followers exit when the socket closes anyway
        }
        for (Peer p : peers) {
            try {
                p.socket.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }
}
