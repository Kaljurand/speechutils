package ee.ioc.phon.android.speechutils.editor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Constants {

    public static final Set<Character> CHARACTERS_WS =
            new HashSet<>(Arrays.asList(new Character[]{' ', '\n', '\t'}));

    // Symbols that should not be preceded by space in a written text.
    public static final Set<Character> CHARACTERS_PUNCT =
            new HashSet<>(Arrays.asList(new Character[]{',', ':', ';', '.', '!', '?', '-', ')'}));

    // Symbols after which the next word should be capitalized.
    // We include ) because ;-) often finishes a sentence.
    public static final Set<Character> CHARACTERS_EOS =
            new HashSet<>(Arrays.asList(new Character[]{'.', '!', '?', ')'}));

    // These symbols stick to the next symbol, i.e. no whitespace is added in front of the
    // following string.
    public static final Set<Character> CHARACTERS_STICKY =
            new HashSet<>(Arrays.asList(new Character[]{'(', '[', '{', '<'}));
}