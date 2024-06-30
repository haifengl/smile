import React from 'react';
import Check from '../assets/check-square.svg?react';
import Clone from '../assets/clone.svg?react';
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

    const button = clicked ? <Check height='16px' fill='#0af20a' />
        : <Clone height='16px' onClick={handleClick} fill='#ddd' />;

    return (
        <div className="copy-btn">
            {navigator.clipboard && button}
        </div>
    )
}
