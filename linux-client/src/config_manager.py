#!/usr/bin/env python3
"""Configuration manager for Linux tether client persistence."""

from __future__ import annotations

import os
import yaml
from typing import Optional, Dict, Any

CONFIG_DIR = os.path.expanduser("~/.config/tetherapp")
CONFIG_FILE = os.path.join(CONFIG_DIR, "config.yaml")

DEFAULT_CONFIG: Dict[str, Any] = {
    "host": "127.0.0.1",
    "port": 1080,
    "username": None,
}


class ConfigManager:
    """Manages loading and saving client connection config in YAML."""

    @staticmethod
    def get_config_path() -> str:
        return CONFIG_FILE

    @classmethod
    def load(cls) -> Dict[str, Any]:
        if not os.path.exists(CONFIG_FILE):
            return dict(DEFAULT_CONFIG)
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                data = yaml.safe_load(f)
                if isinstance(data, dict):
                    cfg = dict(DEFAULT_CONFIG)
                    cfg.update(data)
                    return cfg
        except Exception as exc:
            import logging
            logging.getLogger("tetherapp.linux.config").warning("Failed to load config: %s", exc)
        return dict(DEFAULT_CONFIG)

    @classmethod
    def save(cls, host: str, port: int, username: Optional[str] = None) -> None:
        os.makedirs(CONFIG_DIR, exist_ok=True)
        data = {
            "host": host,
            "port": port,
            "username": username,
        }
        try:
            with open(CONFIG_FILE, "w", encoding="utf-8") as f:
                yaml.safe_dump(data, f)
        except Exception as exc:
            import logging
            logging.getLogger("tetherapp.linux.config").error("Failed to save config: %s", exc)
