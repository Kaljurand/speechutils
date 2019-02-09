Speechutils
===========

[![Codacy Badge](https://api.codacy.com/project/badge/grade/bc2e3589e2714093be39f876016b9ada)](https://www.codacy.com/app/kaljurand/speechutils)

Speechutils is an Android library that helps to implement apps that need to include speech-to-text and text-to-speech functionality.
For example, it provides methods for

- audio recording and encoding
- aggregating speech-to-text and text-to-speech services
- playing audio cues before/after speech-to-text
- pausing the background audio during speech-to-text

Used by
-------

- https://github.com/Kaljurand/K6nele
- https://github.com/Kaljurand/Arvutaja
- https://github.com/willblaschko/AlexaAndroid

Testing
-------

    adb shell am instrument -w -r \
    -e package ee.ioc.phon.android.speechutils -e debug false \
    ee.ioc.phon.android.speechutils.test/android.support.test.runner.AndroidJUnitRunner

Some tests currently fail:

    Tests run: 120,  Failures: 8
