package com.atguigu.gmall.payment.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.payment.util.HttpClient;
import com.atguigu.gmall.payment.util.StreamUtil;
import com.atguigu.gmall.service.OrderService;
import com.github.wxpay.sdk.WXPayUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
public class WxPaymentController {

    @Value("${appid}")
    private String appid;

    @Value("${partner}")
    private String partner;

    @Value("${partnerkey}")
    private String partnerkey;

    @Reference
    OrderService orderService;

    @PostMapping("/wx/submit")
    public Map wxSubmit(String orderId) throws Exception {
        OrderInfo orderInfo = orderService.getOrderInfo(orderId);
        Map paramMap = new HashMap<>();
        paramMap.put("appid",appid);
        paramMap.put("mch_id",partner);
        paramMap.put("nonce_str", WXPayUtil.generateNonceStr());
        //paramMap.put("sign",);
        paramMap.put("body",orderInfo.genSubject());
        paramMap.put("out_trade_no",orderInfo.getOutTradeNo());
        paramMap.put("total_fee",orderInfo.getTotalAmount().multiply(new BigDecimal(100)).toBigInteger().toString());
        paramMap.put("spbill_create_ip","127.0.0.1");
        paramMap.put("notify_url","http://zwh.free.idcfengye.com/wxpay/callback/notify");
        paramMap.put("trade_type","NATIVE");

        String xmlParam = WXPayUtil.generateSignedXml(paramMap, partnerkey);
        HttpClient httpClient = new HttpClient("https://api.mch.weixin.qq.com/pay/unifiedorder");
        httpClient.setXmlParam(xmlParam);
        httpClient.post();
        String content = httpClient.getContent();
        Map<String, String> resultMap = WXPayUtil.xmlToMap(content);
        return resultMap;
/*        if (resultMap.get("code_url")!=null){
            String code_url = resultMap.get("code_url");
            System.out.println(code_url);
            return resultMap;
        }else {
            System.out.println(resultMap.get("return_code"));
            System.out.println(resultMap.get("return_msg"));
            return null;
        }*/
    }

    @PostMapping("/wxpay/callback/notify")
    public String notify(HttpServletRequest request, HttpServletResponse response) throws Exception {
        ServletInputStream inputStream = request.getInputStream();
        String xmlString = StreamUtil.inputStream2String(inputStream, "utf-8");
        if (WXPayUtil.isSignatureValid(xmlString,partnerkey)) {
            Map<String, String> paramMap = WXPayUtil.xmlToMap(xmlString);
            String result_code = paramMap.get("result_code");
            if (result_code!=null&&result_code.equals("SUCCESS")){
                HashMap<String, String> returnMap  = new HashMap<>();
                returnMap.put("return_code","SUCCESS");
                returnMap.put("return_msg","OK");
                String returnXml = WXPayUtil.mapToXml(returnMap);
                response.setContentType("text/xml");
                System.out.println("交易编号："+paramMap.get("out_trade_no")+"支付成功！");
                return returnXml;
            }else {
                System.out.println(paramMap.get("return_code")+"---"+paramMap.get("return_msg"));
            }
        }
        return null;
    }
}
