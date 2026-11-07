package com.xuelvquan.travel.service;

import com.xuelvquan.travel.common.BusinessException;
import com.xuelvquan.travel.entity.CrawlBatch;
import com.xuelvquan.travel.entity.ScrapedInfo;
import com.xuelvquan.travel.repository.CrawlBatchRepository;
import com.xuelvquan.travel.repository.ScrapedInfoRepository;
import com.xuelvquan.travel.service.crawl.ScrapedInfoProvider;
import com.xuelvquan.travel.service.crawl.TrainScheduleProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 采集编排服务：把若干采集器（ScrapedInfoProvider）组织成一次"采集批次"。
 *
 * 职责：
 * 1. 逐个调用采集器，单个失败不影响其他采集器；
 * 2. 按 (类型, 出发地, 目的地, 名称) 做upsert：已存在则更新采集字段，不存在则新增，
 * 避免每次采集把表撑大；
 * 3. 全过程写入CrawlBatch批次日志，如实记录每个采集器的数据来源、
 * 命中模式（真实抓取/离线模板）、条数与失败原因，页面可直接展示"数据是怎么来的"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlService {

    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final List<ScrapedInfoProvider> providers;
    private final ScrapedInfoRepository scrapedInfoRepository;
    private final CrawlBatchRepository crawlBatchRepository;
    private final CityTransportService cityTransportService;

    /**
     * 执行一次采集；triggerType = SCHEDULED/MANUAL/STARTUP
     * @param triggerType 触发方式
     * @return CrawlBatch 本次采集的批次日志
     */
    @Transactional
    public CrawlBatch crawl(String triggerType) {
        LocalDateTime start = LocalDateTime.now();
        CrawlBatch batch = new CrawlBatch();
        batch.setBatchNo("CB" + start.format(BATCH_FMT));
        batch.setTriggerType(triggerType);
        batch.setStatus("RUNNING");
        batch.setStartedAt(start);
        batch.setProviderTotal(providers.size());
        batch.setRealCount(0);
        batch.setFallbackCount(0);
        batch.setFetched(0);
        batch.setInserted(0);
        batch.setUpdated(0);
        batch.setFailed(0);
        crawlBatchRepository.save(batch);

        List<String> lines = new ArrayList<>();
        int realCount = 0;
        int fallbackCount = 0;
        int fetched = 0;
        int inserted = 0;
        int updated = 0;
        int failed = 0;

        for (ScrapedInfoProvider provider : providers) {
            long t0 = System.currentTimeMillis();
            try {
                ScrapedInfoProvider.FetchResult result = provider.fetch();
                int[] stat = upsert(result.items());
                fetched += result.items().size();
                inserted += stat[0];
                updated += stat[1];
                if (result.mode() == ScrapedInfoProvider.FetchResult.Mode.REAL) {
                    realCount++;
                } else {
                    fallbackCount++;
                }
                lines.add(String.format("%s | %s | %s | 抓取 %d / 新增 %d / 更新 %d | %dms | %s",
                        provider.label(), provider.source(),
                        result.mode() == ScrapedInfoProvider.FetchResult.Mode.REAL ? "真实抓取" : "离线模板",
                        result.items().size(), stat[0], stat[1],
                        System.currentTimeMillis() - t0, result.note()));
            } catch (Exception e) {
                failed++;
                lines.add(String.format("%s | %s | 失败 | 0 / 0 / 0 | %dms | %s",
                        provider.label(), provider.source(),
                        System.currentTimeMillis() - t0, brief(e)));
                log.warn("[学旅圈] 采集器 {} 执行失败：{}", provider.code(), e.getMessage());
            }
        }

        LocalDateTime end = LocalDateTime.now();
        batch.setFinishedAt(end);
        batch.setDurationMs(java.time.Duration.between(start, end).toMillis());
        batch.setRealCount(realCount);
        batch.setFallbackCount(fallbackCount);
        batch.setFetched(fetched);
        batch.setInserted(inserted);
        batch.setUpdated(updated);
        batch.setFailed(failed);
        batch.setDetail(String.join("\n", lines));
        batch.setStatus(failed == 0 ? (fallbackCount == 0 ? "SUCCESS" : "PARTIAL")
                : (failed == providers.size() ? "FAILED" : "PARTIAL"));
        batch.setMessage(String.format("共 %d 个采集器：真实抓取 %d 个、离线模板 %d 个、失败 %d 个；"
                        +"抓取 %d 条，新增 %d 条，更新 %d 条，耗时 %dms",
                providers.size(), realCount, fallbackCount, failed, fetched, inserted, updated,
                batch.getDurationMs()));
        crawlBatchRepository.save(batch);
        log.info("[学旅圈] 采集批次 {} 完成：{}", batch.getBatchNo(), batch.getMessage());
        return batch;
    }

    /** 最近批次日志（默认最多20条） */
    @Transactional(readOnly = true)
    public List<CrawlBatch> recentBatches() {
        return crawlBatchRepository.findTop20ByOrderByStartedAtDesc();
    }

    /**
     * 定向采集：按用户指定的「出发地 → 目的地」抓一次公开信息并入库（需求3.19手动触发）。
     *
     * 与 #crawl(String)的区别：后者是定时全量采集，城市池固定；
     * 本方法面向「公开信息浏览」里用户手输的任意城市，直接调12306实时车次接口
     * （车站码表覆盖全国，因此不局限于内置城市池），抓到的车次按 (type, origin, destination, name)
     * upsert入库后即可被列表接口查到。抓不到时也在批次日志里写明原因，不抛异常。
     * @param origin 出发地城市
     * @param destination 目的地城市
     * @param triggerType 触发方式，为空时按MANUAL记录
     * @return CrawlBatch 本次定向采集的批次日志
     */
    @Transactional
    public CrawlBatch crawlRoute(String origin, String destination, String triggerType) {
        String from = origin == null ? "" : origin.trim();
        String to = destination == null ? "" : destination.trim();
        if (from.isEmpty() || to.isEmpty()) {
            throw new BusinessException("请先选择出发地和目的地");
        }
        if (from.equals(to)) {
            throw new BusinessException("出发地和目的地不能相同");
        }

        LocalDateTime start = LocalDateTime.now();
        CrawlBatch batch = new CrawlBatch();
        batch.setBatchNo("CB" + start.format(BATCH_FMT));
        batch.setTriggerType(triggerType == null || triggerType.isBlank() ? "MANUAL" : triggerType);
        batch.setStatus("RUNNING");
        batch.setStartedAt(start);
        batch.setProviderTotal(1);
        crawlBatchRepository.save(batch);

        List<Map<String, Object>> trains;
        String line;
        try {
            trains = cityTransportService.queryTrainDetails(from, to, new ArrayList<>());
            line = String.format("定向采集·12306车次 | 12306 | %s | 抓取 %d / 新增0 / 更新0 | %dms | %s → %s",
                    trains.isEmpty() ? "离线模板" : "真实抓取",
                    trains.size(), System.currentTimeMillis() - start.atZone(java.time.ZoneId.systemDefault())
                            .toInstant().toEpochMilli(),
                    from, to);
        } catch (Exception e) {
            log.warn("[学旅圈] 定向采集 {} → {} 失败：{}", from, to, e.getMessage());
            trains = List.of();
            line = String.format("定向采集·12306车次 | 12306 | 失败 | 0 / 0 / 0 | 0ms | %s", brief(e));
        }

        List<ScrapedInfo> items = new ArrayList<>();
        boolean templateUsed = false;
        if (trains.isEmpty()) {
            // 实时源拿不到（12306对多数线路返回加密载荷）→ 回落该线路的离线模板，
            // 保证「任意城市」至少能查到参考车次；来源如实标注，不冒充实时数据。
            LocalDateTime tplNow = LocalDateTime.now();
            for (ScrapedInfo t : TrainScheduleProvider.templateFor(from, to, tplNow)) {
                t.setNote("外部实时源不可用，本条为离线模板参考值；" + (t.getNote() == null ? "" : t.getNote()));
                items.add(t);
            }
            templateUsed = !items.isEmpty();
        }
        for (Map<String, Object> t : trains) {
            ScrapedInfo info = new ScrapedInfo();
            info.setType("TRAIN");
            info.setOrigin(from);
            info.setDestination(to);
            info.setName(String.valueOf(t.get("code")) + " 次");
            Object depart = t.get("depart");
            Object arrive = t.get("arrive");
            info.setSchedule(depart + "-" + arrive);
            info.setSource(CityTransportService.SRC_12306);
            info.setNote("历时" + t.get("duration") + "（12306公开时刻，仅供浏览参考）");
            info.setUpdatedAt(LocalDateTime.now());
            items.add(info);
        }
        int[] stat = upsert(items);

        LocalDateTime end = LocalDateTime.now();
        batch.setFinishedAt(end);
        batch.setDurationMs(java.time.Duration.between(start, end).toMillis());
        batch.setRealCount(trains.isEmpty() ? 0 : 1);
        batch.setFallbackCount(templateUsed ? 1 : 0);
        batch.setFetched(items.size());
        batch.setInserted(stat[0]);
        batch.setUpdated(stat[1]);
        batch.setFailed(0);
        batch.setDetail(line.replace("新增0 / 更新0", "新增" + stat[0] + " / 更新" + stat[1]));
        if (!trains.isEmpty()) {
            batch.setStatus("SUCCESS");
            batch.setMessage(String.format("定向采集「%s → %s」：抓取 %d 条车次，新增 %d 条、更新 %d 条，耗时 %dms",
                    from, to, items.size(), stat[0], stat[1], batch.getDurationMs()));
        } else if (templateUsed) {
            batch.setStatus("PARTIAL");
            batch.setMessage(String.format(
                    "定向采集「%s → %s」：12306未返回明文车次（接口启用加密载荷），已回落离线模板，写入 %d 条参考车次（新增 %d / 更新 %d）",
                    from, to, items.size(), stat[0], stat[1]));
        } else {
            batch.setStatus("PARTIAL");
            batch.setMessage(String.format(
                    "定向采集「%s → %s」：12306未返回明文车次（接口启用加密载荷），且该线路暂无离线模板，暂无新数据",
                    from, to));
        }
        crawlBatchRepository.save(batch);
        log.info("[学旅圈] 定向采集批次 {} 完成：{}", batch.getBatchNo(), batch.getMessage());
        return batch;
    }

    /**
     * 按业务主键upsert：只覆盖采集得到的字段，保留平台其他来源（如人工录入）的既有值。
     * @param items 本次采集到的条目
     * @return int[] [新增条数, 更新条数]
     */
    private int[] upsert(List<ScrapedInfo> items) {
        int inserted = 0;
        int updated = 0;
        for (ScrapedInfo in : items) {
            Optional<ScrapedInfo> exist = in.getOrigin() == null
                    ? scrapedInfoRepository.findByTypeAndOriginIsNullAndDestinationAndName(
                            in.getType(), in.getDestination(), in.getName())
                    : scrapedInfoRepository.findByTypeAndOriginAndDestinationAndName(
                            in.getType(), in.getOrigin(), in.getDestination(), in.getName());
            if (exist.isPresent()) {
                ScrapedInfo db = exist.get();
                db.setSchedule(in.getSchedule() != null ? in.getSchedule() : db.getSchedule());
                db.setPriceMin(in.getPriceMin() != null ? in.getPriceMin() : db.getPriceMin());
                db.setPriceMax(in.getPriceMax() != null ? in.getPriceMax() : db.getPriceMax());
                db.setRating(in.getRating() != null ? in.getRating() : db.getRating());
                db.setSource(in.getSource() != null ? in.getSource() : db.getSource());
                db.setNote(in.getNote() != null ? in.getNote() : db.getNote());
                db.setUpdatedAt(in.getUpdatedAt() != null ? in.getUpdatedAt() : LocalDateTime.now());
                scrapedInfoRepository.save(db);
                updated++;
            } else {
                if (in.getUpdatedAt() == null) {
                    in.setUpdatedAt(LocalDateTime.now());
                }
                scrapedInfoRepository.save(in);
                inserted++;
            }
        }
        return new int[]{inserted, updated};
    }

    private String brief(Exception e) {
        String m = e.getMessage();
        if (m == null) {
            return e.getClass().getSimpleName();
        }
        return m.length() > 60 ? m.substring(0, 60) : m;
    }
}
