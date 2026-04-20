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
package smile.validation;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RegressionValidationsTest {

    private static RegressionValidation<Object> makeRound(double fitTime,
                                                          double[] truth,
                                                          double[] prediction) {
        RegressionMetrics metrics = RegressionMetrics.of(fitTime, 1.0, truth, prediction);
        return new RegressionValidation<>(null, truth, prediction, metrics);
    }

    @Test
    public void givenSingleRound_whenAggregating_thenReturnsZeroStd() {
        // This used to throw IllegalArgumentException because MathEx.stdev requires >= 2 values.
        double[] truth = {1.0, 2.0, 3.0};
        double[] pred  = {1.0, 2.0, 4.0};
        var round = makeRound(1.0, truth, pred);

        RegressionValidations<?> result = RegressionValidations.of(List.of(round));

        assertNotNull(result);
        assertEquals(1, result.rounds().size());
        assertEquals(0.0, result.std().rmse(), 1E-12);
        assertEquals(0.0, result.std().fitTime(), 1E-12);
    }

    @Test
    public void givenTwoRounds_whenAggregating_thenAvgIsArithmeticMean() {
        // Round 1: truth={1,2,3}, pred={1,2,4} → RSS=1, RMSE=sqrt(1/3)
        double[] truth1 = {1.0, 2.0, 3.0};
        double[] pred1  = {1.0, 2.0, 4.0};

        // Round 2: truth={1,2,3}, pred={1,3,3} → RSS=1, RMSE=sqrt(1/3)
        double[] truth2 = {1.0, 2.0, 3.0};
        double[] pred2  = {1.0, 3.0, 3.0};

        var r1 = makeRound(1.0, truth1, pred1);
        var r2 = makeRound(2.0, truth2, pred2);

        RegressionValidations<?> result = RegressionValidations.of(List.of(r1, r2));

        assertEquals(2, result.rounds().size());
        assertEquals(Math.sqrt(1.0 / 3.0), result.avg().rmse(), 1E-10);
        // Both rounds have same RMSE → std is 0
        assertEquals(0.0, result.std().rmse(), 1E-10);
        // fit times differ → stdev should be positive
        assertTrue(result.std().fitTime() > 0.0);
    }

    @Test
    public void givenPerfectPredictions_whenAggregating_thenAvgR2IsOne() {
        double[] truth = {1.0, 2.0, 3.0, 4.0};
        double[] pred  = {1.0, 2.0, 3.0, 4.0};

        var r1 = makeRound(1.0, truth, pred);
        var r2 = makeRound(2.0, truth, pred);

        RegressionValidations<?> result = RegressionValidations.of(List.of(r1, r2));

        assertEquals(1.0, result.avg().r2(), 1E-12);
        assertEquals(0.0, result.std().r2(), 1E-12);
    }

    @Test
    public void givenTwoRounds_whenAggregating_thenToStringContainsRMSE() {
        double[] truth = {1.0, 2.0, 3.0};
        double[] pred  = {1.0, 2.5, 3.0};

        var r1 = makeRound(1.0, truth, pred);
        var r2 = makeRound(1.0, truth, pred);
        RegressionValidations<?> result = RegressionValidations.of(List.of(r1, r2));

        String text = result.toString();
        assertTrue(text.contains("RMSE"));
    }
}
