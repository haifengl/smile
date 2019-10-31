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
 
package smile.benchmark

import scala.language.postfixOps
import smile.base.cart.SplitRule
import smile.classification._
import smile.data.summary
import smile.data.formula._
import smile.data.`type`._
import smile.data.measure.NominalScale
import smile.read
import smile.util.Paths
import smile.validation._

/**
 *
 * @author Haifeng Li
 */
object Airline {

  def main(args: Array[String]): Unit = {
    benchmark
  }

  def benchmark: Unit = {
    benchmark("1m")
  }

  def benchmark(dataSize: String): Unit = {
    println(s"Airline-${dataSize}")

    // Although read.csv can infer the schema and then we can factorize
    // strings to nominal scale, the test data contains the carriers that
    // are not in training data.
    val airport = new NominalScale("ABE", "ABI", "ABQ", "ABY", "ACK", "ACT",
      "ACV", "ACY", "ADK", "ADQ", "AEX", "AGS", "AKN", "ALB", "ALO", "AMA", "ANC",
      "APF", "ASE", "ATL", "ATW", "AUS", "AVL", "AVP", "AZO", "BDL", "BET", "BFL",
      "BGM", "BGR", "BHM", "BIL", "BIS", "BJI", "BLI", "BMI", "BNA", "BOI", "BOS",
      "BPT", "BQK", "BQN", "BRO", "BRW", "BTM", "BTR", "BTV", "BUF", "BUR", "BWI",
      "BZN", "CAE", "CAK", "CDC", "CDV", "CEC", "CHA", "CHO", "CHS", "CIC", "CID",
      "CKB", "CLD", "CLE", "CLL", "CLT", "CMH", "CMI", "CMX", "COD", "COS", "CPR",
      "CRP", "CRW", "CSG", "CVG", "CWA", "CYS", "DAB", "DAL", "DAY", "DBQ", "DCA",
      "DEN", "DFW", "DHN", "DLG", "DLH", "DRO", "DSM", "DTW", "EAU", "EGE", "EKO",
      "ELM", "ELP", "ERI", "EUG", "EVV", "EWN", "EWR", "EYW", "FAI", "FAR", "FAT",
      "FAY", "FCA", "FLG", "FLL", "FLO", "FMN", "FNT", "FSD", "FSM", "FWA", "GEG",
      "GFK", "GGG", "GJT", "GNV", "GPT", "GRB", "GRK", "GRR", "GSO", "GSP", "GST",
      "GTF", "GTR", "GUC", "HDN", "HHH", "HKY", "HLN", "HNL", "HOU", "HPN", "HRL",
      "HSV", "HTS", "HVN", "IAD", "IAH", "ICT", "IDA", "ILG", "ILM", "IND", "INL",
      "IPL", "ISO", "ISP", "ITO", "IYK", "JAC", "JAN", "JAX", "JFK", "JNU", "KOA",
      "KTN", "LAN", "LAR", "LAS", "LAW", "LAX", "LBB", "LBF", "LCH", "LEX", "LFT",
      "LGA", "LGB", "LIH", "LIT", "LNK", "LRD", "LSE", "LWB", "LWS", "LYH", "MAF",
      "MBS", "MCI", "MCN", "MCO", "MDT", "MDW", "MEI", "MEM", "MFE", "MFR", "MGM",
      "MHT", "MIA", "MKE", "MLB", "MLI", "MLU", "MOB", "MOD", "MOT", "MQT", "MRY",
      "MSN", "MSO", "MSP", "MSY", "MTH", "MTJ", "MYR", "OAJ", "OAK", "OGD", "OGG",
      "OKC", "OMA", "OME", "ONT", "ORD", "ORF", "OTZ", "OXR", "PBI", "PDX", "PFN",
      "PHF", "PHL", "PHX", "PIA", "PIE", "PIH", "PIT", "PLN", "PMD", "PNS", "PSC",
      "PSE", "PSG", "PSP", "PUB", "PVD", "PVU", "PWM", "RAP", "RCA", "RDD", "RDM",
      "RDU", "RFD", "RHI", "RIC", "RNO", "ROA", "ROC", "ROW", "RST", "RSW", "SAN",
      "SAT", "SAV", "SBA", "SBN", "SBP", "SCC", "SCE", "SDF", "SEA", "SFO", "SGF",
      "SGU", "SHV", "SIT", "SJC", "SJT", "SJU", "SLC", "SLE", "SMF", "SMX", "SNA",
      "SOP", "SPI", "SPS", "SRQ", "STL", "STT", "STX", "SUN", "SUX", "SWF", "SYR",
      "TEX", "TLH", "TOL", "TPA", "TRI", "TTN", "TUL", "TUP", "TUS", "TVC", "TWF",
      "TXK", "TYR", "TYS", "VCT", "VIS", "VLD", "VPS", "WRG", "WYS", "XNA", "YAK",
      "YKM", "YUM")
    val schema = DataTypes.struct(
      new StructField("Month", DataTypes.ByteType, new NominalScale("c-1", "c-2", "c-3", "c-4",
        "c-5", "c-6", "c-7", "c-8", "c-9", "c-10", "c-11", "c-12")),
      new StructField("DayofMonth", DataTypes.ByteType, new NominalScale("c-1", "c-2", "c-3", "c-4",
        "c-5", "c-6", "c-7", "c-8", "c-9", "c-10", "c-11", "c-12", "c-13", "c-14", "c-15", "c-16", "c-17", "c-18",
        "c-19", "c-20", "c-21", "c-22", "c-23", "c-24", "c-25", "c-26", "c-27", "c-28", "c-29", "c-30", "c-31")),
      new StructField("DayOfWeek", DataTypes.ByteType, new NominalScale("c-1", "c-2", "c-3", "c-4",
        "c-5", "c-6", "c-7")),
      new StructField("DepTime", DataTypes.IntegerType),
      new StructField("UniqueCarrier", DataTypes.ByteType, new NominalScale("9E", "AA", "AQ", "AS",
        "B6", "CO", "DH", "DL", "EV", "F9", "FL", "HA", "HP", "MQ", "NW", "OH", "OO", "TZ", "UA", "US", "WN", "XE", "YV")),
      new StructField("Origin", DataTypes.ShortType, airport),
      new StructField("Dest", DataTypes.ShortType, airport),
      new StructField("Distance", DataTypes.IntegerType),
      new StructField("dep_delayed_15min", DataTypes.ByteType, new NominalScale("N", "Y"))
    )

    val formula: Formula = "dep_delayed_15min" ~
    val train = read.csv(Paths.getTestData(s"airline/train-${dataSize}.csv").toString, schema = schema)
    val test = read.csv(Paths.getTestData("airline/test.csv").toString, schema = schema)
    val testy = formula.y(test).toIntArray

    println("----- train data -----")
    println(train)
    println("----- test  data -----")
    println(test)

    // The data is unbalanced. Large positive class weight of should improve sensitivity.
    val classWeight = Array(4, 1)

    // Random Forest
    // Train fewer but deeper trees
    val ntrees = dataSize match {
      case "0.1m" => 500
      case "1m" => 300
      case "10m" => 100
      case _ => 100
    }
    println(s"Training Random Forest of $ntrees trees...")
    val forest = test2soft(formula, train, test) { (formula, data) =>
      randomForest(formula, data, ntrees, subsample = 0.632, classWeight = classWeight)
    }

    val depth = forest.trees.map(_.root.depth.toDouble)
    println("Tree Depth:")
    summary(depth)

    println("OOB error rate = %.2f%%" format (100.0 * forest.error()))

    // Gradient Tree Boost
    println("Training Gradient Tree Boost of 300 trees...")
    test2soft(formula, train, test) { (formula, data) =>
      gbm(formula, train, 300, shrinkage = 0.1, subsample = 0.632)
    }

    // AdaBoost
    println("Training AdaBoost of 300 trees...")
    test2soft(formula, train, test) { (formula, data) =>
      adaboost(formula, train, 300)
    }
  }
}
