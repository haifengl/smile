/*******************************************************************************
 * (C) Copyright 2015 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.association

import java.io.PrintStream
import scala.io.Source
import scala.collection.mutable.Buffer
import scala.collection.JavaConverters._
import smile.util._

/** High level association rule operators.
  *
  * @author Haifeng Li
  */
trait Operators {
  /** Frequent item set mining based on the FP-growth (frequent pattern growth)
    * algorithm, which employs an extended prefix-tree (FP-tree) structure to
    * store the database in a compressed form. The FP-growth algorithm is
    * currently one of the fastest approaches to discover frequent item sets.
    * FP-growth adopts a divide-and-conquer approach to decompose both the mining
    * tasks and the databases. It uses a pattern fragment growth method to avoid
    * the costly process of candidate generation and testing used by Apriori.
    *
    * The basic idea of the FP-growth algorithm can be described as a
    * recursive elimination scheme: in a preprocessing step delete
    * all items from the transactions that are not frequent individually,
    * i.e., do not appear in a user-specified minimum
    * number of transactions. Then select all transactions that
    * contain the least frequent item (least frequent among those
    * that are frequent) and delete this item from them. Recurse
    * to process the obtained reduced (also known as projected)
    * database, remembering that the item sets found in the recursion
    * share the deleted item as a prefix. On return, remove
    * the processed item from the database of all transactions
    * and start over, i.e., process the second frequent item etc. In
    * these processing steps the prefix tree, which is enhanced by
    * links between the branches, is exploited to quickly find the
    * transactions containing a given item and also to remove this
    * item from the transactions after it has been processed.
    *
    * @param itemsets the item set database. Each row is a item set, which
    *                 may have different length. The item identifiers have to be in [0, n),
    *                 where n is the number of items. Item set should NOT contain duplicated
    *                 items. Note that it is reordered after the call.
    * @param minSupport the required minimum support of item sets in terms
    *                   of frequency.
    * @return the list of frequent item sets.
    */
  def fpgrowth(itemsets: Array[Array[Int]], minSupport: Int): Buffer[ItemSet] = {
    time {
      val fim = new FPGrowth(itemsets, minSupport)
      fim.learn.asScala
    }
  }

  /** Frequent item set mining based on the FP-growth (frequent pattern growth)
    * algorithm. Usually the algorithm generates too many data to fit
    * in the memory. This alternative prints the results to a stream directly without
    * storing them in the memory.
    *
    * @param itemsets the item set database. Each row is a item set, which
    *                 may have different length. The item identifiers have to be in [0, n),
    *                 where n is the number of items. Item set should NOT contain duplicated
    *                 items. Note that it is reordered after the call.
    * @param minSupport the required minimum support of item sets in terms
    *                   of frequency.
    * @param output a print stream for output of frequent item sets.
    * @return the number of discovered frequent item sets.
    */
  def fpgrowth(itemsets: Array[Array[Int]], minSupport: Int, output: PrintStream): Long = {
    time {
      val fim = new FPGrowth(itemsets, minSupport)
      fim.learn(output)
    }
  }

  /** Frequent item set mining based on the FP-growth (frequent pattern growth)
    * algorithm. Usually the algorithm generates too many data to fit
    * in the memory. This alternative prints the results to a stream directly without
    * storing them in the memory.
    *
    * @param itemsets the item set database. Each row is a item set, which
    *                 may have different length. The item identifiers have to be in [0, n),
    *                 where n is the number of items. Item set should NOT contain duplicated
    *                 items. Note that it is reordered after the call.
    * @param minSupport the required minimum support of item sets in terms
    *                   of frequency.
    * @param output the output file.
    * @return the number of discovered frequent item sets.
    */
  def fpgrowth(itemsets: Array[Array[Int]], minSupport: Int, output: String): Long = {
    time {
      val fim = new FPGrowth(itemsets, minSupport)
      fim.learn(new PrintStream(output))
    }
  }

