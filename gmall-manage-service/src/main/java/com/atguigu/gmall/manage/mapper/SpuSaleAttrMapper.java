package com.atguigu.gmall.manage.mapper;

import com.atguigu.gmall.bean.SpuSaleAttr;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

public interface SpuSaleAttrMapper extends Mapper<SpuSaleAttr> {
    List<SpuSaleAttr> spuSaleAttrList(String spuId);
    List<SpuSaleAttr> selectSpuSaleAttrListCheckBySku(@Param(value = "skuId") String skuId,@Param(value = "spuId") String spuId);
}
