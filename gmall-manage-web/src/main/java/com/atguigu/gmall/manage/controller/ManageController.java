package com.atguigu.gmall.manage.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.atguigu.gmall.bean.*;
import com.atguigu.gmall.service.ListService;
import com.atguigu.gmall.service.ManageService;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

@RestController
@CrossOrigin
public class ManageController {

    @Reference
    ManageService manageService;

    @Reference
    ListService listService;

    @PostMapping("getCatalog1")
    public List<BaseCatalog1> getCatalog1(){
        return manageService.getCatalog1();
    }

    //POST http://localhost:8082/getCatalog2?catalog1Id=2 404
    @PostMapping("getCatalog2")
    public List<BaseCatalog2> getCatalog2(String catalog1Id){
        return manageService.getCatalog2(catalog1Id);
    }

    //POST http://localhost:8082/getCatalog3?catalog2Id=13 404
    @PostMapping("getCatalog3")
    public List<BaseCatalog3> getCatalog3(String catalog2Id){
        return manageService.getCatalog3(catalog2Id);
    }

    //GET http://localhost:8082/attrInfoList?catalog3Id=61 404
    @GetMapping("attrInfoList")
    public List<BaseAttrInfo> attrInfoList(String catalog3Id){
        //System.out.println("aaaa");
        return manageService.getAttrList(catalog3Id);
    }

    //OPTIONS http://localhost:8082/saveAttrInfo 403
    @PostMapping("saveAttrInfo")
    public void saveAttrInfo(@RequestBody BaseAttrInfo baseAttrInfo){
        manageService.saveAttrInfo(baseAttrInfo);
    }

    //POST http://localhost:8082/getAttrValueList?attrId=96 404
    @PostMapping("getAttrValueList")
    public List<BaseAttrValue> getAttrValueList(String attrId){
        BaseAttrInfo attrInfo = manageService.getAttrInfo(attrId);
        return attrInfo.getAttrValueList();
    }

    //saveSpuInfo
    @PostMapping("saveSpuInfo")
    public String saveSpuInfo(@RequestBody SpuInfo spuInfo){
        manageService.saveSpuInfo(spuInfo);
        return "success";
    }

    //POST http://localhost:8082/baseSaleAttrList 404
    @PostMapping("baseSaleAttrList")
    public List<BaseSaleAttr> baseSaleAttrList(){
        return manageService.baseSaleAttrList();
    }

    //GET http://localhost:8082/spuList?catalog3Id=61
    @GetMapping("spuList")
    public List<SpuInfo> spuList(String catalog3Id){
        return manageService.spuList(catalog3Id);
    }

    //http://manage.gmall.com/spuImageList?spuId=59
    @GetMapping("spuImageList")
    public List<SpuImage> spuImageList(String spuId){
        return manageService.spuImageList(spuId);
    }

    //http://manage.gmall.com/spuSaleAttrList?spuId=59
    @GetMapping("spuSaleAttrList")
    public List<SpuSaleAttr> spuSaleAttrList(String spuId){
        return manageService.spuSaleAttrList(spuId);
    }

    @PostMapping("onSale")
    public String onSale(@RequestParam("skuId") String skuId){
        SkuInfo skuInfo = manageService.getSkuInfo(skuId);
        SkuLsInfo skuLsInfo = new SkuLsInfo();
        try {
            BeanUtils.copyProperties(skuLsInfo,skuInfo);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        listService.saveSkuLsInfo(skuLsInfo);
        return "success";
    }
}
