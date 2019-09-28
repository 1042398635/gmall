package com.atguigu.gmall.cart.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.CartInfo;
import com.atguigu.gmall.config.LoginRequire;
import com.atguigu.gmall.service.CartService;
import com.atguigu.gmall.util.CookieUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;

@Controller
public class CartController {

    @Reference
    CartService cartService;

    //http://cart.gmall.com/addToCart
    @PostMapping("addToCart")
    @LoginRequire(autoRedirect = false)
    public String addToCart(@RequestParam("skuId")String skuId, @RequestParam("num")Integer num, HttpServletRequest request, HttpServletResponse response){
        String userId =(String) request.getAttribute("userId");
        if (userId==null){
            userId = CookieUtil.getCookieValue(request, "user_tmp_id", false);
            if (userId==null){
                userId = UUID.randomUUID().toString();
                CookieUtil.setCookie(request,response,"user_tmp_id",userId,60*60*24*7,false);
            }
        }
        CartInfo cartInfo = cartService.addToCart(userId, skuId, num);
        request.setAttribute("cartInfo",cartInfo);
        request.setAttribute("num",num);
        return "success";
    }

    @GetMapping("cartList")
    @LoginRequire(autoRedirect = false)
    public String cartList(HttpServletRequest request){
        String userId = (String) request.getAttribute("userId");
        String userTmpId = CookieUtil.getCookieValue(request, "user_tmp_id", false);
        List<CartInfo> cartList = null;
        if (userId!=null){
            if (userTmpId!=null){
                cartList = cartService.cartList(userTmpId);
                if (cartList!=null&&cartList.size()>0) {
                    cartList = cartService.mergeCartList(userId, userTmpId);
                }else {
                    cartList = cartService.cartList(userId);
                }
            }else {
                cartList = cartService.cartList(userId);
            }
        }else {
            if (userTmpId!=null) {
                cartList = cartService.cartList(userTmpId);
            }
        }

        request.setAttribute("cartList",cartList);
        return "cartList";
    }

    //POST http://cart.gmall.com/checkCart 404
    @PostMapping("checkCart")
    @ResponseBody
    @LoginRequire(autoRedirect = false)
    public void checkCart(HttpServletRequest request,HttpServletResponse response){
        String skuId = request.getParameter("skuId");
        String isChecked = request.getParameter("isChecked");
        String userId = (String) request.getAttribute("userId");
        if (userId==null){
            userId = CookieUtil.getCookieValue(request, "user_tmp_id", false);
        }
        cartService.checkCart(skuId,isChecked,userId);
    }

}
