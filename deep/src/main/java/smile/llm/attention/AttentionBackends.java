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
package smile.llm.attention;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.torch.Native;

/**
 * Process-wide attention backend selector (installed at chat-service startup).
 *
 * @author Haifeng Li
 */
public final class AttentionBackends {
    private static final Logger logger = LoggerFactory.getLogger(AttentionBackends.class);

    private static volatile AttentionBackend backend = AttentionBackend.TORCH_NATIVE;
    private static volatile AttentionKernel kernel = new TorchNativeAttentionKernel();

    private AttentionBackends() {}

    /**
     * Installs the attention backend for this process.
     *
     * <p>When {@link AttentionBackend#FLASHINFER} is requested but the native
     * library lacks FlashInfer support, falls back to
     * {@link AttentionBackend#TORCH_NATIVE} and logs a warning.
     *
     * @param selected backend from config.
     */
    public static synchronized void install(AttentionBackend selected) {
        Objects.requireNonNull(selected, "selected");
        AttentionBackend effective = selected;
        AttentionKernel next;
        switch (selected) {
            case TORCH_NATIVE -> next = new TorchNativeAttentionKernel();
            case FLASHINFER -> {
                if (Native.flashInferAvailable()) {
                    next = new FlashInferAttentionKernel();
                } else {
                    logger.warn(
                            "smile.chat.attention-backend=flashinfer but libsmile_torch has no "
                                    + "USE_FLASHINFER CUDA support — falling back to torch_native");
                    effective = AttentionBackend.TORCH_NATIVE;
                    next = new TorchNativeAttentionKernel();
                }
            }
            default -> throw new IllegalArgumentException("Unsupported backend: " + selected);
        }
        backend = effective;
        kernel = next;
        if (effective == selected) {
            logger.info("Attention backend: {}", effective.id());
        } else {
            logger.info("Attention backend: {} (requested {})", effective.id(), selected.id());
        }
    }

    /** @return currently installed backend. */
    public static AttentionBackend current() {
        return backend;
    }

    /** @return kernel for {@link #current()}. */
    public static AttentionKernel kernel() {
        return kernel;
    }

    /**
     * @return {@code true} when FlashInfer was compiled into {@code libsmile_torch}.
     */
    public static boolean flashInferAvailable() {
        return Native.flashInferAvailable();
    }
}
