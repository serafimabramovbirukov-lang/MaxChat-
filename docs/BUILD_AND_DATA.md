# MaxChat — Android build and data-preservation notes

MaxChat is an Android application written in Kotlin. The current app uses Firebase Realtime Database for user and chat records, SharedPreferences for the local signed-in profile/chat list, and Cloudinary unsigned uploads for media.

## Existing data to preserve

The current client reads and writes these Realtime Database paths:

- `/users/{phone}` — display name, phone, online state, and the legacy password field.
- `/chats/{chatId}` — messages and chat metadata such as typing state.

Do not delete, rename, or reset these paths when updating the app. Keep the existing `applicationId` (`com.swill.maxchat`) so Android treats updates as the same installed app. Keep the Firebase project and its configuration unchanged unless a deliberate migration is planned.

The local preferences file is `MaxChatPrefs`; it stores the current phone/name and local chat history. The app currently uses the same chat and user identifiers as the existing database.

## Important security work before public release

The current legacy registration/login implementation writes and compares passwords as plain text in Realtime Database. This is not safe for a production messaging service. Do not expose this database publicly or assume that client-side checks protect it.

A secure migration must be planned before changing login: introduce Firebase Authentication (or a trusted backend), migrate existing users through a verified account-recovery flow, and then remove legacy password fields only after successful migration. Never silently reset user passwords or delete existing user/chat records. Also review Firebase Realtime Database Security Rules and restrict each user to their own profile and only chats they are authorized to access.

The Cloudinary upload preset is embedded in the client. Unsigned presets must be restricted in the Cloudinary console; a production deployment should use a trusted backend to issue short-lived upload authorization.

## Build a debug APK

GitHub Actions runs the Android build for pull requests to `main`, pushes to `main` and `feature/**`, and manual workflow dispatches.

1. Open the repository's **Actions** tab.
2. Open the **Build APK** workflow and wait for the run to finish.
3. Download the `MaxChat-debug` artifact from the completed run.
4. Extract `app-debug.apk` and install it on an Android device. Android may ask you to allow installation from that source.

The debug APK is for testing. A Play Store or public release needs a signed release build and a managed signing key.

## Compatibility policy for this update

- Keep the Firebase project and existing `/users` and `/chats` records.
- Keep `applicationId = "com.swill.maxchat"`.
- Do not change the current database schema until a backward-compatible migration has been tested with existing accounts.
