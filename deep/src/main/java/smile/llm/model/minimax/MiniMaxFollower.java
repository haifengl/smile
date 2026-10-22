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
package smile.llm.model.minimax;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.llm.cluster.ClusterFollower;

/**
 * A non-leader node of a multi-node MiniMax-M3: loads its share of the experts on its GPUs, joins the leader's NCCL
 * communicator and executes every call the leader mirrors ({@link MiniMax#serveCluster}). It serves no HTTP, loads no
 * vision encoder and discards the logits: only the leader samples.
 *
 * <p>Usage: {@code MiniMaxFollower --leader host:port --node 1 --devices 0,1,2,3 --checkpoint /path/to/model
 * [--timeout-s 3600]}. The checkpoint path is local to this node; everything else (batch size, memory fraction...) is
 * taken from the leader so that all processes agree.
 *
 * @author Haifeng Li
 */
public final class MiniMaxFollower {
    private static final Logger logger = LoggerFactory.getLogger(MiniMaxFollower.class);

    private MiniMaxFollower() { }

    /**
     * Entry point.
     *
     * @param args see the class comment.
     * @throws Exception on any failure; the process then exits non-zero.
     */
    public static void main(String[] args) throws Exception {
        String leader = null;
        int node = 1;
        String devs = null;
        String checkpoint = null;
        long timeoutMs = 3_600_000L;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--leader" -> leader = args[++i];
                case "--node" -> node = Integer.parseInt(args[++i]);
                case "--devices" -> devs = args[++i];
                case "--checkpoint" -> checkpoint = args[++i];
                case "--timeout-s" -> timeoutMs = Long.parseLong(args[++i]) * 1000L;
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        if (leader == null || devs == null || checkpoint == null || !leader.contains(":")) {
            throw new IllegalArgumentException(
                    "usage: MiniMaxFollower --leader host:port --node N --devices 0,1,.. --checkpoint dir [--timeout-s S]");
        }
        String[] parts = devs.split(",");
        byte[] devices = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            devices[i] = Byte.parseByte(parts[i].trim());
        }
        int colon = leader.lastIndexOf(':');
        String host = leader.substring(0, colon);
        int port = Integer.parseInt(leader.substring(colon + 1));

        try (ClusterFollower link = ClusterFollower.connect(host, port, node, devices.length, timeoutMs)) {
            ClusterFollower.Welcome w = link.welcome();
            Map<String, String> p = w.params();
            String mode = p.getOrDefault("weightMode", "");
            MiniMax model = MiniMax.build(checkpoint,
                    Integer.parseInt(p.get("maxBatchSize")), Integer.parseInt(p.get("maxSeqLen")), devices,
                    Double.parseDouble(p.get("memFractionStatic")), Integer.parseInt(p.get("pageSize")),
                    mode.isEmpty() ? null : mode,
                    new MiniMax.ClusterSpec(w.worldSize(), w.rankOffset(), w.uniqueId()));
            logger.info("follower ready: world ranks {}..{} of {}", w.rankOffset(),
                    w.rankOffset() + devices.length - 1, w.worldSize());
            try {
                model.serveCluster(link);
            } finally {
                model.close();
            }
        }
        // do not wait for lingering native threads
        System.exit(0);
    }
}
