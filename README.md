# Reactive Scrabble Benchmarks


## Run a specific benchmark

```
gradlew jmh -PjmhIncludes=Helidon --rerun-tasks
```

## Results (alphabetic)

i9 275HX (8P+16E, 2.7GHz+), 32GB LPDDR5 6400MT CL52, Windows 25H2, JDK 26.0.1

| Benchmark                              | Version |   Score |    Error | Units |
|----------------------------------------|---------|--------:|---------:|-------|
| AkkaStream                             | 2.13    | 268,331 | ±  6,197 | ms/op |
| AnnimonStream                          | 1.2.2   |   9,104 | ±  0,057 | ms/op |
| CyclopsReact                           | 2.1.1   |  21,071 | ±  0,455 | ms/op |
| Guava                                  | 33.6.0s |  14,104 | ±  0,172 | ms/op |
| Helidon                                | 4.5.1   |  15,909 | ±  0,172 | ms/op |
| IEnumerable4Java                       | 1.0.0   |   8,928 | ±  0,087 | ms/op |
| IObservable4Java                       | 1.0.0   |   8,593 | ±  0,094 | ms/op |
| Iterable4Java                          | 0.98.1  |  15,665 | ±  0,191 | ms/op |
| IxJava                                 | 1.0.0   |   9,221 | ±  0,072 | ms/op |
| JOOL                                   | 0.9.15  |  41,213 | ±  0,713 | ms/op |
| JavaForLoop                            | 26.0.1  |   2,523 | ±  0,015 | ms/op |
| JavaStream                             | 26.0.1  |  10,608 | ±  0,129 | ms/op |
| JavaStreamParallel                     | 26.0.1  |   1,321 | ±  0,006 | ms/op |
| Mutiny                                 | 3.3.0   |  27,250 | ±  0,161 | ms/op |
| Reactive4Java                          | 0.98.1  | 355,223 | ± 11,164 | ms/op |
| Reactor                                | 3.8.6   |  15,604 | ±  0,160 | ms/op |
| ReactorParallel                        | 3.8.6   |   3,697 | ±  0,147 | ms/op |
| RxJava1                                | 1.3.8   |  50,612 | ±  0,421 | ms/op |
| RxJava2Flowable                        | 2.2.21  |  13,057 | ±  0,146 | ms/op |
| RxJava2Observable                      | 2.2.21  |  11,494 | ±  0,120 | ms/op |
| RxJava2Parallel                        | 2.2.21  |   3,089 | ±  0,135 | ms/op |
| RxJava3Flowable                        | 3.1.12  |  13,288 | ±  0,173 | ms/op |
| RxJava3Observable                      | 3.1.12  |  11,707 | ±  0,121 | ms/op |
| RxJava3Parallel                        | 3.1.12  |   3,085 | ±  0,123 | ms/op |
| RxJava4Flowable                        | 4.0.0a21|  12,646 | ±  0,138 | ms/op |
| RxJava4Observable                      | 4.0.0a21|  11,818 | ±  0,134 | ms/op |
| RxJava4Streamable                      | 4.0.0a21|  19,190 | ±  0,187 | ms/op |

### by time, lower is better

| Benchmark                              | Version |   Score |    Error | Units |
|----------------------------------------|---------|--------:|---------:|-------|
| JavaStreamParallel                     | 26.0.1  |   1,321 | ±  0,006 | ms/op |
| JavaForLoop                            | 26.0.1  |   2,523 | ±  0,015 | ms/op |
| RxJava3Parallel                        | 3.1.12  |   3,085 | ±  0,123 | ms/op |
| RxJava2Parallel                        | 2.2.21  |   3,089 | ±  0,135 | ms/op |
| ReactorParallel                        | 3.8.6   |   3,697 | ±  0,147 | ms/op |
| IObservable4Java                       | 1.0.0   |   8,593 | ±  0,094 | ms/op |
| IEnumerable4Java                       | 1.0.0   |   8,928 | ±  0,087 | ms/op |
| AnnimonStream                          | 1.2.2   |   9,104 | ±  0,057 | ms/op |
| IxJava                                 | 1.0.0   |   9,221 | ±  0,072 | ms/op |
| JavaStream                             | 26.0.1  |  10,608 | ±  0,129 | ms/op |
| RxJava2Observable                      | 2.2.21  |  11,494 | ±  0,120 | ms/op |
| RxJava3Observable                      | 3.1.12  |  11,707 | ±  0,121 | ms/op |
| RxJava4Observable                      | 4.0.0a21|  11,818 | ±  0,134 | ms/op |
| RxJava4Flowable                        | 4.0.0a21|  12,646 | ±  0,138 | ms/op |
| RxJava2Flowable                        | 2.2.21  |  13,057 | ±  0,146 | ms/op |
| RxJava3Flowable                        | 3.1.12  |  13,288 | ±  0,173 | ms/op |
| Guava                                  | 33.6.0s |  14,104 | ±  0,172 | ms/op |
| Reactor                                | 3.8.6   |  15,604 | ±  0,160 | ms/op |
| Iterable4Java                          | 0.98.1  |  15,665 | ±  0,191 | ms/op |
| Helidon                                | 4.5.1   |  15,909 | ±  0,172 | ms/op |
| RxJava4Streamable                      | 4.0.0a21|  19,190 | ±  0,187 | ms/op |
| CyclopsReact                           | 2.1.1   |  21,071 | ±  0,455 | ms/op |
| Mutiny                                 | 3.3.0   |  27,250 | ±  0,161 | ms/op |
| JOOL                                   | 0.9.15  |  41,213 | ±  0,713 | ms/op |
| RxJava1                                | 1.3.8   |  50,612 | ±  0,421 | ms/op |
| Reactive4Java                          | 0.98.1  | 355,223 | ± 11,164 | ms/op |
| AkkaStream                             | 2.13    | 268,331 | ±  6,197 | ms/op |

