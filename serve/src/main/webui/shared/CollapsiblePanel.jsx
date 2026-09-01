/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
import { useCollapsibleSidebar } from './useCollapsibleSidebar'
import './CollapsiblePanel.css'

/**
 * Collapsible sidebar panel with a compact toggle overlaid on the content boundary.
 *
 * @param {'left'|'right'} side which edge of the main content the panel sits on
 */
export default function CollapsiblePanel({
  side = 'left',
  storageKey,
  defaultExpanded = true,
  width = 280,
  className = '',
  ariaLabel,
  children,
}) {
  const [expanded, toggle] = useCollapsibleSidebar(storageKey, defaultExpanded)
  const panelWidth = expanded ? width : 0
  const toggleLabel = expanded ? `Collapse ${ariaLabel}` : `Expand ${ariaLabel}`

  return (
    <div
      className={[
        'collapsible-panel-host',
        `collapsible-panel-host--${side}`,
        expanded ? 'collapsible-panel-host--expanded' : 'collapsible-panel-host--collapsed',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      style={{ width: panelWidth, flexBasis: panelWidth }}
      aria-label={ariaLabel}
    >
      <div className="collapsible-panel__body" aria-hidden={!expanded}>
        {children}
      </div>
      <button
        type="button"
        className="collapsible-panel__toggle"
        onClick={toggle}
        aria-expanded={expanded}
        aria-label={toggleLabel}
        title={toggleLabel}
      >
        <span className="collapsible-panel__chevron" aria-hidden="true">
          {side === 'left' ? (expanded ? '‹' : '›') : expanded ? '›' : '‹'}
        </span>
      </button>
    </div>
  )
}
