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

public class ClassificationValidationsTest {

    @Test
    public void givenSingleRound_whenAggregating_thenReturnsZeroStd() {
        // This used to throw IllegalArgumentException because MathEx.stdev requires >= 2 values.
        int[] truth      = {0, 1, 1, 0, 1};
        int[] prediction = {0, 1, 0, 0, 1};
        var round = new ClassificationValidation<>(null, 1.0, 2.0, truth, prediction);

        ClassificationValidations<?> result = ClassificationValidations.of(List.of(round));

        assertNotNull(result);
        assertEquals(1, result.rounds().size());
        // std should be 0.0 for a single round, not throw
        assertEquals(0.0, result.std().accuracy(), 1E-12);
        assertEquals(0.0, result.std().fitTime(), 1E-12);
    }

    @Test
    public void givenTwoRounds_whenAggregating_thenAvgIsArithmeticMean() {
        // Round 1: 4/5 = 0.8 accuracy
        int[] truth1 = {0, 1, 1, 0, 1};
        int[] pred1  = {0, 1, 1, 0, 0};  // 1 error → accuracy 0.8

        // Round 2: 3/5 = 0.6 accuracy
        int[] truth2 = {0, 1, 1, 0, 1};
        int[] pred2  = {0, 1, 0, 1, 1};  // 2 errors → accuracy 0.6

        var r1 = new ClassificationValidation<>(null, 1.0, 2.0, truth1, pred1);
        var r2 = new ClassificationValidation<>(null, 1.5, 2.5, truth2, pred2);

        ClassificationValidations<?> result = ClassificationValidations.of(List.of(r1, r2));

        assertEquals(2, result.rounds().size());
        assertEquals(0.7, result.avg().accuracy(), 1E-10);  // (0.8 + 0.6) / 2
        assertTrue(result.std().accuracy() > 0.0);
    }

    @Test
    public void givenMultipleRounds_whenAggregating_thenAvgSizeIsRounded() {
        int[] truth = {0, 0, 1, 1};
        int[] pred  = {0, 0, 1, 1};

        var rounds = List.of(
                new ClassificationValidation<>(null, 1.0, 1.0, truth, pred),
                new ClassificationValidation<>(null, 2.0, 2.0, truth, pred),
                new ClassificationValidation<>(null, 3.0, 3.0, truth, pred)
        );

        ClassificationValidations<?> result = ClassificationValidations.of(rounds);

        assertEquals(4, result.avg().size());
        assertEquals(0, result.avg().error());
        assertEquals(1.0, result.avg().accuracy(), 1E-12);
        assertEquals(0.0, result.std().accuracy(), 1E-12);
    }

    @Test
    public void givenTwoRounds_whenAggregating_thenToStringContainsAccuracy() {
        int[] truth = {0, 1, 0, 1};
        int[] pred  = {0, 1, 0, 0};

        var r1 = new ClassificationValidation<>(null, 1.0, 1.0, truth, pred);
        var r2 = new ClassificationValidation<>(null, 1.0, 1.0, truth, pred);
        ClassificationValidations<?> result = ClassificationValidations.of(List.of(r1, r2));

        String text = result.toString();
        assertTrue(text.contains("accuracy"));
    }
}
