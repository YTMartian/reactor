import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;


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
}
