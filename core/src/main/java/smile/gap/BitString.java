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

package smile.gap;

import smile.math.MathEx;

/**
 * The standard bit string representation of the solution domain.
 * Here are some general guides on parameter setting.
 * <p>
 * Crossover rate determines how often will be crossover performed. If
 * there is no crossover, offspring is exact copy of parents. If there is a
 * crossover, offspring is made from parts of parents' chromosome. If crossover
 * rate is 100%, then all offspring is made by crossover. If it is 0%, whole
 * new generation is made from exact copies of chromosomes from old population.
 * However, it this does not mean that the new generation is the same because
 * of mutation. Crossover is made in hope that new chromosomes will have good
 * parts of old chromosomes and maybe the new chromosomes will be better.
 * However it is good to leave some part of population survive to next
 * generation. Crossover rate generally should be high, about 80% - 95%.
 * However some results show that for some problems crossover rate about 60% is
 * the best.
 * <p>
 * Mutation rate determines how often will be parts of chromosome mutated.
 * If there is no mutation, offspring is taken after crossover (or copy) without
 * any change. If mutation is performed, part of chromosome is changed.
 * Mutation is made to prevent falling GA into local extreme, but it should not
 * occur very often, because then GA will in fact change to random search.
 * Best rates reported are about 0.5% - 1%.
 * 
 * @author Haifeng Li
 */
public class BitString implements Chromosome {

    /**
     * The length of chromosome.
     */
    public final int length;
    /**
     * Binary encoding of chromosome.
     */
    private byte[] bits;
    /**
     * Mutation rate.
     */
    private double mutationRate = 0.01;
    /**
     * Crossover strategy.
     */
    private Crossover crossover = Crossover.TWO_POINT;
    /**
     * Crossover rate.
     */
    private double crossoverRate = 0.9;
    /**
     * The measure to evaluate the fitness of chromosome.
     */
    private FitnessMeasure<BitString> measure;
    /**
     * The fitness of chromosome.
     */
    private double fitness = Double.NaN;

    /**
     * Constructor. Two point cross over, cross over rate 0.9, mutation rate 0.01.
     * @param length the length of bit string.
     * @param measure the fitness measure.
     */
    public BitString(int length, FitnessMeasure<BitString> measure) {
        this(length, measure, Crossover.TWO_POINT, 0.9, 0.01);
    }
    
    /**
     * Constructor.
     * @param length the length of bit string.
     * @param measure the fitness measure.
     * @param crossover the strategy of crossover operation.
     * @param crossoverRate the crossover rate.
     * @param mutationRate the mutation rate.
     */
    public BitString(int length, FitnessMeasure<BitString> measure, Crossover crossover, double crossoverRate, double mutationRate) {
        this(bits(length), measure, crossover, crossoverRate, mutationRate);
    }

    /**
     * Constructor. Two point cross over, cross over rate 0.9, mutation rate 0.01.
     * @param bits the bit string of chromosome.
     * @param measure the fitness measure.
     */
    public BitString(byte[] bits, FitnessMeasure<BitString> measure) {
        this(bits, measure, Crossover.TWO_POINT, 0.9, 0.01);
    }

    /**
     * Constructor.
     * @param bits the bit string of chromosome.
     * @param measure the fitness measure.
     * @param crossover the strategy of crossover operation.
     * @param crossoverRate the crossover rate.
     * @param mutationRate the mutation rate.
     */
    public BitString(byte[] bits, FitnessMeasure<BitString> measure, Crossover crossover, double crossoverRate, double mutationRate) {
        if (crossoverRate < 0.0 || crossoverRate > 1.0) {
            throw new IllegalArgumentException("Invalid crossover rate: " + crossoverRate);
        }

        if (mutationRate < 0.0 || mutationRate > 1.0) {
            throw new IllegalArgumentException("Invalid mutation rate: " + mutationRate);
        }

        this.bits = bits;
        this.length = bits.length;
        this.measure = measure;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.crossover = crossover;
    }

    /** Generate a random bit string. */
    private static byte[] bits(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("Invalid bit string length: " + length);
        }

        byte[] bits = new byte[length];
        for (int i = 0; i < length; i++) {
            bits[i] = (byte) (MathEx.random() > 0.5 ? 1 : 0);
        }

        return bits;
    }

    /** Returns the length of bit string. */
    public int length() {
        return length;
    }

    /**
     * Returns the bit string of chromosome.
     */
    public byte[] bits() {
        return bits;
    }

    @Override
    public int compareTo(Chromosome o) {
        return Double.compare(fitness, o.fitness());
    }
    
    @Override
    public double fitness() {
        if (Double.isNaN(fitness)) {
            fitness = measure.fit(this);
        }

        return fitness;
    }

    @Override
    public BitString newInstance() {
        return new BitString(length, measure, crossover, crossoverRate, mutationRate);
    }

    /** Creates a new instance with given bits. */
    public BitString newInstance(byte[] bits) {
        return new BitString(bits, measure, crossover, crossoverRate, mutationRate);
    }

    @Override
    public BitString[] crossover(Chromosome another) {
        if (!(another instanceof BitString)) {
            throw new IllegalArgumentException("NOT a BitString chromosome.");
        }

        BitString mother = (BitString) another;

        if (MathEx.random() < crossoverRate) {
            return crossover.apply(this, mother);
        } else {
            BitString[] offsprings = {this, mother};
            return offsprings;
        }
    }

    @Override
    public void mutate() {
        for (int i = 0; i < length; i++) {
            if (MathEx.random() < mutationRate) {
                bits[i] ^= 1;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (byte b : bits) {
            sb.append(b);
        }
        return sb.toString();
    }
}
