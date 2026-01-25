/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
import React from "react";

function Sidebar({ models, onSelect, selectedModel }) {
  return (
    <div className="sidebar">
      <h2>Models</h2>
      <ul>
        {models.map((model) => (
          <li
            key={model}
            className={selectedModel === model ? "active" : ""}
            onClick={() => onSelect(model)}
          >
            {model}
          </li>
        ))}
      </ul>
    </div>
  );
}

export default Sidebar;
