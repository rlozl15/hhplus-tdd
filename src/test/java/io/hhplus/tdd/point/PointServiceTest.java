package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

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

    /**
     * Nested 구조를 통해서 각 테스트 코드가 어디에 속하는지 명확하게 확인할 수 있도록 함
     */

    @Nested
    @DisplayName("포인트 조회 테스트")
    public class GetPointTest {
        /**
         * [작성이유]
         * 기존 사용자가 포인트를 조회할 경우, 정상적으로 현재 보유하고 있는 포인트를 반환하는지 확인하기 위해 작성함
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
        /**
         * [작성이유]
         * 포인트가 0인 신규 사용자가 포인트를 조회할 경우, 정상적으로 현재 보유하고 있는 포인트를 반환하는지 확인하기 위해 작성함
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
        /**
         * [작성이유]
         * 사용자 정보가 잘못되었을 경우 (0 이하의 ID), 예외가 발생하는지 확인하기 위해 작성함
         */
        @ParameterizedTest
        @ValueSource(longs = {-10, 0})
        void 사용자_정보가_잘못되었을_때_조회하면_예외가_발생한다(long invalidUserId) {
            // given invalidUserId

            // when & then
            assertThatThrownBy(() -> pointService.getPoint(invalidUserId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("잘못된 사용자 ID입니다.");
        }
    }

    @Nested
    @DisplayName("포인트 충전 테스트")
    public class ChargePointTest {
        /**
         * [작성이유]
         * 기존 사용자가 포인트를 충전할 경우, 정상적으로 포인트가 업데이트 되는지 확인하기 위해 작성함
         * 최소 충전 가능 포인트 1 과 최대 보유 가능한 포인트 100만을 고려하여
         * 경계값 1과 900_000L(기존 포인트 100_000L 가정), 그 동등 분할 범위 대표값 2, 899_999L를 테스트함
         */
        @ParameterizedTest
        @ValueSource(longs = {1, 2, 899_999L, 900_000L})
        void 기존_사용자가_적정_포인트를_충전할_때_포인트가_정상적으로_누적된다(long chargeAmount) {
            // given
            long userId = 1L;
            long existingPoint = 100_000L;
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
        /**
         * [작성이유]
         * 신규 사용자가 포인트를 충전할 경우, 정상적으로 신규 사용자의 포인트가 업데이트 되는지 확인하기 위해 작성함
         * 최소 충전 가능 포인트 1 과 최대 보유 가능한 포인트 100만을 고려하여
         * 경계값 1, 1_000_000L, 그리고 동등 분할 범위 대표값으로 2, 999_999L를 테스트함
         */
        @ParameterizedTest
        @ValueSource(longs = {1, 2, 999_999L, 1_000_000L})
        void 신규_사용자가_적정_포인트를_충전할_때_포인트가_정상적으로_누적된다(long chargeAmount) {
            // given
            long userId = 1L;

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
        /**
         * [작성이유]
         * 사용자가 포인트를 충전할 경우, 충전된 포인트 내역이 정상적으로 기록되는지 확인하기 위해 작성함
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
        /**
         * [작성이유]
         * 사용자 정보가 잘못되었을 경우 (0 이하의 ID), 예외가 발생하는지 확인하기 위해 작성함
         */
        @ParameterizedTest
        @ValueSource(longs = {-10, 0})
        void 사용자_정보가_잘못되었을_때_충전하면_예외가_발생한다(long invalidUserId) {
            // given
            long chargeAmount = 5000L;

            // when & then
            assertThatThrownBy(() -> pointService.charge(invalidUserId, chargeAmount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("잘못된 사용자 ID입니다.");
        }
        /**
         * [작성이유]
         * 사용자가 충전 불가능한 0 이하의 포인트를 충전할 경우, 예외가 발생하는지 확인하기 위해 작성함
         * 경계값 0, 동등 분할 범위 대표값 -1을 테스트함
         */
        @ParameterizedTest
        @ValueSource(longs = {-1, 0})
        void 충전할_포인트가_0_이하일_때_충전하면_예외가_발생한다(long invalidChargeAmount) {
            // given
            long userId = 1L;

            // when & then
            assertThatThrownBy(() -> pointService.charge(userId, invalidChargeAmount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("충전 포인트는 1포인트 이상이어야 합니다.");
        }
        /**
         * [작성이유]
         * 충전했을 때 누적 포인트가 100만을 넘는 경우, 예외가 발생하는지 확인하기 위해 작성함 (최대 보유 가능한 포인트가 100만)
         * 경계값 1_000_001L, 동등 분할 범위 대표값 1_000_100L을 테스트함
         */
        @ParameterizedTest
        @ValueSource(longs = {1_000_001L, 1_000_100L})
        void 충전_후_누적_포인트가_1_000_000을_넘으면_예외가_발생한다(long invalidChargeAmount) {
            // given
            long userId = 1L;

            UserPoint existingUserPoint = UserPoint.empty(userId);

            when(userPointTable.selectById(userId)).thenReturn(existingUserPoint);

            // when & then
            assertThatThrownBy(() -> pointService.charge(userId, invalidChargeAmount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("최대 보유 가능한 포인트는 100만 포인트입니다.");
        }
    }

    @Nested
    @DisplayName("포인트 사용 테스트")
    public class UsePointTest {
        /**
         * [작성이유]
         * 기존 사용자가 현재 보유하고 있는 포인트 이내를 사용할 경우, 정상적으로 포인트가 사용되는지 확인하기 위해 작성함
         * 경계값 100, 100_000L(보유 포인트), 동등 분할 범위 대표값 1_000L을 테스트함
         */
        @ParameterizedTest
        @ValueSource(longs = {100, 1_000L, 100_000L})
        void 기존_사용자가_보유_포인트보다_적은_포인트를_사용할_때_포인트가_정상적으로_사용된다(long usePoint) {
            // given
            long userId = 1L;
            long existingPoint = 100_000L;
            long remainPoint = existingPoint - usePoint;

            UserPoint userPoint = new UserPoint(userId, existingPoint, System.currentTimeMillis());
            UserPoint updatedUserPoint = new UserPoint(userId, remainPoint, System.currentTimeMillis());

            when(userPointTable.selectById(userId)).thenReturn(userPoint);
            when(userPointTable.insertOrUpdate(userId, remainPoint)).thenReturn(updatedUserPoint);

            // when
            UserPoint result = pointService.use(userId, usePoint);

            // then
            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.point()).isEqualTo(remainPoint);
        }
        /**
         * [작성이유]
         * 포인트를 사용할 경우, 사용 내역이 정상적으로 기록되는지 확인하기 위해 작성함
         */
        @Test
        void 포인트를_사용할_때_사용_내역이_정상적으로_기록된다() {
            // given
            long userId = 1L;
            long existingPoint = 100_000L;
            long usePoint = 50_000L;
            UserPoint userPoint = new UserPoint(userId, existingPoint, System.currentTimeMillis());
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when
            pointService.use(userId, usePoint);

            // then
            verify(pointHistoryTable, times(1)).insert(
                    eq(userId),
                    eq(usePoint),
                    eq(TransactionType.USE),
                    anyLong()
            );
        }
        /**
         * [작성이유]
         * 기존 사용자가 현재 보유하고 있는 포인트보다 많은 포인트를 사용할 경우, 예외가 발생하는지 확인하기 위해 작성함
         */
        @Test
        void 기존_사용자가_잔액이_부족할_때_포인트_사용을_시도하면_예외가_발생한다() {
            // given
            long userId = 1L;
            long existingPoint = 100_000L;
            long usePoint = 100_100L;
            UserPoint userPoint = new UserPoint(userId, existingPoint, System.currentTimeMillis());
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThatThrownBy(() -> pointService.use(userId, usePoint))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("포인트 잔액이 부족합니다.");
        }
        /**
         * [작성이유]
         * 초기 0 포인트가 주어지는 신규 사용자가 포인트 사용을 시도하는 경우, 예외가 발생하는지 확인하기 위해 작성함
         */
        @Test
        void 신규_사용자가_포인트_사용을_시도하면_예외가_발생한다() {
            // given
            long userId = 1L;
            long usePoint = 100L;
            UserPoint userPoint = UserPoint.empty(userId);
            when(userPointTable.selectById(userId)).thenReturn(userPoint);

            // when & then
            assertThatThrownBy(() -> pointService.use(userId, usePoint))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("포인트 잔액이 부족합니다.");
        }
        /**
         * [작성이유]
         * 최소로 요청 가능한 사용 포인트가 100일 때 그보다 적은 포인트를 사용하는 경우, 예외가 발생하는지 확인하기 위해 작성함
         */
        @Test
        void 사용_요청_포인트가_최소_사용_단위인_100보다_작으면_예외가_발생한다() {
            // given
            long userId = 1L;
            long usePoint = 99L;

            // when & then
            assertThatThrownBy(() -> pointService.use(userId, usePoint))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("사용 포인트는 최소 100포인트 이상이어야 합니다.");
        }
        /**
         * [작성이유]
         * 사용 포인트가 최소 사용 단위인 100의 배수가 아닐 경우, 예외가 발생하는지 확인하기 위해 작성함
         */
        @Test
        void 사용_요청_포인트가_최소_사용_단위의_배수가_아니면_예외가_발생한다() {
            // given
            long userId = 1L;
            long usePoint = 999L;

            // when & then
            assertThatThrownBy(() -> pointService.use(userId, usePoint))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("사용 포인트는 최소 사용 단위인 100의 배수여야 합니다.");
        }
    }

    @Nested
    @DisplayName("포인트 내역 조회 테스트")
    public class GetPointHistoryTest {
        /**
         * [작성이유]
         * 기존 사용자가 포인트 충전 및 사용 내역을 조회할 경우, 정상적으로 포인트 내역 리스트가 반환되는지 확인하기 위해 작성함
         */
        @Test
        void 기존_사용자가_충전_및_사용_내역을_조회할_때_정상적으로_조회된다() {
            // given
            long userId = 1L;
            List<PointHistory> expectedHistory = Arrays.asList(
                    new PointHistory(1L, userId, 100_000L, TransactionType.CHARGE, System.currentTimeMillis()),
                    new PointHistory(2L, userId, 10_000L, TransactionType.USE, System.currentTimeMillis())
            );

            when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(expectedHistory);

            // when
            List<PointHistory> result = pointService.getPointHistory(userId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result).extracting("userId").containsOnly(userId);
            assertThat(result).extracting("amount").containsExactlyInAnyOrder(100_000L, 10_000L);
        }
        /**
         * [작성이유]
         * 사용자가 포인트 내역을 조회할 경우, 목록이 id를 기준으로 내림차순으로 조회되는지 확인하기 위해 작성함
         */
        @Test
        void 포인트_내역을_조회하면_내림차순으로_조회된다() {
            //given
            long userId = 1L;
            List<PointHistory> expectedHistory = Arrays.asList(
                    new PointHistory(1L, userId, 100_000L, TransactionType.CHARGE, System.currentTimeMillis()),
                    new PointHistory(2L, userId, 10_000L, TransactionType.USE, System.currentTimeMillis())
            );
            when(pointHistoryTable.selectAllByUserId(userId)).thenReturn(expectedHistory);

            // when
            List<PointHistory> result = pointService.getPointHistory(userId);

            // then
            assertThat(result).extracting("id").containsExactly(2L, 1L);

        }
        /**
         * [작성이유]
         * 내역이 없는 사용자가 포인트 내역을 조회할 경우, 정상적으로 빈 리스트의 포인트 내역이 반환되는지 확인하기 위해 작성함
         */
        @Test
        void 내역이_없는_사용자가_충전_및_사용_내역을_조회할_때_정상적으로_빈_리스트가_반환된다() {
            //given
            long userId = 1L;

            // when
            List<PointHistory> result = pointService.getPointHistory(userId);

            // then
            assertThat(result).isEmpty();
        }
    }
}
