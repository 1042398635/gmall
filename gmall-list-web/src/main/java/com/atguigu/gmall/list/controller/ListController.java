package com.atguigu.gmall.list.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.bean.BaseAttrInfo;
import com.atguigu.gmall.bean.BaseAttrValue;
import com.atguigu.gmall.bean.SkuLsParams;
import com.atguigu.gmall.bean.SkuLsResult;
import com.atguigu.gmall.service.ListService;
import com.atguigu.gmall.service.ManageService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Controller
public class ListController {

    @Reference
    ListService listService;

    @Reference
    ManageService manageService;

    @GetMapping("list.html")
    public String list(SkuLsParams skuLsParams, Model model){
        SkuLsResult skuLsResult = listService.search(skuLsParams);
        model.addAttribute("skuLsResult",skuLsResult);
        //JSON.toJSONString(skuLsResult);
        List<String> attrValueIdList = skuLsResult.getAttrValueIdList();
        List<BaseAttrInfo> attrList = manageService.getAttrListByValuesId(attrValueIdList);
        model.addAttribute("attrList",attrList);

        String urlParam = makeParamUrl(skuLsParams);

        List<BaseAttrValue> selectedValueList = new ArrayList<>();

        for (Iterator<BaseAttrInfo> iterator = attrList.iterator(); iterator.hasNext(); ) {
            BaseAttrInfo baseAttrInfo = iterator.next();
            List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
            for (BaseAttrValue baseAttrValue : attrValueList) {
                if (skuLsParams.getValueId()!=null && skuLsParams.getValueId().length>0) {
                    for (int i = 0; i < skuLsParams.getValueId().length; i++) {
                        String selectedValueId = skuLsParams.getValueId()[i];
                        if (baseAttrValue.getId().equals(selectedValueId)) {
                            iterator.remove();
                            String selectedParamUrl = makeParamUrl(skuLsParams, selectedValueId);
                            baseAttrValue.setUrlParam(selectedParamUrl);
                            selectedValueList.add(baseAttrValue);
                        }
                    }
                }
            }
        }

        model.addAttribute("urlParam",urlParam);
        model.addAttribute("selectedValueList",selectedValueList);
        model.addAttribute("keyword",skuLsParams.getKeyword());

        model.addAttribute("pageNo",skuLsParams.getPageNo());
        model.addAttribute("totalPages",skuLsResult.getTotalPages());
        return "list";
    }

    public String makeParamUrl(SkuLsParams skuLsParams,String... excludeValueId){
        String paramUrl="";
        if (skuLsParams.getKeyword()!=null){
            paramUrl += "keyword="+skuLsParams.getKeyword();
        }else if (skuLsParams.getCatalog3Id()!=null){
            paramUrl += "catalog3Id="+skuLsParams.getCatalog3Id();
        }
        if (skuLsParams.getValueId()!=null && skuLsParams.getValueId().length>0){
            for (int i = 0; i < skuLsParams.getValueId().length; i++) {
                String valueId = skuLsParams.getValueId()[i];
                if (excludeValueId!=null && excludeValueId.length>0){
                    String exValueId = excludeValueId[0];
                    if (valueId.equals(exValueId)) {
                        continue;
                    }
                }
                if (paramUrl.length()>0){
                    paramUrl += "&";
                }
                paramUrl += "valueId="+valueId;
            }
        }
        return paramUrl;
    }
}
