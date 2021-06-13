## v1.3.0
- Add import data feature, from exported JSON data (#11).
- Add periodical auto export feature.
- Add option to separate checked and unchecked items in list notes.
- Fixed export data crash (#18).
- Attenuated impact of potential bug where recurring alarms are not set.
  If this happens now, launching the app will set the alarm correctly again.

## v1.2.0
- Added reminders with notifications
    - Reminder can be added from main screen or edit screen
    - Next reminder event date is shown as chip in note preview
- Added labels (note tagging)
    - Labels can be added from main screen or edit screen
    - Labels set on notes are shown in preview, up to customizable limit.
    - Notes can be seen by label by clicking on corresponding drawer item.
- Added ability to pin active notes, showing them first in the list.
- Added option to show note in note preview.
- Added date in edit screen (last modified or creation date)
- Added customizable swipe action in main screen (archive, delete, none).
- Added option to strikethrough checked items.
- Added action to delete checked items and check all items for list notes.
- Added app shortcuts for Android N+ (new text note, new list note, show reminders).
- Checked items text now changes color.
- Changed distance threshold for swiping note action.
- Changed status changes not to update last modified date.
- Removed copy and share actions for deleted notes.
- Fixed auto-correction not enabled in text fields.
- Fixed formatting not removed when pasting text.
- Fixed move action not changed for a selected archived item after undoing archive action through
the Snackbar, in search fragment.
- Fixed selection action mode not dismissed when using edit intent.
- Fixed concurrent exception when restoring main fragment after process death.

## v1.1.0
- Removed synchronization feature.
- Fixed crash when navigating to two destination at the same time.

### v1.0.1
- Fixed links in Settings screen for privacy policy and terms & conditions.

# v1.0.0
- **Initial release**
