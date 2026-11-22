/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
import React, { useCallback, useEffect, useState } from "react";
import Sidebar from "./Sidebar";
import InferenceForm from "./InferenceForm";
import ChatShell from "../chat/ChatShell";
import CollapsiblePanel from "../shared/CollapsiblePanel";
import ThemeToggle from "../shared/ThemeToggle";
import { AuthProvider, useAuth } from "../chat/auth/AuthProvider";
import SsoSignInGate from "../chat/auth/SsoSignInGate";
import { INFER_RESUME_MODEL_KEY } from "../chat/api";
import "./App.css";

/** Maps API {@code kind} to UI panel type. */
function panelType(kind) {
  if (kind === "LLM") return "chat";
  if (kind === "ONNX") return "onnx";
  return "smile";
}

function ServeHeader() {
  return (
    <header className="site-header">
      <div className="site-header__brand">
        <img
          src="/smile.png"
          alt="SMILE"
          className="site-header__logo"
          width="28"
          height="28"
        />
        <span className="site-header__wordmark">SMILE</span>
        <span className="site-header__product">Serve</span>
        <span className="site-header__tagline">
          Statistical Machine Intelligence &amp; Learning Engine
        </span>
      </div>
      <div className="site-header__actions">
        <ThemeToggle />
      </div>
    </header>
  );
}

function AppInner() {
  const {
    loggedIn,
    loading: authLoading,
    ssoRequired,
    oidcLabel,
    authError,
    loginWithOidc,
  } = useAuth();
  const [models, setModels] = useState([]);
  const [selectedModel, setSelectedModel] = useState(null);
  /** Panels kept mounted so switching models preserves chat / form state. */
  const [mountedModels, setMountedModels] = useState([]);

  const blockedBySso = Boolean(!authLoading && (authError || (ssoRequired && !loggedIn)));

  useEffect(() => {
    if (authLoading || authError || (ssoRequired && !loggedIn)) {
      return;
    }
    fetch("/v1/models", { credentials: "include" })
      .then((res) => (res.ok ? res.json() : { data: [] }))
      .catch(() => ({ data: [] }))
      .then((catalog) => {
        const data = Array.isArray(catalog?.data) ? catalog.data : [];
        setModels(
          data
            .filter((m) => m?.id)
            .map((m) => ({
              id: m.id,
              kind: m.kind || "Unknown",
              type: panelType(m.kind),
              version: m.version || null,
              versions: Array.isArray(m.versions) ? m.versions : [],
            }))
        );
      });
  }, [authLoading, authError, ssoRequired, loggedIn]);

  const selectModel = useCallback((model) => {
    setSelectedModel(model);
    if (model?.id) {
      setMountedModels((prev) =>
        prev.some((m) => m.id === model.id) ? prev : [...prev, model]
      );
    }
  }, []);

  useEffect(() => {
    if (!models.length) return;
    const params = new URLSearchParams(window.location.search);
    const queryModelId = params.get("model");
    const resumeId = queryModelId || sessionStorage.getItem(INFER_RESUME_MODEL_KEY);
    if (!resumeId) return;
    sessionStorage.removeItem(INFER_RESUME_MODEL_KEY);
    const model = models.find((m) => m.id === resumeId);
    if (model) {
      selectModel(model);
    }
  }, [models, selectModel]);

  const handleSsoSignIn = useCallback(() => {
    loginWithOidc({ returnTo: "/" });
  }, [loginWithOidc]);

  if (blockedBySso) {
    return (
      <div className="serve-shell">
        <ServeHeader />
        <div className="app">
          <SsoSignInGate
            label={oidcLabel || "SSO"}
            error={authError}
            onSignIn={handleSsoSignIn}
          />
        </div>
      </div>
    );
  }

  const isChat = selectedModel?.type === "chat";

  return (
    <div className="serve-shell">
      <ServeHeader />
      <div className="app">
        <CollapsiblePanel
          side="left"
          storageKey="smile.infer.model-sidebar.expanded"
          defaultExpanded={true}
          width={280}
          className="infer-model-sidebar"
          ariaLabel="Models"
        >
          <Sidebar
            models={models}
            selectedModel={selectedModel}
            onSelect={selectModel}
          />
        </CollapsiblePanel>
        <div className={isChat ? "content content-chat" : "content"}>
          {!selectedModel && (
            <div className="infer-empty-state">
              <p className="toast">Select a model from the sidebar to run inference or chat.</p>
            </div>
          )}
          {mountedModels.map((model) => {
            const active = selectedModel?.id === model.id;
            if (model.type === "chat") {
              return (
                <div key={model.id} className="panel-session" hidden={!active}>
                  <ChatShell embedded model={model.id} title={model.id} />
                </div>
              );
            }
            return (
              <div key={model.id} className="panel-session" hidden={!active}>
                <InferenceForm model={model} />
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function App() {
  return (
    <AuthProvider>
      <AppInner />
    </AuthProvider>
  );
}

export default App;
