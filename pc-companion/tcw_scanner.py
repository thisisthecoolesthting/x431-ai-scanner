"""
TCW Standalone OBD Scanner — Windows companion (.exe)
=====================================================
Talks to an ELM327 adapter over a COM (serial) port — USB cable or Bluetooth SPP.

Bluetooth ELM327 support (no extra dependencies):
  On Windows, a paired Bluetooth ELM327 dongle appears as a virtual COM port
  (Bluetooth SPP / RFCOMM).  Once paired in Windows Bluetooth settings the port
  shows up in the dropdown here, tagged "(Bluetooth)".  The existing pyserial
  path handles it identically to a USB cable — just pick the right COM port and
  click Connect.  38400 baud is the most common default for BT adapters.

Mirrors the Android standalone scanner's ELM327 logic:
  init sequence:  ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0, ATAT2
  read codes:     Mode 03 (stored DTCs) + Mode 07 (pending)
  clear codes:    Mode 04 (with confirmation)
  live data:      Mode 01 PIDs (RPM, speed, coolant, throttle, intake, MAP, load)
  I/M readiness:  Mode 01 PID 01 (monitor ready states, MIL, DTC count)
  export CSV:     current session (VIN, codes, live snapshot)

No external GUI deps — pure Tkinter (stdlib) + pyserial.
Build to a single .exe with:
  pyinstaller --onefile --noconsole --name TCW-OBD-Scanner \
      --add-data "dtc_generic.json;." tcw_scanner.py

(c) Together Car Works — debug/diagnostic tool.
"""

import csv
import json
import os
import sys
import threading
import time
import queue
import urllib.request
import urllib.parse
import tkinter as tk
from tkinter import ttk, messagebox, filedialog

try:
    import serial
    import serial.tools.list_ports
except ImportError:
    raise SystemExit("pyserial is required:  pip install pyserial")


# ----------------------------------------------------------------------------
# Shared DTC description database (single source of truth)
# Candidate paths tried in order:
#   1. sys._MEIPASS/<filename>          — PyInstaller onefile bundle
#   2. <directory of this script>/<filename>
#   3. Absolute repo path (dev machine only)
# ----------------------------------------------------------------------------
_DTC_JSON_NAME = "dtc_generic.json"
_DTC_REPO_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "assets", "offline", _DTC_JSON_NAME
)

def _dtc_json_candidates():
    paths = []
    if hasattr(sys, "_MEIPASS"):
        paths.append(os.path.join(sys._MEIPASS, _DTC_JSON_NAME))
    paths.append(os.path.join(os.path.dirname(os.path.abspath(__file__)), _DTC_JSON_NAME))
    paths.append(os.path.normpath(_DTC_REPO_PATH))
    return paths

def _load_dtc_db():
    """Return dict of code -> title from the shared JSON. Never raises."""
    for path in _dtc_json_candidates():
        try:
            with open(path, "r", encoding="utf-8") as f:
                data = json.load(f)
            return {e["code"]: e.get("title", "") for e in data.get("entries", [])}
        except Exception:
            continue
    return {}

def _load_full_dtc():
    """Prefer the bundled comprehensive DTC database (1500+ codes); fall back to JSON."""
    try:
        from dtc_database import DTC_DB as _FULL
        if isinstance(_FULL, dict) and len(_FULL) > 100:
            merged = dict(_FULL)
            merged.update(_load_dtc_db())  # JSON entries override/augment if present
            return merged
    except Exception:
        pass
    return _load_dtc_db()

DTC_DB = _load_full_dtc()


# ----------------------------------------------------------------------------
# Cloud-upload config  (persisted to ~/.tcw_scanner.json)
# ----------------------------------------------------------------------------
_CONFIG_PATH = os.path.join(os.path.expanduser("~"), ".tcw_scanner.json")
_RELAY_ENDPOINT = "https://tcw.aiaffiliate.builders/api/relay/session"
APP_VERSION = "1.3.0"

# --- TCW Shop Cockpit theme (green-instrument) ---
C_BG       = "#07110D"   # window background
C_PANEL    = "#0D1B14"   # primary panel
C_RAISED   = "#12261C"   # raised panel / tile
C_TILE     = "#102117"
C_BORDER   = "#244D36"
C_TEXT     = "#EAF7EF"
C_MUTED    = "#8FA99A"
C_GREEN    = "#42FF91"   # connected / good / brand active
C_GREEN_D  = "#1E8F52"
C_AMBER    = "#FFB020"   # connecting / caution
C_RED      = "#FF3B30"   # offline / fault
C_BLUE     = "#2F8EE5"
_VERSION_MANIFEST = "https://tcw.aiaffiliate.builders/tcw-exe-version.json"


