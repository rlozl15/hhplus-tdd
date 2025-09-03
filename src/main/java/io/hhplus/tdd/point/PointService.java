package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    private static final long MAXIMUM_POINT = 1_000_000L;

    // 예외 메시지
    private static final String ERROR_INVALID_USER_ID = "잘못된 사용자 ID입니다.";
    private static final String ERROR_INVALID_AMOUNT = "충전 포인트는 1원 이상이어야 합니다.";
    private static final String ERROR_EXCEED_MAXIMUM_POINT = "최대 보유 가능한 포인트는 100만 포인트입니다.";

    public UserPoint getPoint(long userId) {
        if (userId < 1) {
            throw new IllegalArgumentException(ERROR_INVALID_USER_ID);
        }
        return userPointTable.selectById(userId);
    }

    public UserPoint charge(long userId, long amount) {
        if (userId < 1) {
            throw new IllegalArgumentException(ERROR_INVALID_USER_ID);
        }

        if (amount < 1) {
            throw new IllegalArgumentException(ERROR_INVALID_AMOUNT);
        }

        UserPoint storedUserPoint = userPointTable.selectById(userId);
        long newAmount = storedUserPoint.point() + amount;

        if (newAmount > MAXIMUM_POINT) {
            throw new IllegalArgumentException(ERROR_EXCEED_MAXIMUM_POINT);
        }

        UserPoint updatedUserPoint = userPointTable.insertOrUpdate(userId, newAmount);
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, System.currentTimeMillis());

        return updatedUserPoint;
    }
}