  /** Frequent item set mining based on the FP-growth (frequent pattern growth)
    * algorithm. This is for mining frequent item sets by scanning data
    * twice. We first scan the database to obtains the frequency of
    * single items. Then we scan the data again to construct the FP-Tree, which
    * is a compressed form of data.
    * In this way, we don't need load the whole database into the main memory.
    * In the data, the item identifiers have to be in [0, n), where n is
    * the number of items.
    *
    * @param file the input file of item sets. Each row is a item set, which
    *                 may have different length. The item identifiers have to be in [0, n),
    *                 where n is the number of items.
    * @param minSupport the required minimum support of item sets in terms
    *                   of frequency.
    * @param output a print stream for output of frequent item sets.
    * @return the number of discovered frequent item sets.
    */
  def fpgrowth(file: String, minSupport: Int, output: PrintStream): Long = {
    time {
      val items = collection.mutable.Map[Int, Int]().withDefaultValue(0)
      Source.fromFile(file).getLines.foreach { line =>
        line.trim.split("\\s+").foreach { item =>
          val i = item.toInt
          items(i) = items(i) + 1
        }
      }

      val n = items.keySet.foldLeft(0)(Math.max) + 1
      val frequency = new Array[Int](n)
      items.toSeq.foreach { case (item, count) => frequency(item) = count }

      val fim = new FPGrowth(frequency, minSupport)
      Source.fromFile(file).getLines.foreach { line =>
        fim.add(line.trim.split("\\s+").map(_.toInt).toArray)
      }

      fim.learn(output)
    }
  }

  /** Frequent item set mining based on the FP-growth (frequent pattern growth)
    * algorithm. This is for mining frequent item sets by scanning data
    * twice. We first scan the database to obtains the frequency of
    * single items. Then we scan the data again to construct the FP-Tree, which
    * is a compressed form of data.
    * In this way, we don't need load the whole database into the main memory.
    * In the data, the item identifiers have to be in [0, n), where n is
    * the number of items.
    *
    * @param file the input file of item sets. Each row is a item set, which
    *                 may have different length. The item identifiers have to be in [0, n),
    *                 where n is the number of items.
    * @param minSupport the required minimum support of item sets in terms
    *                   of frequency.
    * @param output the output file.
    * @return the number of discovered frequent item sets.
    */
  def fpgrowth(file: String, minSupport: Int, output: String): Long = {
    time {
      fpgrowth(file, minSupport, new PrintStream(output))
    }
  }

  /** Association Rule Mining.
    * Let I = {i<sub>1</sub>, i<sub>2</sub>,..., i<sub>n</sub>} be a set of n
    * binary attributes called items. Let D = {t<sub>1</sub>, t<sub>2</sub>,..., t<sub>m</sub>}
    * be a set of transactions called the database. Each transaction in D has a
    * unique transaction ID and contains a subset of the items in I.
    * An association rule is defined as an implication of the form X &rArr; Y
    * where X, Y &sube; I and X &cap; Y = &Oslash;. The item sets X and Y are called
    * antecedent (left-hand-side or LHS) and consequent (right-hand-side or RHS)
    * of the rule, respectively. The support supp(X) of an item set X is defined as
    * the proportion of transactions in the database which contain the item set.
    * Note that the support of an association rule X &rArr; Y is supp(X &cup; Y).
    * The confidence of a rule is defined conf(X &rArr; Y) = supp(X &cup; Y) / supp(X).
    * Confidence can be interpreted as an estimate of the probability P(Y | X),
    * the probability of finding the RHS of the rule in transactions under the
    * condition that these transactions also contain the LHS.
    * Association rules are usually required to satisfy a user-specified minimum
    * support and a user-specified minimum confidence at the same time.
    *
    * @param itemsets the item set database. Each row is a item set, which
    *                 may have different length. The item identifiers have to be in [0, n),
    *                 where n is the number of items. Item set should NOT contain duplicated
    *                 items. Note that it is reordered after the call.
    * @param minSupport the required minimum support of item sets in terms
    *                   of frequency.
    * @param confidence the confidence threshold for association rules.
    * @return the number of discovered association rules.
    */
  def arm(itemsets: Array[Array[Int]], minSupport: Int, confidence: Double): Buffer[AssociationRule] = {
    time {
      val rule = new ARM(itemsets, minSupport)
      rule.learn(confidence).asScala
    }
  }

