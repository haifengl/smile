;
;   SMILE is free software: you can redistribute it and/or modify it
;   under the terms of the GNU General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   SMILE is distributed in the hope that it will be useful, but
;   WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU General Public License for more details.
;
;   You should have received a copy of the GNU General Public License
;   along with SMILE. If not, see <https://www.gnu.org/licenses/>.

(ns smile.association
  "Association Rule Mining"
  {:author "Haifeng Li"}
  (:import [smile.association FPTree FPGrowth ARM]
           [java.util.stream Stream]))

(defn fptree
  "Builds an FP-tree.

   `min-support` is the required minimum support of item sets in terms of frequency.
   `supplier` is a supplier that returns a stream of item sets. Each item set
   may have different length. The item identifiers have to be in [0, n),
   where n is the number of items. Item set should NOT contain duplicated items."
  [min-support supplier]
  (FPTree/of min-support supplier))

(defn fpgrowth
  "Frequent item set mining based on the FP-growth algorithm.

   `min-support` is the required minimum support of item sets in terms of frequency.
   `itemsets` is the item set database. Each row is an item set, which may have
   different length. The item identifiers have to be in [0, n), where n is the
   number of items. Item set should NOT contain duplicated items."
  [min-support itemsets]
  (let [tree (FPTree/of min-support itemsets)]
    (FPGrowth/apply tree)))

(defn arm
  "Association rule mining.

   `min-confidence` is the minimum confidence of rules.
   `tree` is the FP-tree."
  [min-confidence tree]
  (ARM/apply min-confidence tree))