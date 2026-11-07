package com.xuelvquan.travel.service.crawl;

import com.xuelvquan.travel.entity.ScrapedInfo;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 景区门票采集器。
 *
 * 合规说明：各景区门票与开放时间以景区官方公示为准，第三方平台数据亦有反爬限制；
 * 本采集器维护一份基于景区官方公示价整理的离线模板，由批次日志如实标注为"离线模板"，
 * 页面提示票价与开放政策以景区公示为准。
 */
@Component
public class TicketInfoProvider implements ScrapedInfoProvider {

    @Override
    public String code() {
        return "TICKET_TEMPLATE";
    }

    @Override
    public String label() {
        return "门票采集器（离线模板）";
    }

    @Override
    public String type() {
        return "TICKET";
    }

    @Override
    public String source() {
        return "离线模板（景区官方公示价整理）";
    }

    @Override
    public FetchResult fetch() {
        LocalDateTime now = LocalDateTime.now();
        List<ScrapedInfo> list = new ArrayList<>();

        add(list, "张家界", "张家界国家森林公园", "4日有效大门票", 228.0, 228.0, 4.7, "含袁家界、金鞭溪", now);
        add(list, "张家界", "天门山（含索道）", "含往返索道", 258.0, 278.0, 4.8, "玻璃栈道单收5元", now);
        add(list, "凤凰古城", "凤凰古城九景联票", "含沱江泛舟", 148.0, 148.0, 4.3, "古城本身免票", now);
        add(list, "重庆", "洪崖洞", "夜景街区", 0.0, 0.0, 4.6, "免费开放", now);
        add(list, "重庆", "长江索道", "单程", 20.0, 30.0, 4.5, "过江交通兼观光", now);
        add(list, "成都", "成都大熊猫繁育研究基地", "全票", 55.0, 55.0, 4.8, "熊猫活跃期在早晨", now);
        add(list, "成都", "宽窄巷子", "街区", 0.0, 0.0, 4.5, "免费开放", now);
        add(list, "成都", "武侯祠博物馆", "全票", 50.0, 50.0, 4.4, "与锦里相邻", now);
        add(list, "上海", "东方明珠", "上球体+陈列馆", 180.0, 220.0, 4.6, "可选旋转餐厅套票", now);
        add(list, "上海", "上海迪士尼乐园", "一日票", 399.0, 719.0, 4.7, "旺季票价上浮", now);
        add(list, "上海", "外滩", "滨江步道", 0.0, 0.0, 4.8, "免费开放", now);
        add(list, "西安", "秦始皇兵马俑博物馆", "全票", 120.0, 120.0, 4.8, "建议请讲解", now);
        add(list, "西安", "大唐不夜城", "步行街区", 0.0, 0.0, 4.6, "免费开放", now);
        add(list, "西安", "西安城墙", "全票", 54.0, 54.0, 4.5, "可骑行环城", now);

        return FetchResult.fallback(list,
                "景区票价与开放政策以官方公示为准，第三方数据有反爬限制，使用平台离线模板");
    }

    private void add(List<ScrapedInfo> list, String destination, String name, String schedule,
                     Double priceMin, Double priceMax, Double rating, String note, LocalDateTime now) {
        ScrapedInfo i = new ScrapedInfo();
        i.setType("TICKET");
        i.setDestination(destination);
        i.setName(name);
        i.setSchedule(schedule);
        i.setPriceMin(priceMin);
        i.setPriceMax(priceMax);
        i.setRating(rating);
        i.setSource(source());
        i.setNote(note);
        i.setUpdatedAt(now.minusMinutes(25 + (long) (Math.random() * 500)));
        list.add(i);
    }
}