  /** Association Rule Mining.
    * Usually the algorithm generates too many data to fit
    * in the memory. This alternative prints the results to a stream directly without
    * storing them in the memory.
    *
    * @param itemsets the item set database. Each row is a item set, which
    *                 may have different length. The item identifiers have to be in [0, n),
    *                 where n is the number of items. Item set should NOT contain duplicated
    *                 items. Note that it is reordered after the call.
    * @param minSupport the required minimum support of item sets in terms
    *                   of frequency.
    * @param confidence the confidence threshold for association rules.
    * @param output a print stream for output of association rules.
    * @return the number of discovered association rules.
    */
  def arm(itemsets: Array[Array[Int]], minSupport: Int, confidence: Double, output: PrintStream): Long = {
    time {
      val rule = new ARM(itemsets, minSupport)
      rule.learn(confidence, output)
    }
  }

  /** Association Rule Mining.
    * Usually the algorithm generates too many data to fit
    * in the memory. This alternative prints the results to a stream directly without
    * storing them in the memory.
    *
    * @param itemsets the item set database. Each row is a item set, which
    *                 may have different length. The item identifiers have to be in [0, n),
    *                 where n is the number of items. Item set should NOT contain duplicated
    *                 items. Note that it is reordered after the call.
    * @param minSupport the required minimum support of item sets in terms
    *                   of frequency.
    * @param confidence the confidence threshold for association rules.
    * @param output the output file.
    * @return the number of discovered association rules.
    */
  def arm(itemsets: Array[Array[Int]], minSupport: Int, confidence: Double, output: String): Long = {
    time {
      val rule = new ARM(itemsets, minSupport)
      rule.learn(confidence, new PrintStream(output))
    }
  }

  /** Association Rule Mining. This method scans data
    * twice. We first scan the database to obtains the frequency of
    * single items. Then we scan the data again to construct the FP-Tree, which
    * is a compressed form of data.
    * In this way, we don't need load the whole database into the main memory.
    * In the data, the item identifiers have to be in [0, n), where n is
    * the number of items.
    *
    * @param file the input file. Each row is a item set, which
    *             may have different length. The item identifiers have to be in [0, n),
    *             where n is the number of items. Item set should NOT contain duplicated
    *             items. Note that it is reordered after the call.
    * @param minSupport the required minimum support of item sets in terms
    *                   of frequency.
    * @param confidence the confidence threshold for association rules.
    * @param output a print stream for output of association rules.
    * @return the number of discovered association rules.
    */
  def arm(file: String, minSupport: Int, confidence: Double, output: PrintStream): Long = {
    time {
      val items = collection.mutable.Map[Int, Int]().withDefaultValue(0)
      Source.fromFile(file).getLines.foreach { line =>
        line.trim.split("\\s+").foreach { item =>
          val i = item.toInt
          items(i) = items(i) + 1
        }
      }

      val n = items.keySet.foldLeft(0)(Math.max) + 1
      val frequency = new Array[Int](n)
      items.toSeq.foreach { case (item, count) => frequency(item) = count }

      val rule = new ARM(frequency, minSupport)
      Source.fromFile(file).getLines.foreach { line =>
        rule.add(line.trim.split("\\s+").map(_.toInt).toArray)
      }

      rule.learn(confidence, new PrintStream(output))
    }
  }
  /** Association Rule Mining. This method scans data
    * twice. We first scan the database to obtains the frequency of
    * single items. Then we scan the data again to construct the FP-Tree, which
    * is a compressed form of data.
    * In this way, we don't need load the whole database into the main memory.
    * In the data, the item identifiers have to be in [0, n), where n is
    * the number of items.
    *
    * @param file the input file of item sets. Each row is a item set, which
    *                 may have different length. The item identifiers have to be in [0, n),
    *                 where n is the number of items.
    * @param minSupport the required minimum support of item sets in terms
    *                   of frequency.
    * @param confidence the confidence threshold for association rules.
    * @param output the output file.
    * @return the number of discovered association rules.
    */
  def arm(file: String, minSupport: Int, confidence: Double, output: String): Long = {
    time {
      arm(file, minSupport, confidence, new PrintStream(output))
    }
  }
}