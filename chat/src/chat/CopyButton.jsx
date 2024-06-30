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

    /* css based, working but throw warnings. src must be specified, other <img>
       won't show. Meanwhile, it must be a wrong path so that <img> shows only
       the background that already draws the icon as background mask.
    const icon = clicked ? 'check' : 'clone';
    return (
        <div className="copy-btn">
            {navigator.clipboard &&
                <img src='../assets/clone.svg' className={icon} onClick={handleClick} />
            }
        </div>
    )
    */
    const button = clicked ? <Check className='icon' fill='#0af20a' />
        : <Clone className='icon' onClick={handleClick} fill='#ddd' />;

    return (
        <div className="copy-btn">
            {navigator.clipboard && button}
        </div>
    )
}
