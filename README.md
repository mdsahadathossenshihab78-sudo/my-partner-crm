# My Partner CRM

A separate Android app for Shihab (bdxsukuna03, admin) and one partner (mypartner).

- App name: My Partner CRM
- Package: com.telesales.partner
- Version: 1.0 (versionCode 1)

## Users (login: typed Gmail + Agent ID)

- Shihab (admin): mdsahadathossenshihab78@gmail.com / bdxsukuna03
- Partner: mdsahadathossenshihab1@gmail.com / mypartner

Login is validated against agent-database.json in this repo.

## Data

Numbers live in the Google Sheet "Telesales (Team-03)": each user has their own tab
(header row 5, data from row 6, columns A-F: MOBILE NUMBER | CALLING STATUS |
Valid Conversation | 1ST CALL REMARK | 2ND CALL REMARK | Others number).

- Shihab: tab bdxsukuna03
- Partner: tab mypartner

## Number assignment (admin only)

In the admin Dashboard there is a panel to give numbers to the partner. The admin
enters how many numbers to give (for example 10) and they are MOVED from the
bdxsukuna03 tab to the partner mypartner tab: only numbers not called yet, first
in sheet order. Assigned numbers leave the admin calling list, but the dashboard
total-numbers count keeps showing the combined total (remaining admin numbers +
the partner assigned numbers).

The admin dashboard and report show admin work AND partner work combined (total
numbers, calls done, connected, registration, FTD, total amount). The partner sees
only their own numbers, call list and dashboard.

The assignment system only ever moves numbers out of the bdxsukuna03 tab. No other
agent or user list is touched. Partner numbers never appear in the main Telesales
Status app, which reads only the logged-in agent own tab.

## Build

Pushing to main (or a manual run) triggers the "Build Telesales Native APK" workflow
(.github/workflows/build-native.yml). It builds the debug APK with Gradle and uploads
the artifact "telesales-native-apk".

## Web version

index.html is the web twin of the app with the same login, dashboard, assignment
panel and report.
