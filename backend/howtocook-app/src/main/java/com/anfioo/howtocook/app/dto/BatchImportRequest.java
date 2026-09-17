package com.anfioo.howtocook.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 批量导入请求体（本地开发用，body 传本机目录）。
 */
@Data
public class BatchImportRequest {

    /** 本机目录路径（目录下应含 dishes/ 与/或 tips/ 子目录） */
    @NotBlank(message = "dirPath 不能为空")
    private String dirPath;
}
