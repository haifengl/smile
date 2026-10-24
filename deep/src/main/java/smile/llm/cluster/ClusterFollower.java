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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The follower side of a multi-node model: connects to a {@link ClusterLeader}, learns its ranks and the NCCL unique id,
 * then executes every message the leader sends, in order, acknowledging each. See {@link ClusterLeader} for the wire format.
 *
 * @author Haifeng Li
 */
public final class ClusterFollower implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ClusterFollower.class);

    /**
     * What the leader tells a follower on connection.
     *
     * @param worldSize  ranks of the whole communicator.
     * @param rankOffset world rank of this follower's first rank.
     * @param uniqueId   the NCCL unique id (128 bytes).
     * @param params     settings every process must share.
     */
    public record Welcome(int worldSize, int rankOffset, byte[] uniqueId, Map<String, String> params) { }

    /** Executes one message; the follower acknowledges it afterwards. */
    @FunctionalInterface
    public interface Handler {
        /**
         * Executes a message.
         *
         * @param op      the op code (never the shutdown code).
         * @param in      the payload.
         * @return a value to return with the acknowledgement, or {@code -1} for none.
         * @throws Exception any failure; it is reported to the leader and ends the process.
         */
        int handle(int op, DataInputStream in) throws Exception;
    }

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Welcome welcome;

    private ClusterFollower(Socket socket, DataInputStream in, DataOutputStream out, Welcome welcome) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.welcome = welcome;
    }

    /**
     * Connects to the leader (retrying until it listens) and completes the handshake.
     *
     * @param host      the leader's host.
     * @param port      the leader's cluster port.
     * @param nodeIndex this follower's node number (1, 2, ...; ranks are assigned in node order).
     * @param ranks     number of ranks (GPUs) this follower owns.
     * @param timeoutMs how long to keep retrying.
     * @return the connected follower.
     * @throws IOException if the leader cannot be reached or the handshake is wrong.
     */
    public static ClusterFollower connect(String host, int port, int nodeIndex, int ranks, long timeoutMs)
            throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(host, port), 5000);
                s.setTcpNoDelay(true);
                s.setKeepAlive(true);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream(), 1 << 12));
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream(), 1 << 16));
                out.writeInt(ClusterLeader.MAGIC);
                out.writeInt(nodeIndex);
                out.writeInt(ranks);
                out.writeUTF(InetAddress.getLocalHost().getHostName());
                out.flush();
                // the leader answers only after every follower has connected
                s.setSoTimeout(0);
                if (in.readInt() != ClusterLeader.MAGIC) {
                    throw new IOException("not a SMILE cluster leader at " + host + ":" + port);
                }
                int world = in.readInt();
                int offset = in.readInt();
                byte[] id = new byte[128];
                in.readFully(id);
                int n = in.readInt();
                Map<String, String> params = new LinkedHashMap<>();
                for (int i = 0; i < n; i++) {
                    params.put(in.readUTF(), in.readUTF());
                }
                logger.info("cluster: joined leader {}:{} as node {}, world ranks {}..{} of {}", host, port, nodeIndex,
                        offset, offset + ranks - 1, world);
                return new ClusterFollower(s, in, out, new Welcome(world, offset, id, params));
            } catch (IOException e) {
                last = e;
                try {
                    s.close();
                } catch (IOException ignored) {
                    // best effort
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while connecting to the cluster leader", ie);
                }
            }
        }
        throw new IOException("cluster leader " + host + ":" + port + " not reachable within " + timeoutMs + " ms"
                + (last == null ? "" : ": " + last));
    }

    /**
     * Returns what the leader assigned.
     *
     * @return what the leader assigned.
     */
    public Welcome welcome() {
        return welcome;
    }

    /**
     * Executes the leader's messages until it shuts the cluster down or disconnects. A failing message is reported to the
     * leader and the exception is rethrown (the caller should then exit: the other ranks cannot continue).
     *
     * @param handler executes the messages.
     * @throws Exception the first failure of {@code handler}.
     */
    public void run(Handler handler) throws Exception {
        long n = 0;
        try {
            while (true) {
                int length;
                try {
                    length = in.readInt();
                } catch (java.io.EOFException e) {
                    logger.info("cluster: leader closed the connection");
                    return;
                }
                byte[] body = new byte[length];
                in.readFully(body);
                DataInputStream msg = new DataInputStream(new java.io.ByteArrayInputStream(body));
                int op = msg.readUnsignedByte();
                if (op == ClusterLeader.OP_SHUTDOWN) {
                    logger.info("cluster: shutdown requested by the leader");
                    return;
                }
                n++;
                int value;
                try {
                    value = handler.handle(op, msg);
                } catch (Exception e) {
                    logger.error("cluster: message {} (op {}) failed", n, op, e);
                    try {
                        out.writeByte(1);
                        out.writeLong(n);
                        out.writeInt(-1);
                        out.writeUTF(String.valueOf(e));
                        out.flush();
                    } catch (IOException ignored) {
                        // the leader will notice the closed socket
                    }
                    throw e;
                }
                out.writeByte(0);
                out.writeLong(n);
                out.writeInt(value);
                out.flush();
            }
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best effort
        }
    }
}
