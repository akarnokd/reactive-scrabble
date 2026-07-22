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
import java.util.function.*;
import java.util.stream.*;

import org.openjdk.jmh.annotations.*;

import hu.akarnokd.scrabble.support.ShakespearePlaysScrabble;
import io.smallrye.mutiny.*;

/// Shakespeare plays Scrabble with RxJava 4 Streamable optimized.
///
/// # Oprimitation progress
///
/// i9 275HX, 32GB LPDDR5 6400MT CL52, Windows 25H2, JDK 26.0.1
///
/// | Step | Time (ms) | Improvement | vs Baseline |
/// |------|------|-------------:|-------------:|
/// | Baseline |  | - | - |
///
/// @author José
/// @author akarnokd
public class Mutiny extends ShakespearePlaysScrabble {
    static Multi<Integer> chars(String word) {
        return Multi.createFrom().range(0, word.length()).map(index -> (int)word.charAt(index));
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


        Function<String, Multi<Integer>> toIntegerStreamable =
                string -> chars(string);

        // Histogram of the letters in a given word
        Function<String, Uni<Map<Integer, MutableLong>>> histoOfLetters =
                word -> toIntegerStreamable.apply(word)
                            .collect()
                            .with(MutableLongMapCounter.INSTANCE)
                            ;

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
        Function<String, Uni<Long>> nBlanks =
                word -> histoOfLetters.apply(word).toMulti().onItem().transformToIterable(
                                map -> map.entrySet()
                        )
                        .map(blank)
                        .collect().with(SumLongCollector.INSTANCE)
                    ;


        // can a word be written with 2 blanks?
        Function<String, Uni<Boolean>> checkBlanks =
                word -> nBlanks.apply(word)
                            .map(l -> l <= 2L) ;

        // score taking blanks into account letterScore1
        Function<String, Uni<Integer>> score2 =
                word -> histoOfLetters.apply(word).toMulti().onItem().transformToIterable(
                            map -> map.entrySet()
                        )
                        .map(letterScore)
                        .collect().with(SumIntCollector.INSTANCE)
                    ;

        // Placing the word on the board
        // Building the streams of first and last letters
        Function<String, Multi<Integer>> first3 =
                word -> chars(word).select().first(3) ;
        Function<String, Multi<Integer>> last3 =
                word -> chars(word).skip().first(3) ;


        // Stream to be maxed
        Function<String, Multi<Integer>> toBeMaxed =
            word -> Multi.createBy().concatenating().streams(first3.apply(word), last3.apply(word))
            ;

        // Bonus for double letter
        Function<String, Uni<Integer>> bonusForDoubleLetter =
            word -> toBeMaxed.apply(word)
                        .map(scoreOfALetter)
                        .collect().with(MaxCollector.INSTANCE)
                        ;

        // score of the word put on the board
        Function<String, Uni<Integer>> score3 =
            word ->
                Multi.createBy().concatenating().streams(
                    score2.apply(word).toMulti(),
                    bonusForDoubleLetter.apply(word).toMulti()
                )
                .collect().with(SumIntCollector.INSTANCE)
                .map(v -> v * 2 + (word.length() == 7 ? 50 : 0))
                ;

        Function<Function<String, Uni<Integer>>, Uni<TreeMap<Integer, List<String>>>> buildHistoOnScore =
                score -> Multi.createFrom().iterable(shakespeareWords)
                                .filter(scrabbleWords::contains)
                                .filter(word -> singleGet(checkBlanks.apply(word)))
                                .collect().with(new ReverseTreeMapListCollector(word -> singleGet(score.apply(word))))
                                ;

        // best key / value pairs
        List<Entry<Integer, List<String>>> finalList2 =
                    buildHistoOnScore.apply(score3).toMulti().onItem().transformToIterable(
                            map -> map.entrySet()
                    )
                    .select().first(3)
                    .collect().with(Collectors.toList())
                    .await().indefinitely();

        return finalList2 ;
    }

    static <T> T singleGet(Uni<T> source) {
        try {
            return source.await().indefinitely();
        } catch (Throwable ex) {
            throw new CompletionException(ex);
        }
    }

