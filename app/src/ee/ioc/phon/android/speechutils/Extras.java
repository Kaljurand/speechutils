/*
 * Copyright 2011-2015, Institute of Cybernetics at Tallinn University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ee.ioc.phon.android.speechutils;

/**
 * <p>Set of non-standard extras that K6nele supports.</p>
 *
 * @author Kaarel Kaljurand
 */
public class Extras {

    // SERVER_URL should be a legal URL
    public static final String EXTRA_SERVER_URL = "ee.ioc.phon.android.extra.SERVER_URL";

    // GRAMMAR_URL should be a legal URL
    public static final String EXTRA_GRAMMAR_URL = "ee.ioc.phon.android.extra.GRAMMAR_URL";

    // Identifier of the target language (any string)
    public static final String EXTRA_GRAMMAR_TARGET_LANG = "ee.ioc.phon.android.extra.GRAMMAR_TARGET_LANG";

    // Desired transcription.
    // Using this extra, the user can specify to which string the enclosed audio
    // should be transcribed.
    public static final String EXTRA_PHRASE = "ee.ioc.phon.android.extra.PHRASE";

    // Bundle with information about the editor in which the IME is running
    public static final String EXTRA_EDITOR_INFO = "ee.ioc.phon.android.extra.EDITOR_INFO";

    // Boolean to indicate that the recognition service should not stop after delivering the first result
    public static final String EXTRA_UNLIMITED_DURATION = "ee.ioc.phon.android.extra.UNLIMITED_DURATION";

    // Boolean to indicate that the server has sent final=true, i.e. the following hypotheses
    // will not be transcriptions of the same audio anymore.
    public static final String EXTRA_SEMI_FINAL = "ee.ioc.phon.android.extra.SEMI_FINAL";

    // Boolean. Iff true then the recognizer should play audio cues to indicate start and end of
    // recording, as well as error conditions.
    public static final String EXTRA_AUDIO_CUES = "ee.ioc.phon.android.extra.AUDIO_CUES";

    // Boolean. Use another app to view/evaluate/execute the recognition result. (Arvutaja-specific)
    public static final String EXTRA_USE_EXTERNAL_EVALUATOR = "ee.ioc.phon.android.extra.USE_EXTERNAL_EVALUATOR";

    /**
     * Boolean.
     * Start the recognition session immediately without the user having to press a button.
     */
    public static final String EXTRA_AUTO_START = "ee.ioc.phon.android.extra.AUTO_START";

    /**
     * Boolean.
     * In case of an audio/network/etc. error, finish the RecognizerIntent activity with the error code,
     * allowing the caller to handle the error.
     * Normally errors are handled by the activity so that the activity only returns with success.
     * However, in certain situations it is useful to let the caller handle the errors. If this
     * is desired then the caller can request the returning of the errors using this EXTRA.
     */
    public static final String EXTRA_RETURN_ERRORS = "ee.ioc.phon.android.extra.RETURN_ERRORS";

    // Caller is interested in the recorded audio data (boolean)
    public static final String EXTRA_GET_AUDIO = "android.speech.extra.GET_AUDIO";

    // Caller wants to have the audio data in a certain format (String)
    public static final String EXTRA_GET_AUDIO_FORMAT = "android.speech.extra.GET_AUDIO_FORMAT";

    // Switch on continuous recognition (boolean)
    // Same as EXTRA_UNLIMITED_DURATION
    // Used on Chrome to talk to Google's recognizer?
    // (http://src.chromium.org/svn/trunk/src/content/public/android/java/src/org/chromium/content/browser/SpeechRecognition.java)
    public static final String EXTRA_DICTATION_MODE = "android.speech.extra.DICTATION_MODE";

    /**
     * <p>Key used to retrieve an {@code ArrayList<String>} from the {@link android.os.Bundle} passed to the
     * {@link android.speech.RecognitionListener#onResults(android.os.Bundle)} and
     * {@link android.speech.RecognitionListener#onPartialResults(android.os.Bundle)} methods.
     * This list represents structured data:</p>
     * <ul>
     * <li>raw utterance of hypothesis 1
     * <li>linearization 1.1
     * <li>language code of linearization 1.1
     * <li>linearization 1.2
     * <li>language code of linearization 1.2
     * <li>...
     * <li>raw utterance of hypothesis 2
     * <li>...
     * </ul>
     * <p/>
     * <p>The number of linearizations for each hypothesis is given by an ArrayList<Integer> from a bundle
     * item accessible via the key RESULTS_RECOGNITION_LINEARIZATION_COUNTS.
     * Both of these bundle items have to be present for the client to be able to use the results.</p>
     */
    public static final String RESULTS_RECOGNITION_LINEARIZATIONS = "ee.ioc.phon.android.extra.RESULTS_RECOGNITION_LINEARIZATIONS";
    public static final String RESULTS_RECOGNITION_LINEARIZATION_COUNTS = "ee.ioc.phon.android.extra.RESULTS_RECOGNITION_LINEARIZATION_COUNTS";

    // Byte array. Currently not used.
    public static final String RESULTS_AUDIO_ENCODED = "ee.ioc.phon.android.extra.RESULTS_AUDIO_ENCODED";

    // TODO: experimental
    public static final String EXTRA_RESULT_PREFIX = "ee.ioc.phon.android.extra.RESULT_PREFIX";
    public static final String EXTRA_RESULT_SUFFIX = "ee.ioc.phon.android.extra.RESULT_SUFFIX";

    public static final String EXTRA_LAUNCH_RECOGNIZER = "ee.ioc.phon.android.extra.LAUNCH_RECOGNIZER";

    // API 14
    public static final String ACTION_VOICE_SEARCH_HANDS_FREE = "android.speech.action.VOICE_SEARCH_HANDS_FREE";

    // A non-standard extra
    public static final String EXTRA_ADDITIONAL_LANGUAGES = "android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES";

}
