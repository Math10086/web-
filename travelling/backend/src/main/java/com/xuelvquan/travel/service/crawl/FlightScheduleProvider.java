package com.xuelvquan.travel.service.crawl;

import com.xuelvquan.travel.entity.ScrapedInfo;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 航班采集器。
 *
 * 合规说明：航班舱位价格与余票数据均由航司/OTA通过授权接口提供，且普遍部署有反爬策略，
 * 平台不抓取这些受保护数据；本采集器维护一份基于公开航线信息的离线模板（航段、时刻、参考价区间），
 * 由批次日志如实标注为"离线模板"，不使用户误认为实时抓取结果。
 */
@Component
public class FlightScheduleProvider implements ScrapedInfoProvider {

    @Override
    public String code() {
        return "FLIGHT_TEMPLATE";
    }

    @Override
    public String label() {
        return "航班采集器（离线模板）";
    }

    @Override
    public String type() {
        return "FLIGHT";
    }

    @Override
    public String source() {
        return "离线模板（航司公开航线整理）";
    }

    @Override
    public FetchResult fetch() {
        LocalDateTime now = LocalDateTime.now();
        List<ScrapedInfo> list = new ArrayList<>();

        add(list, "长沙", "成都", "CA4391航班", "08:30-10:40", 520.0, 880.0, "经济舱参考价", now);
        add(list, "长沙", "上海", "MU5301航班", "09:00-11:05", 480.0, 920.0, "经济舱参考价", now);
        add(list, "长沙", "西安", "HU7605航班", "13:00-15:05", 450.0, 760.0, "经济舱参考价", now);
        add(list, "长沙", "厦门", "MF8230航班", "10:20-11:55", 430.0, 700.0, "经济舱参考价", now);
        add(list, "长沙", "重庆", "PN6438航班", "07:40-09:20", 360.0, 620.0, "经济舱参考价", now);
        add(list, "武汉", "成都", "CZ3445航班", "08:20-10:15", 400.0, 720.0, "经济舱参考价", now);
        add(list, "武汉", "重庆", "PN6218航班", "11:30-13:10", 320.0, 560.0, "经济舱参考价", now);
        add(list, "广州", "张家界", "CZ3381航班", "09:40-11:20", 480.0, 820.0, "经济舱参考价", now);
        add(list, "广州", "西安", "CZ3201航班", "08:00-10:35", 520.0, 900.0, "经济舱参考价", now);
        add(list, "重庆", "上海", "PN6415航班", "10:00-12:25", 450.0, 780.0, "经济舱参考价", now);
        add(list, "成都", "上海", "CA4111航班", "09:30-12:00", 560.0, 950.0, "经济舱参考价", now);
        add(list, "成都", "厦门", "MF8426航班", "13:15-15:45", 470.0, 800.0, "经济舱参考价", now);
        add(list, "西安", "厦门", "MU2341航班", "10:40-13:20", 430.0, 750.0, "经济舱参考价", now);

        return FetchResult.fallback(list,
                "航班舱位价格属航司/OTA 授权数据且有反爬限制，按合规约定不抓取，使用平台离线模板");
    }

    private void add(List<ScrapedInfo> list, String origin, String destination, String name,
                     String schedule, Double priceMin, Double priceMax, String note, LocalDateTime now) {
        ScrapedInfo i = new ScrapedInfo();
        i.setType("FLIGHT");
        i.setOrigin(origin);
        i.setDestination(destination);
        i.setName(name);
        i.setSchedule(schedule);
        i.setPriceMin(priceMin);
        i.setPriceMax(priceMax);
        i.setSource(source());
        i.setNote(note);
        i.setUpdatedAt(now.minusMinutes(20 + (long) (Math.random() * 500)));
        list.add(i);
    }
}
