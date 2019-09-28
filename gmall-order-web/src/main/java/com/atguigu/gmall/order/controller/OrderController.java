package com.atguigu.gmall.order.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.enums.OrderStatus;
import com.atguigu.gmall.enums.ProcessStatus;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.ManageService;
import com.atguigu.gmall.service.OrderService;
import com.atguigu.gmall.service.UserService;
import com.atguigu.gmall.util.HttpClientUtil;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

@Controller
public class OrderController {

    @Reference
    UserService userService;

    @Reference
    CartService cartService;

    @Reference
    OrderService orderService;

    @Reference
    ManageService manageService;

    /*    @GetMapping("trade")
    public UserInfo trade(String userId){
        UserInfo userInfo = userService.getUserInfoById(userId);
        return userInfo;
    }*/

    @GetMapping("trade")
    @LoginRequire
    public String trade(HttpServletRequest request){
        String userId = (String) request.getAttribute("userId");

        List<UserAddress> userAddressList = userService.getUserAddressList(userId);

        List<CartInfo> checkedCartList = cartService.getCheckedCartList(userId);
        BigDecimal totalAmount = new BigDecimal("0");
        for (CartInfo cartInfo : checkedCartList) {
            BigDecimal cartInfoAmount = cartInfo.getSkuPrice().multiply(new BigDecimal(cartInfo.getSkuNum()));
            totalAmount = totalAmount.add(cartInfoAmount);
        }

        String tradeNo = orderService.genToken(userId);
        request.setAttribute("tradeNo",tradeNo);
        request.setAttribute("userAddressList",userAddressList);
        request.setAttribute("checkedCartList",checkedCartList);
        request.setAttribute("totalAmount",totalAmount);

        return "trade";
    }

    @PostMapping("submitOrder")
    @LoginRequire
    public String submitOrder(OrderInfo orderInfo,HttpServletRequest request){

        String userId = (String) request.getAttribute("userId");

        String tradeNo = request.getParameter("tradeNo");
        Boolean isEnableToken = orderService.verifyToken(userId, tradeNo);
        if (!isEnableToken){
            request.setAttribute("errMsg","页面已失效，请重新结算！");
            return "tradeFail";
        }

        orderInfo.setOrderStatus(OrderStatus.UNPAID);
        orderInfo.setProcessStatus(ProcessStatus.UNPAID);
        orderInfo.setUserId(userId);
        orderInfo.setCreateTime(new Date());
        orderInfo.setExpireTime(DateUtils.addMinutes(new Date(),15));
        orderInfo.sumTotalAmount();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            SkuInfo skuInfo = manageService.getSkuInfo(orderDetail.getSkuId());
            orderDetail.setImgUrl(skuInfo.getSkuDefaultImg());
            orderDetail.setSkuName(skuInfo.getSkuName());
            if (!orderDetail.getOrderPrice().equals(skuInfo.getPrice())){
                request.setAttribute("errMsg","商品及格已发生变动，请重新下单！");
                return "tradeFail";
            }
        }

        ThreadPoolTaskExecutor threadPoolTaskExecutor=new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setCorePoolSize(10);    //线程数
        threadPoolTaskExecutor.setQueueCapacity(100);    //等待队列容量 ，线程数不够任务会等待
        threadPoolTaskExecutor.setMaxPoolSize(50);     // 最大线程数，等待数不够会增加线程数，直到达此上线  超过这个范围会抛异常
        threadPoolTaskExecutor.initialize();

        List<OrderDetail> errList = Collections.synchronizedList(new ArrayList<>());
        Stream<CompletableFuture<String>> completableFutureStream = orderDetailList.stream().map(orderDetail ->
                CompletableFuture.supplyAsync(() -> checkSkuNum(orderDetail),threadPoolTaskExecutor).whenComplete((hasStock, ex) -> {
                    if (hasStock.equals("0")) {
                        errList.add(orderDetail);
                    }
                })
        );
        CompletableFuture[] completableFutures = completableFutureStream.toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(completableFutures).join();

        if (errList.size()>0){
            StringBuffer errStringBuffer = new StringBuffer();
            for (OrderDetail orderDetail : errList) {
                errStringBuffer.append("商品："+orderDetail.getSkuName()+"库存暂时不足！");
            }
            request.setAttribute("errMsg",errStringBuffer.toString());
            return "tradeFail";
        }

        long currentTimeMillis = System.currentTimeMillis();
        String outTredeNo = "atguigu-"+ userId + "-" + currentTimeMillis;
        orderInfo.setOutTradeNo(outTredeNo);

        String orderId = orderService.saveOrder(orderInfo);
        //删除购物车
        return "redirect://payment.gmall.com/index?orderId="+orderId;
    }

    public String checkSkuNum(OrderDetail orderDetail){
        String hasStock = HttpClientUtil.doGet("http://www.gware.com/hasStock?skuId=" + orderDetail.getSkuId() + "&num=" + orderDetail.getSkuNum());
        return hasStock;
    }

    @Test
    public void test1(){
        List<Integer> list = Arrays.asList(1,2,3,4,5,6,7,8,9);
//        List rsList = new ArrayList();
//        List rsList = new CopyOnWriteArrayList();
        List rsList = Collections.synchronizedList(new ArrayList<>());
        Stream<CompletableFuture<Boolean>> completableFutureStream = list.stream().map(num ->
                CompletableFuture.supplyAsync(() -> checkNum(num)).whenComplete((ifPass, ex) -> {
                    if (ifPass) {
                        rsList.add(num);
                    }
                })
        );
        CompletableFuture[] completableFutures = completableFutureStream.toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(completableFutures).join();
        System.out.println(rsList);
    }
    /*if (checkNum(num)) {
        rsList.add(num);
    }
    return num;*/
    private Boolean checkNum(Integer num){
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (num%3==0){
            return true;
        }else {
            return false;
        }
    }

    @PostMapping("orderSplit")
    @ResponseBody
    public String orderSplit(@RequestParam("orderId")String orderId,@RequestParam("wareSkuMap")String wareSkuMap){
        List<Map> orderDetailForWareList = orderService.orderSplit(orderId,wareSkuMap);
        String jsonString = JSON.toJSONString(orderDetailForWareList);
        return jsonString;
    }

    //http://order.gmall.com/list
    @GetMapping("list")
    public String list(){
        return "list";
    }

}
