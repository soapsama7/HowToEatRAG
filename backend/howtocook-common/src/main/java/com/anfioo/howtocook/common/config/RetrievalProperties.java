package com.anfioo.howtocook.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 混合检索融合参数（howtocook.retrieval，方案设计 §4.2 敲定）。
 * <p>融合策略可插拔（优化 2.7）：默认 {@code rrf}（RRF 排名融合），
 * 设为 {@code weighted} 切回旧版 Min-Max 归一化加权融合（A/B 对照）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "howtocook.retrieval")
public class RetrievalProperties {

    /** 融合策略：rrf（默认）/ weighted */
    private String fusionStrategy = "rrf";

    /** 向量路融合权重（仅 weighted 策略使用） */
    private double vectorWeight = 0.7;

    /** 关键词路融合权重（仅 weighted 策略使用） */
    private double keywordWeight = 0.3;

    /** 融合后返回条数（Top K） */
    private int topK = 5;

    /** 两路各自召回条数 */
    private int recallSize = 20;
}