def _load_config():
    """Return config dict. Never raises."""
    try:
        with open(_CONFIG_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return {}


def _save_config(cfg):
    """Persist config dict. Never raises."""
    try:
        with open(_CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2)
    except Exception:
        pass


# ----------------------------------------------------------------------------
# ELM327 transport — same command discipline as the Android UsbSerialTransport
# ----------------------------------------------------------------------------
class Elm327:
    PROMPT = b">"

    def __init__(self, port, baud=38400, timeout=5.0):
        self.port = port
        self.baud = baud
        self.timeout = timeout
        self.ser = None
        self.lock = threading.Lock()

    def open(self):
        # ELM327 clones are commonly 38400 or 9600; try the requested baud first.
        self.ser = serial.Serial(self.port, self.baud, timeout=0.2)
        time.sleep(0.4)
        self.ser.reset_input_buffer()
        self.ser.reset_output_buffer()

    def close(self):
        try:
            if self.ser and self.ser.is_open:
                self.ser.close()
        except Exception:
            pass
        self.ser = None

    def _read_until_prompt(self, deadline):
        """Poll for the '>' prompt, with a 4096-byte garbage guard (matches Android)."""
        buf = bytearray()
        while time.time() < deadline and len(buf) <= 4096:
            chunk = self.ser.read(64)
            if chunk:
                buf.extend(chunk)
                if self.PROMPT in buf:
                    break
            else:
                time.sleep(0.015)
        return buf.decode("ascii", errors="replace").replace(">", "").strip()

    def send(self, cmd, timeout=None):
        """Send an AT/OBD command, return the cleaned response string."""
        if not self.ser or not self.ser.is_open:
            raise IOError("serial port not open")
        timeout = timeout or self.timeout
        with self.lock:
            self.ser.reset_input_buffer()
            self.ser.write((cmd + "\r").encode("ascii"))
            self.ser.flush()
            return self._read_until_prompt(time.time() + timeout)

    def initialize(self):
        """Run the same init sequence the Android app uses (incl. ATAT2)."""
        seq = ["ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "ATAT2"]
        details = []
        for c in seq:
            r = self.send(c, timeout=4.0)
            details.append(f"{c}->{r or 'OK'}")
            time.sleep(0.05)
        voltage = self.send("ATRV", timeout=2.0).strip()
        ready = self.send("0100", timeout=4.0)
        return voltage, ready, details

    def read_readiness(self):
        """
        Send Mode 01 PID 01 (0101), parse 4 data bytes A B C D per SAE J1979.
        Returns:
          {
            "mil_on": bool,
            "dtc_count": int,
            "monitors": [{"name": str, "supported": bool, "ready": bool}, ...]
          }
        Never raises — returns empty monitors on short/bad payload.
        """
        resp = self.send("0101", timeout=5.0)
        b = _hex_bytes(resp, "4101")
        result = {"mil_on": False, "dtc_count": 0, "monitors": []}
        if len(b) < 4:
            return result

        A, B, C, D = b[0], b[1], b[2], b[3]
        result["mil_on"] = bool(A & 0x80)
        result["dtc_count"] = A & 0x7F

        # Continuous monitors (byte B: support = bits 0-2, incomplete = bits 4-6)
        continuous = [
            ("Misfire",       0x01, 0x10),
            ("Fuel System",   0x02, 0x20),
            ("Components",    0x04, 0x40),
        ]
        for name, sup_mask, inc_mask in continuous:
            supported = bool(B & sup_mask)
            incomplete = bool(B & inc_mask)
            result["monitors"].append({
                "name": name,
                "supported": supported,
                "ready": supported and not incomplete,
            })

        # Non-continuous monitors (byte C = support, byte D = incomplete)
        non_continuous = [
            ("Catalyst",             0x01),
            ("Heated Catalyst",      0x02),
            ("Evaporative System",   0x04),
            ("Secondary Air System", 0x08),
            ("A/C Refrigerant",      0x10),
            ("Oxygen Sensor",        0x20),
            ("Oxygen Sensor Heater", 0x40),
            ("EGR System",           0x80),
        ]
        for name, mask in non_continuous:
            supported = bool(C & mask)
            incomplete = bool(D & mask)
            result["monitors"].append({
                "name": name,
                "supported": supported,
                "ready": supported and not incomplete,
            })

        return result


# ----------------------------------------------------------------------------
# OBD-II decoding (SAE J1979)
# ----------------------------------------------------------------------------
def _hex_bytes(resp, mode_echo):
    """Strip whitespace/echo/framing, return data byte ints after the mode echo (e.g. '410C').

    Robust to BOTH spaced ('41 0C 1A 2B') and unspaced ('410C1A2B') ELM327 output, to
    multi-line CAN frames, and to leftover command echo. mode_echo is the response prefix
    like '410C'. Returns the data bytes AFTER that prefix.
    """
    import re as _re
    up = mode_echo.upper()
    # Collapse the whole response to a continuous hex string (drop anything non-hex).
    cleaned = resp.upper()
    for junk in ("SEARCHING...", "SEARCHING", "NODATA", "NO DATA", "STOPPED", "UNABLE TO CONNECT", "?", ">"):
        cleaned = cleaned.replace(junk, " ")
    # keep only hex digits and spaces, then strip spaces to one blob
    blob = "".join(ch for ch in cleaned if ch in "0123456789ABCDEF ")
    blob = blob.replace(" ", "")
    if not blob:
        return []
    idx = blob.find(up)
    if idx >= 0:
        rest = blob[idx + len(up):]
        # multi-frame CAN can repeat the prefix; cut at the next prefix if present
        nxt = rest.find(up)
        if nxt > 0:
            rest = rest[:nxt]
        # trim to an even number of nibbles
        if len(rest) % 2 == 1:
            rest = rest[:-1]
        return [int(rest[i:i + 2], 16) for i in range(0, len(rest), 2)]
    # no echo found: fall back to any 2-char tokens
    toks = [t for t in resp.replace("\r", " ").replace("\n", " ").split()
            if len(t) == 2 and all(c in "0123456789ABCDEFabcdef" for c in t)]
    return [int(t, 16) for t in toks]


# PID definitions: pid_cmd -> (label, response_echo, decoder, unit)
def dec_rpm(b):       return (b[0] * 256 + b[1]) / 4.0 if len(b) >= 2 else None
def dec_speed(b):     return b[0] if b else None
def dec_temp(b):      return b[0] - 40 if b else None
def dec_pct(b):       return round(b[0] * 100 / 255, 1) if b else None
def dec_map(b):       return b[0] if b else None
def dec_load(b):      return round(b[0] * 100 / 255, 1) if b else None
def dec_maf(b):       return round((b[0] * 256 + b[1]) / 100.0, 2) if len(b) >= 2 else None

LIVE_PIDS = [
    ("010C", "Engine RPM",         "410C", dec_rpm,   "rpm"),
    ("010D", "Vehicle speed",      "410D", dec_speed, "km/h"),
    ("0105", "Coolant temp",       "4105", dec_temp,  "°C"),
    ("0111", "Throttle position",  "4111", dec_pct,   "%"),
    ("010F", "Intake air temp",    "410F", dec_temp,  "°C"),
    ("010B", "Intake MAP",         "410B", dec_map,   "kPa"),
    ("0104", "Engine load",        "4104", dec_load,  "%"),
    ("0110", "MAF air flow",       "4110", dec_maf,   "g/s"),
]


def _dtc_line(code):
    """Format a DTC code with its description if available in the shared DB."""
    desc = DTC_DB.get(code, "")
    return f"{code}  {desc}" if desc else code


def decode_dtcs(resp, mode_echo="43"):
    """Decode Mode 03/07 DTC response into code strings like P0301."""
    tokens = resp.replace("\r", " ").replace("\n", " ").split()
    hexes = [t for t in tokens if len(t) == 2 and all(c in "0123456789ABCDEFabcdef" for c in t)]
    joined = "".join(hexes).upper()
    if "NODATA" in joined or not joined:
        return []
    idx = joined.find(mode_echo)
    if idx >= 0:
        joined = joined[idx + 2:]
    # remove a leading count byte if odd grouping; DTCs are 2 bytes each
    codes = []
    for i in range(0, len(joined) - 3, 4):
        a = joined[i:i + 2]
        b = joined[i + 2:i + 4]
        if a == "00" and b == "00":
            continue
        try:
            first = int(a, 16)
        except ValueError:
            continue
        letter = "PCBU"[(first & 0xC0) >> 6]
        d1 = (first & 0x30) >> 4
        d2 = first & 0x0F
        codes.append(f"{letter}{d1}{d2:X}{b}")
    return codes


# ----------------------------------------------------------------------------
# GUI
# ----------------------------------------------------------------------------
class ScannerApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("TCW OBD Scanner v" + APP_VERSION + " — ELM327 (USB / Bluetooth)")
        self.geometry("960x760")
        self.minsize(860, 680)
        self.elm = None
        self.live_thread = None
        self.live_stop = threading.Event()
        self.ui_queue = queue.Queue()
        # session state for CSV export and cloud upload
        self._session_vin = ""
        self._session_stored = []
        self._session_pending = []
        self._live_snapshot = {}   # label -> (value_str, unit)
        self._readiness_result = None  # last readiness dict, or None
        # config (api key, etc.)
        self._config = _load_config()
        self._build_ui()
        # populate api-key field from saved config
        saved_key = self._config.get("api_key", "")
        if saved_key:
            self.api_key_var.set(saved_key)
        self._update_upload_btn_state()
        self.after(100, self._drain_queue)
        self.refresh_ports()

    # ---- layout ----
    def _apply_theme(self):
        """Apply the TCW Shop Cockpit green-instrument dark theme to ttk + the window."""
        self.configure(bg=C_BG)
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except Exception:
            pass
        style.configure(".", background=C_PANEL, foreground=C_TEXT, fieldbackground=C_RAISED,
                        bordercolor=C_BORDER, font=("Segoe UI", 10))
        style.configure("TFrame", background=C_BG)
        style.configure("TLabel", background=C_BG, foreground=C_TEXT, font=("Segoe UI", 10))
        style.configure("Section.TLabel", background=C_BG, foreground=C_GREEN, font=("Segoe UI Semibold", 11))
        style.configure("Muted.TLabel", background=C_BG, foreground=C_MUTED, font=("Segoe UI", 9))
        # buttons
        style.configure("TButton", background=C_RAISED, foreground=C_TEXT, bordercolor=C_BORDER,
                        focuscolor=C_GREEN, font=("Segoe UI Semibold", 10), padding=(12, 8))
        style.map("TButton", background=[("active", "#244D36"), ("disabled", "#142019")],
                  foreground=[("disabled", "#506658")])
        style.configure("Primary.TButton", background=C_GREEN_D, foreground="#031008",
                        font=("Segoe UI Semibold", 11), padding=(14, 10))
        style.map("Primary.TButton", background=[("active", C_GREEN)])
        style.configure("Danger.TButton", background="#5C1111", foreground="#FFE8E6",
                        font=("Segoe UI Semibold", 10), padding=(12, 10))
        style.map("Danger.TButton", background=[("active", C_RED)])
        style.configure("TCombobox", fieldbackground=C_RAISED, background=C_RAISED, foreground=C_TEXT,
                        arrowcolor=C_GREEN)
        style.configure("TEntry", fieldbackground=C_RAISED, foreground=C_TEXT)
        style.configure("TLabelframe", background=C_BG, foreground=C_GREEN, bordercolor=C_BORDER)
        style.configure("TLabelframe.Label", background=C_BG, foreground=C_GREEN, font=("Segoe UI Semibold", 10))
        style.configure("Treeview", background=C_PANEL, fieldbackground=C_PANEL, foreground=C_TEXT,
                        rowheight=30, font=("Consolas", 12))
        style.configure("Treeview.Heading", background=C_RAISED, foreground=C_GREEN, font=("Segoe UI Semibold", 10))
        style.map("Treeview", background=[("selected", C_RAISED)])
        style.configure("TNotebook", background=C_BG, bordercolor=C_BORDER)
        style.configure("TNotebook.Tab", background=C_RAISED, foreground=C_MUTED, padding=(14, 8),
                        font=("Segoe UI Semibold", 10))
        style.map("TNotebook.Tab", background=[("selected", C_PANEL)], foreground=[("selected", C_GREEN)])

    def _draw_traffic_light(self, state):
        """state in (offline, connecting, connected). Updates the connection dot + word."""
        c = self.tl_canvas
        c.delete("all")
        colors = {"connected": C_GREEN, "connecting": C_AMBER, "offline": C_RED}
        words = {"connected": "CONNECTED", "connecting": "CONNECTING", "offline": "OFFLINE"}
        dot = colors.get(state, C_RED)
        c.create_oval(8, 8, 46, 46, fill=dot, outline=dot)
        c.create_text(58, 18, anchor="w", text=words.get(state, "OFFLINE"),
                      fill=C_TEXT, font=("Segoe UI Semibold", 15))
        sub = getattr(self, "_tl_sub", "")
        c.create_text(58, 38, anchor="w", text=sub, fill=C_MUTED, font=("Consolas", 9))

    def _build_ui(self):
        self._apply_theme()
        self._tl_sub = ""
        pad = {"padx": 8, "pady": 6}

        # Connection traffic-light header
        header = ttk.Frame(self); header.pack(fill="x", **pad)
        self.tl_canvas = tk.Canvas(header, width=240, height=54, bg=C_BG, highlightthickness=0)
        self.tl_canvas.pack(side="left")
        self._draw_traffic_light("offline")

        top = ttk.Frame(self); top.pack(fill="x", **pad)

        ttk.Label(top, text="COM port:").pack(side="left")
        self.port_var = tk.StringVar()
        self.port_combo = ttk.Combobox(top, textvariable=self.port_var, width=28, state="readonly")
        self.port_combo.pack(side="left", padx=6)
        ttk.Button(top, text="Refresh", command=self.refresh_ports).pack(side="left")

        ttk.Label(top, text="Baud:").pack(side="left", padx=(12, 2))
        self.baud_var = tk.StringVar(value="115200")
        ttk.Combobox(top, textvariable=self.baud_var, width=8, state="readonly",
                     values=["115200", "38400", "9600", "57600", "500000"]).pack(side="left")

        self.connect_btn = ttk.Button(top, text="Connect", command=self.toggle_connect)
        self.connect_btn.pack(side="left", padx=10)

        # Bluetooth help hint
        bt_hint = ttk.Label(
            self,
            text=(
                "Bluetooth adapter not listed? Pair it first in Windows Settings > Bluetooth & devices,"
                " then click Refresh. It will appear as a COM port tagged (Bluetooth)."
            ),
            foreground=C_MUTED,
            font=("Segoe UI", 8),
            wraplength=680,
            justify="left",
        )
        bt_hint.pack(fill="x", padx=8, pady=(0, 2))

        # status bar
        self.status_var = tk.StringVar(value="Not connected. Plug in the ELM327 adapter (USB or Bluetooth), pick its COM port, and Connect.")
        status = tk.Label(self, textvariable=self.status_var, anchor="w", bg=C_RAISED, fg=C_TEXT, font=("Segoe UI", 9), padx=8, pady=4)
        status.pack(fill="x", side="bottom")

        # grouped action buttons (labeled rows so a tired mechanic finds them fast)
        def _group(title):
            lf = ttk.Labelframe(self, text=title)
            lf.pack(fill="x", padx=8, pady=(4, 0))
            row = ttk.Frame(lf); row.pack(fill="x", padx=6, pady=6)
            return row

        g_core = _group("DIAGNOSE")
        self.scan_btn = ttk.Button(g_core, text="Read Codes", command=self.read_codes, state="disabled", style="Primary.TButton")
        self.scan_btn.pack(side="left", padx=4)
        self.clear_btn = ttk.Button(g_core, text="Clear Codes", command=self.clear_codes, state="disabled", style="Danger.TButton")
        self.clear_btn.pack(side="left", padx=(18, 4))
        self.live_btn = ttk.Button(g_core, text="Start Live Data", command=self.toggle_live, state="disabled")
        self.live_btn.pack(side="left", padx=4)
        self.vin_btn = ttk.Button(g_core, text="Read VIN", command=self.read_vin, state="disabled")
        self.vin_btn.pack(side="left", padx=4)
        self.readiness_btn = ttk.Button(g_core, text="Readiness", command=self.read_readiness, state="disabled")
        self.readiness_btn.pack(side="left", padx=4)
        self.ai_btn = ttk.Button(g_core, text="AI Diagnose", command=self.ai_diagnose, style="Primary.TButton")
        self.ai_btn.pack(side="left", padx=(18, 4))

        g_deep = _group("DEEP DATA")
        self.freeze_btn = ttk.Button(g_deep, text="Freeze Frame", command=self.read_freeze_frame, state="disabled")
        self.freeze_btn.pack(side="left", padx=4)
        self.perm_btn = ttk.Button(g_deep, text="Permanent DTCs", command=self.read_permanent, state="disabled")
        self.perm_btn.pack(side="left", padx=4)
        self.vehinfo_btn = ttk.Button(g_deep, text="Vehicle Info", command=self.read_vehicle_info, state="disabled")
        self.vehinfo_btn.pack(side="left", padx=4)
        self.allpids_btn = ttk.Button(g_deep, text="Scan All PIDs", command=self.scan_all_pids, state="disabled")
        self.allpids_btn.pack(side="left", padx=4)

        g_mod = _group("MODULES & TESTS")
        self.multiecu_btn = ttk.Button(g_mod, text="All Modules", command=self.scan_all_modules, state="disabled")
        self.multiecu_btn.pack(side="left", padx=4)
        self.mode6_btn = ttk.Button(g_mod, text="Monitor Tests", command=self.read_mode6, state="disabled")
        self.mode6_btn.pack(side="left", padx=4)
        self.mode5_btn = ttk.Button(g_mod, text="O2 Tests", command=self.read_mode5, state="disabled")
        self.mode5_btn.pack(side="left", padx=4)
        self.recalls_btn = ttk.Button(g_mod, text="Recalls", command=self.check_recalls)
        self.recalls_btn.pack(side="left", padx=4)
        self.report_btn = ttk.Button(g_mod, text="Save Report", command=self.save_report)
        self.report_btn.pack(side="left", padx=4)

        g_sys = _group("EXPORT & SYSTEM")
        self.export_btn = ttk.Button(g_sys, text="Export CSV", command=self.export_csv)
        self.export_btn.pack(side="left", padx=4)
        self.upload_btn = ttk.Button(g_sys, text="Cloud Upload", command=self.cloud_upload, state="disabled")
        self.upload_btn.pack(side="left", padx=4)
        self.sendlogs_btn = ttk.Button(g_sys, text="Send Logs", command=self.send_logs)
        self.sendlogs_btn.pack(side="left", padx=4)
        self.update_btn = ttk.Button(g_sys, text="Check for Updates", command=self.check_for_updates)
        self.update_btn.pack(side="left", padx=4)

        # API-key row
        cloud_row = ttk.Frame(self)
        cloud_row.pack(fill="x", padx=8, pady=(0, 4))
        ttk.Label(cloud_row, text="Shop API key:").pack(side="left")
        self.api_key_var = tk.StringVar()
        self.api_key_entry = ttk.Entry(cloud_row, textvariable=self.api_key_var, width=42, show="*")
        self.api_key_entry.pack(side="left", padx=(4, 8))
        self.api_key_var.trace_add("write", self._on_api_key_changed)
        ttk.Button(cloud_row, text="Save key", command=self._save_api_key).pack(side="left")
        self.api_key_hint = ttk.Label(
            cloud_row,
            text="Paste your TCW API key here to enable cloud upload.",
            foreground="#888888",
            font=("TkDefaultFont", 8),
        )
        self.api_key_hint.pack(side="left", padx=(10, 0))

        # notebook: codes / live / readiness / log
        nb = ttk.Notebook(self); nb.pack(fill="both", expand=True, **pad)
        _txtopt = dict(bg=C_PANEL, fg=C_TEXT, insertbackground=C_GREEN,
                       relief="flat", highlightthickness=1, highlightbackground=C_BORDER,
                       font=("Consolas", 11))

        # codes tab
        self.codes_box = tk.Text(nb, height=8, wrap="word", **_txtopt)
        nb.add(self.codes_box, text="Codes")

        # live tab
        live_frame = ttk.Frame(nb)
        self.live_tree = ttk.Treeview(live_frame, columns=("val", "unit"), show="tree headings", height=10)
        self.live_tree.heading("#0", text="Parameter")
        self.live_tree.heading("val", text="Value")
        self.live_tree.heading("unit", text="Unit")
        self.live_tree.column("#0", width=240)
        self.live_tree.column("val", width=120, anchor="e")
        self.live_tree.column("unit", width=80, anchor="center")
        self.live_tree.pack(fill="both", expand=True)
        for _, label, _, _, unit in LIVE_PIDS:
            self.live_tree.insert("", "end", iid=label, text=label, values=("—", unit))
        nb.add(live_frame, text="Live data")

        # readiness tab
        self.readiness_box = tk.Text(nb, height=8, wrap="word", **_txtopt)
        nb.add(self.readiness_box, text="Readiness")

        # log tab
        self.log_box = tk.Text(nb, height=8, wrap="word", **_txtopt)
        nb.add(self.log_box, text="Log")

    # ---- helpers ----
    def log(self, msg):
        self.ui_queue.put(("log", msg))

    def set_status(self, msg):
        self.ui_queue.put(("status", msg))

    def _drain_queue(self):
        try:
            while True:
                kind, payload = self.ui_queue.get_nowait()
                if kind == "log":
                    self.log_box.insert("end", time.strftime("[%H:%M:%S] ") + payload + "\n")
                    self.log_box.see("end")
                elif kind == "status":
                    self.status_var.set(payload)
                elif kind == "codes":
                    self.codes_box.delete("1.0", "end")
                    self.codes_box.insert("end", payload)
                elif kind == "live":
                    label, value, unit = payload
                    self.live_tree.item(label, values=(value, unit))
                    self._live_snapshot[label] = (value, unit)
                elif kind == "readiness":
                    self.readiness_box.delete("1.0", "end")
                    self.readiness_box.insert("end", payload)
                elif kind == "connected":
                    self._set_connected(payload)
                elif kind == "enable_sendlogs":
                    self.sendlogs_btn.config(state="normal")
                elif kind == "enable_update":
                    self.update_btn.config(state="normal")
                elif kind == "enable_ai":
                    self.ai_btn.config(state="normal")
                elif kind == "ai_result":
                    self.set_status("AI diagnosis ready.")
                    self._show_ai_result(payload)
                elif kind == "upload_done":
                    self.status_var.set(payload)
                    self.upload_btn.config(state="normal")
        except queue.Empty:
            pass
        self.after(100, self._drain_queue)

    def _set_connected(self, ok):
        self._tl_sub = (self.port_var.get()[:18] + " · " + self.baud_var.get()) if ok else ""
        self._draw_traffic_light("connected" if ok else "offline")
        state = "normal" if ok else "disabled"
        for b in (self.scan_btn, self.clear_btn, self.live_btn, self.vin_btn, self.readiness_btn,
                  self.freeze_btn, self.perm_btn, self.vehinfo_btn, self.allpids_btn,
                  self.multiecu_btn, self.mode6_btn, self.mode5_btn):
            b.config(state=state)
        self.connect_btn.config(text="Disconnect" if ok else "Connect")

    @staticmethod
    def _port_type_tag(port_info):
        """
        Return "(Bluetooth)", "(USB)", or "" based on pyserial port metadata.
        Bluetooth SPP virtual COM ports advertise themselves in .hwid or
        .description with tokens like "BTHENUM", "Bluetooth", or "BT".
        USB OBD cables typically carry "USB" or "VID:PID" in the hwid.
        """
        combined = f"{port_info.hwid or ''} {port_info.description or ''}".upper()
        if any(tok in combined for tok in ("BTHENUM", "BLUETOOTH", "BTH\\")):
            return "(Bluetooth)"
        if "USB" in combined or "VID:" in combined or "PID:" in combined:
            return "(USB)"
        return ""

    def refresh_ports(self):
        ports = list(serial.tools.list_ports.comports())
        labels = []
        port_map = {}
        for p in ports:
            tag = self._port_type_tag(p)
            tag_str = f"  {tag}" if tag else ""
            label = f"{p.device} — {p.description}{tag_str}"
            labels.append(label)
            port_map[label] = p.device
        self._port_map = port_map
        self.port_combo["values"] = labels
        if labels and not self.port_var.get():
            self.port_var.set(labels[0])
        bt_count = sum(1 for lbl in labels if "(Bluetooth)" in lbl)
        usb_count = sum(1 for lbl in labels if "(USB)" in lbl)
        self.log(
            f"Found {len(labels)} serial port(s)"
            + (f": {usb_count} USB, {bt_count} Bluetooth." if (usb_count or bt_count) else ".")
        )

    def _selected_port(self):
        sel = self.port_var.get()
        return self._port_map.get(sel, sel.split(" ")[0] if sel else None)

    # ---- connection ----
    def toggle_connect(self):
        if self.elm:
            self.disconnect()
        else:
            self.connect()

    def connect(self):
        port = self._selected_port()
        if not port:
            messagebox.showwarning("No port", "Select the ELM327 COM port first.")
            return
        baud = int(self.baud_var.get())
        self._draw_traffic_light("connecting")
        self.set_status(f"Connecting to {port} @ {baud}…")
        self.connect_btn.config(state="disabled")
        threading.Thread(target=self._connect_worker, args=(port, baud), daemon=True).start()

    def _connect_worker(self, port, baud):
        # Auto-baud: try the chosen baud first, then sweep common ELM327 rates if the
        # handshake comes back garbled (no clean ELM/OK response).
        baud_order = [baud] + [b for b in (115200, 38400, 9600, 57600, 500000) if b != baud]
        last_err = None
        for trybaud in baud_order:
            try:
                elm = Elm327(port, trybaud)
                elm.open()
                probe = elm.send("ATZ", timeout=2.0)
                # clean response contains ELM / OK / a v-number; garbage has none
                if not any(k in probe.upper() for k in ("ELM", "OK", "V1", "V2", "V3")):
                    elm.close()
                    last_err = "garbled at %d baud" % trybaud
                    continue
                voltage, ready, details = elm.initialize()
                self.elm = elm
                if trybaud != baud:
                    self.log("Auto-baud: connected at %d (you selected %d)" % (trybaud, baud))
                    self.ui_queue.put(("status", "Auto-baud locked %d. Set the dropdown to match next time." % trybaud))
                for d in details:
                    self.log(d)
                self.log(f"Battery: {voltage}  |  0100: {ready}")
                self.ui_queue.put(("connected", True))
                self.set_status(f"Connected on {port}  •  battery {voltage}")
                self.connect_btn.config(state="normal")
                return
            except Exception as e:
                last_err = str(e)
                try:
                    elm.close()
                except Exception:
                    pass
                continue
        # all bauds failed
        self.set_status("Connect failed: " + str(last_err))
        self.log("ERROR connect: " + str(last_err))
        self.elm = None
        self.ui_queue.put(("connected", False))
        self.connect_btn.config(state="normal")

    def disconnect(self):
        self.live_stop.set()
        if self.elm:
            self.elm.close()
            self.elm = None
        self.ui_queue.put(("connected", False))
        self.live_btn.config(text="Start live data")
        self.set_status("Disconnected.")

    # ---- actions ----
    def _require(self):
        if not self.elm:
            messagebox.showinfo("Not connected", "Connect to the ELM327 cable first.")
            return False
        return True

    def read_codes(self):
        if not self._require():
            return
        threading.Thread(target=self._read_codes_worker, daemon=True).start()

    def _read_codes_worker(self):
        try:
            self.set_status("Reading stored codes (Mode 03)…")
            stored = decode_dtcs(self.elm.send("03", timeout=5.0), "43")
            pending = decode_dtcs(self.elm.send("07", timeout=5.0), "47")
            self._session_stored = stored
            self._session_pending = pending
            lines = []
            lines.append(f"Stored DTCs ({len(stored)}):")
            lines += [f"   {_dtc_line(c)}" for c in stored] or ["   (none)"]
            lines.append("")
            lines.append(f"Pending DTCs ({len(pending)}):")
            lines += [f"   {_dtc_line(c)}" for c in pending] or ["   (none)"]
            if DTC_DB:
                lines.append(f"\n[{len(DTC_DB)} descriptions loaded from shared DB]")
            self.ui_queue.put(("codes", "\n".join(lines)))
            self.set_status(f"Read complete: {len(stored)} stored, {len(pending)} pending.")
        except Exception as e:
            self.set_status(f"Read failed: {e}")
            self.log(f"ERROR read_codes: {e}")

    def clear_codes(self):
        if not self._require():
            return
        if not messagebox.askyesno("Clear codes",
                                   "Clear all stored codes and turn off the check-engine light?\n\n"
                                   "This cannot be undone, and erases freeze-frame data."):
            return
        threading.Thread(target=self._clear_worker, daemon=True).start()

    def _clear_worker(self):
        try:
            self.set_status("Clearing codes (Mode 04)…")
            r = self.elm.send("04", timeout=5.0)
            self.log(f"Mode 04 -> {r}")
            self.ui_queue.put(("codes", "Codes cleared. Re-scan to confirm."))
            self.set_status("Codes cleared.")
        except Exception as e:
            self.set_status(f"Clear failed: {e}")
            self.log(f"ERROR clear: {e}")

    def read_vin(self):
        if not self._require():
            return
        threading.Thread(target=self._vin_worker, daemon=True).start()

    def _vin_worker(self):
        try:
            self.set_status("Reading VIN (Mode 09 PID 02)…")
            resp = self.elm.send("0902", timeout=6.0)
            # extract ASCII from the hex payload
            tokens = [t for t in resp.replace("\r", " ").split() if len(t) == 2]
            chars = []
            for t in tokens:
                try:
                    v = int(t, 16)
                    if 32 <= v < 127:
                        chars.append(chr(v))
                except ValueError:
                    pass
            vin = "".join(chars)
            # VIN is 17 chars; trim leading junk
            vin = "".join(c for c in vin if c.isalnum())[-17:]
            self._session_vin = vin
            self.log(f"VIN raw: {resp}")
            self.ui_queue.put(("codes", f"VIN: {vin or '(not reported)'}"))
            self.set_status(f"VIN: {vin or '(not reported)'}")
        except Exception as e:
            self.set_status(f"VIN read failed: {e}")
            self.log(f"ERROR vin: {e}")

    # ---- I/M readiness ----
    def read_readiness(self):
        if not self._require():
            return
        threading.Thread(target=self._readiness_worker, daemon=True).start()

    def _readiness_worker(self):
        try:
            self.set_status("Reading I/M readiness (Mode 01 PID 01)…")
            result = self.elm.read_readiness()
            lines = []
            mil_str = "ON  (check-engine light is illuminated)" if result["mil_on"] else "OFF"
            lines.append(f"MIL (Check Engine Light): {mil_str}")
            lines.append(f"Stored DTCs reported:     {result['dtc_count']}")
            lines.append("")
            lines.append(f"{'Monitor':<25} {'Status'}")
            lines.append("-" * 40)
            supported_count = 0
            ready_count = 0
            for m in result["monitors"]:
                if not m["supported"]:
                    status = "Not Supported"
                elif m["ready"]:
                    status = "Ready"
                    supported_count += 1
                    ready_count += 1
                else:
                    status = "Not Ready"
                    supported_count += 1
                lines.append(f"{m['name']:<25} {status}")
            lines.append("")
            if supported_count > 0 and ready_count == supported_count:
                lines.append(">>> READY FOR EMISSIONS TEST (all supported monitors complete) <<<")
            elif supported_count > 0:
                lines.append(f"Not ready: {supported_count - ready_count} of {supported_count} supported monitor(s) still incomplete.")
            else:
                lines.append("No monitor data returned — vehicle may not support this PID.")
            self._readiness_result = result
            self.ui_queue.put(("readiness", "\n".join(lines)))
            self.set_status(f"Readiness: {ready_count}/{supported_count} monitors ready.")
        except Exception as e:
            self.set_status(f"Readiness read failed: {e}")
            self.log(f"ERROR readiness: {e}")

    # ---- CSV export ----
    def export_csv(self):
        path = filedialog.asksaveasfilename(
            defaultextension=".csv",
            filetypes=[("CSV files", "*.csv"), ("All files", "*.*")],
            title="Export session to CSV",
            initialfile=f"tcw_scan_{time.strftime('%Y%m%d_%H%M%S')}.csv",
        )
        if not path:
            return
        try:
            with open(path, "w", newline="", encoding="utf-8") as f:
                writer = csv.writer(f)

                # --- meta section ---
                writer.writerow(["# TCW OBD Scanner — session export"])
                writer.writerow(["timestamp", time.strftime("%Y-%m-%d %H:%M:%S")])
                writer.writerow(["vin", self._session_vin or "(not read)"])
                writer.writerow([])

                # --- codes section ---
                writer.writerow(["# CODES"])
                writer.writerow(["type", "code", "description"])
                if self._session_stored or self._session_pending:
                    for c in self._session_stored:
                        writer.writerow(["stored", c, DTC_DB.get(c, "")])
                    for c in self._session_pending:
                        writer.writerow(["pending", c, DTC_DB.get(c, "")])
                else:
                    writer.writerow(["—", "(no codes read this session)", ""])
                writer.writerow([])

                # --- live data section ---
                writer.writerow(["# LIVE DATA SNAPSHOT"])
                writer.writerow(["parameter", "value", "unit"])
                if self._live_snapshot:
                    for label, (value, unit) in self._live_snapshot.items():
                        writer.writerow([label, value, unit])
                else:
                    writer.writerow(["(no live data captured this session)", "", ""])

            self.set_status(f"Exported: {os.path.basename(path)}")
            self.log(f"CSV saved: {path}")
        except Exception as e:
            self.set_status(f"Export failed: {e}")
            self.log(f"ERROR export_csv: {e}")
            messagebox.showerror("Export failed", str(e))

    # ---- API key / cloud config ----
    def _on_api_key_changed(self, *_):
        self._update_upload_btn_state()

    def _save_api_key(self):
        key = self.api_key_var.get().strip()
        self._config["api_key"] = key
        _save_config(self._config)
        self._update_upload_btn_state()
        self.set_status("API key saved." if key else "API key cleared.")

    def _update_upload_btn_state(self):
        key = self.api_key_var.get().strip()
        if key:
            self.upload_btn.config(state="normal")
            self.api_key_hint.config(text="Key set. Click 'Cloud upload' to push the current session.")
        else:
            self.upload_btn.config(state="disabled")
            self.api_key_hint.config(text="Paste your TCW API key here to enable cloud upload.")

    # ---- cloud upload ----
    # ---- Wave 3: multi-ECU scan (AT SH header switching) ----
    # Common 11-bit CAN module request headers (engine, trans, ABS, body, etc.)
    ECU_HEADERS = [
        ("7E0", "Engine (ECM)"), ("7E1", "Transmission (TCM)"),
        ("760", "ABS / Brakes"), ("720", "Body (BCM)"),
        ("7A0", "Airbag (SRS)"), ("7C0", "Instrument cluster"),
    ]

    def scan_all_modules(self):
        if not self._require():
            return
        threading.Thread(target=self._multiecu_worker, daemon=True).start()

    def _multiecu_worker(self):
        try:
            self.set_status("Scanning all modules (this takes a moment)...")
            lines = ["MULTI-MODULE SCAN"]
            found_any = False
            for hdr, name in self.ECU_HEADERS:
                try:
                    self.elm.send("ATSH" + hdr, timeout=2.0)
                    resp = self.elm.send("03", timeout=3.0)
                    codes = decode_dtcs(resp, "43")
                    if "NO DATA" in resp.upper() or "UNABLE" in resp.upper():
                        continue
                    found_any = True
                    if codes:
                        lines.append("")
                        lines.append("%s [%s]: %d code(s)" % (name, hdr, len(codes)))
                        lines += ["   " + _dtc_line(c) for c in codes]
                    else:
                        lines.append("%s [%s]: no codes" % (name, hdr))
                except Exception:
                    continue
            # restore default header behaviour
            try:
                self.elm.send("ATSP0", timeout=2.0)
            except Exception:
                pass
            if not found_any:
                lines.append("Only the engine module responded on this vehicle/bus.")
            self.ui_queue.put(("codes", "\n".join(lines)))
            self.set_status("Multi-module scan complete.")
        except Exception as e:
            self.set_status("Multi-module scan failed: " + str(e))

    # ---- Wave 3: Mode 06 on-board monitor tests ----
    def read_mode6(self):
        if not self._require():
            return
        threading.Thread(target=self._mode6_worker, daemon=True).start()

    def _mode6_worker(self):
        try:
            self.set_status("Reading on-board monitor tests (Mode 06)...")
            lines = ["ON-BOARD MONITOR TESTS (Mode 06)",
                     "Pass/fail + values for non-continuous self-tests (catalyst, O2, EVAP, misfire)."]
            # Mode 06 needs the supported MIDs from 0600; then query each MID.
            try:
                sup = self.elm.send("0600", timeout=3.0)
                lines.append("Supported test groups: " + (sup.strip() or "(none reported)"))
            except Exception:
                pass
            got = 0
            for mid in range(0x01, 0x0C):
                try:
                    r = self.elm.send("06%02X" % mid, timeout=2.5)
                    ru = r.upper()
                    if "NO DATA" in ru or not ru.strip():
                        continue
                    res = self._decode_mode6(r, mid)
                    if res:
                        lines += res; got += 1
                except Exception:
                    continue
            if got == 0:
                lines.append("No monitor test data returned (some vehicles report only via 0600).")
            self.ui_queue.put(("codes", "\n".join(lines)))
            self.set_status("Monitor tests read.")
        except Exception as e:
            self.set_status("Monitor test read failed: " + str(e))

    def _decode_mode6(self, resp, mid):
        """Decode a Mode 06 response: TID, value, min, max -> pass/fail line(s)."""
        blob = "".join(t for t in resp.replace("\r"," ").upper().split() if all(c in "0123456789ABCDEF" for c in t))
        idx = blob.find("46")  # Mode 06 response prefix is 0x46
        out = []
        if idx < 0:
            return out
        data = blob[idx:]
        # each record: 46 MID TID stdID value(2) min(2) max(2) -> step ~ 18 hex chars
        i = 0
        while i + 18 <= len(data):
            try:
                tid = int(data[i+4:i+6], 16)
                val = int(data[i+8:i+12], 16)
                mn = int(data[i+12:i+16], 16)
                mx = int(data[i+16:i+20], 16) if i+20 <= len(data) else 0xFFFF
                status = "PASS" if mn <= val <= mx else "FAIL"
                out.append("  MID%02X TID%02X: val=%d range=%d..%d  %s" % (mid, tid, val, mn, mx, status))
            except Exception:
                break
            i += 20
        return out

    # ---- Wave 3: Mode 05 O2 sensor tests ----
    def read_mode5(self):
        if not self._require():
            return
        threading.Thread(target=self._mode5_worker, daemon=True).start()

    def _mode5_worker(self):
        try:
            self.set_status("Reading O2 sensor tests (Mode 05)...")
            lines = ["O2 SENSOR MONITOR TESTS (Mode 05)"]
            got = 0
            # Common test IDs 0x01 (rich->lean threshold) for each sensor 0x01-0x08
            for tid in (0x01, 0x02, 0x07, 0x08):
                for sensor in range(0x01, 0x09):
                    try:
                        r = self.elm.send("05%02X%02X" % (tid, sensor), timeout=1.5)
                        ru = r.upper()
                        if "NO DATA" in ru or not ru.strip():
                            continue
                        blob = "".join(t for t in ru.replace("\r"," ").split() if all(c in "0123456789ABCDEF" for c in t))
                        j = blob.find("45")
                        if j >= 0 and len(blob) >= j+8:
                            raw = int(blob[j+6:j+10], 16) if len(blob) >= j+10 else 0
                            lines.append("  TID%02X Sensor%d: raw=%d" % (tid, sensor, raw))
                            got += 1
                    except Exception:
                        continue
                if got > 0:
                    break
            if got == 0:
                lines.append("No Mode 05 data (most CAN vehicles report O2 tests via Mode 06 instead).")
            self.ui_queue.put(("codes", "\n".join(lines)))
            self.set_status("O2 tests read.")
        except Exception as e:
            self.set_status("O2 test read failed: " + str(e))

    # ---- Wave 2: full PID auto-discovery (Mode 01) ----
    PID_NAMES = {
        0x04: ("Calculated load", "%"), 0x05: ("Coolant temp", "C"),
        0x06: ("Short fuel trim B1", "%"), 0x07: ("Long fuel trim B1", "%"),
        0x08: ("Short fuel trim B2", "%"), 0x09: ("Long fuel trim B2", "%"),
        0x0A: ("Fuel pressure", "kPa"), 0x0B: ("Intake MAP", "kPa"),
        0x0C: ("Engine RPM", "rpm"), 0x0D: ("Vehicle speed", "km/h"),
        0x0E: ("Timing advance", "deg"), 0x0F: ("Intake air temp", "C"),
        0x10: ("MAF air flow", "g/s"), 0x11: ("Throttle position", "%"),
        0x14: ("O2 B1S1 voltage", "V"), 0x15: ("O2 B1S2 voltage", "V"),
        0x1F: ("Run time since start", "s"), 0x21: ("Distance with MIL on", "km"),
        0x2C: ("Commanded EGR", "%"), 0x2D: ("EGR error", "%"),
        0x2E: ("Commanded EVAP purge", "%"), 0x2F: ("Fuel tank level", "%"),
        0x31: ("Distance since clear", "km"), 0x33: ("Barometric pressure", "kPa"),
        0x42: ("Control module voltage", "V"), 0x43: ("Absolute load", "%"),
        0x44: ("Commanded A/F ratio", ""), 0x45: ("Rel throttle position", "%"),
        0x46: ("Ambient air temp", "C"), 0x47: ("Abs throttle B", "%"),
        0x49: ("Accel pedal D", "%"), 0x4A: ("Accel pedal E", "%"),
        0x4C: ("Commanded throttle", "%"), 0x5C: ("Engine oil temp", "C"),
        0x5E: ("Fuel rate", "L/h"),
    }

    def scan_all_pids(self):
        if not self._require():
            return
        threading.Thread(target=self._allpids_worker, daemon=True).start()

    def _allpids_worker(self):
        try:
            self.set_status("Discovering supported PIDs...")
            supported = self._discover_supported_pids()
            if not supported:
                self.set_status("No PIDs reported (engine off?).")
                return
            lines = ["SUPPORTED LIVE PARAMETERS (%d):" % len(supported)]
            for pid in supported:
                name, unit = self.PID_NAMES.get(pid, ("PID 0x%02X" % pid, ""))
                cmd = "01%02X" % pid
                try:
                    resp = self.elm.send(cmd, timeout=1.5)
                    b = _hex_bytes(resp, "41%02X" % pid)
                    val = self._generic_decode(pid, b)
                    sval = "—" if val is None else ("%g" % val if isinstance(val, float) else str(val))
                except Exception:
                    sval = "err"
                lines.append("  %-26s %s %s" % (name, sval, unit))
            self.ui_queue.put(("codes", "\n".join(lines)))
            self.set_status("PID scan complete: %d supported." % len(supported))
        except Exception as e:
            self.set_status("PID scan failed: " + str(e))

    def _discover_supported_pids(self):
        """Query support bitmaps 0100/0120/0140/0160 and return list of supported PID numbers."""
        supported = []
        for base, probe in ((0x00, "0100"), (0x20, "0120"), (0x40, "0140"), (0x60, "0160")):
            try:
                resp = self.elm.send(probe, timeout=3.0)
                b = _hex_bytes(resp, "41%02X" % base)
                if len(b) < 4:
                    break
                bits = (b[0] << 24) | (b[1] << 16) | (b[2] << 8) | b[3]
                for i in range(32):
                    if bits & (1 << (31 - i)):
                        pidnum = base + i + 1
                        # skip the "next-range supported" PIDs (0x20/0x40/0x60)
                        if pidnum not in (0x20, 0x40, 0x60, 0x80):
                            supported.append(pidnum)
                # stop if this range does not advertise the next range
                if not (bits & 1):
                    break
            except Exception:
                break
        return supported

    def _generic_decode(self, pid, b):
        """Decode common PIDs to a human value; fall back to first byte."""
        if not b:
            return None
        if pid == 0x0C:
            return (b[0]*256 + b[1])/4.0 if len(b) >= 2 else None
        if pid in (0x05, 0x0F, 0x46, 0x5C):
            return b[0] - 40
        if pid in (0x04, 0x11, 0x2C, 0x2F, 0x43, 0x45, 0x47, 0x49, 0x4A, 0x4C):
            return round(b[0]*100/255, 1)
        if pid in (0x06, 0x07, 0x08, 0x09, 0x2D):
            return round((b[0]-128)*100/128, 1)
        if pid == 0x0D:
            return b[0]
        if pid == 0x10:
            return (b[0]*256 + b[1])/100.0 if len(b) >= 2 else None
        if pid == 0x42:
            return (b[0]*256 + b[1])/1000.0 if len(b) >= 2 else None
        if pid in (0x0B, 0x33):
            return b[0]
        if pid == 0x1F:
            return (b[0]*256 + b[1]) if len(b) >= 2 else None
        return b[0]

    # ---- Wave 1: freeze frame (Mode 02) ----
    def read_freeze_frame(self):
        if not self._require():
            return
        threading.Thread(target=self._freeze_worker, daemon=True).start()

    def _freeze_worker(self):
        try:
            self.set_status("Reading freeze frame (Mode 02)...")
            # Mode 02 PID 02 returns the DTC that triggered the freeze frame
            dtc_resp = self.elm.send("0202", timeout=4.0)
            # Read a set of freeze-frame PIDs (frame 00): RPM, load, coolant, speed, STFT/LTFT, MAP
            frame = {}
            for pid, label, decoder, unit in [
                ("020C", "RPM", dec_rpm, "rpm"),
                ("0204", "Engine load", dec_load, "%"),
                ("0205", "Coolant", dec_temp, "C"),
                ("020D", "Speed", dec_speed, "km/h"),
                ("0206", "STFT B1", dec_pct, "%"),
                ("0207", "LTFT B1", dec_pct, "%"),
                ("020B", "MAP", dec_map, "kPa"),
                ("020F", "Intake temp", dec_temp, "C"),
            ]:
                try:
                    r = self.elm.send(pid, timeout=2.0)
                    b = _hex_bytes(r, "42" + pid[2:])
                    v = decoder(b)
                    if v is not None:
                        frame[label] = "%g %s" % (v, unit)
                except Exception:
                    pass
            lines = ["FREEZE FRAME (snapshot when fault set)"]
            tokens = [t for t in dtc_resp.replace("\r", " ").split() if len(t) == 2]
            lines.append("Triggered by DTC data: " + (dtc_resp.strip() or "(none)"))
            if frame:
                for k, v in frame.items():
                    lines.append("  %-14s %s" % (k, v))
            else:
                lines.append("  (no freeze-frame data stored - no recent fault)")
            self.ui_queue.put(("codes", "\n".join(lines)))
            self.set_status("Freeze frame read.")
        except Exception as e:
            self.set_status("Freeze frame failed: " + str(e))

    # ---- Wave 1: permanent DTCs (Mode 0A) ----
    def read_permanent(self):
        if not self._require():
            return
        threading.Thread(target=self._perm_worker, daemon=True).start()

    def _perm_worker(self):
        try:
            self.set_status("Reading permanent DTCs (Mode 0A)...")
            resp = self.elm.send("0A", timeout=5.0)
            codes = decode_dtcs(resp, "4A")
            if codes:
                lines = ["PERMANENT DTCs (cannot be cleared until repair verified):"]
                lines += ["   " + _dtc_line(c) for c in codes]
            else:
                lines = ["PERMANENT DTCs: none (good - nothing unverified)."]
            self.ui_queue.put(("codes", "\n".join(lines)))
            self.set_status("Permanent DTC read complete: %d code(s)." % len(codes))
        except Exception as e:
            self.set_status("Permanent DTC read failed: " + str(e))

    # ---- Wave 1: vehicle info (Mode 09 CALID/CVN) ----
    def read_vehicle_info(self):
        if not self._require():
            return
        threading.Thread(target=self._vehinfo_worker, daemon=True).start()

    def _vehinfo_worker(self):
        try:
            self.set_status("Reading ECU calibration info (Mode 09)...")
            lines = ["VEHICLE / ECU INFO"]
            # CALID (09 04) - ASCII calibration ID
            cal = self.elm.send("0904", timeout=5.0)
            cal_txt = self._ascii_from_hex(cal, "4904")
            lines.append("  Calibration ID (CALID): " + (cal_txt or "(not reported)"))
            # CVN (09 06) - calibration verification number (hex)
            cvn = self.elm.send("0906", timeout=4.0)
            cvn_hex = "".join(t for t in cvn.replace("\r", " ").split() if len(t) == 2)
            lines.append("  Cal Verification (CVN): " + (cvn_hex[-8:].upper() or "(not reported)"))
            if self._session_vin:
                lines.append("  VIN: " + self._session_vin)
            self.ui_queue.put(("codes", "\n".join(lines)))
            self.set_status("Vehicle info read.")
        except Exception as e:
            self.set_status("Vehicle info failed: " + str(e))

    def _ascii_from_hex(self, resp, echo):
        joined = "".join(t for t in resp.replace("\r", " ").upper().split() if all(c in "0123456789ABCDEF" for c in t))
        idx = joined.find(echo.upper())
        if idx >= 0:
            joined = joined[idx + len(echo):]
        chars = []
        for i in range(0, len(joined) - 1, 2):
            try:
                v = int(joined[i:i+2], 16)
                if 32 <= v < 127:
                    chars.append(chr(v))
            except ValueError:
                pass
        return "".join(chars).strip()

    # ---- Wave 1: NHTSA recalls + VIN decode ----
    def check_recalls(self):
        threading.Thread(target=self._recalls_worker, daemon=True).start()

    def _recalls_worker(self):
        import json as _json
        try:
            vin = self._session_vin
            if not vin or len(vin) < 11:
                self.set_status("Read the VIN first (Read VIN button), then check recalls.")
                return
            self.set_status("Decoding VIN + checking recalls (NHTSA)...")
            # Decode VIN
            url = "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/" + vin + "?format=json"
            with urllib.request.urlopen(url, timeout=20) as r:
                dec = _json.loads(r.read().decode("utf-8"))
            res = (dec.get("Results") or [{}])[0]
            make = res.get("Make", ""); model = res.get("Model", ""); year = res.get("ModelYear", "")
            lines = ["VEHICLE: %s %s %s" % (year, make, model)]
            # Recalls by make/model/year
            if make and model and year:
                rurl = ("https://api.nhtsa.gov/recalls/recallsByVehicle?make=%s&model=%s&modelYear=%s"
                        % (urllib.parse.quote(make), urllib.parse.quote(model), year))
                try:
                    with urllib.request.urlopen(rurl, timeout=20) as r2:
                        rec = _json.loads(r2.read().decode("utf-8"))
                    items = rec.get("results", [])
                    if items:
                        lines.append("")
                        lines.append("OPEN RECALLS (%d):" % len(items))
                        for it in items[:12]:
                            lines.append("  - " + (it.get("Component", "") or "Recall") + ": " +
                                         (it.get("Summary", "")[:120]))
                    else:
                        lines.append("No recalls found for this make/model/year.")
                except Exception as e2:
                    lines.append("Recall lookup error: " + str(e2))
            self.ui_queue.put(("codes", "\n".join(lines)))
            self.set_status("Recall check complete.")
        except Exception as e:
            self.set_status("Recall check failed: " + str(e))

    # ---- Wave 1: save session report (HTML) ----
    def save_report(self):
        from tkinter import filedialog
        path = filedialog.asksaveasfilename(
            defaultextension=".html",
            filetypes=[("HTML report", "*.html"), ("All files", "*.*")],
            title="Save diagnostic report",
        )
        if not path:
            return
        try:
            import html as _html, datetime as _dt
            rows = ""
            for c in self._session_stored:
                rows += "<tr><td>%s</td><td>%s</td><td>stored</td></tr>" % (c, _html.escape(DTC_DB.get(c, "")))
            for c in self._session_pending:
                rows += "<tr><td>%s</td><td>%s</td><td>pending</td></tr>" % (c, _html.escape(DTC_DB.get(c, "")))
            live = ""
            for label, (value, unit) in self._live_snapshot.items():
                live += "<tr><td>%s</td><td>%s %s</td></tr>" % (_html.escape(label), _html.escape(str(value)), _html.escape(unit))
            doc = (
                "<html><head><meta charset='utf-8'><title>TCW Diagnostic Report</title>"
                "<style>body{font-family:Segoe UI,Arial,sans-serif;background:#0D1B14;color:#EAF7EF;padding:24px}"
                "h1{color:#42FF91}table{border-collapse:collapse;width:100%%;margin:12px 0}"
                "td,th{border:1px solid #244D36;padding:8px;text-align:left}th{background:#12261C;color:#42FF91}"
                ".muted{color:#8FA99A}</style></head><body>"
                "<h1>Together Car Works - Diagnostic Report</h1>"
                "<p class='muted'>Generated %s</p>"
                "<p><b>VIN:</b> %s</p>"
                "<h2>Trouble Codes</h2><table><tr><th>Code</th><th>Description</th><th>Status</th></tr>%s</table>"
                "<h2>Live Data Snapshot</h2><table><tr><th>Parameter</th><th>Value</th></tr>%s</table>"
                "</body></html>"
            ) % (_dt.datetime.now().strftime("%Y-%m-%d %H:%M"),
                 self._session_vin or "(not read)",
                 rows or "<tr><td colspan=3 class='muted'>No codes</td></tr>",
                 live or "<tr><td colspan=2 class='muted'>No live data</td></tr>")
            with open(path, "w", encoding="utf-8") as f:
                f.write(doc)
            self.log("Report saved: " + path)
            self.set_status("Report saved: " + path)
        except Exception as e:
            self.set_status("Report save failed: " + str(e))

    def ai_diagnose(self):
        self.ai_btn.config(state="disabled")
        self.set_status("Asking AI for a diagnosis...")
        threading.Thread(target=self._ai_worker, daemon=True).start()

    def _ai_worker(self):
        import json as _json
        try:
            codes = list(self._session_stored) + ["(pending) " + c for c in self._session_pending]
            live = {lbl: (str(v) + " " + u).strip() for lbl, (v, u) in self._live_snapshot.items()}
            payload = {
                "vin": self._session_vin or None,
                "codes": codes,
                "live": live or None,
            }
            body = _json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(
                "https://tcw.aiaffiliate.builders/api/ai-diagnose",
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=60) as resp:
                out = _json.loads(resp.read().decode("utf-8"))
            if out.get("ok"):
                analysis = out.get("analysis", "(no analysis)")
                self.ui_queue.put(("ai_result", analysis))
            else:
                self.ui_queue.put(("status", "AI error: " + str(out.get("error"))))
        except Exception as e:
            self.ui_queue.put(("status", "AI diagnose failed: " + str(e)))
        finally:
            self.ui_queue.put(("enable_ai", None))

    def _show_ai_result(self, text):
        win = tk.Toplevel(self)
        win.title("AI Diagnosis")
        win.geometry("560x460")
        box = tk.Text(win, wrap="word", padx=10, pady=10)
        box.insert("1.0", text)
        box.config(state="disabled")
        box.pack(fill="both", expand=True)
        ttk.Button(win, text="Close", command=win.destroy).pack(pady=6)

    def check_for_updates(self):
        self.update_btn.config(state="disabled")
        self.set_status("Checking for updates...")
        threading.Thread(target=self._update_worker, daemon=True).start()

    def _ver_tuple(self, v):
        try:
            return tuple(int(x) for x in str(v).strip().split("."))
        except Exception:
            return (0,)

    def _update_worker(self):
        import json as _json, tempfile, os, sys, subprocess
        try:
            req = urllib.request.Request(_VERSION_MANIFEST, headers={"Cache-Control": "no-cache"})
            with urllib.request.urlopen(req, timeout=20) as r:
                man = _json.loads(r.read().decode("utf-8"))
            latest = man.get("version", "0")
            url = man.get("url", "")
            notes = man.get("notes", "")
            if self._ver_tuple(latest) <= self._ver_tuple(APP_VERSION):
                self.ui_queue.put(("status", "You are up to date (v%s)." % APP_VERSION))
                self.ui_queue.put(("enable_update", None))
                return
            # download new exe to a temp file next to the current one
            self.ui_queue.put(("status", "Downloading update v%s..." % latest))
            cur = sys.executable if getattr(sys, "frozen", False) else os.path.abspath(__file__)
            folder = os.path.dirname(cur)
            new_path = os.path.join(folder, "TCW-OBD-Scanner-new.exe")
            with urllib.request.urlopen(url, timeout=120) as resp, open(new_path, "wb") as out:
                out.write(resp.read())
            # write a small batch that waits, replaces the exe, and relaunches
            if getattr(sys, "frozen", False):
                bat = os.path.join(folder, "_tcw_update.bat")
                with open(bat, "w") as b:
                    b.write("@echo off\r\n")
                    b.write("timeout /t 2 /nobreak >nul\r\n")
                    b.write("move /y \"%s\" \"%s\" >nul\r\n" % (new_path, cur))
                    b.write("start \"\" \"%s\"\r\n" % cur)
                    b.write("del \"%%~f0\"\r\n")
                self.ui_queue.put(("status", "Update ready (v%s) - restarting..." % latest))
                subprocess.Popen(["cmd", "/c", bat], creationflags=0x00000008)
                self.after(500, self.on_close)
            else:
                self.ui_queue.put(("status", "Downloaded v%s to %s" % (latest, new_path)))
                self.ui_queue.put(("enable_update", None))
        except Exception as e:
            self.ui_queue.put(("status", "Update failed: " + str(e)))
            self.ui_queue.put(("log", "Update check failed: " + str(e)))
            self.ui_queue.put(("enable_update", None))

    def send_logs(self):
        """Upload the entire log box to the TCW debug endpoint so support can read it."""
        try:
            text = self.log_box.get("1.0", "end").strip()
        except Exception:
            text = ""
        if not text:
            text = "(log was empty)"
        # include a little context
        import platform
        header = "TCW .exe log | %s | port=%s baud=%s\n\n" % (
            platform.platform(),
            getattr(self, "port_var", None).get() if hasattr(self, "port_var") else "?",
            getattr(self, "baud_var", None).get() if hasattr(self, "baud_var") else "?",
        )
        self.sendlogs_btn.config(state="disabled")
        self.set_status("Sending logs…")
        threading.Thread(target=self._send_logs_worker, args=(header + text,), daemon=True).start()

    def _send_logs_worker(self, text):
        import json as _json
        try:
            body = _json.dumps({"source": "exe", "label": "send-logs", "text": text}).encode("utf-8")
            req = urllib.request.Request(
                "https://tcw.aiaffiliate.builders/api/debug-log",
                data=body,
                headers={"Content-Type": "application/json", "x-log-token": "tcwlogs2026"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=20) as resp:
                out = _json.loads(resp.read().decode("utf-8"))
            log_id = out.get("id", "?")
            self.ui_queue.put(("log", "Logs sent. ID: " + str(log_id)))
            self.ui_queue.put(("status", "Logs sent - tell support the ID: " + str(log_id)))
        except Exception as e:
            self.ui_queue.put(("log", "Send logs failed: " + str(e)))
            self.ui_queue.put(("status", "Send logs failed: " + str(e)))
        finally:
            self.ui_queue.put(("enable_sendlogs", None))

    def cloud_upload(self):
        key = self.api_key_var.get().strip()
        if not key:
            messagebox.showinfo("No API key", "Paste your Shop API key in the field above first.")
            return
        # auto-save key whenever Upload is clicked
        self._config["api_key"] = key
        _save_config(self._config)
        self.upload_btn.config(state="disabled")
        self.set_status("Uploading to cloud…")
        threading.Thread(target=self._upload_worker, args=(key,), daemon=True).start()

    def _upload_worker(self, api_key):
        try:
            codes = [c for c in self._session_stored] + [c for c in self._session_pending]
            live_dict = {label: {"value": v, "unit": u} for label, (v, u) in self._live_snapshot.items()}
            payload = {
                "vin": self._session_vin or None,
                "vehicle": None,
                "mileage": None,
                "codes": codes,
                "live": live_dict if live_dict else None,
                "readiness": self._readiness_result,
                "notes": None,
                "source": "exe",
            }
            body = json.dumps(payload).encode("utf-8")
            req = urllib.request.Request(
                _RELAY_ENDPOINT,
                data=body,
                method="POST",
                headers={
                    "Content-Type": "application/json",
                    "x-api-key": api_key,
                },
            )
            with urllib.request.urlopen(req, timeout=15) as resp:
                resp_body = resp.read().decode("utf-8", errors="replace")
                try:
                    resp_json = json.loads(resp_body)
                    session_id = resp_json.get("id") or resp_json.get("session_id") or ""
                    msg = f"Uploaded to cloud (session #{session_id})" if session_id else "Uploaded to cloud."
                except Exception:
                    msg = "Uploaded to cloud."
            self.log(f"Cloud upload OK: {msg}")
            self.ui_queue.put(("upload_done", msg))
        except urllib.error.HTTPError as e:
            if e.code == 401:
                msg = "Cloud upload failed: Invalid API key."
            else:
                try:
                    detail = e.read().decode("utf-8", errors="replace")[:200]
                except Exception:
                    detail = str(e)
                msg = f"Cloud upload failed: HTTP {e.code} — {detail}"
            self.log(f"ERROR upload: {msg}")
            self.ui_queue.put(("upload_done", msg))
        except Exception as e:
            msg = f"Cloud upload failed: {e}"
            self.log(f"ERROR upload: {e}")
            self.ui_queue.put(("upload_done", msg))

    # ---- live data ----
    def toggle_live(self):
        if not self._require():
            return
        if self.live_thread and self.live_thread.is_alive():
            self.live_stop.set()
            self.live_btn.config(text="Start live data")
            self.set_status("Live data stopped.")
        else:
            self.live_stop.clear()
            self.live_thread = threading.Thread(target=self._live_worker, daemon=True)
            self.live_thread.start()
            self.live_btn.config(text="Stop live data")
            self.set_status("Streaming live data…")

    def _live_worker(self):
        import csv as _csv, datetime as _dt, os as _os, tempfile as _tmp
        # Road-test logging: write every cycle to a timestamped CSV in the user temp folder.
        log_path = _os.path.join(_tmp.gettempdir(),
            "tcw-livelog-" + _dt.datetime.now().strftime("%Y%m%d-%H%M%S") + ".csv")
        self._live_log_path = log_path
        labels = [l for _, l, _, _, _ in LIVE_PIDS]
        try:
            logf = open(log_path, "w", newline="", encoding="utf-8")
            writer = _csv.writer(logf)
            writer.writerow(["timestamp"] + labels)
            self.ui_queue.put(("status", "Live logging to " + log_path))
        except Exception:
            logf = None; writer = None
        try:
            while not self.live_stop.is_set() and self.elm:
                row_vals = {}
                for pid_cmd, label, echo, decoder, unit in LIVE_PIDS:
                    if self.live_stop.is_set():
                        break
                    try:
                        resp = self.elm.send(pid_cmd, timeout=1.0)
                        b = _hex_bytes(resp, echo)
                        val = decoder(b)
                        shown = "—" if val is None else (f"{val:g}" if isinstance(val, float) else str(val))
                        self.ui_queue.put(("live", (label, shown, unit)))
                        row_vals[label] = "" if val is None else val
                    except Exception:
                        self.ui_queue.put(("live", (label, "err", unit)))
                        row_vals[label] = ""
                if writer:
                    try:
                        writer.writerow([_dt.datetime.now().strftime("%H:%M:%S")] + [row_vals.get(l, "") for l in labels])
                        logf.flush()
                    except Exception:
                        pass
                time.sleep(0.25)
        finally:
            if logf:
                try: logf.close()
                except Exception: pass

    def on_close(self):
        self.live_stop.set()
        if self.elm:
            self.elm.close()
        self.destroy()


if __name__ == "__main__":
    app = ScannerApp()
    app.protocol("WM_DELETE_WINDOW", app.on_close)
    app.mainloop()
