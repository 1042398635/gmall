package com.atguigu.gmall.order.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.OrderDetail;
import com.atguigu.gmall.bean.OrderInfo;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.enums.ProcessStatus;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.service.OrderService;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Service
//@org.springframework.stereotype.Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    OrderInfoMapper orderInfoMapper;

    @Autowired
    OrderDetailMapper orderDetailMapper;

    @Autowired
    RedisUtil redisUtil;

    @Override
    @Transactional
    public String saveOrder(OrderInfo orderInfo) {
        orderInfoMapper.insertSelective(orderInfo);
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insertSelective(orderDetail);
        }
        return orderInfo.getId();
    }

    @Override
    public String genToken(String userId) {
        String token = UUID.randomUUID().toString();
        String tokenKey = "user:" + userId + ":trade_code";
        Jedis jedis = redisUtil.getJedis();
        jedis.setex(tokenKey,60*10,token);
        jedis.close();
        return token;
    }

    @Override
    public Boolean verifyToken(String userId, String token) {
        String tokenKey = "user:" + userId + ":trade_code";
        Jedis jedis = redisUtil.getJedis();
        String tokenExists = jedis.get(tokenKey);
        jedis.watch(tokenKey);
        Transaction transaction = jedis.multi();
        if (tokenExists!=null&&tokenExists.equals(token)){
            transaction.del(tokenKey);
        }
        List<Object> exec = transaction.exec();
        if (exec!=null&&exec.size()>0&&1L==(Long) exec.get(0)){
            return true;
        }
        return false;
    }

    @Override
    public OrderInfo getOrderInfo(String orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectByPrimaryKey(orderId);

        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setOrderId(orderId);
        List<OrderDetail> orderDetailList = orderDetailMapper.select(orderDetail);
        orderInfo.setOrderDetailList(orderDetailList);
        return orderInfo;
    }

    @Override
    public void updateStatus(String orderId, ProcessStatus paid,OrderInfo... orderInfos) {
        OrderInfo orderInfo = new OrderInfo();
        if (orderInfos!=null&&orderInfos.length>0){
            orderInfo = orderInfos[0];
        }
        orderInfo.setProcessStatus(paid);
        orderInfo.setOrderStatus(paid.getOrderStatus());
        orderInfo.setId(orderId);
        orderInfoMapper.updateByPrimaryKeySelective(orderInfo);
    }

    public List<Integer> checkExpiredCoupon(){
        return Arrays.asList(1,2,3,4,5,6,7);
    }

    @Async
    public void handleExpiredCoupon(Integer id){
        try {
            System.out.println("购物券："+ id +"发送用户");
            Thread.sleep(1000);

            System.out.println("购物券："+ id +"删除");
            Thread.sleep(1000);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Map> orderSplit(String orderId, String wareSkuMapJson) {
        OrderInfo orderInfoParent = getOrderInfo(orderId);
        List<Map> mapList = JSON.parseArray(wareSkuMapJson, Map.class);
        List<Map> wareParamMapList = new ArrayList<>();
        for (Map wareSkuMap : mapList) {
            OrderInfo orderInfoSub = new OrderInfo();
            try {
                BeanUtils.copyProperties(orderInfoSub,orderInfoParent);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            List<String> skuIdList = (List<String>) wareSkuMap.get("skuIds");
            List<OrderDetail> orderDetailList = orderInfoParent.getOrderDetailList();
            ArrayList<OrderDetail> orderDetailSubList = new ArrayList<>();
            for (String skuId : skuIdList) {
                for (OrderDetail orderDetail : orderDetailList) {
                    if (skuId.equals(orderDetail.getSkuId())){
                        OrderDetail orderDetailSub = new OrderDetail();
                        try {
                            BeanUtils.copyProperties(orderDetailSub,orderDetail);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                        orderDetailSub.setId(null);
                        orderDetailSub.setOrderId(null);
                        orderDetailSubList.add(orderDetailSub);
                    }
                }
            }
            orderInfoSub.setOrderDetailList(orderDetailSubList);
            orderInfoSub.setId(null);
            orderInfoSub.sumTotalAmount();
            orderInfoSub.setParentOrderId(orderId);
            saveOrder(orderInfoSub);

            Map paramMap = new HashMap();
            paramMap.put("orderId",orderId);
            paramMap.put("consignee",orderInfoParent.getConsignee());
            paramMap.put("consigneeTel",orderInfoParent.getConsigneeTel());
            paramMap.put("orderComment",orderInfoParent.getOrderComment());
            paramMap.put("orderBody",orderInfoParent.genSubject());
            paramMap.put("deliveryAddress",orderInfoParent.getDeliveryAddress());
            paramMap.put("paymentWay","2");
            List<Map> details = new ArrayList<>();
            for (OrderDetail orderDetail : orderInfoParent.getOrderDetailList()) {
                HashMap<String, String> orderDetailMap = new HashMap<>();
                orderDetailMap.put("skuId",orderDetail.getSkuId());
                orderDetailMap.put("skuName",orderDetail.getSkuName());
                orderDetailMap.put("skuNum",orderDetail.getSkuNum().toString());
                details.add(orderDetailMap);
            }
            paramMap.put("details",details);
            paramMap.put("wareId",wareSkuMap.get("wareId"));
            wareParamMapList.add(paramMap);
        }
        updateStatus(orderId,ProcessStatus.SPLIT);
        return wareParamMapList;
    }

}
