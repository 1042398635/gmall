package com.atguigu.gmall.payment.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.bean.PaymentInfo;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.enums.PaymentStatus;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Controller
public class PaymentController {

    @Reference
    OrderService orderService;

    @Autowired
    AlipayClient alipayClient;

    @Reference
    PaymentService paymentService;

    // payment.gmall.com/index?orderId=104
    @GetMapping("index")
    @LoginRequire
    public String index(String orderId, HttpServletRequest request){
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);
        request.setAttribute("orderId",orderId);
        request.setAttribute("totalAmount",orderInfo.getTotalAmount());
        return "index";
    }
    //alipay/submit
    @PostMapping("/alipay/submit")
    @ResponseBody
    public String alipaySubmit(String orderId, HttpServletResponse response){
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderId(orderId);
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfo.setSubject(orderInfo.genSubject());
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID);
        paymentService.savePaymentInfo(paymentInfo);

        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();

        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);

//        long currentTimeMillis = System.currentTimeMillis();
//        String outTredeNo = "atguigu-"+orderId + "-" + currentTimeMillis;

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("out_trade_no",paymentInfo.getOutTradeNo());
        jsonObject.put("product_code","FAST_INSTANT_TRADE_PAY");
        jsonObject.put("subject",paymentInfo.getSubject());
        jsonObject.put("total_amount",paymentInfo.getTotalAmount());
        alipayRequest.setBizContent(jsonObject.toJSONString());

        String submitHtml ="";
        try {
            submitHtml = alipayClient.pageExecute(alipayRequest).getBody();
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        response.setContentType("text/html:cartset=UTF-8");

        paymentService.sendDelayPaymentResult(paymentInfo.getOutTradeNo(),10,3);
        return submitHtml;
    }

    //payment.gmall.com/alipay/callback/return
    @GetMapping("/alipay/callback/return")
    @ResponseBody
    public String callbackReturn(){
        //return "交易成功";
        return "redirect:"+AlipayConfig.return_order_url;

    }

    @PostMapping("/alipay/callback/notify")
    public String notify (@RequestParam Map<String,String> paramMap, HttpServletRequest request) throws AlipayApiException {

        boolean ifPass = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, "utf-8", AlipayConfig.sign_type);
        if (!ifPass){
            return "fail";
        }
        String trade_status = paramMap.get("trade_status");
        if ("TRADE_SUCCESS".equals(trade_status)){
            String out_trade_no = paramMap.get("out_trade_no");
            PaymentInfo paymentInfoQuery = new PaymentInfo();
            paymentInfoQuery.setOutTradeNo(out_trade_no);
            PaymentInfo paymentInfo = paymentService.getPaymentInfo(paymentInfoQuery);
            String total_amount = paramMap.get("total_amount");
            if (paymentInfo.getTotalAmount().compareTo(new BigDecimal(total_amount))==0){
                if (paymentInfo.getPaymentStatus().equals(PaymentStatus.UNPAID)){
                    PaymentInfo paymentInfoForUpdate = new PaymentInfo();
                    paymentInfoForUpdate.setPaymentStatus(PaymentStatus.PAID);
                    paymentInfoForUpdate.setCallbackTime(new Date());
                    paymentInfoForUpdate.setCallbackContent(JSON.toJSONString(paramMap));
                    paymentInfoForUpdate.setAlipayTradeNo(paramMap.get("trade_no"));
                    paymentService.updatePaymentInfo(out_trade_no,paymentInfoForUpdate);
                    //异步消息给订单
                    return "success";
                }else if (paymentInfo.getPaymentStatus().equals(PaymentStatus.ClOSED)){
                    return "fail";
                }else if (paymentInfo.getPaymentStatus().equals(PaymentStatus.PAID)){
                    return "success";
                }
            }
        }

        return "fail";
    }

    @GetMapping("sendPayment")
    @ResponseBody
    public String sendPayment(String orderId){
        paymentService.sendPaymentToOrder(orderId,"success");
        return "success";
    }

    @GetMapping("refund")
    @ResponseBody
    public String refund(String orderId) throws AlipayApiException {
        PaymentInfo paymentInfoQuery = new PaymentInfo();
        paymentInfoQuery.setOrderId(orderId);
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(paymentInfoQuery);

        //AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do","app_id","your private_key","json","GBK","alipay_public_key");
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
/*        request.setBizContent("{" +
                "    \"out_trade_no\":\""+paymentInfo.getOutTradeNo()+"\"," +
                "    \"refund_amount\":"+paymentInfo.getTotalAmount()+
                "  }");*/
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("out_trade_no",paymentInfo.getOutTradeNo());
        jsonObject.put("refund_amount",paymentInfo.getTotalAmount());//.add(new BigDecimal(1))
        request.setBizContent(jsonObject.toJSONString());
        AlipayTradeRefundResponse response = alipayClient.execute(request);
        if(response.isSuccess()){
            System.out.println("调用成功");
            System.out.println("业务退款成功!");
            PaymentInfo paymentInfoForUpdate = new PaymentInfo();
            paymentInfoForUpdate.setPaymentStatus(PaymentStatus.ClOSED);
            paymentService.updatePaymentInfo(paymentInfo.getOutTradeNo(),paymentInfoForUpdate);
            return "success";
        } else {
            System.out.println("调用失败");
            return response.getSubCode()+":"+response.getSubMsg();
        }
    }

}
