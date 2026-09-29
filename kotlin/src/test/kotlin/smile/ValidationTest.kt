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
package smile.validation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import smile.classification.cart as cartClassifier
import smile.classification.knn
import smile.datasets.Iris
import smile.datasets.Longley
import smile.math.MathEx
import smile.model.rbf.RBF
import smile.regression.cart as cartRegressor
import smile.regression.lm
import smile.regression.rbfnet
import smile.util.Index

class ValidationTest {

    @Test
    fun testClassificationMetrics() {
        val truth = intArrayOf(1, 1, 0, 1, 0, 0, 1, 0, 1, 0)
        val pred = intArrayOf(1, 0, 0, 1, 0, 0, 0, 1, 1, 0)
        val prob = doubleArrayOf(0.9, 0.4, 0.1, 0.8, 0.2, 0.3, 0.4, 0.6, 0.85, 0.15)

        val cm = confusion(truth, pred)
        assertNotNull(cm)

        val acc = accuracy(truth, pred)
        assertEquals(0.7, acc, 1e-4)

        val rec = recall(truth, pred)
        val sens = sensitivity(truth, pred)
        assertEquals(rec, sens, 1e-6)

        val prec = precision(truth, pred)
        assertTrue(prec in 0.0..1.0)

        val spec = specificity(truth, pred)
        val fall = fallout(truth, pred)
        assertEquals(1.0, spec + fall, 1e-6)

        val fd = fdr(truth, pred)
        assertEquals(1.0, prec + fd, 1e-6)

        val f1Score = f1(truth, pred)
        assertTrue(f1Score in 0.0..1.0)

        val fscoreVal = fscore(truth, pred, 1.0)
        assertEquals(f1Score, fscoreVal, 1e-6)

        val aucVal = auc(truth, prob)
        assertTrue(aucVal in 0.5..1.0)

        val ll = logloss(truth, prob)
        assertTrue(ll > 0.0)

        val prob2d = Array(truth.size) { i -> doubleArrayOf(1.0 - prob[i], prob[i]) }
        val ce = crossentropy(truth, prob2d)
        assertTrue(ce > 0.0)

        val mccVal = mcc(truth, pred)
        assertTrue(mccVal in -1.0..1.0)
    }

    @Test
    fun testRegressionMetrics() {
        val truth = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val pred = doubleArrayOf(1.1, 1.9, 3.2, 3.8, 5.1)

        val mseVal = mse(truth, pred)
        assertTrue(mseVal > 0.0)

        val rmseVal = rmse(truth, pred)
        assertEquals(Math.sqrt(mseVal), rmseVal, 1e-6)

        val rssVal = rss(truth, pred)
        assertEquals(mseVal * truth.size, rssVal, 1e-4)

        val madVal = mad(truth, pred)
        assertTrue(madVal > 0.0)

        val r2Val = r2(truth, pred)
        assertTrue(r2Val in 0.9..1.0)
    }

    @Test
    fun testClusteringMetrics() {
        val y1 = intArrayOf(0, 0, 0, 1, 1, 1, 2, 2)
        val y2 = intArrayOf(0, 0, 1, 1, 1, 2, 2, 2)

        val ri = randIndex(y1, y2)
        assertTrue(ri in 0.0..1.0)

        val ari = adjustedRandIndex(y1, y2)
        assertTrue(ari in -1.0..1.0)

        val nmiVal = nmi(y1, y2)
        assertTrue(nmiVal in 0.0..1.0)
    }

    @Test
    fun testValidateClassification() {
        val iris = Iris()
        val n = iris.data.nrow()
        val testIndices = (0 until n step 3).toList().toIntArray()
        val trainIndices = (0 until n).filter { it % 3 != 0 }.toIntArray()

        val train = iris.data.get(Index.of(*trainIndices))
        val test = iris.data.get(Index.of(*testIndices))

        val resDf = validate.classification(iris.formula, train, test) { f, d ->
            cartClassifier(f, d)
        }
        assertNotNull(resDf.model)
        assertTrue(resDf.metrics.accuracy in 0.0..1.0)

        val x = iris.x()
        val y = iris.y()
        val trainX = MathEx.slice(x, trainIndices)
        val trainY = MathEx.slice(y, trainIndices)
        val testX = MathEx.slice(x, testIndices)
        val testY = MathEx.slice(y, testIndices)

        val resArr = validate.classification(trainX, trainY, testX, testY) { tx, ty ->
            knn(tx, ty, 3)
        }
        assertNotNull(resArr.model)
        assertTrue(resArr.metrics.accuracy in 0.0..1.0)
    }

    @Test
    fun testValidateRegression() {
        val longley = Longley()
        val n = longley.data.nrow()
        val split = 12
        val train = longley.data.slice(0, split)
        val test = longley.data.slice(split, n)

        val resDf = validate.regression(longley.formula, train, test) { f, d ->
            lm(f, d)
        }
        assertNotNull(resDf.model)
        assertTrue(resDf.metrics.rmse >= 0.0)

        val x = longley.x()
        val y = longley.y()
        val trainX = x.sliceArray(0 until split)
        val trainY = y.sliceArray(0 until split)
        val testX = x.sliceArray(split until n)
        val testY = y.sliceArray(split until n)

        val resArr = validate.regression(trainX, trainY, testX, testY) { tx, ty ->
            rbfnet(tx, ty, RBF.fit(tx, 5))
        }
        assertNotNull(resArr.model)
        assertTrue(resArr.metrics.rmse >= 0.0)
    }

