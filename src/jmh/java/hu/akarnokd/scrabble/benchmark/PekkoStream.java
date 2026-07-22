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

/*
 * Copyright (C) 2019 José Paumard
 */

package hu.akarnokd.scrabble.benchmark;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;

import org.openjdk.jmh.annotations.*;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.function.Function;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.*;
import hu.akarnokd.scrabble.support.ShakespearePlaysScrabble;

/**
 * Shakespeare with Akka-Stream Optimized.
 *
 * @author José
 * @author akarnokd
 */
public class PekkoStream extends ShakespearePlaysScrabble {

    ActorSystem actorSystem;

    Materializer materializer;

    @Setup
    public void setup() {

        actorSystem = ActorSystem.create("sys");
        materializer = Materializer.createMaterializer(actorSystem);

    }

    @TearDown
    public void teardown() {
        actorSystem.terminate();
    }

    static Source<Integer, NotUsed> chars(String word) {
        return Source.from(ix.Ix.characters(word));
    }

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
    public List<Entry<Integer, List<String>>> measureThroughput() throws Exception {

        //  to compute the score of a given word
        Function<Integer, Integer> scoreOfALetter = letter -> letterScores[letter - 'a'];

        // score of the same letters in a word
        Function<Entry<Integer, MutableLong>, Integer> letterScore =
                entry ->
                        letterScores[entry.getKey() - 'a'] *
                        Integer.min(
                                (int)entry.getValue().get(),
                                scrabbleAvailableLetters[entry.getKey() - 'a']
                            )
                    ;


        Function<String, Source<Integer, NotUsed>> toIntegerIx =
                string -> chars(string);

        // Histogram of the letters in a given word
        Function<String, Source<HashMap<Integer, MutableLong>, NotUsed>> histoOfLetters =
                word -> {
                    return toIntegerIx.apply(word)
                            .fold(new HashMap<Integer, MutableLong>(), (map, value) -> {
                                map.computeIfAbsent(value, _ -> new MutableLong()).incAndSet();
                                return map;
                            });
                };
        // number of blanks for a given letter
        Function<Entry<Integer, MutableLong>, Long> blank =
                entry ->
                        Long.max(
                            0L,
                            entry.getValue().get() -
                            scrabbleAvailableLetters[entry.getKey() - 'a']
                        )
                    ;

        // number of blanks for a given word
        Function<String, Source<Long, NotUsed>> nBlanks =
                word -> histoOfLetters.apply(word)
                            .mapConcat(map -> map.entrySet())
                            .map(blank)
                            .reduce((a, b) -> a + b);


        // can a word be written with 2 blanks?
        Function<String, Source<Boolean, NotUsed>> checkBlanks =
                word -> nBlanks.apply(word)
                            .map(l -> l <= 2L) ;

        // score taking blanks into account letterScore1
        Function<String, Source<Integer, NotUsed>> score2 =
                word -> histoOfLetters.apply(word)
                            .mapConcat(map -> map.entrySet())
                            .map(letterScore)
                            .reduce((a, b) -> a + b);

        // Placing the word on the board
        // Building the streams of first and last letters
        Function<String, Source<Integer, NotUsed>> first3 =
                word -> chars(word).take(3) ;
        Function<String, Source<Integer, NotUsed>> last3 =
                word -> chars(word).drop(3) ;


        // Stream to be maxed
        Function<String, Source<Integer, NotUsed>> toBeMaxed =
            word -> first3.apply(word).concat(last3.apply(word))
            ;

        // Bonus for double letter
        Function<String, Source<Integer, NotUsed>> bonusForDoubleLetter =
            word -> toBeMaxed.apply(word)
                        .map(scoreOfALetter)
                        .reduce((a, b) -> Math.max(a, b));

        // score of the word put on the board
        Function<String, Source<Integer, NotUsed>> score3 =
            word ->
                score2.apply(word)
                .concat(bonusForDoubleLetter.apply(word))
                .reduce((a, b) -> a + b).map(v -> v * 2 + (word.length() == 7 ? 50 : 0));

        Function<Function<String, Source<Integer, NotUsed>>, Source<TreeMap<Integer, List<String>>, NotUsed>> buildHistoOnScore =
                score -> {
                    return Source.from(shakespeareWords)
                                    .filter(scrabbleWords::contains)
                                    .flatMapConcat(4, word ->
                                        checkBlanks.apply(word)
                                        .filter(v -> v)
                                        .flatMapConcat(_ -> score.apply(word))
                                        .map(key -> Map.entry(key, word)))
                                    .fold(new TreeMap<Integer, List<String>>(Comparator.reverseOrder()), (map, e) -> {
                                        map.computeIfAbsent(e.getKey(), _ -> new ArrayList<String>()).add(e.getValue());
                                        return map;
                                    });
                } ;

        // best key / value pairs
        List<Entry<Integer, List<String>>> finalList2 =
                buildHistoOnScore.apply(score3)
                    .mapConcat(map -> map.entrySet())
                    .take(3)
                    .runWith(Sink.seq(), materializer)
                    .toCompletableFuture().join();


//        System.out.println(finalList2);

        return finalList2 ;
    }

    public static void main(String[] args) throws Exception {
        PekkoStream s = new PekkoStream();
        s.init();
        s.setup();
        try {
            System.out.println(s.measureThroughput());
        } finally {
            s.teardown();
        }

    }
}