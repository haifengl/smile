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
package smile.llm.parallel;

import java.util.Optional;
import java.util.function.UnaryOperator;
import smile.deep.tensor.Device;

/**
 * Resolves GPU placement for auxiliary model components — the vision tower and the
 * DSpark/speculative draft head — across all multimodal and speculative model families
 * (MiniMax, DeepSeek-V4.1, GLM-5.3, and Inkling-2).
 *
 * <p>Users can explicitly pin either component to any CUDA device (either one of the
 * tensor-parallel ranks or an external GPU outside {@code smile.chat.devices}):
 * <ul>
 *   <li>Vision tower: {@code -Dsmile.chat.vision.device=<id>} or {@code SMILE_CHAT_VISION_DEVICE=<id>}</li>
 *   <li>DSpark draft: {@code -Dsmile.chat.dspark.device=<id>} or {@code SMILE_CHAT_DSPARK_DEVICE=<id>}</li>
 * </ul>
 *
 * <p>Precedence is: explicit caller override &gt; JVM system property &gt; environment variable &gt;
 * automatic multi-GPU balancing. When unset on a multi-GPU setup ({@code tpDevices.length >= 2}),
 * auto-balancing spreads the two auxiliary components across distinct ranks (DSpark on rank 0,
 * vision on rank 1, or avoiding whichever rank the other component was explicitly pinned to) so
 * both do not pile onto rank 0 and starve its KV cache budget.
 *
 * @param vision resolved vision tower placement.
 * @param dspark resolved DSpark/draft placement.
 * @author Haifeng Li
 */
public record AuxDevicePlacement(Target vision, Target dspark) {

    /** System property for explicit vision tower CUDA device index. */
    public static final String VISION_DEVICE_PROPERTY = "smile.chat.vision.device";

    /** Environment variable for explicit vision tower CUDA device index. */
    public static final String VISION_DEVICE_ENV = "SMILE_CHAT_VISION_DEVICE";

    /** System property for explicit DSpark/draft CUDA device index. */
    public static final String DSPARK_DEVICE_PROPERTY = "smile.chat.dspark.device";

    /** Environment variable for explicit DSpark/draft CUDA device index. */
    public static final String DSPARK_DEVICE_ENV = "SMILE_CHAT_DSPARK_DEVICE";

    /**
     * Resolved target device for an auxiliary component.
     *
     * @param device   target compute device (CUDA or CPU).
     * @param tpRank   index inside {@code tpDevices}, or {@code -1} if the device is outside the TP group.
     * @param explicit {@code true} if explicitly selected by config, system property, or environment variable.
     */
    public record Target(Device device, int tpRank, boolean explicit) {
        /**
         * Returns {@code true} when the target is an in-group TP rank.
         *
         * @return {@code true} if {@code tpRank >= 0}.
         */
        public boolean isInGroup() {
            return tpRank >= 0;
        }

        /**
         * Returns {@code true} when the target is a CUDA device outside the TP group.
         *
         * @return {@code true} if external CUDA GPU.
         */
        public boolean isExternal() {
            return tpRank < 0 && device.isCUDA();
        }

        /**
         * Concise human-readable description for startup logging.
         *
         * @return formatted placement summary.
         */
        public String describe() {
            String origin = explicit ? "explicit" : "auto";
            if (!device.isCUDA()) {
                return device + " (" + origin + ")";
            }
            String location = tpRank >= 0 ? "TP rank " + tpRank : "external GPU";
            return device + " (" + location + ", " + origin + ")";
        }
    }

    /**
     * Resolves auxiliary device placement from system properties and environment variables,
     * falling back to automatic multi-GPU balancing.
     *
     * @param tpDevices CUDA device indices of the TP ranks, or {@code null} for CPU.
     * @return resolved placement.
     */
    public static AuxDevicePlacement resolve(byte[] tpDevices) {
        return resolve(tpDevices, Optional.empty(), Optional.empty());
    }

    /**
     * Resolves auxiliary device placement with optional caller-supplied overrides
     * (for example from Quarkus {@code ChatServiceConfig}).
     *
     * @param tpDevices      CUDA device indices of the TP ranks, or {@code null} for CPU.
     * @param visionOverride optional explicit vision CUDA device index.
     * @param dsparkOverride optional explicit DSpark CUDA device index.
     * @return resolved placement.
     */
    public static AuxDevicePlacement resolve(
            byte[] tpDevices,
            Optional<Integer> visionOverride,
            Optional<Integer> dsparkOverride) {
        return resolve(tpDevices, visionOverride, dsparkOverride, System::getProperty, System::getenv);
    }

