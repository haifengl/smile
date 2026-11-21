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
import { useCallback, useEffect, useState } from "react";

const STORAGE_KEY = "smile-theme";
const CHANGE_EVENT = "smile-theme-change";

function readTheme() {
  if (typeof document === "undefined") {
    return "light";
  }
  const attr = document.documentElement.getAttribute("data-theme");
  if (attr === "dark" || attr === "light") {
    return attr;
  }
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "dark" || stored === "light") {
      return stored;
    }
  } catch {
    // Ignore storage failures (private mode / restricted origin)
  }
  return "light";
}

function applyTheme(next) {
  if (typeof document !== "undefined") {
    document.documentElement.setAttribute("data-theme", next);
  }
  try {
    localStorage.setItem(STORAGE_KEY, next);
  } catch {
    // Ignore storage failures
  }
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: next }));
  }
}

export function useTheme() {
  const [theme, setThemeState] = useState(readTheme);

  useEffect(() => {
    const onThemeChange = (event) => {
      const next = event?.detail;
      if (next === "dark" || next === "light") {
        setThemeState(next);
      } else {
        setThemeState(readTheme());
      }
    };
    const onStorage = (event) => {
      if (event.key === STORAGE_KEY) {
        const next = event.newValue === "dark" ? "dark" : "light";
        document.documentElement.setAttribute("data-theme", next);
        setThemeState(next);
      }
    };
    window.addEventListener(CHANGE_EVENT, onThemeChange);
    window.addEventListener("storage", onStorage);
    return () => {
      window.removeEventListener(CHANGE_EVENT, onThemeChange);
      window.removeEventListener("storage", onStorage);
    };
  }, []);

  const setTheme = useCallback((next) => {
    const normalized = next === "dark" ? "dark" : "light";
    setThemeState(normalized);
    applyTheme(normalized);
  }, []);

  const toggleTheme = useCallback(() => {
    setTheme(readTheme() === "dark" ? "light" : "dark");
  }, [setTheme]);

  return { theme, isDark: theme === "dark", setTheme, toggleTheme };
}
