package com.ssafy.S14P21A205.order.service;

import com.ssafy.S14P21A205.order.dto.CurrentOrderResponse;
import com.ssafy.S14P21A205.order.dto.RegularOrderRequest;
import com.ssafy.S14P21A205.order.dto.RegularOrderResponse;

public interface OrderService {
    CurrentOrderResponse getCurrentOrder(Integer userId, Integer menuId);

    RegularOrderResponse createRegularOrder(Integer userId, RegularOrderRequest request);
}