    /**
     * Package-private / testable overload allowing injected property and environment lookups.
     *
     * @param tpDevices      CUDA device indices of the TP ranks, or {@code null} for CPU.
     * @param visionOverride optional explicit vision CUDA device index.
     * @param dsparkOverride optional explicit DSpark CUDA device index.
     * @param sysProps       system property lookup.
     * @param env            environment variable lookup.
     * @return resolved placement.
     */
    public static AuxDevicePlacement resolve(
            byte[] tpDevices,
            Optional<Integer> visionOverride,
            Optional<Integer> dsparkOverride,
            UnaryOperator<String> sysProps,
            UnaryOperator<String> env) {
        Optional<Byte> explicitVision = resolveExplicit(
                visionOverride, VISION_DEVICE_PROPERTY, VISION_DEVICE_ENV, sysProps, env);
        Optional<Byte> explicitDspark = resolveExplicit(
                dsparkOverride, DSPARK_DEVICE_PROPERTY, DSPARK_DEVICE_ENV, sysProps, env);

        if (tpDevices == null || tpDevices.length == 0) {
            Target vision = explicitVision
                    .map(id -> new Target(Device.CUDA(id), -1, true))
                    .orElseGet(() -> new Target(Device.CPU(), 0, false));
            Target dspark = explicitDspark
                    .map(id -> new Target(Device.CUDA(id), -1, true))
                    .orElseGet(() -> new Target(Device.CPU(), 0, false));
            return new AuxDevicePlacement(vision, dspark);
        }

        int explicitVisionRank = explicitVision.map(id -> findRank(tpDevices, id)).orElse(-1);
        int explicitDsparkRank = explicitDspark.map(id -> findRank(tpDevices, id)).orElse(-1);

        Target dspark;
        if (explicitDspark.isPresent()) {
            byte id = explicitDspark.get();
            dspark = new Target(Device.CUDA(id), explicitDsparkRank, true);
        } else {
            int autoRank = (tpDevices.length >= 2 && explicitVisionRank == 0) ? 1 : 0;
            dspark = new Target(Device.CUDA(tpDevices[autoRank]), autoRank, false);
        }

        Target vision;
        if (explicitVision.isPresent()) {
            byte id = explicitVision.get();
            vision = new Target(Device.CUDA(id), explicitVisionRank, true);
        } else {
            int autoRank;
            if (tpDevices.length == 1) {
                autoRank = 0;
            } else {
                autoRank = dspark.tpRank() == 1 ? 0 : 1;
            }
            vision = new Target(Device.CUDA(tpDevices[autoRank]), autoRank, false);
        }

        return new AuxDevicePlacement(vision, dspark);
    }

    /**
     * Convenience wrapper returning a single-device placement for a single CUDA device.
     *
     * @param device         CUDA device index.
     * @param visionOverride optional explicit vision CUDA device index.
     * @param dsparkOverride optional explicit DSpark CUDA device index.
     * @return resolved placement.
     */
    public static AuxDevicePlacement resolveSingle(
            byte device,
            Optional<Integer> visionOverride,
            Optional<Integer> dsparkOverride) {
        return resolve(new byte[]{device}, visionOverride, dsparkOverride);
    }

    private static Optional<Byte> resolveExplicit(
            Optional<Integer> override,
            String propName,
            String envName,
            UnaryOperator<String> sysProps,
            UnaryOperator<String> env) {
        if (override != null && override.isPresent()) {
            return Optional.of(validateDeviceId(override.get(), propName));
        }
        String prop = sysProps.apply(propName);
        if (prop != null && !prop.isBlank()) {
            return Optional.of(parseDeviceId(prop, propName));
        }
        String envVal = env.apply(envName);
        if (envVal != null && !envVal.isBlank()) {
            return Optional.of(parseDeviceId(envVal, envName));
        }
        return Optional.empty();
    }

    private static byte parseDeviceId(String raw, String source) {
        String trimmed = raw.trim();
        final int parsed;
        try {
            parsed = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    source + " must be a non-negative integer CUDA device index, got: '" + raw + "'", e);
        }
        return validateDeviceId(parsed, source);
    }

    private static byte validateDeviceId(int id, String source) {
        if (id < 0 || id > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(
                    source + " must be in [0, " + Byte.MAX_VALUE + "], got: " + id);
        }
        return (byte) id;
    }

    private static int findRank(byte[] tpDevices, byte deviceId) {
        for (int r = 0; r < tpDevices.length; r++) {
            if (tpDevices[r] == deviceId) {
                return r;
            }
        }
        return -1;
    }
}
