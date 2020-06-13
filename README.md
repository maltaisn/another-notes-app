# Another notes app

![App icon](app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png)

This is a simple Android app for taking notes, like there have been tens of thousands before.
The app has Material UI, was built following MVVM architecture, uses Dagger and some Jetpack
components.

<a href="https://github.com/maltaisn/another-notes-app/releases/tag/v1.0.1"
    target="blank"><img alt="Direct APK download"
    src=".github/assets/direct-apk-download.png"
    height="80"/></a>

#### Features
- Text and list notes.
- Archive and recycle bin.
- Searching notes.
- Synchronize & backup notes on the cloud. (optional)
- Light and dark theme support.

### Screenshots

<img alt="Screenshot 1"
     src="app/src/main/play/listings/en-US/graphics/phone-screenshots/1.png"
     width="40%"/>  <img alt="Screenshot 2"
     src="app/src/main/play/listings/en-US/graphics/phone-screenshots/2.png"
     width="40%"/>

### Synchronization
The synchronization feature was removed in future versions. I thought of it more like an experiment
that a definitive feature from the start. It actually turned out pretty great. However I don't like
the fact the it relies on Firebase to work. Firebase will also make cloud functions part of the Blaze
plan only as of 2021, which I have no intention of using.

Additionally, the need to support synchronization introduced a lot of limitations:

- No media notes like images or voice clips. There's no way the free Firebase plan had space for hosting that for more than a few accounts.
- Encryption: true encryption would prevent the user from resetting their password.
- Firebase isn't allowed on FDroid, requiring two different flavors.
- Increases development time, everything has to be made in a way that can be synced.

The synchronization feature implemented in this branch works. The cloud functions code is available
in the [functions](/functions) directory if anyone is interested in hosting their own version. But
there will be no further support for it.

### Changelog
[View changelog here][changelog] for the app release notes.

### License
- All code is licensed under Apache License 2.0.
- Icons were mostly found at [Material Design Icons][mdi-icons], license can be found
[here][mdi-icons-license].


[changelog]: CHANGELOG.md
[mdi-icons]: https://materialdesignicons.com
[mdi-icons-license]: https://github.com/Templarian/MaterialDesign#license
