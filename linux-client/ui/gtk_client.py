#!/usr/bin/env python3
"""Cyberpunk-themed GTK3 GUI for the Linux tethering client.

Provides:
- Connection controls (host/port, connect/disconnect)
- Real-time bandwidth statistics
- Connection health monitoring
- System tray integration
"""

from __future__ import annotations

import gi
gi.require_version("Gtk", "3.0")
gi.require_version("Gdk", "3.0")
gi.require_version("GdkPixbuf", "3.0")
gi.require_version("Notify", "0.7")

from gi.repository import Gtk, Gdk, GdkPixbuf, GLib, Notify
import logging
import threading
import time
from typing import Optional

from .linux_connection_manager import LinuxConnectionManager, LinuxConnectionState

logger = logging.getLogger("tetherapp.linux.gui")

CYBER_BG = "#0a0a0a"
CYBER_SURFACE = "#141414"
CYBER_PRIMARY = "#00ffcc"
CYBER_ACCENT = "#ff00aa"
CYBER_TEXT = "#e0e0e0"
CYBER_DIM = "#888888"

CSS = f"""
* {{
    font-family: 'Share Tech Mono', monospace;
    color: {CYBER_TEXT};
}}
window {{
    background-color: {CYBER_BG};
}}
frame {{
    border: 1px solid {CYBER_PRIMARY};
    border-radius: 4px;
    background-color: {CYBER_SURFACE};
}}
label.title {{
    color: {CYBER_PRIMARY};
    font-weight: bold;
    font-size: 16px;
}}
button.connect {{
    background-color: {CYBER_PRIMARY};
    color: {CYBER_BG};
    font-weight: bold;
    border-radius: 4px;
}}
button.connect:disabled {{
    background-color: {CYBER_DIM};
}}
button.disconnect {{
    background-color: {CYBER_ACCENT};
    color: {CYBER_BG};
    font-weight: bold;
    border-radius: 4px;
}}
label.status.connected {{
    color: {CYBER_PRIMARY};
    font-weight: bold;
}}
label.status.disconnected {{
    color: {CYBER_ACCENT};
    font-weight: bold;
}}
label.metric {{
    color: {CYBER_DIM};
}}
"""


