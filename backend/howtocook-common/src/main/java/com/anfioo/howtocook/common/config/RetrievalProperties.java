package com.anfioo.howtocook.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 混合检索融合参数（howtocook.retrieval，方案设计 §4.2 敲定）。
 * <p>融合公式固定：每路 Min-Max 归一化后 {@code final = vectorWeight * vector' + keywordWeight * keyword'}，
 * 仅命中单路的另一路按 0 计（开发文档 §5.3，不得更改）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "howtocook.retrieval")
public class RetrievalProperties {

    /** 向量路融合权重 */
    private double vectorWeight = 0.7;

    /** 关键词路融合权重 */
    private double keywordWeight = 0.3;

    /** 融合后返回条数（Top K） */
    private int topK = 5;

    /** 两路各自召回条数 */
    private int recallSize = 20;
}