    @Test
    fun testCrossValidation() {
        val iris = Iris()
        val cvDf = cv.classification(5, iris.formula, iris.data) { f, d ->
            cartClassifier(f, d)
        }
        assertEquals(5, cvDf.rounds.size)
        assertTrue(cvDf.avg.accuracy in 0.0..1.0)

        val x = iris.x()
        val y = iris.y()
        val cvArr = cv.classification(5, x, y) { tx, ty ->
            knn(tx, ty, 3)
        }
        assertEquals(5, cvArr.rounds.size)
        assertTrue(cvArr.avg.accuracy in 0.0..1.0)

        val cvRepArr = cv.classification(2, 5, x, y) { tx, ty ->
            knn(tx, ty, 3)
        }
        assertEquals(10, cvRepArr.rounds.size)

        val cvRepDf = cv.classification(2, 5, iris.formula, iris.data) { f, d ->
            cartClassifier(f, d)
        }
        assertEquals(10, cvRepDf.rounds.size)

        val stratArr = cv.stratify(5, x, y) { tx, ty ->
            knn(tx, ty, 3)
        }
        assertEquals(5, stratArr.rounds.size)

        val stratDf = cv.stratify(5, iris.formula, iris.data) { f, d ->
            cartClassifier(f, d)
        }
        assertEquals(5, stratDf.rounds.size)

        val stratRepArr = cv.stratify(2, 5, x, y) { tx, ty ->
            knn(tx, ty, 3)
        }
        assertEquals(10, stratRepArr.rounds.size)

        val stratRepDf = cv.stratify(2, 5, iris.formula, iris.data) { f, d ->
            cartClassifier(f, d)
        }
        assertEquals(10, stratRepDf.rounds.size)

        val longley = Longley()
        val cvRegDf = cv.regression(3, longley.formula, longley.data) { f, d ->
            lm(f, d)
        }
        assertEquals(3, cvRegDf.rounds.size)
        assertTrue(cvRegDf.avg.rmse >= 0.0)

        val cvRegArr = cv.regression(3, longley.x(), longley.y()) { tx, ty ->
            rbfnet(tx, ty, RBF.fit(tx, 5))
        }
        assertEquals(3, cvRegArr.rounds.size)
        assertTrue(cvRegArr.avg.rmse >= 0.0)

        val cvRegRepDf = cv.regression(2, 3, longley.formula, longley.data) { f, d ->
            lm(f, d)
        }
        assertEquals(6, cvRegRepDf.rounds.size)

        val cvRegRepArr = cv.regression(2, 3, longley.x(), longley.y()) { tx, ty ->
            rbfnet(tx, ty, RBF.fit(tx, 5))
        }
        assertEquals(6, cvRegRepArr.rounds.size)
    }

    @Test
    fun testLOOCV() {
        val iris = Iris()
        val subIndices = (0 until 150 step 5).toList().toIntArray()
        val subData = iris.data.get(Index.of(*subIndices))
        val looDf = loocv.classification(iris.formula, subData) { f, d ->
            cartClassifier(f, d)
        }
        assertEquals(30, looDf.size)
        assertTrue(looDf.accuracy in 0.0..1.0)

        val subX = MathEx.slice(iris.x(), subIndices)
        val subY = MathEx.slice(iris.y(), subIndices)
        val looArr = loocv.classification(subX, subY) { tx, ty ->
            knn(tx, ty, 3)
        }
        assertEquals(30, looArr.size)
        assertTrue(looArr.accuracy in 0.0..1.0)

        val longley = Longley()
        val looRegDf = loocv.regression(longley.formula, longley.data) { f, d ->
            cartRegressor(f, d)
        }
        assertEquals(16, looRegDf.size)
        assertTrue(looRegDf.rmse >= 0.0)

        val subLongleyX = longley.x().sliceArray(0 until 10)
        val subLongleyY = longley.y().sliceArray(0 until 10)
        val looRegArr = loocv.regression(subLongleyX, subLongleyY) { tx, ty ->
            rbfnet(tx, ty, RBF.fit(tx, 5))
        }
        assertEquals(10, looRegArr.size)
        assertTrue(looRegArr.rmse >= 0.0)
    }

    @Test
    fun testBootstrap() {
        val iris = Iris()
        val bootDf = bootstrap.classification(5, iris.formula, iris.data) { f, d ->
            cartClassifier(f, d)
        }
        assertEquals(5, bootDf.rounds.size)

        val x = iris.x()
        val y = iris.y()
        val bootArr = bootstrap.classification(5, x, y) { tx, ty ->
            knn(tx, ty, 3)
        }
        assertEquals(5, bootArr.rounds.size)

        val longley = Longley()
        val bootRegDf = bootstrap.regression(3, longley.formula, longley.data) { f, d ->
            lm(f, d)
        }
        assertEquals(3, bootRegDf.rounds.size)

        val bootRegArr = bootstrap.regression(3, longley.x(), longley.y()) { tx, ty ->
            rbfnet(tx, ty, RBF.fit(tx, 5))
        }
        assertEquals(3, bootRegArr.rounds.size)
    }
}
