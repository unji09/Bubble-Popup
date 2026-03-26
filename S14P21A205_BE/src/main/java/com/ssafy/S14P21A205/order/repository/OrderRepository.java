package com.ssafy.S14P21A205.order.repository;

import com.ssafy.S14P21A205.order.entity.Order;
import com.ssafy.S14P21A205.order.entity.OrderType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
            select o
            from Order o
            where o.store.id = :storeId
              and o.orderedDay = :orderedDay
              and (o.orderType is null or o.orderType = com.ssafy.S14P21A205.order.entity.OrderType.NORMAL)
            order by o.id asc
            """)
    List<Order> findDailyStartOrders(
            @Param("storeId") Long storeId,
            @Param("orderedDay") Integer orderedDay
    );

    @Query("""
            select o
            from Order o
            where o.store.id = :storeId
              and o.orderedDay = :orderedDay
              and o.orderType = com.ssafy.S14P21A205.order.entity.OrderType.EMERGENCY
              and coalesce(o.isArrived, false) = false
            order by o.arrivedTime asc, o.id asc
            """)
    List<Order> findPendingEmergencyOrders(
            @Param("storeId") Long storeId,
            @Param("orderedDay") Integer orderedDay
    );

    List<Order> findByStoreIdAndOrderedDayAndOrderTypeOrderByArrivedTimeAscIdAsc(
            Long storeId,
            Integer orderedDay,
            OrderType orderType
    );

    List<Order> findByStoreIdAndOrderTypeOrderByArrivedTimeAscIdAsc(
            Long storeId,
            OrderType orderType
    );

    Optional<Order> findFirstByStore_IdAndOrderedDayLessThanEqualAndOrderTypeAndSalePriceIsNotNullOrderByOrderedDayDescIdDesc(
            Long storeId,
            Integer orderedDay,
            OrderType orderType
    );

    Optional<Order> findFirstByStore_IdAndOrderedDayLessThanEqualAndSalePriceIsNotNullOrderByOrderedDayDescIdDesc(
            Long storeId,
            Integer orderedDay
    );

    default Optional<Order> findDailyStartOrder(Long storeId, Integer orderedDay) {
        return findDailyStartOrders(storeId, orderedDay).stream().findFirst();
    }

    default Optional<Order> findPendingEmergencyOrder(Long storeId, Integer orderedDay) {
        return findPendingEmergencyOrders(storeId, orderedDay).stream().findFirst();
    }
}
