package com.atguigu.gmall.cart.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.bean.SkuInfo;
import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.service.ManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import redis.clients.jedis.Jedis;

import java.util.*;

@Service
@CrossOrigin
public class CartServiceImpl implements CartService {

    @Autowired
    RedisUtil redisUtil;

    @Autowired
    CartInfoMapper cartInfoMapper;

    @Reference
    ManageService manageService;

    @Override
    public CartInfo addToCart(String userId, String skuId, Integer num) {

        CartInfo cartInfo = new CartInfo();
        cartInfo.setSkuId(skuId);
        cartInfo.setUserId(userId);
        cartInfo = cartInfoMapper.selectOne(cartInfo);
        SkuInfo skuInfo = manageService.getSkuInfo(skuId);

        if (cartInfo==null){
            cartInfo = new CartInfo();
            cartInfo.setSkuId(skuId);
            cartInfo.setUserId(userId);
            cartInfo.setSkuNum(num);
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfoMapper.insertSelective(cartInfo);
        }else {
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setCartPrice(skuInfo.getPrice());
            cartInfo.setSkuNum(cartInfo.getSkuNum()+num);
            cartInfoMapper.updateByPrimaryKeySelective(cartInfo);
        }

        loadCartCache(userId);
        return cartInfo;
    }

    @Override
    public List<CartInfo> cartList(String userId) {
        Jedis jedis = redisUtil.getJedis();
        String cartKey="cart:"+userId+":info";
        List<String> cartJsonList = jedis.hvals(cartKey);
        if (cartJsonList!=null && cartJsonList.size()>0){
            List<CartInfo> cartList = new ArrayList<>();
            for (String cartJson : cartJsonList) {
                CartInfo cartInfo = JSON.parseObject(cartJson, CartInfo.class);
                cartList.add(cartInfo);
            }
            cartList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    return o2.getId().compareTo(o1.getId());
                }
            });
            return cartList;
        }else {
            return loadCartCache(userId);
        }
    }

    public List<CartInfo> loadCartCache(String userId) {
        List<CartInfo> cartInfoList = cartInfoMapper.selectCartListWithSkuPrice(userId);
        if (cartInfoList!=null && cartInfoList.size()>0){
            Map<String, String> cartMap = new HashMap<>();
            for (CartInfo cartInfo : cartInfoList) {
                cartMap.put(cartInfo.getSkuId(),JSON.toJSONString(cartInfo));
            }
            Jedis jedis = redisUtil.getJedis();
            String cartKey = "cart:"+userId+":info";
            jedis.del(cartKey);
            jedis.hmset(cartKey,cartMap);
            jedis.expire(cartKey,60*60*24);
            jedis.close();
        }
        return cartInfoList;
    }

    @Override
    public List<CartInfo> mergeCartList(String userId, String userTmpId) {
        cartInfoMapper.mergeCartList(userId,userTmpId);

        CartInfo cartInfo = new CartInfo();
        cartInfo.setUserId(userTmpId);
        cartInfoMapper.delete(cartInfo);

        Jedis jedis = redisUtil.getJedis();
        jedis.del("cart:"+userTmpId+":info");
        jedis.close();

        List<CartInfo> cartInfoList = loadCartCache(userId);
        return cartInfoList;
    }

    @Override
    public void checkCart(String skuId, String isChecked, String userId) {
        loadCartCacheIfNotExists(userId);
        String cartKey = "cart:" + userId + ":info";
        Jedis jedis = redisUtil.getJedis();
        String cartInfoJson = jedis.hget(cartKey, skuId);
        CartInfo cartInfo = JSON.parseObject(cartInfoJson, CartInfo.class);
        cartInfo.setIsChecked(isChecked);
        String cartInfoJsonNew = JSON.toJSONString(cartInfo);
        jedis.hset(cartKey,skuId,cartInfoJsonNew);

        String checkedCartKey = "cart:"+userId + ":checked";
        if ("1".equals(isChecked)){
            jedis.hset(checkedCartKey,skuId,cartInfoJsonNew);
            jedis.expire(checkedCartKey,60*60);
        }else {
            jedis.hdel(checkedCartKey,skuId);
        }
        jedis.close();
    }

    @Override
    public List<CartInfo> getCheckedCartList(String userId) {
        String checkedCartKey = "cart:"+userId + ":checked";
        Jedis jedis = redisUtil.getJedis();
        List<String> checkedCartList = jedis.hvals(checkedCartKey);
        List<CartInfo> cartInfoList = new ArrayList<>();
        for (String cartInfoJson : checkedCartList) {
            CartInfo cartInfo = JSON.parseObject(cartInfoJson, CartInfo.class);
            cartInfoList.add(cartInfo);
        }
        jedis.close();
        return cartInfoList;
    }

    private void loadCartCacheIfNotExists(String userId) {
        String cartkey="cart:"+userId+":info";
        Jedis jedis = redisUtil.getJedis();
        Long ttl = jedis.ttl(cartkey);
        int i = ttl.intValue();
        jedis.expire(cartkey,i+60);
        Boolean exists = jedis.exists(cartkey);
        jedis.close();
        if (!exists){
            loadCartCache(userId);
        }
    }
}
