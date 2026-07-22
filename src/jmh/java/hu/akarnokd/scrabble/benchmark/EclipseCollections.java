/*
 * Copyright (C) 2019 José Paumard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package hu.akarnokd.scrabble.benchmark;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

import org.eclipse.collections.api.LazyIterable;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.function.primitive.IntFunction;
import org.eclipse.collections.api.block.function.primitive.ObjectIntToObjectFunction;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.impl.factory.Strings;
import org.eclipse.collections.impl.map.sorted.mutable.TreeSortedMap;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.openjdk.jmh.annotations.*;

import hu.akarnokd.scrabble.support.ShakespearePlaysScrabble;

/**
 * Shakespeare plays Scrabble with Eclipse Collections (Lazy API).
 * @author José
 */
public class EclipseCollections extends ShakespearePlaysScrabble {

    @SuppressWarnings("unused")
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(
        iterations = 5, time = 1
    )
    @Measurement(
        iterations = 5, time = 1
    )
    @Fork(1)
    public List<Entry<Integer, List<String>>> measureThroughput() {

        // Function to compute the score of a given letter
        IntFunction<Integer> scoreOfALetter = letter -> letterScores[letter - 'a'];

        // score of the same letters in a word
        ObjectIntToObjectFunction<Integer, Integer> letterScore =
                (letter, count) ->
                    letterScores[letter - 'a'] *
                    Integer.min(
                        count,
                        scrabbleAvailableLetters[letter - 'a']
                    );

        // Lazy view over the letters of a given word
        Function<String, LazyIterable<Integer>> letters =
                word -> Strings.asChars(word).asLazy().collect(c -> (int) c);

        // Histogram of the letters in a given word
        Function<String, MutableBag<Integer>> histoOfLetters =
                word -> letters.apply(word).toBag();

        // number of blanks for a given letter
        ObjectIntToObjectFunction<Integer, Long> blank =
                (letter, count) ->
                    Long.max(
                        0L,
                        count -
                        scrabbleAvailableLetters[letter - 'a']
                    );

        // number of blanks for a given word
        Function<String, Long> nBlanks =
                word -> histoOfLetters.apply(word)
                            .collectWithOccurrences(blank)
                            .sumOfLong(Long::longValue);

        // can a word be written with 2 blanks?
        Predicate<String> checkBlanks = word -> nBlanks.apply(word) <= 2;

        // score taking blanks into account
        Function<String, Integer> score2 =
                word -> (int) histoOfLetters.apply(word)
                            .collectWithOccurrences(letterScore)
                            .sumOfInt(Integer::intValue);

        // Placing the word on the board
        // Building the lazy views of first and last letters
        Function<String, LazyIterable<Integer>> first3 = word -> letters.apply(word).take(3);
        Function<String, LazyIterable<Integer>> last3 = word -> letters.apply(word).drop(Integer.max(0, word.length() - 4));

        // Lazy view to be maxed
        Function<String, LazyIterable<Integer>> toBeMaxed =
            word -> first3.apply(word).concatenate(last3.apply(word));

        // Bonus for double letter
        ToIntFunction<String> bonusForDoubleLetter =
            word -> toBeMaxed.apply(word)
                        .collectInt(scoreOfALetter)
                        .maxIfEmpty(0);

        // score of the word put on the board
        Function<String, Integer> score3 =
            word ->
                 2 * (score2.apply(word) + bonusForDoubleLetter.applyAsInt(word))
                 + (word.length() == 7 ? 50 : 0);

        Function<Function<String, Integer>, TreeSortedMap<Integer, List<String>>> buildHistoOnScore =
                score -> LazyIterate.adapt(shakespeareWords)
                                .select(scrabbleWords::contains)
                                // .filter(canWrite)    // filter out the words that needs blanks
                                .select(checkBlanks) // filter out the words that needs more than 2 blanks
                                .aggregateBy(
                                   score,
                                   ArrayList::new,
                                   (list, word) -> { list.add(word); return list; },
                                   TreeSortedMap.newMap(Comparator.reverseOrder())
                                );

        // best key / value pairs
        List<Entry<Integer, List<String>>> finalList =
                LazyIterate.adapt(buildHistoOnScore.apply(score3).entrySet())
                    .take(3)
                    .toList() ;

//        System.out.println(finalList) ;

        return finalList ;
    }
    public static void main(String[] args) throws Exception {
        EclipseCollections s = new EclipseCollections();
        s.init();
        System.out.println(s.measureThroughput());
    }
}