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
import java.util.function.*;
import java.util.stream.Collectors;

import org.openjdk.jmh.annotations.*;

import com.landawn.abacus.util.stream.*;
import com.landawn.abacus.util.stream.Stream.StreamEx;

import hu.akarnokd.scrabble.support.ShakespearePlaysScrabble;

/**
 * Shakespeare plays Scrabble with Amaembo StreamEx (slightly modified).
 * @author José
 */
public class AbacusCommon extends ShakespearePlaysScrabble {

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

        // Function to compute the score of a given word
        IntUnaryOperator scoreOfALetter = letter -> letterScores[letter - 'a'];

        // score of the same letters in a word
        ToIntFunction<Entry<Integer, Long>> letterScore =
                entry ->
                    letterScores[entry.getKey() - 'a'] *
                    Integer.min(
                        entry.getValue().intValue(),
                        scrabbleAvailableLetters[entry.getKey() - 'a']
                    );


        // Histogram of the letters in a given word
        Function<String, Map<Integer, Long>> histoOfLetters =
                word -> CharStream.of(word).mapToInt(v -> (int)v).boxed()
                            .collect(
                                Collectors.groupingBy(
                                    Function.identity(),
                                    Collectors.counting()
                                )
                            );

        // number of blanks for a given letter
        ToLongFunction<Entry<Integer, Long>> blank =
                entry ->
                    Long.max(
                        0L,
                        entry.getValue() -
                        scrabbleAvailableLetters[entry.getKey() - 'a']
                    );

        // number of blanks for a given word
        Function<String, Long> nBlanks =
                word -> Stream.of(histoOfLetters.apply(word)
                            .entrySet())
                            .mapToLong(blank)
                            .sum();

        // can a word be written with 2 blanks?
        Predicate<String> checkBlanks = word -> nBlanks.apply(word) <= 2;

        // score taking blanks into account
        Function<String, Integer> score2 =
                word -> Stream.of(histoOfLetters.apply(word)
                            .entrySet())
                            .mapToInt(letterScore)
                            .sum();

        // Placing the word on the board
        // Building the streams of first and last letters
        Function<String, CharStream> first3 = word -> CharStream.of(word).limit(3);
        Function<String, CharStream> last3 = word -> CharStream.of(word).skip(Integer.max(0, word.length() - 4));

        // Stream to be maxed
        Function<String, CharStream> toBeMaxed =
            word -> CharStream.concat(first3.apply(word), last3.apply(word));

        // Bonus for double letter
        ToIntFunction<String> bonusForDoubleLetter =
            word -> toBeMaxed.apply(word)
                        .mapToInt(v -> (int)v)
                        .map(scoreOfALetter)
                        .max()
                        .orElse(0);

        // score of the word put on the board
        Function<String, Integer> score3 =
            word ->
                 2 * (score2.apply(word) + bonusForDoubleLetter.applyAsInt(word))
                 + (word.length() == 7 ? 50 : 0);

        Function<Function<String, Integer>, Map<Integer, List<String>>> buildHistoOnScore =
                score -> StreamEx.of(shakespeareWords)
                                .filter(scrabbleWords::contains)
                                // .filter(canWrite)    // filter out the words that needs blanks
                                .filter(checkBlanks) // filter out the words that needs more than 2 blanks
                                .collect(
                                   Collectors.groupingBy(
                                      score,
                                      () -> new TreeMap<Integer, List<String>>(Comparator.reverseOrder()),
                                      Collectors.toList()
                                   )
                                );


        // best key / value pairs
        List<Entry<Integer, List<String>>> finalList =
                StreamEx.of(buildHistoOnScore.apply(score3).entrySet())
                    .limit(3)
                    .collect(Collectors.toList()) ;

//        System.out.println(finalList) ;

        return finalList ;
    }
    public static void main(String[] args) throws Exception {
        AbacusCommon s = new AbacusCommon();
        s.init();
        System.out.println(s.measureThroughput());
    }
}