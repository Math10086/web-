package com.xuelvquan.travel.service.crawl;

import com.xuelvquan.travel.entity.ScrapedInfo;

import java.util.List;

/**
 * 采集器（Provider）接口：一个实现对应一类公开信息的数据源。
 *
 * 设计约定：
 * 1. 每个采集器只负责"取数"，不负责入库（入库与批次日志由CrawlService统一处理）；
 * 2. 真实数据源不可用时允许内部回落离线模板，但必须如实返回FetchResult#mode() = FALLBACK，
 * 由批次日志记录，避免把模板数据伪装成实时抓取；
 * 3. 采集器之间相互独立，单个采集器失败不影响其他采集器（CrawlService逐个try-catch）。
 */
public interface ScrapedInfoProvider {

    /** 采集器编码，如TRAIN_12306 */
    String code();

    /** 采集器名称（展示用），如12306车次采集器 */
    String label();

    /** 产生的信息类型：TRAIN/FLIGHT/HOTEL/TICKET/REFERENCE */
    String type();

    /** 数据来源说明，最终写入ScrapedInfo.source */
    String source();

    /**
     * 执行采集。
     *
     * @throws Exception 真实数据源与回落模板都不可用时抛出，由编排层计入失败
     */
    FetchResult fetch() throws Exception;

    /** 采集结果：数据+命中模式+备注 */
    record FetchResult(List<ScrapedInfo> items, Mode mode, String note) {

        public enum Mode {
            /** 真实抓取外部公开数据源成功 */
            REAL,
            /** 外部数据源不可达/结构变更，已回落离线模板 */
            FALLBACK
        }

        public static FetchResult real(List<ScrapedInfo> items, String note) {
            return new FetchResult(items, Mode.REAL, note);
        }

        public static FetchResult fallback(List<ScrapedInfo> items, String note) {
            return new FetchResult(items, Mode.FALLBACK, note);
        }
    }
}
