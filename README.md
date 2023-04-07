Speechutils
===========

Speechutils is an Android library that helps to implement apps that need to include speech-to-text and text-to-speech functionality.
For example, it provides methods for

- audio recording and encoding
- aggregating speech-to-text and text-to-speech services
- playing audio cues before/after speech-to-text
- pausing the background audio during speech-to-text

Used by
-------

- https://github.com/Kaljurand/K6nele
- https://github.com/Kaljurand/K6nele-service
- https://github.com/Kaljurand/Arvutaja
- https://github.com/willblaschko/AlexaAndroid
- https://github.com/alex-vt/WhisperInput
- ...

Testing
-------

    $ adb devices -l
    List of devices attached
    ... transport_id:xx
    ...

    $ adb -t xx shell am instrument -w -r \
    -e package ee.ioc.phon.android.speechutils -e debug false \
    ee.ioc.phon.android.speechutils.test/androidx.test.runner.AndroidJUnitRunner

Some tests currently fail:

    Tests run: 163,  Failures: 10
