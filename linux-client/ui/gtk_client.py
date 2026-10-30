#!/usr/bin/env python3
"""Cyberpunk-themed GTK3 GUI for the Linux tethering client.

Provides:
- Connection controls (host/port, connect/disconnect, mDNS scan)
- Real-time bandwidth statistics and Cairo graph
- Connection health monitoring
- System tray integration (AppIndicator)
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
from .tray_indicator import TrayIndicator

logger = logging.getLogger("tetherapp.linux.gui")

CYBER_BG = "#0d0f18"
CYBER_SURFACE = "#161b26"
CYBER_PRIMARY = "#00f0ff"
CYBER_ACCENT = "#ff007f"
CYBER_SUCCESS = "#00ff9d"
CYBER_TEXT = "#f0f6fc"
CYBER_DIM = "#8b949e"

CSS = f"""
* {{
    font-family: 'Inter', 'Liberation Sans', sans-serif;
    font-size: 13px;
    color: {CYBER_TEXT};
}}
window {{
    background-color: {CYBER_BG};
}}
frame {{
    border: 1px solid rgba(0, 240, 255, 0.3);
    border-radius: 8px;
    background-color: {CYBER_SURFACE};
    padding: 8px;
}}
entry {{
    background-color: #1a2234;
    border: 1px solid rgba(0, 240, 255, 0.4);
    border-radius: 6px;
    color: {CYBER_TEXT};
    padding: 6px 10px;
}}
entry:focus {{
    border: 1px solid {CYBER_PRIMARY};
    box-shadow: 0 0 5px rgba(0, 240, 255, 0.5);
}}
button {{
    background-color: #212d40;
    border: 1px solid rgba(0, 240, 255, 0.3);
    border-radius: 6px;
    color: {CYBER_TEXT};
    padding: 6px 12px;
    font-weight: 600;
}}
button:hover {{
    background-color: #283852;
    border-color: {CYBER_PRIMARY};
}}
button.connect {{
    background-color: {CYBER_PRIMARY};
    color: #000000;
    font-weight: bold;
    border-radius: 6px;
    padding: 8px 16px;
}}
button.connect:hover {{
    background-color: #33f3ff;
}}
button.connect:disabled {{
    background-color: #1a2234;
    color: {CYBER_DIM};
    border-color: rgba(255, 255, 255, 0.1);
}}
button.disconnect {{
    background-color: {CYBER_ACCENT};
    color: #ffffff;
    font-weight: bold;
    border-radius: 6px;
    padding: 8px 16px;
}}
button.disconnect:hover {{
    background-color: #ff3399;
}}
button.disconnect:disabled {{
    background-color: #1a2234;
    color: {CYBER_DIM};
    border-color: rgba(255, 255, 255, 0.1);
}}
separator {{
    background: rgba(0, 240, 255, 0.2);
    min-height: 1px;
}}
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
    def __init__(self, app: Gtk.Application, manager: LinuxConnectionManager, tray_indicator: Optional[TrayIndicator] = None):
        super().__init__(application=app)
        self.manager = manager
        self.tray_indicator = tray_indicator
        self.set_title("Bether — Linux Tether Client")
        self.set_default_size(700, 600)
        self.set_position(Gtk.WindowPosition.CENTER)

        self._css_provider = Gtk.CssProvider()
        self._css_provider.load_from_data(CSS.encode())
        Gtk.StyleContext.add_provider_for_screen(
            Gdk.Screen.get_default(),
            self._css_provider,
            Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION,
        )

        self.history_sent = [0.0] * 60
        self.history_recv = [0.0] * 60
        self._last_bytes_sent = 0
        self._last_bytes_recv = 0
        self._last_poll_time = time.time()

        self._build_ui()
        self._update_ui_from_state()

        # Polling loop for stats
        self._poll_source = GLib.timeout_add(1000, self._on_poll)

    def _build_ui(self):
        vbox = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        vbox.set_margin_top(20)
        vbox.set_margin_bottom(20)
        vbox.set_margin_start(20)
        vbox.set_margin_end(20)
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
        vbox.pack_start(separator, False, False, 4)

        # Connection config frame
        cfg_frame = Gtk.Frame()
        cfg_frame.set_shadow_type(Gtk.ShadowType.ETCHED_OUT)
        cfg_label = Gtk.Label()
        cfg_label.set_markup(f'<span weight="bold" color="{CYBER_PRIMARY}">Connection</span>')
        cfg_frame.set_label_widget(cfg_label)
        cfg_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        cfg_box.set_margin_top(10)
        cfg_box.set_margin_bottom(10)
        cfg_box.set_margin_start(10)
        cfg_box.set_margin_end(10)
        cfg_frame.add(cfg_box)
        vbox.pack_start(cfg_frame, False, False, 0)

        # Host row with Scan LAN button
        host_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        host_label = Gtk.Label(label="Host:")
        host_label.set_width_chars(8)
        host_label.set_halign(Gtk.Align.START)
        self.entry_host = Gtk.Entry()
        self.entry_host.set_text(self.manager.host)
        self.entry_host.set_width_chars(20)
        self.btn_scan = Gtk.Button(label="Scan LAN")
        self.btn_scan.connect("clicked", self._on_scan_lan)
        self.btn_wifi_scan = Gtk.Button(label="Scan Wi-Fi")
        self.btn_wifi_scan.connect("clicked", self._on_scan_wifi)
        host_row.pack_start(host_label, False, False, 0)
        host_row.pack_start(self.entry_host, False, False, 0)
        host_row.pack_start(self.btn_scan, False, False, 0)
        host_row.pack_start(self.btn_wifi_scan, False, False, 0)
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
        if self.manager.username:
            self.entry_user.set_text(self.manager.username)
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
        vbox.pack_start(separator2, False, False, 4)

        # Status frame
        st_frame = Gtk.Frame()
        st_label = Gtk.Label()
        st_label.set_markup(f'<span weight="bold" color="{CYBER_PRIMARY}">Connection Status</span>')
        st_frame.set_label_widget(st_label)
        st_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        st_box.set_margin_top(10)
        st_box.set_margin_bottom(10)
        st_box.set_margin_start(10)
        st_box.set_margin_end(10)
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
        vbox.pack_start(separator3, False, False, 4)

        # Metrics frame with Bandwidth Graph
        m_frame = Gtk.Frame()
        m_label = Gtk.Label()
        m_label.set_markup(f'<span weight="bold" color="{CYBER_PRIMARY}">Real-time Metrics & Bandwidth Graph</span>')
        m_frame.set_label_widget(m_label)
        m_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=6)
        m_box.set_margin_top(10)
        m_box.set_margin_bottom(10)
        m_box.set_margin_start(10)
        m_box.set_margin_end(10)
        m_frame.add(m_box)
        vbox.pack_start(m_frame, True, True, 0)

        info_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=16)
        self.lbl_sent = Gtk.Label()
        self.lbl_sent.set_halign(Gtk.Align.START)
        self.lbl_recv = Gtk.Label()
        self.lbl_recv.set_halign(Gtk.Align.START)
        info_box.pack_start(self.lbl_sent, False, False, 0)
        info_box.pack_start(self.lbl_recv, False, False, 0)
        m_box.pack_start(info_box, False, False, 0)

        self.drawing_area = Gtk.DrawingArea()
        self.drawing_area.set_size_request(-1, 90)
        self.drawing_area.connect("draw", self._on_draw_graph)
        m_box.pack_start(self.drawing_area, True, True, 0)

    def _on_scan_wifi(self, button):
        self.btn_wifi_scan.set_sensitive(False)
        self.btn_wifi_scan.set_label("Scanning Wi-Fi...")
        def _scan():
            try:
                from ..src.wifi_scanner import WifiScanner
                networks = WifiScanner.scan_networks()
                kaosnet = WifiScanner.find_kaosnet(networks)
                GLib.idle_add(lambda: self._on_wifi_scan_finished(networks, kaosnet))
            except Exception as exc:
                logger.error("Wi-Fi scan error: %s", exc)
                GLib.idle_add(lambda: self._on_wifi_scan_finished([], None))
        threading.Thread(target=_scan, daemon=True).start()

    def _on_wifi_scan_finished(self, networks, kaosnet):
        self.btn_wifi_scan.set_sensitive(True)
        self.btn_wifi_scan.set_label("Scan Wi-Fi")
        if kaosnet:
            self._notify("KAOSNET Found", f"Found hotspot KAOSNET (Signal: {kaosnet.get('signal')}%). Connecting...")
            def _conn():
                from ..src.wifi_scanner import WifiScanner
                success = WifiScanner.connect_wifi("KAOSNET", "tetherapp123")
                GLib.idle_add(lambda: self._notify("Wi-Fi Connected", "Successfully connected to KAOSNET" if success else "Failed to connect to KAOSNET"))
            threading.Thread(target=_conn, daemon=True).start()
        else:
            self._notify("KAOSNET Not Found", f"Scanned {len(networks)} networks, but KAOSNET was not found.")

    def _on_scan_finished(self, hosts):
        self.btn_scan.set_sensitive(True)
        self.btn_scan.set_label("Scan LAN")
        if hosts:
            h = hosts[0]
            self.entry_host.set_text(h.get("host", ""))
            if h.get("port"):
                self.entry_port.set_text(str(h.get("port")))
            self._notify("Host Discovered", f"Found {h.get('name')} at {h.get('host')}:{h.get('port')}")
        else:
            self._notify("Scan Completed", "No tether hosts found on LAN")

    def _notify(self, title: str, message: str):
        try:
            Notify.Notification.new(title, message, "network-vpn").show()
        except Exception:
            pass

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

    def _update_ui_from_state(self):
        state = self.manager.state
        state_str = state.value.upper()
        if self.tray_indicator:
            self.tray_indicator.update_status(state_str)

        if state == LinuxConnectionState.CONNECTED:
            self.lbl_state.set_markup(f'<span class="status connected" color="{CYBER_PRIMARY}">STATE: CONNECTED</span>')
            self.btn_connect.set_sensitive(False)
            self.btn_disconnect.set_sensitive(True)
            self.entry_host.set_sensitive(False)
            self.entry_port.set_sensitive(False)
        elif state == LinuxConnectionState.CONNECTING or state == LinuxConnectionState.RECONNECTING:
            self.lbl_state.set_markup(f'<span color="{CYBER_DIM}">STATE: {state_str}</span>')
            self.btn_connect.set_sensitive(False)
            self.btn_disconnect.set_sensitive(True)
        else:
            self.lbl_state.set_markup(f'<span class="status disconnected" color="{CYBER_ACCENT}">STATE: {state_str}</span>')
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
        try:
            self._update_ui_from_state()

            now = time.time()
            dt = now - self._last_poll_time
            if dt <= 0:
                dt = 1.0
            self._last_poll_time = now

            total_sent, total_recv = self.manager.get_bandwidth_stats()
            rate_sent = max(0.0, (total_sent - self._last_bytes_sent) / dt)
            rate_recv = max(0.0, (total_recv - self._last_bytes_recv) / dt)
            self._last_bytes_sent = total_sent
            self._last_bytes_recv = total_recv

            self.history_sent.pop(0)
            self.history_sent.append(rate_sent)
            self.history_recv.pop(0)
            self.history_recv.append(rate_recv)
            self.drawing_area.queue_draw()

            self.lbl_sent.set_markup(
                f'<span color="{CYBER_PRIMARY}">Sent: {self._format_bytes(total_sent)} ({self._format_bytes(rate_sent)}/s)</span>'
            )
            self.lbl_recv.set_markup(
                f'<span color="{CYBER_TEXT}">Recv: {self._format_bytes(total_recv)} ({self._format_bytes(rate_recv)}/s)</span>'
            )
        except Exception as exc:
            logger.debug("Poll error: %s", exc)
        return True

    def _on_draw_graph(self, widget, cr):
        width = widget.get_allocated_width()
        height = widget.get_allocated_height()

        # Background
        cr.set_source_rgb(0.06, 0.06, 0.06)
        cr.paint()

        # Grid lines
        cr.set_source_rgba(0.2, 0.2, 0.2, 0.4)
        cr.set_line_width(1)
        for i in range(1, 4):
            y = height * i / 4.0
            cr.move_to(0, y)
            cr.line_to(width, y)
            cr.stroke()

        max_val = max(max(self.history_sent, default=1.0), max(self.history_recv, default=1.0), 1024.0)

        def draw_series(history, r, g, b):
            if not history:
                return
            cr.set_source_rgb(r, g, b)
            cr.set_line_width(2)
            n = len(history)
            dx = width / max(n - 1, 1)
            for i, val in enumerate(history):
                x = i * dx
                y = height - (val / max_val) * (height - 8) - 4
                if i == 0:
                    cr.move_to(x, y)
                else:
                    cr.line_to(x, y)
            cr.stroke()

        # Sent (Cyan)
        draw_series(self.history_sent, 0.0, 1.0, 0.8)
        # Recv (Pink)
        draw_series(self.history_recv, 1.0, 0.0, 0.67)
        return False

    def do_delete_event(self, event):
        self.hide()
        return True


class TetherClientApplication(Gtk.Application):
    def __init__(self, manager: LinuxConnectionManager):
        super().__init__(application_id="com.tetherapp.linux.client")
        self.manager = manager
        self.window: Optional[TetherClientWindow] = None
        self.tray_indicator: Optional[TrayIndicator] = None

    def do_activate(self):
        if not self.window:
            try:
                Notify.init("tetherapp")
            except Exception:
                pass
            self.tray_indicator = TrayIndicator(self.manager, None)
            self.window = TetherClientWindow(self, self.manager, self.tray_indicator)
            self.tray_indicator.window = self.window
            self.window.show_all()
        else:
            self.window.present()

    def do_shutdown(self):
        try:
            Notify.uninit()
        except Exception:
            pass
        super().do_shutdown()


def run_gui(manager: LinuxConnectionManager):
    app = TetherClientApplication(manager)
    app.run(None)
