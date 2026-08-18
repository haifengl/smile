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
package smile.llm.qwen;

import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

/**
 * Per-request DeltaNet recurrent and causal-conv state for linear-attention layers.
 *
 * @author Haifeng Li
 */
public class DeltaNetStatePool implements AutoCloseable {
    final int numLinearLayers;
    final int numVHeads;
    final int keyHeadDim;
    final int valueHeadDim;
    final int convDim;
    final int convStateLen;
    final int maxBatchSize;
    final Device device;
    final ScalarType dtype;

    /** Recurrent states {@code [B, V, Kdim, Vdim]} per linear layer. */
    final Tensor[] recurrent;
    /** Conv left-context {@code [B, C, K-1]} per linear layer. */
    final Tensor[] conv;

    private int boundBatch;

    /**
     * Constructor.
     *
     * @param numLinearLayers number of linear-attention layers.
     * @param numVHeads       DeltaNet value head count.
     * @param keyHeadDim      key head dim.
     * @param valueHeadDim    value head dim.
     * @param convDim         fused QKV channel count.
     * @param convKernel      causal conv kernel size.
     * @param maxBatchSize    maximum batch size.
     * @param device          compute device.
     * @param dtype           element dtype.
     */
    public DeltaNetStatePool(int numLinearLayers, int numVHeads, int keyHeadDim, int valueHeadDim,
                             int convDim, int convKernel, int maxBatchSize,
                             Device device, ScalarType dtype) {
        if (numLinearLayers < 1) throw new IllegalArgumentException("numLinearLayers must be >= 1");
        if (convKernel < 1) throw new IllegalArgumentException("convKernel must be >= 1");
        this.numLinearLayers = numLinearLayers;
        this.numVHeads = numVHeads;
        this.keyHeadDim = keyHeadDim;
        this.valueHeadDim = valueHeadDim;
        this.convDim = convDim;
        this.convStateLen = Math.max(0, convKernel - 1);
        this.maxBatchSize = maxBatchSize;
        this.device = device;
        this.dtype = dtype;
        this.recurrent = new Tensor[numLinearLayers];
        this.conv = new Tensor[numLinearLayers];
        var opts = new Tensor.Options().device(device).dtype(dtype).requireGradients(false);
        for (int i = 0; i < numLinearLayers; i++) {
            recurrent[i] = Tensor.zeros(opts, maxBatchSize, numVHeads, keyHeadDim, valueHeadDim);
            if (convStateLen > 0) {
                conv[i] = Tensor.zeros(opts, maxBatchSize, convDim, convStateLen);
            } else {
                conv[i] = null;
            }
        }
        this.boundBatch = 0;
    }

    /**
     * Zeros all states and records the active batch size for this request.
     * @param batchSize active batch size.
     */
    public void reset(int batchSize) {
        if (batchSize < 1 || batchSize > maxBatchSize) {
            throw new IllegalArgumentException("batchSize out of range: " + batchSize);
        }
        this.boundBatch = batchSize;
        for (int i = 0; i < numLinearLayers; i++) {
            recurrent[i].fill_(0.0);
            if (conv[i] != null) {
                conv[i].fill_(0.0);
            }
        }
    }

    /** Active batch size from the last {@link #reset}. */
    public int boundBatch() {
        return boundBatch;
    }

    public Tensor recurrent(int linearLayerId) {
        return recurrent[linearLayerId];
    }

    public Tensor conv(int linearLayerId) {
        return conv[linearLayerId];
    }

    public int numLinearLayers() {
        return numLinearLayers;
    }

    @Override
    public void close() {
        for (int i = 0; i < numLinearLayers; i++) {
            if (recurrent[i] != null) {
                recurrent[i].close();
                recurrent[i] = null;
            }
            if (conv[i] != null) {
                conv[i].close();
                conv[i] = null;
            }
        }
    }
}
