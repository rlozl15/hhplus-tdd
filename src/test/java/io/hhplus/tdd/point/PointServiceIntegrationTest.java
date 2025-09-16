package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
public class PointServiceIntegrationTest {
    @Autowired
    private PointService pointService;
    @Autowired
    private UserPointTable userPointTable;
    @Autowired
    private PointHistoryTable pointHistoryTable;
    private final long userId = 1L;
    private final long initAmount = 100_000L;

    @BeforeEach
    void setup() {
        // 테스트용 사용자 초기 포인트 세팅
        userPointTable.insertOrUpdate(userId,initAmount);
    }

    @Test
    void 사용자가_동시에_100번_100_포인트를_충전한다면_10_000_포인트가_증가한다() throws InterruptedException{
        // given
        int threadCount = 100;
        long amount = 100L;
        int beforeSize = pointHistoryTable.selectAllByUserId(userId).size();

        // threadCount만큼의 스레드 풀을 만들어 병렬 작업 처리
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        // threadCount만큼의 카운트를 가지고 있는 동기화 도구 (모든 스레드가 다 끝날 때까지 기다리도록 보장)
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    pointService.charge(userId, amount);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
        executor.shutdown();

        // then
        // 10_000 포인트 충전
        UserPoint result = pointService.getPoint(userId);
        assertThat(result.point()).isEqualTo(initAmount + amount * threadCount);

        // 100번 기록
        List<PointHistory> pointHistoryList = pointHistoryTable.selectAllByUserId(userId);
        assertThat(pointHistoryList.size()).isEqualTo(threadCount + beforeSize);
    }

    @Test
    void 사용자가_동시에_100번_100_포인트를_사용한다면_10_000_포인트가_감소한다() throws InterruptedException{
        // given
        int threadCount = 100;
        long amount = 100L;
        int beforeSize = pointHistoryTable.selectAllByUserId(userId).size();

        // threadCount만큼의 스레드 풀을 만들어 병렬 작업 처리
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        // threadCount만큼의 카운트를 가지고 있는 동기화 도구 (모든 스레드가 다 끝날 때까지 기다리도록 보장)
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    pointService.use(userId, amount);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                } finally {
                    countDownLatch.countDown();
                }
            });
        }
        countDownLatch.await();
        executor.shutdown();

        // then
        // 10_000 포인트 충전
        UserPoint result = pointService.getPoint(userId);
        assertThat(result.point()).isEqualTo(initAmount - amount * threadCount);

        // 100번 기록
        List<PointHistory> pointHistoryList = pointHistoryTable.selectAllByUserId(userId);
        assertThat(pointHistoryList.size()).isEqualTo(threadCount + beforeSize);
    }
}
