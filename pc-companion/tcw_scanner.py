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

DTC_DB = _load_dtc_db()


# ----------------------------------------------------------------------------
# Cloud-upload config  (persisted to ~/.tcw_scanner.json)
# ----------------------------------------------------------------------------
_CONFIG_PATH = os.path.join(os.path.expanduser("~"), ".tcw_scanner.json")
_RELAY_ENDPOINT = "https://tcw.aiaffiliate.builders/api/relay/session"


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
    """Strip whitespace/echo, return data byte ints after the mode echo (e.g. '41 0C')."""
    tokens = resp.replace("\r", " ").replace("\n", " ").split()
    hexes = [t for t in tokens if len(t) == 2 and all(ch in "0123456789ABCDEFabcdef" for ch in t)]
    if mode_echo in resp.replace(" ", ""):
        # find the echo position
        joined = "".join(hexes)
        idx = joined.find(mode_echo)
        if idx >= 0:
            rest = joined[idx + len(mode_echo):]
            return [int(rest[i:i + 2], 16) for i in range(0, len(rest) - 1, 2)]
    return [int(h, 16) for h in hexes]


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
        self.title("TCW OBD Scanner — ELM327 (USB / Bluetooth)")
        self.geometry("720x600")
        self.minsize(640, 520)
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
    def _build_ui(self):
        pad = {"padx": 8, "pady": 6}
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
            foreground="#555555",
            font=("TkDefaultFont", 8),
            wraplength=680,
            justify="left",
        )
        bt_hint.pack(fill="x", padx=8, pady=(0, 2))

        # status bar
        self.status_var = tk.StringVar(value="Not connected. Plug in the ELM327 adapter (USB or Bluetooth), pick its COM port, and Connect.")
        status = ttk.Label(self, textvariable=self.status_var, relief="sunken", anchor="w")
        status.pack(fill="x", side="bottom")

        # action buttons
        actions = ttk.Frame(self); actions.pack(fill="x", **pad)
        self.scan_btn = ttk.Button(actions, text="Read codes", command=self.read_codes, state="disabled")
        self.scan_btn.pack(side="left")
        self.clear_btn = ttk.Button(actions, text="Clear codes", command=self.clear_codes, state="disabled")
        self.clear_btn.pack(side="left", padx=6)
        self.live_btn = ttk.Button(actions, text="Start live data", command=self.toggle_live, state="disabled")
        self.live_btn.pack(side="left", padx=6)
        self.vin_btn = ttk.Button(actions, text="Read VIN", command=self.read_vin, state="disabled")
        self.vin_btn.pack(side="left", padx=6)
        self.readiness_btn = ttk.Button(actions, text="Readiness", command=self.read_readiness, state="disabled")
        self.readiness_btn.pack(side="left", padx=6)
        self.export_btn = ttk.Button(actions, text="Export CSV", command=self.export_csv)
        self.export_btn.pack(side="left", padx=6)
        self.upload_btn = ttk.Button(actions, text="Cloud upload", command=self.cloud_upload, state="disabled")
        self.upload_btn.pack(side="left", padx=6)
        self.sendlogs_btn = ttk.Button(actions, text="Send Logs", command=self.send_logs)
        self.sendlogs_btn.pack(side="left", padx=6)

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

        # codes tab
        self.codes_box = tk.Text(nb, height=8, wrap="word")
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
        self.readiness_box = tk.Text(nb, height=8, wrap="word", font=("Courier New", 10))
        nb.add(self.readiness_box, text="Readiness")

        # log tab
        self.log_box = tk.Text(nb, height=8, wrap="word")
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
                elif kind == "upload_done":
                    self.status_var.set(payload)
                    self.upload_btn.config(state="normal")
        except queue.Empty:
            pass
        self.after(100, self._drain_queue)

    def _set_connected(self, ok):
        state = "normal" if ok else "disabled"
        for b in (self.scan_btn, self.clear_btn, self.live_btn, self.vin_btn, self.readiness_btn):
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
        self.set_status(f"Connecting to {port} @ {baud}…")
        self.connect_btn.config(state="disabled")
        threading.Thread(target=self._connect_worker, args=(port, baud), daemon=True).start()

    def _connect_worker(self, port, baud):
        try:
            elm = Elm327(port, baud)
            elm.open()
            voltage, ready, details = elm.initialize()
            self.elm = elm
            for d in details:
                self.log(d)
            self.log(f"Battery: {voltage}  |  0100: {ready}")
            self.ui_queue.put(("connected", True))
            self.set_status(f"Connected on {port}  •  battery {voltage}")
        except Exception as e:
            self.set_status(f"Connect failed: {e}")
            self.log(f"ERROR connect: {e}")
            try:
                if 'elm' in dir():
                    elm.close()
            except Exception:
                pass
            self.elm = None
            self.ui_queue.put(("connected", False))
        finally:
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
        while not self.live_stop.is_set() and self.elm:
            for pid_cmd, label, echo, decoder, unit in LIVE_PIDS:
                if self.live_stop.is_set():
                    break
                try:
                    resp = self.elm.send(pid_cmd, timeout=1.0)
                    b = _hex_bytes(resp, echo)
                    val = decoder(b)
                    shown = "—" if val is None else (f"{val:g}" if isinstance(val, float) else str(val))
                    self.ui_queue.put(("live", (label, shown, unit)))
                except Exception:
                    self.ui_queue.put(("live", (label, "err", unit)))
            time.sleep(0.25)

    def on_close(self):
        self.live_stop.set()
        if self.elm:
            self.elm.close()
        self.destroy()


if __name__ == "__main__":
    app = ScannerApp()
    app.protocol("WM_DELETE_WINDOW", app.on_close)
    app.mainloop()
