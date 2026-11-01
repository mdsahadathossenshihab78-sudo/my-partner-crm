# Telesales Status App — bdxsukuna30 only
- Spreadsheet: Telesales (Team-03), tab bdxsukuna30 only, header row 5 (A-F), data rows 6-55 (50 rows).
- Agent: SUKUNA, Agent ID bdxsukuna30 (from tab header).
- App: Google sign-in with Sheets scope, live read A6:F55, per-row status save to B{row} via Sheets API.
- Status options: blank, call connected, not connected. Other columns read-only in v1.
- Spreadsheet ID is wired into index.html only (not in chat/memory). Signed-in account for testing is Editor (not Owner) - editor permission is sufficient to write status cells.
- NOT yet live, NOT yet tested with a real status change + revert.
