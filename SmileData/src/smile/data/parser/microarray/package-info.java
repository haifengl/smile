/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

/**
 * Parsers for microarray gene expression datasets. A DNA microarray is a
 * collection of microscopic DNA spots attached to a solid surface. Scientists
 * use DNA microarrays to measure the expression levels of large numbers of
 * genes simultaneously or to genotype multiple regions of a genome. Each DNA
 * spot contains picomoles (10?12 moles) of a specific DNA sequence, known as
 * probes (or reporters). These can be a short section of a gene or other DNA
 * element that are used to hybridize a cDNA or cRNA sample (called target)
 * under high-stringency conditions. Probe-target hybridization is usually
 * detected and quantified by detection of fluorophore-, silver-, or
 * chemiluminescence-labeled targets to determine relative abundance of nucleic
 * acid sequences in the target. Since an array can contain tens of thousands
 * of probes, a microarray experiment can accomplish many genetic tests in
 * parallel. Therefore arrays have dramatically accelerated many types of
 * investigation.
 * <p>
 * In standard microarrays, the probes are synthesized and then attached via
 * surface engineering to a solid surface by a covalent bond to a chemical
 * matrix (via epoxy-silane, amino-silane, lysine, polyacrylamide or others).
 * The solid surface can be glass or a silicon chip, in which case they are
 * colloquially known as an Affy chip when an Affymetrix chip is used.
 * Other microarray platforms, such as Illumina, use microscopic beads,
 * instead of the large solid support. Alternatively, microarrays can be
 * constructed by the direct synthesis of oligonucleotide probes on solid
 * surfaces. DNA arrays are different from other types of microarray only
 * in that they either measure DNA or use DNA as part of its detection system.
 * <p>
 * DNA microarrays can be used to measure changes in expression levels,
 * to detect single nucleotide polymorphisms (SNPs), or to genotype or
 * resequence mutant genomes (see uses and types section). Microarrays
 * also differ in fabrication, workings, accuracy, efficiency, and cost
 * (see fabrication section). Additional factors for microarray experiments
 * are the experimental design and the methods of analyzing the data.
 * 
 * @author Haifeng Li
 */
package smile.data.parser.microarray;
