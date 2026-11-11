/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.bot;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import ioa.agent.Agent;
import ioa.agent.AgentCard;
import ioa.agent.AgentDirectory;
import ioa.agent.AgentEndpoint;
import ioa.agent.AgentNames;
import ioa.agent.AgentRequest;
import ioa.agent.Capability;
import smile.studio.StudioConfig;

/**
 * {@link AgentEndpoint} representing one {@code (instance, persona)} pair hosted
 * on an Agent Hub ({@code @mike#hub} or {@code @mike#<label>}).
 *
 * <p>Accepting a request submits it over HTTP to {@code POST /v1/admin/requests}
 * via {@link HubClient#submitRequest}. Errors from the Hub (or an offline Hub)
 * are returned directly to the {@code AgentRequest} tool so the caller never
 * receives a false {@code "queued"} confirmation.
 *
 * @author Haifeng Li
 */
public final class HubAgentEndpoint implements AgentEndpoint {

    /**
     * Metadata for a peer request accepted by an Agent Hub, used to deliver a
     * completion {@link AgentRequest.Kind#NOTICE} back to the sending Studio panel
     * when the run reaches a terminal status on the SSE stream.
     *
     * @param runId              the Hub run ID returned by {@code POST /v1/admin/requests}.
     * @param senderCallName     the local Studio panel call-out name that sent the request.
     * @param targetInstanceName the qualified Hub instance target (e.g. {@code "mike#hub"}).
     * @param client             the {@link HubClient} for the Hub hosting the run.
     */
    public record SubmittedRun(
            long runId,
            String senderCallName,
            String targetInstanceName,
            HubClient client
    ) { }

    private final String callName;
    private final String instanceLabel;
    private final AgentCard card;
    private final Supplier<HubClient> clientSupplier;
    private final BiConsumer<Long, SubmittedRun> onSubmitted;

    /**
     * Creates an endpoint for a persona hosted on an Agent Hub instance.
     *
     * @param callName       the persona's bare call-out name (e.g. {@code "mike"}).
     * @param instanceLabel  the instance label (e.g. {@code "hub"} or {@code "lab-server"}).
     * @param description    the persona's description.
     * @param capabilities   the persona's capabilities.
     * @param clientSupplier supplier for the target {@link HubClient}.
     * @param onSubmitted    optional callback invoked when a request is accepted by the Hub.
     */
    public HubAgentEndpoint(
            String callName,
            String instanceLabel,
            String description,
            List<Capability> capabilities,
            Supplier<HubClient> clientSupplier,
            BiConsumer<Long, SubmittedRun> onSubmitted
    ) {
        this.callName = AgentNames.baseName(Objects.requireNonNull(callName, "callName"));
        this.instanceLabel = Objects.requireNonNull(instanceLabel, "instanceLabel").trim().toLowerCase(Locale.ROOT);
        if (this.callName.isEmpty()) {
            throw new IllegalArgumentException("callName must not be blank");
        }
        if (!AgentNames.isValidLabel(this.instanceLabel)) {
            throw new IllegalArgumentException("Invalid instance label: " + instanceLabel);
        }
        this.card = new AgentCard(
                this.callName + "#" + this.instanceLabel,
                description == null ? "" : description,
                capabilities == null ? List.of() : capabilities
        );
        this.clientSupplier = Objects.requireNonNull(clientSupplier, "clientSupplier");
        this.onSubmitted = onSubmitted;
    }

    /** Returns the bare persona call-out name (without {@code #<instance>}). */
    public String callName() {
        return callName;
    }

    /** Returns the instance label (e.g. {@code "hub"} or {@code "lab-server"}). */
    public String instanceLabel() {
        return instanceLabel;
    }

    @Override
    public AgentCard card() {
        return card;
    }

