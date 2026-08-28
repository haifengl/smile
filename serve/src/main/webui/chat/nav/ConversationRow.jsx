/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import './ConversationRow.css'

export default function ConversationRow({
  conversation,
  active,
  onSelect,
  onPin,
  onRename,
  onDelete,
}) {
  const title = conversation.title || 'New chat'

  return (
    <div className={`conv-row ${active ? 'conv-row--active' : ''}`}>
      <button type="button" className="conv-row__title" onClick={() => onSelect(conversation)}>
        {title}
      </button>
      <div className="conv-row__actions">
        <button
          type="button"
          className="conv-row__icon"
          title={conversation.pinned ? 'Unpin' : 'Pin'}
          onClick={() => onPin(conversation)}
        >
          {conversation.pinned ? '★' : '☆'}
        </button>
        <button
          type="button"
          className="conv-row__icon"
          title="Rename"
          onClick={() => onRename(conversation)}
        >
          ✎
        </button>
        <button
          type="button"
          className="conv-row__icon conv-row__icon--danger"
          title="Delete"
          onClick={() => onDelete(conversation)}
        >
          ×
        </button>
      </div>
    </div>
  )
}
