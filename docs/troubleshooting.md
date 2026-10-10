# Troubleshooting Folio

Folio is still in development. Phone makers, Android updates, the Google app and widget apps can all change how
things behave, so here's what to try when something's off.

## Folio isn't my Home

Press Home and pick Folio, or open Folio and tap **Choose Home App** / **Set as home app**. To switch away, use
**Change home app** in Folio Settings or Android's **Settings › Apps › Default apps › Home app**.

## An update won't install

Android only updates an app when the new APK is signed with the same key. The debug, fast and release builds can use
different keys, so one can't install over another.

Don't uninstall just to try a different build. That wipes your layout, wallpaper and widget connections. Save a backup
first (**Folio Settings › Backup › Save Backup…**), and expect widgets from other apps to need **Reconnect** afterwards.

## Pull-down gestures don't open Notification or Control Center

Those need Folio's accessibility service. Open **Folio Settings › Privacy & Permissions** and turn it on in Android's
Accessibility settings. Folio never turns it on by itself. If it just started, swipe down again.

- Top left opens Notification Center, top right opens Control Center, lower on Home opens Spotlight.
- A dock that's already scrolled keeps the downward swipe for itself.

## The Today View or Discover is empty

The Today View is Folio's own and works offline. Google Discover needs the Google app and depends on your phone
supporting it. If a recovery card shows up, tap **Retry** or **Open Google**, and use **Back to Home** or Back to
leave. Google handles swipes inside its own feed, so a short swipe that starts inside the feed may bounce back.

## Search doesn't open Google

If **Search opens the Google app** is on and the Google app can't open its search screen, Folio uses Spotlight
instead. Turn the setting off to always use Spotlight.

## A widget won't add or finish setting up

Android decides whether a widget can be added, and some apps need their own setup or permissions.

- **Widget not added**: dismiss it and try again.
- **Finish setup**: tap to continue, or cancel to remove the placeholder.
- **Reconnect**: shown after restoring a backup. Tap it and approve Android's prompt. Use **Replace** if the app isn't installed anymore.

Don't clear Folio's storage or the widget app's data to fix a widget. That breaks the connection for good.

## A widget won't move or resize

Hold still until it lifts, then drag. On scrolling widgets like a calendar list, moving first scrolls the widget
instead. Long press and let go for the widget menu, then **Resize on Home**. A red outline means that size would overlap
something, go off the page, or is bigger or smaller than the widget allows.

## Work apps are missing

Open the App Library and switch to **Work**. If the work profile is paused, tap **Turn on work apps**. If your IT admin
keeps it off, Folio can't open those apps or add their widgets.

## Holding the side key does nothing

1. Make Folio your digital assistant: Settings › Apps › Default apps › Digital assistant app › Folio. If it was already
   Folio, pick a different app and then Folio again, so Android picks up Folio's assistant service.
2. On Samsung, set Side button › Press and hold › Digital assistant.
3. Using Good Lock's **RegiStar**? Its side key action runs before Android's. Set RegiStar's press-and-hold action to
   Digital assistant, or turn it off.

## Wallpaper didn't change

Picking a photo shows a preview first. Tap **Apply** to use it; **Cancel** keeps the old one. Backups don't include
photos.

## Folio keeps crashing

After a few crashes in a row Folio opens in **Safe Mode** with tweaks off. Go to **Help › Safe Mode & crash reports** to
see what happened, turn off whatever you changed last, or share the report.

## Reporting a bug

Include the Folio version (**Help › What's New**), your phone, Android version, whether it was folded or unfolded, and
what you did right before it happened. Check screenshots and crash reports before sharing: they can show your apps,
widgets, notifications or account names.
