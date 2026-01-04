// Statistical functions, distributions, and hypothesis test

import java.util.stream.*;
import smile.stat.*;
import smile.stat.distribution.*;
import smile.stat.hypothesis.*;
import static smile.math.MathEx.*;

double[] x = {0.53236606, -1.36750258, -1.47239199, -0.12517888, -1.24040594, 1.90357309,
             -0.54429527, 2.22084140, -1.17209146, -0.68824211, -1.75068914, 0.48505896,
              2.75342248, -0.90675303, -1.05971929, 0.49922388, -1.23214498, 0.79284888,
              0.85309580, 0.17903487, 0.39894754, -0.52744720, 0.08516943, -1.93817962,
              0.25042913, -0.56311389, -1.08608388, 0.11912253, 2.87961007, -0.72674865,
              1.11510699, 0.39970074, 0.50060532, -0.82531807, 0.14715616, -0.96133601,
             -0.95699473, -0.71471097, -0.50443258, 0.31690224, 0.04325009, 0.85316056,
              0.83602606, 1.46678847, 0.46891827, 0.69968175, 0.97864326, 0.66985742,
             -0.20922486, -0.15265994};

IO.println("mean = " + mean(x));
IO.println("std dev = " + stdev(x));

//--- CELL ---
// Estimate a mixture model of Gaussian, exponential and gamma distribution
var gaussian = new GaussianDistribution(-2.0, 1.0);
var exp = new ExponentialDistribution(0.8);
var gamma = new GammaDistribution(2.0, 3.0);

var data = DoubleStream.concat(
            DoubleStream.concat(
                DoubleStream.generate(gaussian::rand).limit(500),
                DoubleStream.generate(exp::rand).limit(500)),
            DoubleStream.generate(gamma::rand).limit(1000)).toArray();

var a = new Mixture.Component(0.3, new GaussianDistribution(0.0, 1.0));
var b = new Mixture.Component(0.3, new ExponentialDistribution(1.0));
var c = new Mixture.Component(0.4, new GammaDistribution(1.0, 2.0));

var mixture = ExponentialFamilyMixture.fit(data, a, b, c);
IO.println("distribution = " + mixture);
IO.println("mixture mean = " + mixture.mean());
IO.println("mixture variance = " + mixture.variance());
IO.println("mixture std dev = " + mixture.sd());

IO.println("pdf(2) = " + mixture.p(2));
IO.println("cdf(2) = " + mixture.cdf(2));
IO.println("quantile(0.1) = " + mixture.quantile(0.1));
double[] samples = {1.0, 1.1, 0.9, 1.5};
IO.println("log likelihood = " + mixture.logLikelihood(samples));

// Kernel density estimation
var k = new KernelDensity(data);
IO.println("kernel pdf(2) = " + k.p(2));

//--- CELL ---
// Hypothesis Test
// One-sample chi-squared test
// Given the array x containing the observed numbers of events,
// and an array prob containing the expected probabilities of events,
// and given the number of constraints (normally one), a small value
// of p-value indicates a significant difference between the distributions.
int[] bins = {20, 22, 13, 22, 10, 13};
double[] prob = {1.0/6, 1.0/6, 1.0/6, 1.0/6, 1.0/6, 1.0/6};
IO.println(ChiSqTest.test(bins, prob));

// Two-sample chi-squared test
int[] bins1 = {8, 13, 16, 10, 3};
int[] bins2 = {4,  9, 14, 16, 7};
IO.println(ChiSqTest.test(bins1, bins2));

// Independence test
// A contingency table is the frequency distribution of two or more
// categorical variables
int[][] count = { {12, 7}, {5, 7} };
IO.println(ChiSqTest.test(count));

//--- CELL ---
// Kolmogorov–Smirnov Test
// The one-sample K-S test for the null hypothesis that the data set
// is drawn from the given distribution.
IO.println(KSTest.test(x, new GaussianDistribution(0, 1)));

// The two-sample K–S test for the null hypothesis that the data sets
// are drawn from the same distribution.
double[] y = {0.95791391, 0.16203847, 0.56622013, 0.39252941, 0.99126354, 0.65639108,
              0.07903248, 0.84124582, 0.76718719, 0.80756577, 0.12263981, 0.84733360,
              0.85190907, 0.77896244, 0.84915723, 0.78225903, 0.95788055, 0.01849366,
              0.21000365, 0.97951772, 0.60078520, 0.80534223, 0.77144013, 0.28495121,
              0.41300867, 0.51547517, 0.78775718, 0.07564151, 0.82871088, 0.83988694};

IO.println(KSTest.test(x, y));
