/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

/**
 * A taxonomy is a tree of terms (concepts) where leaves
 * must be named but intermediary nodes can be anonymous.
 * Concept is a set of synonyms, i.e. group of words that are roughly
 * synonymous in a given context.
 * The distance between two concepts a and b is defined by the length of the
 * path from a to their lowest common ancestor and then to b.
 *
 * @author Haifeng Li
 */
package smile.taxonomy;
