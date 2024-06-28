import React from "react";
import './CopyButton.css';

export default function CopyButton({ children }) {
    const [copyOk, setCopyOk] = React.useState(false);

    const iconColor = copyOk ? '#0af20a' : '#ddd';
    const icon = copyOk ? 'fa-check-square' : 'fa-copy';

    const handleClick = (e) => {
        navigator.clipboard.writeText(children[0].props.children[0]);

        setCopyOk(true);
        setTimeout(() => {
            setCopyOk(false);
        }, 500);
    }

    return (
        <div className="copy-btn">
            <i className={`fas ${icon}`} onClick={handleClick} style={{color: iconColor}} />
        </div>
    )
}
