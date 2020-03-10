// Benchmark on Airline data
// https://github.com/szilard/benchm-ml

import smile.data.*;
import smile.data.formula.*;
import smile.data.measure.*;
import smile.data.type.*;
import smile.io.*;
import smile.base.cart.SplitRule;
import smile.classification.*;
import smile.validation.*;
import org.apache.commons.csv.CSVFormat;

var airport = new NominalScale("ABE", "ABI", "ABQ", "ABY", "ACK", "ACT",
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
  "YKM", "YUM");

var schema = DataTypes.struct(
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
);

var format = CSVFormat.DEFAULT.withFirstRecordAsHeader();
var train = Read.csv(smile.util.Paths.getTestData("airline/train-1m.csv"), format, schema);
var test = Read.csv(smile.util.Paths.getTestData("airline/test.csv"), format, schema);

var formula = Formula.lhs("dep_delayed_15min");
var testy = formula.y(test).toIntArray();

System.out.println("----- train data -----");
System.out.println(train);
System.out.println("----- test  data -----");
System.out.println(test);

// The data is unbalanced. Large positive class weight of should improve sensitivity.
int[] classWeight = {4, 1};

// Random Forest
System.out.println("Training Random Forest of 500 trees...");
var forest = RandomForest.fit(formula, train, 500, 2, SplitRule.GINI, 20, train.size()/5, 5, 0.632, classWeight);
System.out.format("OOB error rate = %.2f%%%n", (100.0 * forest.error()));

var leafs = Arrays.stream(forest.trees()).mapToInt(tree -> tree.root().leafs());
System.out.format("Tree Leaf Nodes: %s%n", leafs.summaryStatistics());

var depth = Arrays.stream(forest.trees()).mapToInt(tree -> tree.root().depth());
System.out.format("Tree Depth: %s%n", depth.summaryStatistics());

var y = new int[testy.length]
var prob = new double[testy.length]
var posteriori = new double[2]
for (int i = 0; i < y.length; i++) {
  y[i] = forest.predict(test.get(i), posteriori);
  prob[i] = posteriori[1];
}

System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, y)));
System.out.format("Sensitivity/Recall = %.2f%%%n", (100.0 * Sensitivity.of(testy, y)));
System.out.format("Specificity = %.2f%%%n", (100.0 * Specificity.of(testy, y)));
System.out.format("Precision = %.2f%%%n", (100.0 * Precision.of(testy, y)));
System.out.format("F1-Score = %.2f%%%n", (100.0 * FMeasure.of(testy, y)));
System.out.format("F2-Score = %.2f%%%n", (100.0 * new FMeasure(2).measure(testy, y)));
System.out.format("F0.5-Score = %.2f%%%n", (100.0 * new FMeasure(0.5).measure(testy, y)));
System.out.format("AUC = %.2f%%%n", (100.0 * AUC.of(testy, prob)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, y));

// Gradient Tree Boost
System.out.println("Training Gradient Tree Boost of 300 trees...");
var gbm = GradientTreeBoost.fit(formula, train, 300, 20, 6, 5, 0.1, 0.632);
for (int i = 0; i < y.length; i++) {
  y[i] = gbm.predict(test.get(i), posteriori);
  prob[i] = posteriori[1];
}

System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, y)));
System.out.format("Sensitivity/Recall = %.2f%%%n", (100.0 * Sensitivity.of(testy, y)));
System.out.format("Specificity = %.2f%%%n", (100.0 * Specificity.of(testy, y)));
System.out.format("Precision = %.2f%%%n", (100.0 * Precision.of(testy, y)));
System.out.format("F1-Score = %.2f%%%n", (100.0 * FMeasure.of(testy, y)));
System.out.format("F2-Score = %.2f%%%n", (100.0 * new FMeasure(2).measure(testy, y)));
System.out.format("F0.5-Score = %.2f%%%n", (100.0 * new FMeasure(0.5).measure(testy, y)));
System.out.format("AUC = %.2f%%%n", (100.0 * AUC.of(testy, prob)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, y));

// AdaBoost
System.out.println("Training AdaBoost of 300 trees...");
var ada = AdaBoost.fit(formula, train, 300, 20, 6, 1);
for (int i = 0; i < y.length; i++) {
  y[i] = ada.predict(test.get(i), posteriori);
  prob[i] = posteriori[1];
}

System.out.format("Accuracy = %.2f%%%n", (100.0 * Accuracy.of(testy, y)));
System.out.format("Sensitivity/Recall = %.2f%%%n", (100.0 * Sensitivity.of(testy, y)));
System.out.format("Specificity = %.2f%%%n", (100.0 * Specificity.of(testy, y)));
System.out.format("Precision = %.2f%%%n", (100.0 * Precision.of(testy, y)));
System.out.format("F1-Score = %.2f%%%n", (100.0 * FMeasure.of(testy, y)));
System.out.format("F2-Score = %.2f%%%n", (100.0 * new FMeasure(2).measure(testy, y)));
System.out.format("F0.5-Score = %.2f%%%n", (100.0 * new FMeasure(0.5).measure(testy, y)));
System.out.format("AUC = %.2f%%%n", (100.0 * AUC.of(testy, prob)));
System.out.format("Confusion Matrix: %s%n", ConfusionMatrix.of(testy, y));

/exit
