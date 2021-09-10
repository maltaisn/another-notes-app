# Translating

Translating the app can be done on Crowdin: https://crowdin.com/project/another-notes-app
Most strings are accompanied with a screenshot to give the context.

Note that some strings are to be used in constrained space situations,
and in this case it's entirely fine to slightly change the meaning.

There are a few additionnal steps to consider, but they are not mandatory:

- The app also relies on a library, [recurpickerlib](https://github.com/maltaisn/recurpickerlib) (which I
  maintain), so you might want to make sure it's also available in the new language.
  The translation process is not available through Crowdin for now.

- Optionally, the app listing can be translated. This is also not on Crowdin.
  Create a folder in `app/src/main/play/listings` with the locale code. The locale code must be
  one [supported by Google Play][play_store_locales]. The folder must contain the following:
    - `graphics/phone-screenshots` subfolders, will eventually contain screenshots.
    - `title.txt`: app title (max 50 chars).
    - `short-description.txt`: short app description (max 80 chars).
    - `full-description.txt`: full app description (max 4000 chars).
    - See the `en-US` folder for reference content.

  Screenshots are generated automatically. However some notes are shown in each screenshot
  and these should ideally be translated to match the listing language. It can be done by
  translating the [`strings.xml`][strings_xml_screenshots] located in the `androidTest` source set,
  exactly like the main set of strings was translated. There are some instructions to follow in the XML file.

All modifications should be made on the `dev` branch and be sent as a pull request.

[strings_xml]: app/src/main/res/values/strings.xml
[strings_xml_screenshots]: app/src/androidTest/res/values/strings.xml
[play_store_locales]: https://support.google.com/googleplay/android-developer/answer/3125566