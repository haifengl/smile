;   Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
;
;   Smile is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Lesser General Public License as
;   published by the Free Software Foundation, either version 3 of
;   the License, or (at your option) any later version.
;
;   Smile is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;   GNU Lesser General Public License for more details.
;
;   You should have received a copy of the GNU Lesser General Public License
;   along with Smile.  If not, see <https://www.gnu.org/licenses/>.

 (ns smile.ai
  "Main namespace for REPL, referring to all public vars of
  other namespaces except that smile.regression :as regression
  to avoid name conflicts with smile.classifictation."
  {:author "Haifeng Li"}
  (:require [smile.io :refer :all]
            [smile.classification :refer :all]
            [smile.regression :as regression]
            [smile.clustering :refer :all]
            [smile.manifold :refer :all]))
