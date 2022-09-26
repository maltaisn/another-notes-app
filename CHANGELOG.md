## v1.4.5 (2022-09-25)
- Fixed cursor moving to first line when scrolling (#60, thanks to @nhoeher)

## v1.4.4 (2022-07-24)
- Added separate swipe left & right actions, with animated icon shown on swipe (#36).
- Fixed cursor going to end of note when scrolling long text notes (#63, potentially #60).
- Fixed movement with arrow keys not working in text fields when editing note (#67).
- Fixed crash with notification on Android 12 (#65).

## v1.4.3 (2022-01-30)
- Fixed critical crash occuring when using reminders (#61, #62).
- Allow blank notes with a reminder.
- Fixed deleting and archiving notes not changing last modified date, messing with recycle bin delay (#56).

## v1.4.2 (2022-01-09)
- Added new translations:
    - Arabic: @afmbsr
    - Italian: carallo
    - Polish: Sebastian Jasiński
    - Russian: Zakhar Timoshenko
    - Turkish: language_is_alive
    - Ukrainian: axmed99
- Improved import merge: consider last modified date, merge labels, do not merge if reminders differ (#49).
- More note text is now shown in reminder notification.
- Select newly created label when created from main screen.
- Select newly created label when setting labels on note.
- Focus text note content if background is clicked.
- Fixed export not completely overwriting JSON file if new content smaller.
- Fixed crash during search if maximum lines shown in preview is 1 or 2 (#47).
- Fixed crash during search if only negative term is used (e.g. `-a`) (#47).
- Fixed regression, destination not changed after selected label is deleted.
- Fixed rare crash when opening a list note.
- Fixed input field cut off in landscape mode in edit label dialog (#53).
- Fixed broken long press after converting note twice (#34).

## v1.4.1 (2021-09-06)
- Added dialog to change sort field and direction (#31).
- Ellipsize start of content when search highlight falls outside of preview (#32).
- Set the initial reminder time from a list of presets instead of the same hour every time.
- Trim text notes whitespace in preview.
- Automatically show reminder dialog when creating note in reminder section.
- Fixed text change not registered in edit screen when pasting changes the Editable instance.
- Fixed backpress not deselecting notes after opening note in edit screen.
- Fixed NPE in showKeyboard due to missing view focus during configuration change.
- Fixed corrupted internal state when converting text note with trimmable blank lines to list (#38).
- Fixed loss of changes after changing reminder in edit screen (#40).
- Fixed screen not responding after cancelling reminder postpone (#41).
- Fixed simultaneous notifications all opening the same note (#43).
- Fixed notification click not working if already editing a note.
- Fixed notification creating new note if clicked after note is deleted.
- Fixed postpone check failing if note reminder is changed before postponing.
- Remove check for internal list note consistency between content and metadata.

## v1.4.0 (2021-07-30)
- Added a label attribute to hide all notes with that label in active & archive destinations.
The notes are still visible in the trash and in the label's destination.
- Added clickable links for website & email in edit screen.
- Added reminder chip in edit screen.
- Added Spanish translation
- Show keyboard when note is converted to text or list.
- Fixed bug allowing to create two labels with the same name, and blank labels.
- Fixed bug where the label chip showed in that label's destination after its name was changed.
- Fixed keyboard not showing up on focus changes in edit screen.
- Fixed crash on import due to existing label reference conflict.
- Fixed reminder alarms not updated on data import and when all data is cleared.
- Fixed disabled RTL layout & RTL layout improvements.
- Fixed note conversion from text with bullets to list not removing bullets if note had whitespace before first item.

## v1.3.0 (2021-06-13)
- Added import data feature, from exported JSON data (#11).
- Added periodical auto export feature.
- Added option to separate checked and unchecked items in list notes.
- Fixed export data crash (#18).
- Attenuated impact of potential bug where recurring alarms are not set.
  If this happens now, launching the app will set the alarm correctly again.

## v1.2.0 (2021-05-14)
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

## v1.1.0 (2020-07-02)
- Removed synchronization feature.
- Fixed crash when navigating to two destination at the same time.

### v1.0.1 (2020-04-30)
- Fixed links in Settings screen for privacy policy and terms & conditions.

# v1.0.0 (2020-04-27)
- **Initial release**
