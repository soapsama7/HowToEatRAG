package com.anfioo.howtocook.app.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 文档索引消息生产者：上传 / 重索引后投递 {taskNo, docId} 到 doc-index-topic。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIndexProducer {

    public static final String TOPIC_DOC_INDEX = "doc-index-topic";

    private final RocketMQTemplate rocketMQTemplate;

    /** 同步发送（本地开发，发送失败快速暴露） */
    public void send(String taskNo, Long docId) {
        rocketMQTemplate.syncSend(TOPIC_DOC_INDEX,
                MessageBuilder.withPayload(new DocumentIndexMessage(taskNo, docId)).build());
        log.info("索引消息已发送: topic={}, taskNo={}, docId={}", TOPIC_DOC_INDEX, taskNo, docId);
    }
}
