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
                .doOnSubscribe(r -> r.request(0))
        );
        result.subscribe(i -> {
            System.out.println("+++" + i);
        });
    }
}