    static final class MutableLongMapCounter
    implements Collector<Integer, Map<Integer, MutableLong>, Map<Integer, MutableLong>> {

        static final MutableLongMapCounter INSTANCE = new MutableLongMapCounter();

        private MutableLongMapCounter() { }

        @Override
        public Supplier<Map<Integer, MutableLong>> supplier() {
            return HashMap::new;
        }

        @Override
        public BiConsumer<Map<Integer, MutableLong>, Integer> accumulator() {
            return (m, v) -> {
                m.computeIfAbsent(v, _ -> new MutableLong()).incAndSet();
            };
        }

        @Override
        public BinaryOperator<Map<Integer, MutableLong>> combiner() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.function.Function<Map<Integer, MutableLong>, Map<Integer, MutableLong>> finisher() {
            return v -> v;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return EnumSet.of(Characteristics.UNORDERED);
        }
    }

    static final class SumIntCollector
    implements Collector<Integer, MutableLong, Integer> {

        static final SumIntCollector INSTANCE = new SumIntCollector();

        private SumIntCollector() { }

        @Override
        public Supplier<MutableLong> supplier() {
            return MutableLong::new;
        }

        @Override
        public BiConsumer<MutableLong, Integer> accumulator() {
            return (m, v) -> m.add(v);
        }

        @Override
        public BinaryOperator<MutableLong> combiner() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.function.Function<MutableLong, Integer> finisher() {
            return m -> (int)m.get();
        }

        @Override
        public Set<Characteristics> characteristics() {
            return null;
        }
    }
    static final class SumLongCollector
    implements Collector<Long, MutableLong, Long> {

        static final SumLongCollector INSTANCE = new SumLongCollector();

        private SumLongCollector() { }

        @Override
        public Supplier<MutableLong> supplier() {
            return MutableLong::new;
        }

        @Override
        public BiConsumer<MutableLong, Long> accumulator() {
            return (m, v) -> m.add(v);
        }

        @Override
        public BinaryOperator<MutableLong> combiner() {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.function.Function<MutableLong, Long> finisher() {
            return m -> m.get();
        }

        @Override
        public Set<Characteristics> characteristics() {
            return null;
        }
    }

    record ReverseTreeMapListCollector(Function<String, Integer> valueMapper)
    implements Collector<String, TreeMap<Integer, List<String>>, TreeMap<Integer, List<String>>> {

        @Override
        public Supplier<TreeMap<Integer, List<String>>> supplier() {
            return () -> new TreeMap<Integer, List<String>>(Comparator.reverseOrder());
        }

        @Override
        public BiConsumer<TreeMap<Integer, List<String>>, String> accumulator() {
            return (m, v) -> {
                Integer result;

                try {
                    result = valueMapper.apply(v);
                } catch (Throwable ex) {
                    result = 0;
                }
                m.computeIfAbsent(result, _ -> new ArrayList<String>()).add(v);
            };
        }

        @Override
        public BinaryOperator<TreeMap<Integer, List<String>>> combiner() {
            return null;
        }

        @Override
        public java.util.function.Function<TreeMap<Integer, List<String>>, TreeMap<Integer, List<String>>> finisher() {
            return v -> v;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return null;
        }
    }

    record MaxCollector() implements Collector<Integer, int[], Integer> {

        static final MaxCollector INSTANCE = new MaxCollector();

        @Override
        public Supplier<int[]> supplier() {
            return () -> new int[1];
        }

        @Override
        public BiConsumer<int[], Integer> accumulator() {
            return (m, i) -> {
                m[0] = Math.max(m[0], i);
            };
        }

        @Override
        public java.util.function.Function<int[], Integer> finisher() {
            return m -> m[0];
        }

        @Override
        public Set<Characteristics> characteristics() {
            return null;
        }

        @Override
        public BinaryOperator<int[]> combiner() {
            return null;
        }
    }

    public static void main(String[] args) throws Throwable {
        Mutiny s = new Mutiny();
        s.init();
        System.out.println(s.measureThroughput());
    }
}