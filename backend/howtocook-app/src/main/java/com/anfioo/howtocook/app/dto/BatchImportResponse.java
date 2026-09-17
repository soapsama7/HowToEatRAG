package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 批量导入汇总。
 */
@Getter
@Builder
public class BatchImportResponse {

    /** 发现并处理的文件总数 */
    private final int total;

    /** 上传成功（已入索引队列）数 */
    private final int succeeded;

    /** 失败数 */
    private final int failed;

    /** 失败明细（文件名 + 原因，最多 50 条） */
    private final List<String> failures;
}