    @Override
    public String accept(AgentRequest request) {
        if (request == null) {
            return "Request is required.";
        }
        HubClient client = clientSupplier.get();
        if (client == null) {
            return "Agent Hub is not running.";
        }
        String rawFrom = AgentNames.normalize(request.from());
        String senderBase = AgentNames.baseName(rawFrom);
        String qualifiedFrom = rawFrom.isEmpty()
                ? "studio"
                : (AgentNames.instance(rawFrom) == null ? rawFrom + "#studio" : rawFrom);
        String expiresAt = request.expiresAt() != null
                ? AgentRequest.formatDeadline(request.expiresAt())
                : null;
        String idempotencyKey = UUID.randomUUID().toString();

        HubClient.RequestSubmission sub = client.submitRequest(
                qualifiedFrom,
                callName,
                request.task(),
                request.artifacts(),
                expiresAt,
                idempotencyKey
        );
        if (!sub.accepted()) {
            return sub.error() != null ? sub.error() : "Agent Hub rejected the request.";
        }
        if (onSubmitted != null && !senderBase.isEmpty()) {
            onSubmitted.accept(sub.runId(), new SubmittedRun(sub.runId(), senderBase, card.name(), client));
        }
        return null;
    }

    /**
     * Registers {@code #hub} and all configured named Hub instances from
     * {@code studio.json} in the given {@link AgentDirectory} for every
     * supplied {@link Agent.Spec}.
     *
     * @param directory         the directory to register endpoints in.
     * @param specs             the persona specifications to expose on each Hub instance.
     * @param attachedHubClient supplier for the attached {@code #hub} client.
     * @param namedInstances    configured named instances from {@link StudioConfig#instances()}.
     * @param onSubmitted       optional callback invoked when a run is accepted.
     */
    public static void registerAll(
            AgentDirectory directory,
            Collection<Agent.Spec> specs,
            Supplier<HubClient> attachedHubClient,
            Map<String, StudioConfig.HubInstance> namedInstances,
            BiConsumer<Long, SubmittedRun> onSubmitted
    ) {
        if (directory == null || specs == null) {
            return;
        }
        for (Agent.Spec spec : specs) {
            if (spec == null) {
                continue;
            }
            String callName = spec.callName();
            String description = spec.description();
            if (attachedHubClient != null) {
                directory.register(new HubAgentEndpoint(
                        callName,
                        AgentNames.HUB_INSTANCE,
                        description,
                        List.of(),
                        attachedHubClient,
                        onSubmitted
                ));
            }
            if (namedInstances != null) {
                for (StudioConfig.HubInstance inst : namedInstances.values()) {
                    if (inst == null || !AgentNames.isInstanceLabel(inst.label())) {
                        continue;
                    }
                    HubClient namedClient = new HubClient(inst.baseUrl(), inst.adminToken());
                    directory.register(new HubAgentEndpoint(
                            callName,
                            inst.label(),
                            description,
                            List.of(),
                            () -> namedClient,
                            onSubmitted
                    ));
                }
            }
        }
    }

    /**
     * Returns true when {@code status} is a terminal run status
     * ({@code COMPLETED}, {@code FAILED}, or {@code CANCELLED}).
     *
     * @param status the run status string.
     * @return true when terminal.
     */
    public static boolean isTerminalRunStatus(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "COMPLETED", "FAILED", "CANCELLED" -> true;
            default -> false;
        };
    }

    /**
     * Formats the completion notice delivered to the sending Studio panel when
     * a submitted Hub run reaches {@code COMPLETED}, {@code FAILED}, or {@code CANCELLED}.
     *
     * @param targetInstanceName the Hub instance name (e.g. {@code "mike#hub"}).
     * @param run                the terminal run summary.
     * @param client             the Hub client hosting the run.
     * @return the formatted notice message with a link to the transcript.
     */
    public static String formatCompletionNotice(
            String targetInstanceName,
            HubClient.BotRunSummary run,
            HubClient client
    ) {
        String status = run.status() == null ? "COMPLETED" : run.status().trim().toUpperCase(Locale.ROOT);
        String verb = switch (status) {
            case "FAILED" -> "failed";
            case "CANCELLED" -> "cancelled";
            default -> "finished";
        };
        String detail;
        if ("COMPLETED".equals(status)) {
            detail = (run.summary() == null || run.summary().isBlank()) ? "Completed." : run.summary().strip();
        } else {
            detail = (run.error() == null || run.error().isBlank()) ? status : run.error().strip();
        }
        String transcriptUrl = (client != null ? client.baseUrl() : "http://127.0.0.1:8890")
                + "/v1/admin/runs/" + run.id() + "/transcript";
        return "@" + targetInstanceName + " " + verb + " run " + run.id() + ": " + detail
                + "\n\n[Transcript](" + transcriptUrl + ")";
    }
}
