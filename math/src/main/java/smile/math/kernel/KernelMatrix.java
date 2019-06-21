/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/

package smile.math.kernel;

/**
 * A kernel matrix of dataset is the array of k(x<sub>i</sub>, x<sub>j</sub>).
 * Various cache strategies can be used to reduce the evaluations of kernel
 * functions during training a kernel machine such as SVM.
 *
 * @author Haifeng Li
 */
public interface KernelMatrix {

    /**
     * Returns the element k(x<sub>i</sub>, x<sub>j</sub>) of kernel matrix for
     * a given dataset.
     */
    double k(int i, int j);
}
