package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 포인트 비즈니스 로직을 처리하는 서비스 클래스
 * 포인트 조회, 충전, 사용, 내역 조회 기능
 */
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

    /**
     * 사용자 ID를 통해 포인트 정보 조회
     * @param userId 조회할 사용자 ID (1 이상)
     * @return 사용자 포인트 정보
     */
    public UserPoint getPoint(long userId) {
        if (userId < 1) {
            throw new IllegalArgumentException(ERROR_INVALID_USER_ID);
        }
        return userPointTable.selectById(userId);
    }

    /**
     * 특정 사용자에게 포인트를 충전
     * 동기화를 위해 사용자별 lock 사용
     * @param userId 포인트를 충전할 사용자 ID
     * @param amount 충전할 포인트 (1 이상)
     * @return 충전 후 업데이트된 사용자 포인트 정보
     */
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

    /**
     * 특정 사용자의 포인트를 사용
     * 동기화를 위해 사용자별 lock 사용
     * @param userId 포인트를 사용할 사용자 ID
     * @param amount 사용한 포인트 (100 단위)
     * @return 사용 후 업데이트된 사용자 포인트 정보
     */
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

    /**
     * 특정 사용자의 포인트 충전 및 사용 내역 조회
     * 포인트 내역은 내림차순으로 정렬
     * @param userId 포인트 내역을 조회할 사용자 ID
     * @return 검색된 포인트 내역 리스트
     */
    public List<PointHistory> getPointHistory(long userId) {
        return pointHistoryTable.selectAllByUserId(userId)
                .stream()
                .sorted(Comparator.comparing(PointHistory::id).reversed())
                .toList();
    }
}
