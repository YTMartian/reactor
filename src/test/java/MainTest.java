import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;


@Slf4j
public class MainTest {

    @Test
    public void blockLastTest() {
        List<Integer> list = new ArrayList<>();
        list.add(3);
        list.add(6);
        list.add(5);
        Flux.fromIterable(list).map(num -> {
            System.out.println(num);
            return 1;
        }).map(num -> {
            System.out.println(num);
            return Mono.empty();
        }).blockLast();
        System.out.println();
        Flux.range(3, 3).subscribe(System.out::println);
        System.out.println();
        Flux.fromIterable(list).doOnNext(num -> {
            System.out.println("num:" + num);
        }).flatMap(num -> {
            System.out.println("num-" + num);
            return Mono.empty();
        }).blockLast();
    }

    @Test
    public void timeoutTest() {
        Mono<String> delayMono = Mono.fromCallable(() -> {
            Thread.sleep(5000);
            return null;
        });
        Mono<String> mono = delayMono.timeout(Duration.ofSeconds(8)).onErrorResume(e -> {
            log.error("timeoutTest error: ", e);
            return Mono.error(e);
        });
        mono.subscribe(
                result -> log.info("result: {}", result),
                error -> log.error("error: ", error)
        );
    }

    @Test
    public void flatMapConcurrenceTest() {
        Mono<Integer> mono = Mono.just(1);
        Flux<Integer> result = mono.flatMapMany(value -> Flux.range(1, 10)
                .doOnRequest(r -> {
                    System.out.println("main each request consumer invoke: " + r);
                })
                .flatMap(i -> Mono.fromCallable(() -> {
                    Thread.sleep(10);//模拟耗时操作
                    return value * i;
                }).doOnRequest(r -> {
                    System.out.println("inner each request consumer invoke: " + r);
                }), 3, 2)
        );
        result.subscribe(i -> {
            System.out.println("+++" + i);
        });
    }

    /**
    * Flux的map和handle区别
    */
    @Test
    public void mapAndHandleTest() {
        // 用于有条件地处理元素，允许选择性地发送新的元素或标记结束
        Flux.just(1, 2, 3)
                .handle((t, sink) -> {
                    if (t % 2 == 0) {
                        sink.next(t);
                    }
                })
                .subscribe(
                        result -> System.out.println("handle result: " + result),
                        error -> System.out.println("handle error: " + error)
                );

        // map用于对每个元素进行转换，返回一个新的 Flux
        Flux.just(1, 2, 3)
                .map(t -> {
                    if (t % 2 == 0) {
                        return t;
                    }
                    return t;
                })
                .subscribe(
                        result -> System.out.println("map result: " + result),
                        error -> System.out.println("map error: " + error)
                );
    }

    /**
    * 多值onErrorResume
    */
    @Test
    public void multiValueOnErrorResume() {
        Flux.just(1, 2, 3)
                .map(t -> {
                    if (t == 2) {
                        throw new RuntimeException("test");
                    }
                    return t;
                })
                .onErrorResume(e -> Flux.just(11, 12, 13))
                .subscribe(
                        result -> System.out.println("result: " + result),
                        error -> System.out.println("error: " + error)
                );
        // or
        Flux.just(1, 2, 3)
                .handle((t, sink) -> {
                    if (t == 2) {
                        sink.error(new RuntimeException("test"));
                        return;
                    }
                    sink.next(t);
                })
                .onErrorResume(e -> Flux.just(11, 12, 13))
                .subscribe(
                        result -> System.out.println("result: " + result),
                        error -> System.out.println("error: " + error)
                );
    }

    /**
    * onErrorResume和onErrorReturn区别（流的执行跟顺序有关）
    */
    @Test
    public void onErrorResumeAndOnErrorReturnTest() {
        Function<Integer, Integer> fun = value->{
            throw new RuntimeException("test");
        };

        Mono.fromCallable(()->fun.apply(1))
                .onErrorResume(res->{System.out.println("yes");return Mono.just(2);})
                .onErrorReturn(3)
                .subscribe(
                        result->System.out.println("result: " + result),
                        error->System.out.println("error: " + error)
                );

        Mono.fromCallable(()->fun.apply(1))
                .onErrorReturn(3)
                .onErrorResume(res->{System.out.println("yes");return Mono.just(2);})
                .subscribe(
                        result->System.out.println("result: " + result),
                        error->System.out.println("error: " + error)
                );
    }

    /**
    * fromCallable和fromRunnable区别
    */
    @Test
    public void fromCallableAndFromRunnable() {
        Function<Integer, Integer> fun = value-> value * value;

        Mono.fromCallable(()->fun.apply(2))
                .subscribe(
                        result->System.out.println("fromCallable result: " + result),
                        error->System.out.println("fromCallable error: " + error)
                );
        Mono.fromRunnable(()->fun.apply(3))
                .subscribe(
                        result->System.out.println("fromRunnable result: " + result),
                        error->System.out.println("fromRunnable error: " + error)
                );
    }

    /**
    * reactor形式的try-with-resource
    */

    @Test
    public void reactorTryWithResource() {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();) {
            log.info("size1: {}", byteArrayOutputStream.size());
        } catch (IOException e) {
            log.error("error1: ", e);
        }

//        Flux.using(
//                        ByteArrayOutputStream::new,
//                        byteArrayOutputStream -> {
//                            log.info("size2: {}", byteArrayOutputStream.size());
//                            return Flux.empty();
//                        },
//                        ByteArrayOutputStream::close
//                )
//                .subscribe(
//                        result -> log.info("result: {}", result),
//                        error -> log.info("error: ", error)
//                );
    }

    /**
    * parallel和flatmap的concurrency测试
    */
    @Test
    public void parallelAndConcurrency() {
        // 并发起作用
        Flux.range(1, 5)
                .parallel(5)
                .runOn(Schedulers.boundedElastic())
                .flatMap(i -> Mono.fromRunnable(() -> {
                    log.info("parallel: {}", i);
                    sleep(1000);
                }))
                .subscribe();

        // 并发起作用
        Flux.range(1, 5)
                .flatMap(i -> Mono.fromRunnable(() -> {
                    log.info("concurrency-1: {}", i);
                    sleep(1000);
                }).subscribeOn(Schedulers.boundedElastic()), 5, 3)
                .subscribe();

        // 并发不起作用
        Flux.range(1, 5)
                .flatMap(i -> Mono.fromRunnable(() -> {
                    log.info("concurrency-0: {}", i);
                    sleep(1000);
                }), 5, 3)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        sleep(10000); //等待执行结束
    }

    void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception ignored) {

        }
    }
    
    
}
