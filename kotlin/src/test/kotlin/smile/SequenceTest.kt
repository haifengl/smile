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
package smile.sequence

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import smile.data.Tuple
import smile.data.type.DataTypes
import smile.data.type.StructField
import smile.data.type.StructType
import smile.tensor.DenseMatrix

class SequenceTest {

    @Test
    fun testHMMFromProbabilities() {
        val pi = doubleArrayOf(0.5, 0.5)
        val a = arrayOf(doubleArrayOf(0.8, 0.2), doubleArrayOf(0.2, 0.8))
        val b = arrayOf(doubleArrayOf(0.6, 0.4), doubleArrayOf(0.4, 0.6))

        val modelMatrix = hmm(pi, DenseMatrix.of(a), DenseMatrix.of(b))
        assertNotNull(modelMatrix)
        assertArrayEquals(pi, modelMatrix.initialStateProbabilities, 1E-7)

        val modelArrays = hmm(pi, a, b)
        assertNotNull(modelArrays)
        assertArrayEquals(pi, modelArrays.initialStateProbabilities, 1E-7)

        val o = intArrayOf(0, 0, 1, 0, 1, 1)
        val pred = modelArrays.predict(o)
        assertEquals(o.size, pred.size)
        for (state in pred) {
            assertTrue(state == 0 || state == 1)
        }

        val p = modelArrays.p(o)
        assertTrue(p > 0.0)
        assertEquals(Math.log(p), modelArrays.logp(o), 1E-5)
    }

    @Test
    fun testHMMFitIntArrays() {
        val observations = arrayOf(
            intArrayOf(0, 1, 0, 1, 1, 0),
            intArrayOf(1, 0, 1, 0, 0, 1),
            intArrayOf(0, 0, 1, 1, 0, 1),
            intArrayOf(1, 1, 0, 0, 1, 0)
        )
        val labels = arrayOf(
            intArrayOf(0, 1, 0, 1, 1, 0),
            intArrayOf(1, 0, 1, 0, 0, 1),
            intArrayOf(0, 0, 1, 1, 0, 1),
            intArrayOf(1, 1, 0, 0, 1, 0)
        )

        val model = hmm(observations, labels)
        assertNotNull(model)

        val testSeq = intArrayOf(0, 1, 0, 1)
        val pred = model.predict(testSeq)
        assertEquals(testSeq.size, pred.size)
        for (state in pred) {
            assertTrue(state == 0 || state == 1)
        }
    }

    @Test
    fun testGenericHMM() {
        val observations = arrayOf(
            arrayOf("H", "T", "H", "T", "T"),
            arrayOf("T", "H", "T", "H", "H"),
            arrayOf("H", "H", "T", "T", "H")
        )
        val labels = arrayOf(
            intArrayOf(0, 1, 0, 1, 1),
            intArrayOf(1, 0, 1, 0, 0),
            intArrayOf(0, 0, 1, 1, 0)
        )

        val labeler = hmm(observations, labels) { if (it == "H") 0 else 1 }
        assertNotNull(labeler)

        val testSeq = arrayOf("H", "T", "T", "H")
        val pred = labeler.predict(testSeq)
        assertEquals(testSeq.size, pred.size)
        for (state in pred) {
            assertTrue(state == 0 || state == 1)
        }

        val joint = labeler.p(testSeq, pred)
        assertTrue(joint > 0.0)
    }

    private fun makeTupleSequences(): Array<Array<Tuple>> {
        val schema = StructType(StructField("x", DataTypes.IntType))
        return arrayOf(
            arrayOf(Tuple.of(schema, intArrayOf(0)), Tuple.of(schema, intArrayOf(1)), Tuple.of(schema, intArrayOf(0)), Tuple.of(schema, intArrayOf(1))),
            arrayOf(Tuple.of(schema, intArrayOf(1)), Tuple.of(schema, intArrayOf(0)), Tuple.of(schema, intArrayOf(1)), Tuple.of(schema, intArrayOf(0))),
            arrayOf(Tuple.of(schema, intArrayOf(0)), Tuple.of(schema, intArrayOf(0)), Tuple.of(schema, intArrayOf(1)), Tuple.of(schema, intArrayOf(1))),
            arrayOf(Tuple.of(schema, intArrayOf(1)), Tuple.of(schema, intArrayOf(1)), Tuple.of(schema, intArrayOf(0)), Tuple.of(schema, intArrayOf(0)))
        )
    }

    @Test
    fun testCRF() {
        val trainLabels = arrayOf(
            intArrayOf(0, 1, 0, 1),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(1, 1, 0, 0)
        )

        val trainObs = makeTupleSequences()
        val model = crf(trainObs, trainLabels, ntrees = 2, maxDepth = 2, maxNodes = 2, nodeSize = 1)
        assertNotNull(model)

        val testSeq = trainObs[0]
        val pred = model.predict(testSeq)
        assertEquals(testSeq.size, pred.size)

        val vit = model.viterbi(testSeq)
        assertEquals(testSeq.size, vit.size)

        val optionsModel = crf(makeTupleSequences(), trainLabels, CRF.Options(2, 2, 2, 1, 1.0))
        assertNotNull(optionsModel)
    }

    @Test
    fun testGenericCRFAndGCRF() {
        val trainObs = arrayOf(
            arrayOf(0, 1, 0, 1),
            arrayOf(1, 0, 1, 0),
            arrayOf(0, 0, 1, 1),
            arrayOf(1, 1, 0, 0)
        )
        val trainLabels = arrayOf(
            intArrayOf(0, 1, 0, 1),
            intArrayOf(1, 0, 1, 0),
            intArrayOf(0, 0, 1, 1),
            intArrayOf(1, 1, 0, 0)
        )

        val labeler = crf(trainObs, trainLabels, ntrees = 2, maxDepth = 2, maxNodes = 2, nodeSize = 1) {
            Tuple.of(StructType(StructField("x", DataTypes.IntType)), intArrayOf(it))
        }
        assertNotNull(labeler)

        val testSeq = arrayOf(0, 1, 0, 1, 0)
        val pred = labeler.predict(testSeq)
        assertEquals(testSeq.size, pred.size)

        val vit = labeler.viterbi(testSeq)
        assertEquals(testSeq.size, vit.size)

        // Test gcrf alias
        val gcrfLabeler = gcrf(trainObs, trainLabels, ntrees = 2, maxDepth = 2, maxNodes = 2, nodeSize = 1) {
            Tuple.of(StructType(StructField("x", DataTypes.IntType)), intArrayOf(it))
        }
        assertNotNull(gcrfLabeler)
        val gcrfPred = gcrfLabeler.predict(arrayOf(1, 0, 1))
        assertEquals(3, gcrfPred.size)
    }
}
