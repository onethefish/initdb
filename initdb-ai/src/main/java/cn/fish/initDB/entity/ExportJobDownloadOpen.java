package cn.fish.initDB.entity;

import cn.fish.initDB.enums.ExportFormat;

import java.io.InputStream;

/**
 * 已校验可下载的导出任务：文件流与格式（调用方负责关闭流）。
 */
public record ExportJobDownloadOpen(InputStream inputStream, ExportFormat format) {
}
