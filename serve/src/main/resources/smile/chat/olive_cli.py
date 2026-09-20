# Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
"""Launch Olive CLI with safe ORT EP registration.

Olive's maybe_register_ep_libraries() auto-registers every DLL returned by
onnxruntime.get_available_providers(). On Windows GPU wheels that includes
TensorRT even when --provider is CUDA/CPU; missing nvinfer_*.dll then aborts
the whole optimize run.

This launcher patches registration to only touch EPs Olive explicitly
requested, and to skip optional EPs that fail to load.
"""
from __future__ import annotations

import logging
import sys
from pathlib import Path


def _patch_ep_registration() -> None:
    import olive.common.ort_inference as oi

    def maybe_register_ep_libraries(ep_paths: dict) -> None:
        try:
            import onnxruntime as ort
        except ImportError:
            return
        if not oi.ort_supports_ep_devices():
            return

        log = logging.getLogger("smile.olive")
        to_register: dict[str, str] = {}
        # Only providers Olive put in ep_paths (accelerator config) — never
        # expand to every get_available_providers() DLL.
        for provider, path in list(ep_paths.items()):
            if path is None:
                lib = (
                    "onnxruntime_providers_"
                    + provider.replace("ExecutionProvider", "").lower()
                    + ".dll"
                )
                if (Path(ort.__file__).parent / "capi" / lib).exists():
                    path = lib
                else:
                    continue
            to_register[provider] = path

        for ep_name, ep_path in to_register.items():
            try:
                ort.register_execution_provider_library(ep_name, ep_path)
            except Exception as exc:  # noqa: BLE001 — skip broken optional EPs
                if "already registered" in str(exc).lower():
                    continue
                log.warning("Skipping ORT EP %s (%s)", ep_name, exc)

    oi.maybe_register_ep_libraries = maybe_register_ep_libraries


def main() -> None:
    _patch_ep_registration()
    from olive.cli.launcher import main as olive_main

    sys.argv[0] = "olive"
    olive_main()


if __name__ == "__main__":
    main()
