package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    private static final long MAXIMUM_POINT = 1_000_000L;

    // 예외 메시지
    private static final String ERROR_INVALID_USER_ID = "잘못된 사용자 ID입니다.";
    private static final String ERROR_INVALID_CHARGE_AMOUNT = "충전 포인트는 1포인트 이상이어야 합니다.";
    private static final String ERROR_EXCEED_MAXIMUM_POINT = "최대 보유 가능한 포인트는 100만 포인트입니다.";
    private static final String ERROR_INSUFFICIENT_POINT = "포인트 잔액이 부족합니다.";
    private static final String ERROR_INVALID_USE_AMOUNT = "사용 포인트는 최소 100포인트 이상이어야 합니다.";
    private static final String ERROR_MINIMUM_POINT_UNIT = "사용 포인트는 최소 사용 단위인 100의 배수여야 합니다.";

    // 사용자별로 동기화 객체 생성
    private final ConcurrentHashMap<Long, Object> userLocks = new ConcurrentHashMap<>();

    private Object getLock(long userId) {
        return userLocks.computeIfAbsent(userId, k -> new Object());
    }

    public UserPoint getPoint(long userId) {
        if (userId < 1) {
            throw new IllegalArgumentException(ERROR_INVALID_USER_ID);
        }
        return userPointTable.selectById(userId);
    }

    public UserPoint charge(long userId, long amount) {
        if (amount < 1) {
            throw new IllegalArgumentException(ERROR_INVALID_CHARGE_AMOUNT);
        }

        Object lock = getLock(userId);
        synchronized (lock) {
            UserPoint storedUserPoint = getPoint(userId);
            long newAmount = storedUserPoint.point() + amount;

            if (newAmount > MAXIMUM_POINT) {
                throw new IllegalArgumentException(ERROR_EXCEED_MAXIMUM_POINT);
            }

            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newAmount);
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());
            return updatedUserPoint;
        }
    }

    public UserPoint use(long userId, long amount) {
        if (amount < 100) {
            throw new IllegalArgumentException(ERROR_INVALID_USE_AMOUNT);
        }
        if (amount % 100 != 0) {
            throw new IllegalArgumentException(ERROR_MINIMUM_POINT_UNIT);
        }

        Object lock = getLock(userId);
        synchronized (lock) {
            UserPoint storedUserPoint = getPoint(userId);
            long newAmount = storedUserPoint.point() - amount;

            if (newAmount < 0) {
                throw new IllegalArgumentException(ERROR_INSUFFICIENT_POINT);
            }

            UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newAmount);
            pointHistoryTable.insert(userId, amount, TransactionType.USE, System.currentTimeMillis());
            return updatedUserPoint;
        }
    }

    public List<PointHistory> getPointHistory(long userId) {
        return pointHistoryTable.selectAllByUserId(userId)
                .stream()
                .sorted(Comparator.comparing(PointHistory::id).reversed())
                .toList();
    }
}
