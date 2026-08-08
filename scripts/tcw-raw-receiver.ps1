# tcw-raw-receiver.ps1 — Raw TCP fallback receiver (port 8766).
# The tablet auto-falls-back to this if the HTTP upload fails twice with HTTP errors.
#
# Protocol:
#   Client sends: "<name>|<size>|<sha256>\n" then exactly <size> bytes.
#   Server saves bytes to $HOME\TCWBundles\<name>, verifies sha256, replies:
#   "OK <name> <size> <sha256>\n"  or  "ERR <reason>\n"
#
# TODO: Implement full raw-TCP receiver if the HTTP path proves unreliable on-site.
# For now this is a documented stub so the install script can reference it.

param([int]$Port = 8766)

Write-Host "TCW Raw TCP receiver — stub (not yet implemented)"
Write-Host "The HTTP receiver on port 8765 is the primary path."
Write-Host "This script is reserved for TCP fallback if needed."
Write-Host ""
Write-Host "If you need the raw receiver, open a GitHub issue or drop a prompt in cursor-dispatch/outbox/."
exit 0
