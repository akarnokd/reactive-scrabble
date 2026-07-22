/*
 * Copyright (c) 2016-present, David Karnok & Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package hu.akarnokd.scrabble.benchmark;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import com.softwaremill.jox.flows.*;
import com.softwaremill.jox.structured.ThrowingFunction;

import hu.akarnokd.scrabble.support.ShakespearePlaysScrabble;

/**
 * Shakespeare plays Scrabble with RxJava 4 Flowable optimized.
 * @author José
 * @author akarnokd
 */
public class JoxFlows extends ShakespearePlaysScrabble {
    static Flow<Integer> chars(String word) {
//        return Flowable.range(0, word.length()).map(i -> (int)word.charAt(i));
        return Flows.range(0, word.length() - 1, 1).map(i -> (int)word.charAt(i));
//        return Flows.fromIterable(ix.Ix.characters(word));
    }

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
    @Fork(value = 1, jvmArgs = {
            "-XX:MaxInlineLevel=20"
//            , "-XX:+UnlockDiagnosticVMOptions",
//            , "-XX:+PrintAssembly",
//            , "-XX:+TraceClassLoading",
//            , "-XX:+LogCompilation"
    })
    public List<Entry<Integer, List<String>>> measureThroughput() throws Throwable {

        //  to compute the score of a given word
        ThrowingFunction<Integer, Integer> scoreOfALetter = letter -> letterScores[letter - 'a'];

        // score of the same letters in a word
        ThrowingFunction<Entry<Integer, MutableLong>, Integer> letterScore =
                entry ->
                        letterScores[entry.getKey() - 'a'] *
                        Integer.min(
                                (int)entry.getValue().get(),
                                scrabbleAvailableLetters[entry.getKey() - 'a']
                            )
                    ;


        ThrowingFunction<String, Flow<Integer>> toIntegerFlow =
                string -> chars(string);

        // Histogram of the letters in a given word
        ThrowingFunction<String, Flow<HashMap<Integer, MutableLong>>> histoOfLetters =
                word -> Flows.usingEmit(emit -> emit.apply(toIntegerFlow.apply(word)
                            .scan(
                                new HashMap<Integer, MutableLong>(),
                                (map, value) ->
                                    {
                                        MutableLong newValue = map.get(value) ;
                                        if (newValue == null) {
                                            newValue = new MutableLong();
                                            map.put(value, newValue);
                                        }
                                        newValue.incAndSet();
                                        return map;
                                    }
                            ).runLast()));

        // number of blanks for a given letter
        ThrowingFunction<Entry<Integer, MutableLong>, Long> blank =
                entry ->
                        Long.max(
                            0L,
                            entry.getValue().get() -
                            scrabbleAvailableLetters[entry.getKey() - 'a']
                        )
                    ;

        // number of blanks for a given word
        ThrowingFunction<String, Flow<Long>> nBlanks =
                word -> Flows.usingEmit(emit -> emit.apply(
                            histoOfLetters.apply(word).mapConcat(
                                    map -> map.entrySet()
                            )
                            .map(blank)
                            .runReduce((a, b) -> a + b)
                        ))
                    ;


        // can a word be written with 2 blanks?
        ThrowingFunction<String, Flow<Boolean>> checkBlanks =
                word -> nBlanks.apply(word)
                            .map(l -> l <= 2L) ;

        // score taking blanks into account letterScore1
        ThrowingFunction<String, Flow<Integer>> score2 =
                word -> Flows.usingEmit(emit -> emit.apply(
                            histoOfLetters.apply(word).mapConcat(
                                map -> map.entrySet()
                            )
                            .map(letterScore)
                            .runReduce((a, b) -> a + b)
                        )) ;

        // Placing the word on the board
        // Building the streams of first and last letters
        ThrowingFunction<String, Flow<Integer>> first3 =
                word -> chars(word).take(3) ;
        ThrowingFunction<String, Flow<Integer>> last3 =
                word -> chars(word).drop(3) ;


        // Stream to be maxed
        ThrowingFunction<String, Flow<Integer>> toBeMaxed =
            word -> Flows.concat(first3.apply(word), last3.apply(word))
            ;

        // Bonus for double letter
        ThrowingFunction<String, Flow<Integer>> bonusForDoubleLetter =
            word -> Flows.usingEmit(emit -> emit.apply(
                        toBeMaxed.apply(word)
                        .map(scoreOfALetter)
                        .runReduce((a, b) -> Math.max(a, b))
                        )) ;

        // score of the word put on the board
        ThrowingFunction<String, Flow<Integer>> score3 =
            word ->
                Flows.<Integer>usingEmit(emit -> emit.apply(
                    Flows.concat(
                        score2.apply(word),
                        bonusForDoubleLetter.apply(word)
                    )
                    .runReduce((a, b) -> a + b)
                ))
                .map(v -> v * 2 + (word.length() == 7 ? 50 : 0))
                ;

            ThrowingFunction<ThrowingFunction<String, Flow<Integer>>, Flow<TreeMap<Integer, List<String>>>> buildHistoOnScore =
                score -> Flows.usingEmit(emit -> emit.apply(
                            Flows.fromIterable(shakespeareWords)
                                .filter(scrabbleWords::contains)
                                .filter(word -> {
                                    try {
                                        return checkBlanks.apply(word).runLast();
                                    } catch (Exception e) {
                                        return false;
                                    }
                                })
                                .scan(
                                    new TreeMap<Integer, List<String>>(Comparator.reverseOrder()),
                                    (map, word) -> {
                                        Integer key = score.apply(word).runLast();
                                        List<String> list = map.get(key) ;
                                        if (list == null) {
                                            list = new ArrayList<>() ;
                                            map.put(key, list) ;
                                        }
                                        list.add(word);
                                        return map;
                                    }
                                ).runLast()));

        // best key / value pairs
        List<Entry<Integer, List<String>>> finalList2 =
                    buildHistoOnScore.apply(score3).mapConcat(
                            map -> map.entrySet()
                    )
                    .take(3)
                    .scan(
                        new ArrayList<Entry<Integer, List<String>>>(),
                        (list, entry) -> {
                            list.add(entry) ;
                            return list;
                        }
                    )
                    .runLast();


//        System.out.println(finalList2);

        return finalList2 ;
    }

    public static void main(String[] args) throws Throwable {
        JoxFlows s = new JoxFlows();
        s.init();
        System.out.println(s.measureThroughput());
    }
}