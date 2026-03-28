/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * it under the terms of the GNU General Public License as published by
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
import React from 'react';
import './CopyButton.css';

export default function CopyButton({ children }) {
    const [clicked, setClicked] = React.useState(false);

    const handleClick = (e) => {
        // clipboard requires a secure origin — either HTTPS or localhost
        if (!clicked && navigator.clipboard) {
            navigator.clipboard.writeText(children.props.children);

            setClicked(true);
            setTimeout(() => {
                setClicked(false);
            }, 500);
        }
    }

    const button = clicked ?
          <svg xmlns="http://www.w3.org/2000/svg" // check-square
               viewBox="0 0 512 512"
               fill="#4BB543"
               width="16"
               height="16"
          >
               <path d="M400 32H48C21.49 32 0 53.49 0 80v352c0 26.51 21.49 48 48 48h352c26.51 0 48-21.49 48-48V80c0-26.51-21.49-48-48-48zm0 400H48V80h352v352zm-35.864-241.724L191.547 361.48c-4.705 4.667-12.303 4.637-16.97-.068l-90.781-91.516c-4.667-4.705-4.637-12.303.069-16.971l22.719-22.536c4.705-4.667 12.303-4.637 16.97.069l59.792 60.277 141.352-140.216c4.705-4.667 12.303-4.637 16.97.068l22.536 22.718c4.667 4.706 4.637 12.304-.068 16.971z"/>
          </svg>
        : <svg xmlns="http://www.w3.org/2000/svg" // clone
              viewBox="0 0 512 512"
              fill="#ddd"
              width="16"
              height="16"
          >
              <path d="M464 0H144c-26.51 0-48 21.49-48 48v48H48c-26.51 0-48 21.49-48 48v320c0 26.51 21.49 48 48 48h320c26.51 0 48-21.49 48-48v-48h48c26.51 0 48-21.49 48-48V48c0-26.51-21.49-48-48-48zM362 464H54a6 6 0 0 1-6-6V150a6 6 0 0 1 6-6h42v224c0 26.51 21.49 48 48 48h224v42a6 6 0 0 1-6 6zm96-96H150a6 6 0 0 1-6-6V54a6 6 0 0 1 6-6h308a6 6 0 0 1 6 6v308a6 6 0 0 1-6 6z"/>
          </svg>;


    return (
        <div className="copy-btn" onClick={handleClick}>
            {navigator.clipboard && button}
        </div>
    )
}
