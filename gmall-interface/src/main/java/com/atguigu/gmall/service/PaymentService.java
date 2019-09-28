package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.PaymentInfo;

public interface PaymentService {
    void savePaymentInfo(PaymentInfo paymentInfo);

    PaymentInfo getPaymentInfo(PaymentInfo paymentInfo);

    void updatePaymentInfo(String out_trade_no, PaymentInfo paymentInfoForUpdate);

    void sendPaymentToOrder(String orderId,String result);

    void sendDelayPaymentResult(String outTradeNo,int delaySec,int checkCount);

}
