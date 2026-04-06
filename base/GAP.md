# Genetic Algorithm User Guide

This guide explains how to use the `smile.gap` package for genetic algorithms in SMILE.

## Overview

`smile.gap` provides:

- `Chromosome<T>`: the contract for candidate solutions.
- `LamarckianChromosome<T>`: chromosome with optional local-search steps.
- `GeneticAlgorithm<T>`: the evolution engine.
- `BitString`: built-in binary chromosome implementation.
- `Selection`: parent-selection strategies.
- `Crossover`: crossover strategies for `BitString`.
- `Fitness<T>`: fitness scoring contract (used by `BitString`).

The framework assumes **higher fitness is better**.

## Core Concepts

### Chromosome

A chromosome must implement:

- `fitness()`: returns quality score.
- `newInstance()`: creates a random chromosome.
- `crossover(T other)`: creates two children.
- `mutate()`: applies mutation.
- `compareTo(...)`: orders by fitness (ascending for internal sorting).

### Fitness

For `BitString`, fitness is typically provided via `Fitness<BitString>`:

- `double score(BitString chromosome)`

### Selection

Available selection strategies:

- `Selection.RouletteWheel()`
- `Selection.ScaledRouletteWheel()`
- `Selection.Rank()`
- `Selection.Tournament(size, probability)` (default choice in many scenarios)

### Crossover (`BitString` only)

Available crossover types:

- `Crossover.SINGLE_POINT`
- `Crossover.TWO_POINT`
- `Crossover.UNIFORM`

## Quick Start (BitString)

```java
import smile.gap.*;

public class GapQuickStart {
    static class OnesFitness implements Fitness<BitString> {
        @Override
        public double score(BitString chromosome) {
            int sum = 0;
            for (byte bit : chromosome.bits()) {
                sum += bit;
            }
            return sum;
        }
    }

    public static void main(String[] args) {
        int populationSize = 100;
        int chromosomeLength = 64;

        BitString[] seeds = new BitString[populationSize];
        Fitness<BitString> fitness = new OnesFitness();
        for (int i = 0; i < populationSize; i++) {
            seeds[i] = new BitString(
                    chromosomeLength,
                    fitness,
                    Crossover.TWO_POINT,
                    0.9,   // crossover rate
                    0.01   // mutation rate
            );
        }

        GeneticAlgorithm<BitString> ga = new GeneticAlgorithm<>(
                seeds,
                Selection.Tournament(3, 0.95),
                1 // elitism
        );

        BitString best = ga.evolve(200, chromosomeLength);
        System.out.println("Best fitness: " + best.fitness());
        System.out.println("Best chromosome: " + best);
    }
}
```

## Using Your Own Chromosome Type

If `BitString` is not suitable, implement `Chromosome<T>` directly.

Checklist:

- Make `fitness()` deterministic for a fixed chromosome state.
- Ensure `crossover()` returns **new child objects**.
- Keep mutation and crossover valid for your domain constraints.
- Implement `compareTo` so better chromosomes compare larger.

## Lamarckian Search

If your chromosome supports local improvement, implement `LamarckianChromosome<T>`.

`GeneticAlgorithm` can apply local search steps during evaluation:

```java
GeneticAlgorithm<MyChromosome> ga = new GeneticAlgorithm<>(seeds);
ga.setLocalSearchSteps(5);  // 0 disables local search
MyChromosome best = ga.evolve(500);
```

## Constructor and Runtime Constraints

- Population size must be **greater than 1**.
- `elitism` must satisfy: `0 <= elitism < population.length`.
- `generation` in `evolve(...)` must be greater than 0.
- `setLocalSearchSteps(t)` requires `t >= 0`.
- `BitString` requires valid rates in `[0, 1]`.

## Choosing Parameters

Reasonable defaults to start with:

- Selection: `Tournament(3, 0.95)`
- Crossover rate: `0.8 - 0.95`
- Mutation rate: `0.005 - 0.02`
- Elitism: `1 - 2`
- Population size: `50 - 200` (problem dependent)

Tune these based on convergence speed and solution quality.

## Stopping Criteria

Use one or both:

- Fixed generations: `evolve(maxGenerations)`
- Fitness threshold: `evolve(maxGenerations, threshold)`

The second form stops early if the threshold is reached.

## Logging

`GeneticAlgorithm` logs per-generation fitness statistics (best and average).
This is useful for tracking convergence and diagnosing stagnation.

## Common Pitfalls

- Returning shared parent instances from custom `crossover()` instead of new children.
- Mutating chromosomes without invalidating cached fitness in custom implementations.
- Using fitness values with poor scaling (can reduce selection pressure).
- Very low mutation leading to premature convergence.
- Very high mutation turning search into near-random walk.

## Testing Recommendations

When adding a custom chromosome, test:

- Constructor preconditions.
- `crossover()` shape and invariants.
- `mutate()` behavior under fixed RNG seeds.
- `fitness()` consistency/caching behavior.
- End-to-end convergence on a small synthetic objective.

## See Also

- Package docs: `base/src/main/java/smile/gap/package-info.java`
- Built-in tests: `base/src/test/java/smile/gap`
