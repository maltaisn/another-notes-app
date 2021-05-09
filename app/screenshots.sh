#!/bin/bash

# script to take screenshots using android emulator.
# an androidTest class is used to take the screenshots, this is only a launcher.
# note that the Screenshots test must be run from AS at least once before using this script.
# also note that it will only work on Unix operating systems due to some of the commands used.

# most of it was taken from fastlane's screengrab, but this script is easier
# for me than to deal with their obscure configuration, plus I already use gradle play publisher.

# locales for which to take screenshots
# a directory with the locale name will be created in main/play/listings
LOCALES=(
  "en-US"
  "fr-CA"
)
# adb executable
ADB=adb
# app package ID
PACKAGE=com.maltaisn.notes.sync.debug
# test class for taking screenshots
TEST_CLASS=com.maltaisn.notes.screenshot.Screenshots
# test runner, leave empty for auto-detection
# auto-detection might fail if multiple test apps are installed
TEST_RUNNER= #$PACKAGE.debug.test/androidx.test.runner.AndroidJUnitRunner
# adb device, leave empty for auto-detection
# if no device is connected on startup, script will wait
ADB_DEVICE=
# destination folder, concatenated with locale folder in between
DESTINATION1=temp
DESTINATION2=graphics/phone-screenshots

# set taking_screenshots environment variable that will be picked up by the build.gradle script
# and will set a BuildConfig field so that app can disable debug features. this is needed because
# androidTest can only be run in debug mode, and relying only on BuildConfig.DEBUG for enabling
# debug features would show them in screenshots.
export taking_screenshots=true

echo "Assembling androidTest"
../gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:installDebugAndroidTest

# get device name
echo "Waiting for device"
$ADB wait-for-device
if [ -z $ADB_DEVICE ]
then
  ADB_DEVICE=$($ADB devices | sed -n '2p' | awk '{ print $1 }')
fi
echo "Device name: $ADB_DEVICE"
ADBD="$ADB -s $ADB_DEVICE"

# install app and grant permissions
echo "Installing test app"
$ADBD install -r -g ./build/outputs/apk/debug/app-debug.apk

if [ -z $TEST_RUNNER ]
then
  echo "Obtaining test runner class"
  TEST_RUNNER=$($ADB shell pm list instrumentation | sed "s/^instrumentation:\($PACKAGE.*\) .*$/\1/")
fi
echo "Test runner is: $TEST_RUNNER"

# activate system ui demo mode
# the demo mode will be setup in the test itself, because changing the locale resets the settings.
$ADBD shell settings put global sysui_demo_allowed 1

# run screenshots test
for locale in "${LOCALES[@]}"; do
  echo "Taking screenshots for locale $locale"
  $ADBD shell pm clear $PACKAGE  # not mandatory, ensure no data is persisted across locale tests
  $ADBD shell am instrument --no-window-animation -w -e testLocale "$locale" -e endingLocale "en-US" \
      -e debug false -e class $TEST_CLASS -e package $PACKAGE "$TEST_RUNNER"

  echo "Copying screenshots to listing directory"
  mkdir -p $DESTINATION1/"$locale"/$DESTINATION2
  $ADBD pull /sdcard/Android/data/$PACKAGE/files/Pictures/. $DESTINATION1/"$locale"/$DESTINATION2
done

export taking_screenshots=false
