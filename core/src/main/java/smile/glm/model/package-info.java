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

/**
 * The error distribution models (GLM families). Each family specifies
 * a link function, variance function, deviance, and log-likelihood.
 * Available families:
 * <ul>
 *   <li>{@link smile.glm.model.Gaussian} – Normal distribution (identity, log, inverse links).</li>
 *   <li>{@link smile.glm.model.Bernoulli} – Bernoulli distribution (logit link).</li>
 *   <li>{@link smile.glm.model.Binomial} – Binomial distribution (logit link).</li>
 *   <li>{@link smile.glm.model.Poisson} – Poisson distribution (log link).</li>
 * </ul>
 *
 * @author Haifeng Li
 */
package smile.glm.model;