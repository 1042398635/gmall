package com.atguigu.gmall.passport.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.UserInfo;
import com.atguigu.gmall.service.UserService;
import com.atguigu.gmall.util.JwtUtil;
import org.junit.Test;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
public class PassportController {

    @Reference
    UserService userService;

    @GetMapping("index")
    public String index(@RequestParam("originUrl") String originUrl,Model model){
        model.addAttribute("originUrl",originUrl);
        return "index";
    }

    String jwtKey = "atguigu";

    @PostMapping("login")
    @ResponseBody
    public String login(UserInfo userInfo, HttpServletRequest request){
        UserInfo userInfoExist = userService.login(userInfo);
        if (userInfoExist!=null){
            Map<String,Object> map = new HashMap<>();
            map.put("userId",userInfoExist.getId());
            map.put("nickName",userInfoExist.getNickName());
            String ipAddr = request.getHeader("X-forwarded-for");
            String token = JwtUtil.encode(jwtKey, map, ipAddr);
            return token;
        }
        return "fail";
    }

    @Test
    public void testJwt(){
        Map<String,Object> map = new HashMap<>();
        map.put("userId","123");
        map.put("nickName","zhang3");
        String token = JwtUtil.encode("atguigu", map, "192.168.66.99");
        System.out.println(token);
        Map<String, Object> atguigu = JwtUtil.decode(token, "atguigu", "192.168.66.99");
        System.out.println(atguigu);
    }

    @GetMapping("verify")
    @ResponseBody
    public String verify(@RequestParam("token") String token,@RequestParam("currentIP")String currentIp){
        Map<String, Object> userMap = JwtUtil.decode(token, jwtKey, currentIp);
        if (userMap!=null){
            String userId =(String) userMap.get("userId");
            Boolean isLogin = userService.verify(userId);
            if (isLogin){
                return "success";
            }
        }
        return "fail";
    }

}
