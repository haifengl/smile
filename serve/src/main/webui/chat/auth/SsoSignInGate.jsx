/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 */
import SsoIcon from '../nav/SsoIcon'
import './SsoSignInGate.css'

export default function SsoSignInGate({ label = 'SSO', error = null, onSignIn }) {
  return (
    <div className="sso-gate" role="region" aria-label="Single Sign-On">
      <div className="sso-gate__card">
        <div className="sso-gate__icon-wrap">
          <SsoIcon className="sso-gate__icon" />
        </div>
        <h2 className="sso-gate__title">
          {error ? 'Authentication Unavailable' : 'Sign in to SMILE Serve'}
        </h2>
        {error ? (
          <p className="sso-gate__error" role="alert">
            {error}
          </p>
        ) : (
          <>
            <p className="sso-gate__subtitle">
              Your organization requires Single Sign-On to access chat and inference models.
            </p>
            <button type="button" className="sso-gate__button" onClick={onSignIn}>
              <SsoIcon className="sso-gate__button-icon" />
              Sign in with {label}
            </button>
          </>
        )}
      </div>
    </div>
  )
}
