/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */

/** Shield-and-lock mark for Enterprise SSO sign-in buttons. */
export default function SsoIcon({ className = '' }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      focusable="false"
    >
      <path
        d="M12 2.5L4.5 5.75V11.5C4.5 16.4 7.7 20.85 12 22C16.3 20.85 19.5 16.4 19.5 11.5V5.75L12 2.5Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
      />
      <rect
        x="9"
        y="11"
        width="6"
        height="4.75"
        rx="1.2"
        stroke="currentColor"
        strokeWidth="1.6"
      />
      <path
        d="M10.25 11V9.5C10.25 8.53 11.03 7.75 12 7.75C12.97 7.75 13.75 8.53 13.75 9.5V11"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  )
}
