# Permissions — what AK Jarvis asks for and why

Every permission below is requested **in context, only when you use the related feature**, and can be revoked at any time (in the app: **Settings → Permissions**, or in Android system settings).

| Permission | Type | Why it's needed | What happens if you deny it |
|---|---|---|---|
| `INTERNET` | Normal | Cloud AI requests and web search | App works in Offline Mode |
| `RECORD_AUDIO` | **Runtime** | Voice input (mic button) and optional wake-word service | Typing still works; voice features disabled |
| `READ_CONTACTS` | **Runtime, opt-in** | Resolving a name to a number when you say "mama ko call karo". The dialer still opens — nothing is called automatically | Contact-name calling disabled; "call <number>" still works |
| `POST_NOTIFICATIONS` | **Runtime** (Android 13+) | Reminder notifications | Reminders are saved but silently |
| `SCHEDULE_EXACT_ALARM` | Special (Android 12+) | Reminders fire exactly on time | Reminders may fire a few minutes late (automatic fallback to inexact alarms) |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Normal | Keeps the optional wake-word service alive with a visible notification | Wake word disabled (service never starts unless you enable it) |
| `RECEIVE_BOOT_COMPLETED` | Normal | Re-arms your reminders after reboot | Reminders need to be re-created after reboot |
| Notification Listener access | **Special, opt-in** | Reading notification titles/text when you ask "read my notifications" | That command reports "no access"; everything else works |
| Package visibility (`queries`) | Normal | Listing launchable apps for "open \<app\>" | App launching by name is limited |

## What the app will NEVER do

- Record audio silently — the mic runs only during an explicit listening session or the wake-word service you enabled (which shows a permanent notification).
- Ask for, store, or transmit passwords, OTPs, or banking codes.
- Send SMS, place calls, or make purchases by itself — those commands only open the dialer/messaging app; you press the final button.
- Upload your notes, reminders, chat history or notifications anywhere. All of it stays in the app's private storage.

## How to revoke everything

1. **In the app:** Settings → Permissions → manage each item.
2. **In Android:** Settings → Apps → AK Jarvis → Permissions.
3. **Notification listener:** Settings → Notification access (or the button in the app).
4. **Wake word:** toggle it off in Settings → Voice; the foreground service stops immediately.
