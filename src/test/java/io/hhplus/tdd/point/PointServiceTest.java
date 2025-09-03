package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PointServiceTest {
    @Mock
    private PointHistoryTable pointHistoryTable;
    @Mock
    private UserPointTable userPointTable;
    @InjectMocks
    private PointService pointService;

    /*
     Nested 구조를 통해서 각 테스트 코드가 어디에 속하는지 명확하게 확인할 수 있도록 함
     */

    @Nested
    @DisplayName("포인트 조회 테스트")
    public class GetPointTest {
        /*
         [작성이유]
         기존 사용자가 포인트를 조회할 경우, 정상적으로 현재 보유하고 있는 포인트를 반환하는지 확인하기 위해 작성함
         */
        @Test
        void 기존_사용자가_포인트를_조회할_때_정상적으로_보유_포인트가_반환된다() {
            // given
            long userId = 1L;
            long existingPoint = 100_000L;
            UserPoint userPoint = new UserPoint(userId, existingPoint, System.currentTimeMillis());
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when
            UserPoint result = pointService.getPoint(userId);

            // then
            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.point()).isEqualTo(existingPoint);
        }
        /*
         [작성이유]
         포인트가 0인 신규 사용자가 포인트를 조회할 경우, 정상적으로 현재 보유하고 있는 포인트를 반환하는지 확인하기 위해 작성함
         */
        @Test
        void 신규_사용자가_포인트를_조회할_때_정상적으로_보유_포인트가_반환된다() {
            // given
            long userId = 1L;
            UserPoint userPoint = UserPoint.empty(userId);
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when
            UserPoint result = pointService.getPoint(userId);

            // then
            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.point()).isEqualTo(0);
        }
        /*
         [작성이유]
         사용자 정보가 잘못되었을 경우 (0 이하의 ID), 예외가 발생하는지 확인하기 위해 작성함
         */
        @Test
        void 사용자_정보가_잘못되었을_때_조회하면_예외가_발생한다() {
            // given
            long invalidUserId = -1L;

            // when & then
            assertThatThrownBy(() -> pointService.getPoint(invalidUserId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("잘못된 사용자 ID입니다.");
        }
    }

    @Nested
    @DisplayName("포인트 충전 테스트")
    public class ChargePointTest {
        /*
         [작성이유]
         기존 사용자가 포인트를 충전할 경우, 정상적으로 포인트가 업데이트 되는지 확인하기 위해 작성함
         경계값인 보유 10만 포인트 충전 90만 포인트로 충전해봄 (최대 보유 가능한 포인트가 100만)
         */
        @Test
        void 기존_사용자가_적정_포인트를_충전할_때_포인트가_정상적으로_누적된다() {
            // given
            long userId = 1L;
            long existingPoint = 100_000L;
            long chargeAmount = 900_000L;
            long newAmount = existingPoint + chargeAmount;

            UserPoint existingUserPoint = new UserPoint(userId, existingPoint, System.currentTimeMillis());
            UserPoint updatedUserPoint = new UserPoint(userId, newAmount, System.currentTimeMillis());

            when(userPointTable.selectById(userId)).thenReturn(existingUserPoint);
            when(userPointTable.insertOrUpdate(userId, newAmount)).thenReturn(updatedUserPoint);

            // when
            UserPoint result = pointService.charge(userId, chargeAmount);

            // then
            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.point()).isEqualTo(newAmount);
        }
        /*
         [작성이유]
         신규 사용자가 포인트를 충전할 경우, 정상적으로 신규 사용자의 포인트가 업데이트 되는지 확인하기 위해 작성함
         경계값인 100만 포인트로 충전해봄 (최대 보유 가능한 포인트가 100만)
         */
        @Test
        void 신규_사용자가_적정_포인트를_충전할_때_포인트가_정상적으로_누적된다() {
            // given
            long userId = 1L;
            long chargeAmount = 1_000_000L;

            UserPoint existingUserPoint = UserPoint.empty(userId);
            UserPoint updatedUserPoint = new UserPoint(userId, chargeAmount, System.currentTimeMillis());

            when(userPointTable.selectById(userId)).thenReturn(existingUserPoint);
            when(userPointTable.insertOrUpdate(userId, chargeAmount)).thenReturn(updatedUserPoint);

            // when
            UserPoint result = pointService.charge(userId, chargeAmount);

            // then
            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.point()).isEqualTo(chargeAmount);
        }
        /*
         [작성이유]
         사용자가 포인트를 충전할 경우, 충전된 포인트 내역이 정상적으로 기록되는지 확인하기 위해 작성함
         */
        @Test
        void 포인트를_충전할_때_충전_내역이_정상적으로_기록된다() {
            // given
            long userId = 1L;
            long chargeAmount = 5000L;

            UserPoint existingUserPoint = UserPoint.empty(userId);

            when(userPointTable.selectById(userId)).thenReturn(existingUserPoint); // 메소드 내에서 storedUserPoint 사용함으로 값 부여해야함

            // when
            pointService.charge(userId, chargeAmount);

            // then
            verify(pointHistoryTable, times(1)).insert(
                    eq(userId),
                    eq(chargeAmount),
                    eq(TransactionType.CHARGE),
                    anyLong()
            );
        }
        /*
         [작성이유]
         사용자 정보가 잘못되었을 경우 (0 이하의 ID), 예외가 발생하는지 확인하기 위해 작성함
         */
        @Test
        void 사용자_정보가_잘못되었을_때_충전하면_예외가_발생한다() {
            // given
            long invalidUserId = 0L;
            long chargeAmount = 5000L;

            // when & then
            assertThatThrownBy(() -> pointService.charge(invalidUserId, chargeAmount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("잘못된 사용자 ID입니다.");
        }
        /*
         [작성이유]
         사용자가 충전 불가능한 0 이하의 포인트를 충전할 경우, 예외가 발생하는지 확인하기 위해 작성함
         */
        @Test
        void 충전할_포인트가_0_이하일_때_충전하면_예외가_발생한다() {
            // given
            long userId = 1L;
            long invalidChargeAmount  = 0L;

            // when & then
            assertThatThrownBy(() -> pointService.charge(userId, invalidChargeAmount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("충전 포인트는 1원 이상이어야 합니다.");
        }
        /*
         [작성이유]
         충전했을 때 누적 포인트가 100만을 넘는 경우, 예외가 발생하는지 확인하기 위해 작성함 (최대 보유 가능한 포인트가 100만)
         */
        @Test
        void 충전_후_누적_포인트가_1_000_000을_넘으면_예외가_발생한다() {
            // given
            long userId = 1L;
            long chargeAmount  = 1_000_001L;

            UserPoint existingUserPoint = UserPoint.empty(userId);

            when(userPointTable.selectById(userId)).thenReturn(existingUserPoint);

            // when & then
            assertThatThrownBy(() -> pointService.charge(userId, chargeAmount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("최대 보유 가능한 포인트는 100만 포인트입니다.");
        }
    }
}
