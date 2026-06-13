# TCW OBD Scanner — Windows companion (.exe)

A standalone Windows desktop scanner that talks to an **ELM327 USB-to-OBD cable**
over a COM (serial) port. It mirrors the Android standalone scanner's ELM327 logic,
so you can diagnose a car straight from a laptop using the USB cable.

## Run it

1. Plug the **ELM327 USB-to-OBD cable** into the car's OBD-II port and into your PC's USB.
   - Windows installs it as a COM port (CH340, FTDI, Prolific, or CP210x driver).
   - If Windows doesn't recognize it, install the cable's USB-serial driver (usually CH340).
2. Turn the ignition to **ON** (engine running for live data).
3. Double-click **`dist\TCW-OBD-Scanner.exe`**.
4. Pick the cable's COM port from the dropdown (use **Refresh** if it isn't listed), leave
   Baud at **38400** (try **9600** if connect fails), and click **Connect**.

## What it does

- **Connect** — runs the ELM327 init sequence: `ATZ ATE0 ATL0 ATS0 ATH0 ATSP0 ATAT2`,
  reads battery voltage and protocol (`0100`).
- **Read codes** — Mode 03 (stored DTCs) + Mode 07 (pending), decoded to P/C/B/U codes.
- **Clear codes** — Mode 04, **with a confirmation prompt** (erases codes + freeze frame).
- **Read VIN** — Mode 09 PID 02.
- **Live data** — streams Mode 01 PIDs: RPM, speed, coolant temp, throttle, intake air temp,
  MAP, engine load, MAF.

## Build it yourself

```
pip install pyserial pyinstaller
cd pc-companion
pyinstaller --onefile --noconsole --name TCW-OBD-Scanner tcw_scanner.py
```
Output: `pc-companion/dist/TCW-OBD-Scanner.exe` (~10 MB, no Python install needed to run).

## Notes / limits

- ELM327 **clones** vary; if a command times out, try a lower baud or re-seat the cable.
- DTC **descriptions** are not bundled here (codes only). The Android app uses an AI lookup
  for plain-English explanations; this companion is the lightweight serial tool.
- This is a debug/diagnostic utility — clearing codes does not fix the underlying fault.