class TetherClientWindow(Gtk.ApplicationWindow):
    def __init__(self, app: Gtk.Application, manager: LinuxConnectionManager):
        super().__init__(application=app)
        self.manager = manager
        self.set_title("Bether — Linux Tether Client")
        self.set_default_size(700, 520)
        self.set_position(Gtk.WindowPosition.CENTER)

        self._css_provider = Gtk.CssProvider()
        self._css_provider.load_from_data(CSS.encode())
        Gtk.StyleContext.add_provider_for_screen(
            Gdk.Screen.get_default(),
            self._css_provider,
            Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION,
        )

        self._build_ui()
        self._update_ui_from_state()

        # Polling loop for stats
        self._poll_source = GLib.timeout_add(1000, self._on_poll)

        # Connect to state changes via periodic check (manager has no signal API here)
        self._last_state = manager.state

    def _build_ui(self):
        vbox = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        vbox.set_margin_top(24)
        vbox.set_margin_bottom(24)
        vbox.set_margin_start(24)
        vbox.set_margin_end(24)
        self.add(vbox)

        # Header
        header = Gtk.Label()
        header.set_markup(f'<span size="x-large" weight="bold" color="{CYBER_PRIMARY}">BETHER</span>'
                          f'<span size="large" color="{CYBER_TEXT}"> — Linux Tether Client</span>')
        header.set_halign(Gtk.Align.START)
        vbox.pack_start(header, False, False, 0)

        sub = Gtk.Label()
        sub.set_markup(f'<span color="{CYBER_DIM}">System-wide traffic routing via SOCKS5 proxy to Android host</span>')
        sub.set_halign(Gtk.Align.START)
        vbox.pack_start(sub, False, False, 0)

        separator = Gtk.Separator(orientation=Gtk.Orientation.HORIZONTAL)
        vbox.pack_start(separator, False, False, 8)

        # Connection config frame
        cfg_frame = Gtk.Frame()
        cfg_frame.set_shadow_type(Gtk.ShadowType.ETCHED_OUT)
        cfg_label = Gtk.Label()
        cfg_label.set_markup(f'<span weight="bold" color="{CYBER_PRIMARY}">Connection</span>')
        cfg_frame.set_label_widget(cfg_label)
        cfg_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        cfg_box.set_margin_top(12)
        cfg_box.set_margin_bottom(12)
        cfg_box.set_margin_start(12)
        cfg_box.set_margin_end(12)
        cfg_frame.add(cfg_box)
        vbox.pack_start(cfg_frame, False, False, 0)

        # Host row
        host_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        host_label = Gtk.Label(label="Host:")
        host_label.set_width_chars(8)
        host_label.set_halign(Gtk.Align.START)
        self.entry_host = Gtk.Entry()
        self.entry_host.set_text(self.manager.host)
        self.entry_host.set_width_chars(24)
        host_row.pack_start(host_label, False, False, 0)
        host_row.pack_start(self.entry_host, False, False, 0)
        cfg_box.pack_start(host_row, False, False, 0)

        # Port row
        port_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        port_label = Gtk.Label(label="Port:")
        port_label.set_width_chars(8)
        port_label.set_halign(Gtk.Align.START)
        self.entry_port = Gtk.Entry()
        self.entry_port.set_text(str(self.manager.port))
        self.entry_port.set_width_chars(8)
        port_row.pack_start(port_label, False, False, 0)
        port_row.pack_start(self.entry_port, False, False, 0)
        cfg_box.pack_start(port_row, False, False, 0)

        # Auth row (optional)
        auth_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        user_label = Gtk.Label(label="User:")
        user_label.set_width_chars(8)
        user_label.set_halign(Gtk.Align.START)
        self.entry_user = Gtk.Entry()
        self.entry_user.set_placeholder_text("username (optional)")
        self.entry_user.set_width_chars(16)
        pass_label = Gtk.Label(label="Pass:")
        pass_label.set_width_chars(8)
        pass_label.set_halign(Gtk.Align.START)
        self.entry_pass = Gtk.Entry()
        self.entry_pass.set_visibility(False)
        self.entry_pass.set_placeholder_text("password (optional)")
        self.entry_pass.set_width_chars(16)
        auth_row.pack_start(user_label, False, False, 0)
        auth_row.pack_start(self.entry_user, False, False, 0)
        auth_row.pack_start(pass_label, False, False, 0)
        auth_row.pack_start(self.entry_pass, False, False, 0)
        cfg_box.pack_start(auth_row, False, False, 0)

        # Buttons row
        btn_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        self.btn_connect = Gtk.Button(label="CONNECT")
        self.btn_connect.get_style_context().add_class("connect")
        self.btn_connect.connect("clicked", self._on_connect)
        self.btn_disconnect = Gtk.Button(label="DISCONNECT")
        self.btn_disconnect.get_style_context().add_class("disconnect")
        self.btn_disconnect.connect("clicked", self._on_disconnect)
        self.btn_disconnect.set_sensitive(False)
        btn_row.pack_start(self.btn_connect, True, True, 0)
        btn_row.pack_start(self.btn_disconnect, True, True, 0)
        cfg_box.pack_start(btn_row, False, False, 0)

        separator2 = Gtk.Separator(orientation=Gtk.Orientation.HORIZONTAL)
        vbox.pack_start(separator2, False, False, 8)

        # Status frame
        st_frame = Gtk.Frame()
        st_label = Gtk.Label()
        st_label.set_markup(f'<span weight="bold" color="{CYBER_PRIMARY}">Connection Status</span>')
        st_frame.set_label_widget(st_label)
        st_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        st_box.set_margin_top(12)
        st_box.set_margin_bottom(12)
        st_box.set_margin_start(12)
        st_box.set_margin_end(12)
        st_frame.add(st_box)
        vbox.pack_start(st_frame, False, False, 0)

        self.lbl_state = Gtk.Label()
        self.lbl_state.set_markup(f'<span color="{CYBER_ACCENT}">STATE: DISCONNECTED</span>')
        self.lbl_state.set_halign(Gtk.Align.START)
        st_box.pack_start(self.lbl_state, False, False, 0)

        self.lbl_uptime = Gtk.Label()
        self.lbl_uptime.set_halign(Gtk.Align.START)
        st_box.pack_start(self.lbl_uptime, False, False, 0)

        self.lbl_error = Gtk.Label()
        self.lbl_error.set_halign(Gtk.Align.START)
        st_box.pack_start(self.lbl_error, False, False, 0)

        separator3 = Gtk.Separator(orientation=Gtk.Orientation.HORIZONTAL)
        vbox.pack_start(separator3, False, False, 8)

        # Metrics frame
        m_frame = Gtk.Frame()
        m_label = Gtk.Label()
        m_label.set_markup(f'<span weight="bold" color="{CYBER_PRIMARY}">Real-time Metrics</span>')
        m_frame.set_label_widget(m_label)
        m_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        m_box.set_margin_top(12)
        m_box.set_margin_bottom(12)
        m_box.set_margin_start(12)
        m_box.set_margin_end(12)
        m_frame.add(m_box)
        vbox.pack_start(m_frame, False, False, 0)

        self.lbl_sent = Gtk.Label()
        self.lbl_sent.set_halign(Gtk.Align.START)
        m_box.pack_start(self.lbl_sent, False, False, 0)

        self.lbl_recv = Gtk.Label()
        self.lbl_recv.set_halign(Gtk.Align.START)
        m_box.pack_start(self.lbl_recv, False, False, 0)

        self.lbl_rate = Gtk.Label()
        self.lbl_rate.set_halign(Gtk.Align.START)
        m_box.pack_start(self.lbl_rate, False, False, 0)

    # ------------------------------------------------------------------ #
    # Event handlers
    # ------------------------------------------------------------------ #
    def _on_connect(self, button):
        host = self.entry_host.get_text().strip() or "127.0.0.1"
        try:
            port = int(self.entry_port.get_text().strip() or "1080")
        except ValueError:
            port = 1080
        user = self.entry_user.get_text().strip() or None
        pwd = self.entry_pass.get_text().strip() or None

        self.manager.host = host
        self.manager.port = port
        self.manager.username = user
        self.manager.password = pwd

        ok = self.manager.connect()
        if not ok:
            self._notify("Connection failed", "Could not connect to SOCKS5 host")
        self._update_ui_from_state()

    def _on_disconnect(self, button):
        self.manager.disconnect()
        self._update_ui_from_state()

    # ------------------------------------------------------------------ #
    # UI refresh / polling
    # ------------------------------------------------------------------ #
    def _update_ui_from_state(self):
        state = self.manager.state
        if state == LinuxConnectionState.CONNECTED:
            self.lbl_state.set_markup(f'<span class="status connected" color="{CYBER_PRIMARY}">STATE: CONNECTED</span>')
            self.btn_connect.set_sensitive(False)
            self.btn_disconnect.set_sensitive(True)
            self.entry_host.set_sensitive(False)
            self.entry_port.set_sensitive(False)
        elif state == LinuxConnectionState.CONNECTING or state == LinuxConnectionState.RECONNECTING:
            self.lbl_state.set_markup(f'<span color="{CYBER_DIM}">STATE: {state.value.upper()}</span>')
            self.btn_connect.set_sensitive(False)
            self.btn_disconnect.set_sensitive(True)
        else:
            self.lbl_state.set_markup(f'<span class="status disconnected" color="{CYBER_ACCENT}">STATE: {state.value.upper()}</span>')
            self.btn_connect.set_sensitive(True)
            self.btn_disconnect.set_sensitive(False)
            self.entry_host.set_sensitive(True)
            self.entry_port.set_sensitive(True)

        if self.manager.start_time:
            uptime = int(time.time() - self.manager.start_time)
            self.lbl_uptime.set_markup(f'<span color="{CYBER_TEXT}">Uptime: {uptime}s</span>')
        else:
            self.lbl_uptime.set_markup("")

        if self.manager.last_error:
            self.lbl_error.set_markup(f'<span color="{CYBER_ACCENT}">Error: {self.manager.last_error}</span>')
        else:
            self.lbl_error.set_markup("")

    def _format_bytes(self, num: float) -> str:
        for unit in ("B", "KB", "MB", "GB"):
            if abs(num) < 1024.0:
                return f"{num:.2f} {unit}"
            num /= 1024.0
        return f"{num:.2f} TB"

    def _on_poll(self):
        # Poll bandwidth from the SOCKS client socket if connected
        try:
            status = self.manager.get_status()
            self._update_ui_from_state()

            if self.manager.state == LinuxConnectionState.CONNECTED:
                # Use socket stats as best-effort throughput proxy
                sent = 0
                recv = 0
                sock = self.manager.socks_client.sock
                if sock is not None:
                    try:
                        sent = sock.getsockopt(
                            __import__("socket").SOL_SOCKET,
                            __import__("socket").SO_SNDBUF,
                        )
                    except OSError:
                        pass
                self.lbl_sent.set_markup(
                    f'<span color="{CYBER_PRIMARY}">Sent: {self._format_bytes(sent)}</span>'
                )
                self.lbl_recv.set_markup(
                    f'<span color="{CYBER_TEXT}">Received: {self._format_bytes(recv)}</span>'
                )
                self.lbl_rate.set_markup(
                    f'<span color="{CYBER_DIM}">Proxy: {self.manager.host}:{self.manager.port}</span>'
                )
        except Exception as exc:
            logger.debug("Poll error: %s", exc)
        return True  # keep polling

    def do_delete_event(self, event):
        # Minimize to tray instead of closing if tray supported
        self.hide()
        return True


