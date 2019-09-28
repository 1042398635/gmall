package com.atguigu.gmall.service;

import com.atguigu.gmall.bean.CartInfo;

import java.util.List;

public interface CartService {
    CartInfo addToCart(String userId, String skuId, Integer num);

    List<CartInfo> cartList(String userId);

    List<CartInfo> loadCartCache(String userId);

    List<CartInfo> mergeCartList(String userId, String userTmpId);

    void checkCart(String skuId, String isChecked, String userId);

    List<CartInfo> getCheckedCartList(String userId);
}
