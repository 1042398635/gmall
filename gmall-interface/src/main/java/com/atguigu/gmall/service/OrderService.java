package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.enums.ProcessStatus;

import java.util.List;
import java.util.Map;

public interface OrderService {

    String saveOrder(OrderInfo orderInfo);

    String genToken(String userId);

    Boolean verifyToken(String userId,String token);

    OrderInfo getOrderInfo(String orderId);

    void updateStatus(String orderId, ProcessStatus paid,OrderInfo... orderInfos);

    List<Integer> checkExpiredCoupon();

    void handleExpiredCoupon(Integer id);

    List<Map> orderSplit(String orderId, String wareSkuMap);
}
