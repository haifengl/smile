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

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests of the leader/follower control channel (no GPU or NCCL needed).
 *
 * @author Haifeng Li
 */
public class ClusterLinkTest {
    private static final int OP_ECHO = 1;
    private static final int OP_FAIL = 2;

    @BeforeAll
    public static void noHalt() {
        ClusterLeader.setFailFast(false);   // a failing follower must not halt the test JVM
    }

    @AfterAll
    public static void restore() {
        ClusterLeader.setFailFast(true);
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static byte[] id() {
        byte[] id = new byte[128];
        for (int i = 0; i < id.length; i++) {
            id[i] = (byte) (i * 7);
        }
        return id;
    }

    private static ClusterFollower connect(int port, int node, int ranks) {
        try {
            return ClusterFollower.connect("127.0.0.1", port, node, ranks, 20_000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testGivenTwoFollowersWhenStartedThenRanksFollowNodeOrderAndParamsArrive() throws Exception {
        int port = freePort();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pageSize", "1");
        params.put("weightMode", "");
        // node 2 connects first, but its ranks must come after node 1's
        CompletableFuture<ClusterFollower> f2 = CompletableFuture.supplyAsync(() -> connect(port, 2, 3));
        Thread.sleep(300);
        CompletableFuture<ClusterFollower> f1 = CompletableFuture.supplyAsync(() -> connect(port, 1, 2));
        try (ClusterLeader leader = ClusterLeader.start(port, 2, 4, 20_000, params, id());
             ClusterFollower n1 = f1.get(20, TimeUnit.SECONDS);
             ClusterFollower n2 = f2.get(20, TimeUnit.SECONDS)) {
            assertEquals(9, leader.worldSize());
            assertEquals(4, n1.welcome().rankOffset());
            assertEquals(6, n2.welcome().rankOffset());
            assertEquals(9, n1.welcome().worldSize());
            assertArrayEquals(id(), n1.welcome().uniqueId());
            assertEquals("1", n2.welcome().params().get("pageSize"));
            assertEquals("", n2.welcome().params().get("weightMode"));
        }
    }

    @Test
    public void testGivenMessagesWhenSentThenFollowerExecutesThemInOrderAndReturnsValues() throws Exception {
        int port = freePort();
        CompletableFuture<ClusterFollower> f = CompletableFuture.supplyAsync(() -> connect(port, 1, 1));
        List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
        try (ClusterLeader leader = ClusterLeader.start(port, 1, 1, 20_000, new LinkedHashMap<>(), id());
             ClusterFollower follower = f.get(20, TimeUnit.SECONDS)) {
            CompletableFuture<Void> loop = CompletableFuture.runAsync(() -> {
                try {
                    follower.run((op, in) -> {
                        int v = in.readInt();
                        seen.add(v);
                        return v * 2;
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            long last = 0;
            for (int i = 1; i <= 50; i++) {
                final int v = i;
                last = leader.send(OP_ECHO, o -> o.writeInt(v));
            }
            leader.awaitAck(last);
            assertEquals(50, seen.size());
            for (int i = 0; i < 50; i++) {
                assertEquals(i + 1, seen.get(i), "messages must run in send order");
            }
            assertEquals(100, leader.valueOf(0, last), "the acknowledgement carries the handler's value");
            leader.close();
            loop.get(20, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testGivenFollowerFailureWhenAwaitedThenLeaderReportsItAndRefusesFurtherSends() throws Exception {
        int port = freePort();
        CompletableFuture<ClusterFollower> f = CompletableFuture.supplyAsync(() -> connect(port, 1, 1));
        try (ClusterLeader leader = ClusterLeader.start(port, 1, 1, 20_000, new LinkedHashMap<>(), id());
             ClusterFollower follower = f.get(20, TimeUnit.SECONDS)) {
            CompletableFuture<Void> loop = CompletableFuture.runAsync(() -> {
                try {
                    follower.run((op, in) -> {
                        if (op == OP_FAIL) {
                            throw new IllegalStateException("boom");
                        }
                        return -1;
                    });
                } catch (Exception expected) {
                    // the follower reports the failure to the leader and rethrows
                }
            });
            long n = leader.send(OP_FAIL, null);
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> leader.awaitAck(n));
            assertTrue(e.getMessage().contains("boom"), e.getMessage());
            assertThrows(IllegalStateException.class, () -> leader.send(OP_ECHO, null));
            loop.get(20, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testGivenMissingFollowerWhenTimeoutThenStartFails() throws Exception {
        int port = freePort();
        assertThrows(java.io.IOException.class,
                () -> ClusterLeader.start(port, 1, 1, 500, new LinkedHashMap<>(), id()));
    }
}
