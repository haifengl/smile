/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
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

package smile.gap;

import smile.math.Math;

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
     * The types of crossover operation.
     */
    public enum Crossover {

        /**
         * Single point crossover - one crossover point is selected, binary
         * string from beginning of chromosome to the crossover point is copied
         * from one parent, the rest is copied from the second parent.
         */
        SINGLE_POINT,
        /**
         * Two point crossover - two crossover point are selected, binary string
         * from beginning of chromosome to the first crossover point is copied
         * from one parent, the part from the first to the second crossover
         * point is copied from the second parent and the rest is copied from
         * the first parent.
         */
        TWO_POINT,
        /**
         * Uniform crossover - bits are randomly copied from the first or from
         * the second parent.
         */
        UNIFORM,
    }

    /**
     * The length of chromosome.
     */
    public final int length;
    /**
     * Binary encoding of chromosome.
     */
    private int[] bits;
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
        if (length <= 0) {
            throw new IllegalArgumentException("Invalid bit string length: " + length);
        }
        
        this.length = length;
        this.measure = measure;

        bits = new int[length];
        for (int i = 0; i < length; i++) {
            bits[i] = Math.random() > 0.5 ? 1 : 0;
        }        
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
        if (length <= 0) {
            throw new IllegalArgumentException("Invalid bit string length: " + length);
        }
        
        if (crossoverRate < 0.0 || crossoverRate > 1.0) {
            throw new IllegalArgumentException("Invalid crossover rate: " + crossoverRate);
        }
        
        if (mutationRate < 0.0 || mutationRate > 1.0) {
            throw new IllegalArgumentException("Invalid mutation rate: " + mutationRate);
        }
        
        this.length = length;
        this.measure = measure;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.crossover = crossover;

        bits = new int[length];
        for (int i = 0; i < length; i++) {
            bits[i] = Math.random() > 0.5 ? 1 : 0;
        }
    }

    /**
     * Constructor. Two point cross over, cross over rate 0.9, mutation rate 0.01.
     * @param bits the bit string of chromosome.
     * @param measure the fitness measure.
     */
    public BitString(int[] bits, FitnessMeasure<BitString> measure) {
        this.bits = bits;
        this.length = bits.length;
        this.measure = measure;
    }

    /**
     * Constructor.
     * @param bits the bit string of chromosome.
     * @param measure the fitness measure.
     * @param crossover the strategy of crossover operation.
     * @param crossoverRate the crossover rate.
     * @param mutationRate the mutation rate.
     */
    public BitString(int[] bits, FitnessMeasure<BitString> measure, Crossover crossover, double crossoverRate, double mutationRate) {
        this.bits = bits;
        this.length = bits.length;
        this.measure = measure;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.crossover = crossover;
    }

    /**
     * Returns the bit string of chromosome.
     */
    public int[] bits() {
        return bits;
    }

    @Override
    public int compareTo(Chromosome o) {
        return (int) Math.signum(fitness - o.fitness());
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

    @Override
    public BitString[] crossover(Chromosome another) {
        if (!(another instanceof BitString)) {
            throw new IllegalArgumentException("The input parent is NOT bit string chromosome.");
        }

        BitString mother = (BitString) another;
        BitString[] offsprings = new BitString[2];
        if (Math.random() < crossoverRate) {
            switch (crossover) {
                case SINGLE_POINT:
                    singlePointCrossover(this, mother, offsprings);
                    break;
                case TWO_POINT:
                    twoPointCrossover(this, mother, offsprings);
                    break;
                case UNIFORM:
                    uniformCrossover(this, mother, offsprings);
                    break;
            }
        } else {
            offsprings[0] = this;
            offsprings[1] = mother;
        }

        return offsprings;
    }

    /**
     * Single point crossover.
     */
    private void singlePointCrossover(BitString father, BitString mother, BitString[] offsprings) {
        int point = 0; // crossover point
        while (point == 0) {
            point = Math.randomInt(length);
        }

        int[] son = new int[length];
        System.arraycopy(father.bits, 0, son, 0, point);
        System.arraycopy(mother.bits, point, son, point, length - point);

        int[] daughter = new int[length];
        System.arraycopy(mother.bits, 0, daughter, 0, point);
        System.arraycopy(father.bits, point, daughter, point, length - point);

        offsprings[0] = new BitString(son, measure, crossover, crossoverRate, mutationRate);
        offsprings[1] = new BitString(daughter, measure, crossover, crossoverRate, mutationRate);
    }

    /**
     * Two point crossover.
     */
    private void twoPointCrossover(BitString father, BitString mother, BitString[] offsprings) {
        int point1 = 0; // first crossover point
        while (point1 == 0 || point1 == length - 1) {
            point1 = Math.randomInt(length);
        }

        int point2 = 0; // second crossover point
        while (point2 == point1 || point2 == 0 || point2 == length - 1) {
            point2 = Math.randomInt(length);
        }
        
        if (point2 < point1) {
            int p = point1;
            point1 = point2;
            point2 = p;
        }

        int[] son = new int[length];
        System.arraycopy(father.bits, 0, son, 0, point1);
        System.arraycopy(mother.bits, point1, son, point1, point2 - point1);
        System.arraycopy(father.bits, point2, son, point2, length - point2);

        int[] daughter = new int[length];
        System.arraycopy(mother.bits, 0, daughter, 0, point1);
        System.arraycopy(father.bits, point1, daughter, point1, point2 - point1);
        System.arraycopy(mother.bits, point2, daughter, point2, length - point2);
        
        offsprings[0] = new BitString(son, measure, crossover, crossoverRate, mutationRate);
        offsprings[1] = new BitString(daughter, measure, crossover, crossoverRate, mutationRate);
    }

    /**
     * Uniform crossover.
     */
    private void uniformCrossover(BitString father, BitString mother, BitString[] offsprings) {
        int[] son = new int[length];
        int[] daughter = new int[length];

        for (int i = 0; i < length; i++) {
            if (Math.random() < 0.5) {
                son[i] = father.bits[i];
                daughter[i] = mother.bits[i];
            } else {
                son[i] = mother.bits[i];
                daughter[i] = father.bits[i];
            }
        }

        offsprings[0] = new BitString(son, measure, crossover, crossoverRate, mutationRate);
        offsprings[1] = new BitString(daughter, measure, crossover, crossoverRate, mutationRate);
    }

    @Override
    public void mutate() {
        for (int i = 0; i < length; i++) {
            if (Math.random() < mutationRate) {
                bits[i] ^= 1;
            }
        }
    }
}
