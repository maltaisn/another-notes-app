#!/bin/bash

#
# Copyright 2022 Nicolas Maltais
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# script to take screenshots using android emulator.
# an androidTest class is used to take the screenshots, this is only a launcher.
# note that the Screenshots test must be run from AS at least once before using this script.
# also note that it will only work on Unix operating systems due to some of the commands used.

# - the selected build type should be "debug"
# - the script must be run from the app/ directory
# - uncomment @Ignore annotation in Screenshots test
# - set keyboard to GBoard, with light theme .
#   (in "System auto" theme GBoard doesn't update itself during process...)

# most of it was taken from fastlane's screengrab, but this script is easier
# for me than to deal with their obscure configuration, plus I already use gradle play publisher.

# to debug commands
# set -x

# locales for which to take screenshots
# a directory with the locale name will be created in main/play/listings
# comment locales to not take screenshot for them
LOCALES=(
  "en-US"
  "fr-CA"
  "es-ES"
)
# adb executable
ADB=adb
# app package ID
PACKAGE=com.maltaisn.notes.sync.debug
# test class for taking screenshots
TEST_CLASS=com.maltaisn.notes.screenshot.Screenshots
# test runner, leave empty for auto-detection
# auto-detection might fail if multiple test apps are installed
TEST_RUNNER=
# adb device, leave empty for auto-detection
# if no device is connected on startup, script will wait
ADB_DEVICE=
# source screenshot folder
SOURCE=/sdcard/Pictures/screenshot_
# destination folder, concatenated with locale folder in between
DESTINATION1=src/main/play/listings
DESTINATION2=graphics/phone-screenshots

# set taking_screenshots environment variable that will be picked up by the build.gradle script
# and will set a BuildConfig field so that app can disable debug features. this is needed because
# androidTest can only be run in debug mode, and relying only on BuildConfig.DEBUG for enabling
# debug features would show them in screenshots.
export taking_screenshots=true

echo "Assembling app"
../gradlew assembleDebug
echo "Assembling androidTest"
../gradlew assembleDebugAndroidTest
echo "Installing androidTest"
../gradlew uninstallDebugAndroidTest installDebugAndroidTest

# get device name
echo "Waiting for device"
$ADB wait-for-device
if [ -z "$ADB_DEVICE" ]; then
  ADB_DEVICE=$($ADB devices | sed -n '2p' | awk '{ print $1 }')
fi
echo "Device name: $ADB_DEVICE"
ADBD="$ADB -s $ADB_DEVICE"

# uninstall app first to prevent signature mismatch error
echo "Uninstalling test app"
$ADBD uninstall $PACKAGE

# install app and grant permissions
echo "Installing test app"
$ADBD install -r -g ./build/outputs/apk/debug/app-debug.apk

# get test runnner string
if [ -z "$TEST_RUNNER" ]; then
  echo "Obtaining test runner class"
  TEST_RUNNER=$($ADB shell pm list instrumentation | sed -n "s/^instrumentation:\($PACKAGE.*\) .*$/\1/p")
fi
echo "Test runner is: $TEST_RUNNER"

# activate system ui demo mode
# the demo mode will be setup in the test itself, because changing the locale resets the settings.
$ADBD shell settings put global sysui_demo_allowed 1

# run screenshots test
for locale in "${LOCALES[@]}"; do
  echo "Taking screenshots for locale $locale"
  $ADBD shell pm clear $PACKAGE # not mandatory, ensure no data is persisted across locale tests
  if ! $ADBD shell am instrument --no-window-animation -w -e testLocale "$locale" -e endingLocale "en-US" \
    -e debug false -e class $TEST_CLASS -e package $PACKAGE "$TEST_RUNNER"; then
    echo "Instrumentation failed"
    exit 1
  fi

  echo "Copying screenshots to listing directory"
  mkdir -p $DESTINATION1/"$locale"/$DESTINATION2
  $ADBD pull $SOURCE/. $DESTINATION1/"$locale"/$DESTINATION2
  $ADBD shell rm -rf $SOURCE
done

export taking_screenshots=false

echo "DONE"