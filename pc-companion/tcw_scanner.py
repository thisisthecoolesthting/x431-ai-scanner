"""
TCW Standalone OBD Scanner — Windows companion (.exe)
=====================================================
Talks to an ELM327 USB-to-OBD cable over a COM (serial) port.

Mirrors the Android standalone scanner's ELM327 logic:
  init sequence:  ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0, ATAT2
  read codes:     Mode 03 (stored DTCs) + Mode 07 (pending)
  clear codes:    Mode 04 (with confirmation)
  live data:      Mode 01 PIDs (RPM, speed, coolant, throttle, intake, MAP, load)

No external GUI deps — pure Tkinter (stdlib) + pyserial.
Build to a single .exe with:  pyinstaller --onefile --noconsole --name TCW-OBD-Scanner tcw_scanner.py

(c) Together Car Works — debug/diagnostic tool.
"""

import threading
import time
import queue
import tkinter as tk
from tkinter import ttk, messagebox

try:
    import serial
    import serial.tools.list_ports
except ImportError:
    raise SystemExit("pyserial is required:  pip install pyserial")


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
        self.title("TCW OBD Scanner — ELM327 (USB)")
        self.geometry("720x560")
        self.minsize(640, 480)
        self.elm = None
        self.live_thread = None
        self.live_stop = threading.Event()
        self.ui_queue = queue.Queue()
        self._build_ui()
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
        self.baud_var = tk.StringVar(value="38400")
        ttk.Combobox(top, textvariable=self.baud_var, width=8, state="readonly",
                     values=["38400", "9600", "115200", "57600", "500000"]).pack(side="left")

        self.connect_btn = ttk.Button(top, text="Connect", command=self.toggle_connect)
        self.connect_btn.pack(side="left", padx=10)

        # status bar
        self.status_var = tk.StringVar(value="Not connected. Plug in the ELM327 cable, pick its COM port, and Connect.")
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

        # notebook: codes / live / log
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
                elif kind == "connected":
                    self._set_connected(payload)
        except queue.Empty:
            pass
        self.after(100, self._drain_queue)

    def _set_connected(self, ok):
        state = "normal" if ok else "disabled"
        for b in (self.scan_btn, self.clear_btn, self.live_btn, self.vin_btn):
            b.config(state=state)
        self.connect_btn.config(text="Disconnect" if ok else "Connect")

    def refresh_ports(self):
        ports = list(serial.tools.list_ports.comports())
        labels = [f"{p.device} — {p.description}" for p in ports]
        self._port_map = {f"{p.device} — {p.description}": p.device for p in ports}
        self.port_combo["values"] = labels
        if labels and not self.port_var.get():
            self.port_var.set(labels[0])
        self.log(f"Found {len(labels)} serial port(s).")

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
                if 'elm' in dir(): elm.close()
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
            lines = []
            lines.append(f"Stored DTCs ({len(stored)}):")
            lines += [f"   {c}" for c in stored] or ["   (none)"]
            lines.append("")
            lines.append(f"Pending DTCs ({len(pending)}):")
            lines += [f"   {c}" for c in pending] or ["   (none)"]
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
            self.log(f"VIN raw: {resp}")
            self.ui_queue.put(("codes", f"VIN: {vin or '(not reported)'}"))
            self.set_status(f"VIN: {vin or '(not reported)'}")
        except Exception as e:
            self.set_status(f"VIN read failed: {e}")
            self.log(f"ERROR vin: {e}")

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
