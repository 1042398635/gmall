package com.atguigu.gmall.list.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.atguigu.gmall.bean.SkuLsInfo;
import com.atguigu.gmall.bean.SkuLsParams;
import com.atguigu.gmall.bean.SkuLsResult;
import com.atguigu.gmall.config.RedisUtil;
import com.atguigu.gmall.service.ListService;
import io.searchbox.client.JestClient;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.Update;
import io.searchbox.core.search.aggregation.TermsAggregation;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ListServiceImpl implements ListService{

    @Autowired
    RedisUtil redisUtil;

    @Autowired
    JestClient jestClient;

    public static final String index_name_gmall="gmall";

    public static final String type_name_gmall="SkuInfo";

    @Override
    public void saveSkuLsInfo(SkuLsInfo skuLsInfo){
        Index index = new Index.Builder(skuLsInfo).index(index_name_gmall).type(type_name_gmall).id(skuLsInfo.getId()).build();
        try {
            jestClient.execute(index);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public SkuLsResult search(SkuLsParams skuLsParams) {

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        if (skuLsParams.getKeyword()!=null) {
            MatchQueryBuilder skuName = new MatchQueryBuilder("skuName", skuLsParams.getKeyword());
            boolQueryBuilder.must(skuName);
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field("skuName");
            highlightBuilder.preTags("<span style='color:red'>");
            highlightBuilder.postTags("</span>");
            searchSourceBuilder.highlight(highlightBuilder);
        }
        if (skuLsParams.getCatalog3Id()!=null) {
            boolQueryBuilder.filter(new TermQueryBuilder("catalog3Id", skuLsParams.getCatalog3Id()));
        }
        if (skuLsParams.getValueId()!=null&&skuLsParams.getValueId().length>0){
            String[] paramsValueId = skuLsParams.getValueId();
            for (int i = 0; i < paramsValueId.length; i++) {
                String valueId = paramsValueId[i];
                boolQueryBuilder.filter(new TermQueryBuilder("skuAttrValueList.valueId",valueId));
            }
        }
        //boolQueryBuilder.filter(new RangeQueryBuilder("price").gte("3200"));
        searchSourceBuilder.query(boolQueryBuilder);
        int form = (skuLsParams.getPageNo() - 1) * skuLsParams.getPageSize();
        searchSourceBuilder.from(form);
        searchSourceBuilder.size(skuLsParams.getPageSize());

        searchSourceBuilder.sort("hotScore", SortOrder.DESC);

        TermsBuilder groupby_valueid = AggregationBuilders.terms("groupby_valueid").field("skuAttrValueList.valueId").size(100);
        searchSourceBuilder.aggregation(groupby_valueid);

        System.out.println(searchSourceBuilder.toString());
        Search.Builder builder = new Search.Builder(searchSourceBuilder.toString());
        Search search = builder.addIndex(index_name_gmall).addType(type_name_gmall).build();
        SkuLsResult skuLsResult = new SkuLsResult();
        try {
            SearchResult searchResult = jestClient.execute(search);

            List<SkuLsInfo> skuLsInfoList = new ArrayList<>();
            List<SearchResult.Hit<SkuLsInfo, Void>> hits = searchResult.getHits(SkuLsInfo.class);
            for (SearchResult.Hit<SkuLsInfo, Void> hit : hits) {
                SkuLsInfo skuLsInfo = hit.source;
                String skuNameHL = hit.highlight.get("skuName").get(0);
                skuLsInfo.setSkuName(skuNameHL);
                skuLsInfoList.add(skuLsInfo);
            }
            skuLsResult.setSkuLsInfoList(skuLsInfoList);
            Long total = searchResult.getTotal();
            skuLsResult.setTotal(total);
            long totalPage = (total + skuLsParams.getPageSize() - 1) / skuLsParams.getPageSize();
            skuLsResult.setTotalPages(totalPage);

            List<String> attrValueIdList = new ArrayList<>();
            List<TermsAggregation.Entry> buckets = searchResult.getAggregations().getTermsAggregation("groupby_valueid").getBuckets();
            for (TermsAggregation.Entry bucket : buckets) {
                attrValueIdList.add(bucket.getKey());
            }
            skuLsResult.setAttrValueIdList(attrValueIdList);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return skuLsResult;
    }

    @Override
    public void incrHotScore(String skuId) {
        Jedis jedis = redisUtil.getJedis();
        String hotScoreKey = "sku:"+skuId+":hotscore";
        Long hotScore = jedis.incr(hotScoreKey);
        if (hotScore%10==0){
            updateHotScoreEs(skuId,hotScore);
        }
    }

    public void updateHotScoreEs(String skuId,Long hotScore){
        String updateString="{\n" +
                "  \"doc\": {\n" +
                "    \"hotScore\":"+hotScore+"\n" +
                "  }\n" +
                "}";
        Update update = new Update.Builder(updateString).index("gmall").type("SkuInfo").id(skuId).build();
        try {
            jestClient.execute(update);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