class TetherClientApplication(Gtk.Application):
    def __init__(self, manager: LinuxConnectionManager):
        super().__init__(application_id="com.tetherapp.linux.client")
        self.manager = manager
        self.window: Optional[TetherClientWindow] = None
        self.tray_icon: Optional[Gtk.StatusIcon] = None

    def do_activate(self):
        if not self.window:
            self.window = TetherClientWindow(self, self.manager)
            self.window.show_all()
            self._setup_tray()
        else:
            self.window.present()

    def _setup_tray(self):
        try:
            Notify.init("tetherapp")
            self.tray_icon = Gtk.StatusIcon()
            self.tray_icon.set_from_icon_name("network-vpn")
            self.tray_icon.set_title("Bether Tether Client")
            self.tray_icon.connect("activate", lambda *_: self.window.present())
            menu = Gtk.Menu()
            item_quit = Gtk.MenuItem(label="Quit")
            item_quit.connect("activate", lambda *_: Gtk.main_quit())
            menu.append(item_quit)
            menu.show_all()

            def _on_popup(icon, button, activate_time):
                menu.popup(None, None, None, None, button, activate_time)

            self.tray_icon.connect("popup-menu", _on_popup)
        except Exception as exc:
            logger.debug("Tray setup skipped: %s", exc)

    def do_shutdown(self):
        if self.tray_icon:
            self.tray_icon.set_from_icon_name(None)
        Notify.uninit()
        super().do_shutdown()


def run_gui(manager: LinuxConnectionManager):
    app = TetherClientApplication(manager)
    app.run(None)
