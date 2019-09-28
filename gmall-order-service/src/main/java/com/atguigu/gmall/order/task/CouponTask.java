package com.atguigu.gmall.order.task;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.service.OrderService;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.util.List;

//@Component
//@EnableScheduling
public class CouponTask {

    //@Scheduled(cron = "0 30 2 21 * ?")
    //@Scheduled(cron = "0 0 * * * ?")
    //@Scheduled(cron = "* 0/30 * * * ?")
    //@Scheduled(cron = "5 * * * * ?")

    @Reference
    OrderService orderService;

    @Scheduled(cron = "0/1 * * * * ?")
    public void work() throws InterruptedException{
        System.out.println("thread 1====== "+ Thread.currentThread());
        List<Integer> integers = orderService.checkExpiredCoupon();
        for (Integer couponId : integers) {
            orderService.handleExpiredCoupon(couponId);
        }
        //Thread.sleep(10000);
    }

/*    @Scheduled(cron = "0/1 * * * * ?")
    public void work2() throws InterruptedException{
        System.out.println("thread 2====== "+ Thread.currentThread());
    }*/

    @Bean
    public TaskScheduler taskScheduler(){
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(5);
        return taskScheduler;
    }
}




