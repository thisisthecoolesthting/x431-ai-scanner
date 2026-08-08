/**
 * Frida hook: capture VCI Bluetooth / LocalSocket traffic on X431 tablet.
 * Usage: frida -U -f com.cnlaunch.x431pro -l scripts/frida-vci-intercept.js --no-pause
 */
Java.perform(function () {
  function logHex(tag, bytes) {
    if (!bytes) return;
    var out = [];
    for (var i = 0; i < bytes.length; i++) {
      var b = (bytes[i] & 0xff).toString(16);
      if (b.length < 2) b = "0" + b;
      out.push(b);
    }
    console.log(tag + " len=" + bytes.length + " hex=" + out.join(""));
  }

  try {
    var LocalSocketClient = Java.use("com.cnlaunch.bluetooth.localsocket.LocalSocketClient");
    LocalSocketClient.send.overload("[B").implementation = function (arr) {
      logHex("[VCI SEND]", arr);
      return this.send(arr);
    };
  } catch (e) {
    console.log("LocalSocketClient hook skipped: " + e);
  }

  try {
    var ByteHexHelper = Java.use("com.cnlaunch.bluetooth.ByteHexHelper");
    ByteHexHelper.bytesToHexString.overload("[B").implementation = function (arr) {
      var hex = this.bytesToHexString(arr);
      console.log("[HEX OUT] " + hex);
      return hex;
    };
    ByteHexHelper.hexStringToBytes.overload("java.lang.String").implementation = function (s) {
      console.log("[HEX IN] " + s);
      return this.hexStringToBytes(s);
    };
  } catch (e) {
    console.log("ByteHexHelper hook skipped: " + e);
  }
});
