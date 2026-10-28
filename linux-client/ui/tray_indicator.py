#!/usr/bin/env python3
"""System tray indicator (AppIndicator) for Linux GTK tether client."""

from __future__ import annotations

import logging
from typing import Optional

logger = logging.getLogger("tetherapp.linux.tray")

try:
    import gi
    gi.require_version("Gtk", "3.0")
    from gi.repository import Gtk, GLib

    APP_INDICATOR_AVAILABLE = False
    for indicator_name in ["AppIndicator3", "AyatanaAppIndicator3"]:
        try:
            gi.require_version(indicator_name, "0.1")
            if indicator_name == "AppIndicator3":
                from gi.repository import AppIndicator3 as AppIndicator
            else:
                from gi.repository import AyatanaAppIndicator3 as AppIndicator
            APP_INDICATOR_AVAILABLE = True
            break
        except (ValueError, ImportError):
            continue
except Exception:
    APP_INDICATOR_AVAILABLE = False


class TrayIndicator:
    """Manages system tray icon and menu for Bether Linux client."""

    def __init__(self, manager, window):
        self.manager = manager
        self.window = window
        self.indicator = None

        if not APP_INDICATOR_AVAILABLE:
            logger.warning("AppIndicator3/AyatanaAppIndicator3 not available; system tray disabled.")
            return

        try:
            self.indicator = AppIndicator.Indicator.new(
                "bether-tether-client",
                "network-vpn",
                AppIndicator.IndicatorCategory.APPLICATION_STATUS
            )
            self.indicator.set_status(AppIndicator.IndicatorStatus.ACTIVE)
            self.indicator.set_menu(self._build_menu())
            logger.info("System tray indicator initialized successfully.")
        except Exception as exc:
            logger.error("Failed to initialize AppIndicator: %s", exc)
            self.indicator = None

    def _build_menu(self) -> Gtk.Menu:
        menu = Gtk.Menu()

        item_status = Gtk.MenuItem(label="Status: Disconnected")
        item_status.set_sensitive(False)
        self.item_status = item_status
        menu.append(item_status)

        menu.append(Gtk.SeparatorMenuItem())

        item_connect = Gtk.MenuItem(label="Connect")
        item_connect.connect("activate", lambda *_: self._on_connect())
        menu.append(item_connect)

        item_disconnect = Gtk.MenuItem(label="Disconnect")
        item_disconnect.connect("activate", lambda *_: self._on_disconnect())
        menu.append(item_disconnect)

        menu.append(Gtk.SeparatorMenuItem())

        item_show = Gtk.MenuItem(label="Show Window")
        item_show.connect("activate", lambda *_: self._on_show())
        menu.append(item_show)

        item_quit = Gtk.MenuItem(label="Quit")
        item_quit.connect("activate", lambda *_: Gtk.main_quit())
        menu.append(item_quit)

        menu.show_all()
        return menu

    def _on_connect(self):
        if self.window:
            self.window._on_connect(None)

    def _on_disconnect(self):
        if self.window:
            self.window._on_disconnect(None)

    def _on_show(self):
        if self.window:
            self.window.present()

    def update_status(self, state_str: str):
        if hasattr(self, "item_status"):
            self.item_status.set_label(f"Status: {state_str}")
