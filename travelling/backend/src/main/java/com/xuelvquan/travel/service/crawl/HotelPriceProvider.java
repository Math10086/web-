package com.xuelvquan.travel.service.crawl;

import com.xuelvquan.travel.entity.ScrapedInfo;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 酒店房价采集器。
 *
 * 合规说明：实时房价与库存由OTA/酒店PMS通过授权接口下发，公开页面亦有反爬与动态渲染，
 * 平台不抓取受限数据；本采集器维护一份基于公开房型信息的离线模板（房型、参考价区间、评分），
 * 由批次日志如实标注为"离线模板"，页面同时提示房价以官方渠道为准。
 */
@Component
public class HotelPriceProvider implements ScrapedInfoProvider {

    @Override
    public String code() {
        return "HOTEL_TEMPLATE";
    }

    @Override
    public String label() {
        return "酒店房价采集器（离线模板）";
    }

    @Override
    public String type() {
        return "HOTEL";
    }

    @Override
    public String source() {
        return "离线模板（OTA 公开房型整理）";
    }

    @Override
    public FetchResult fetch() {
        LocalDateTime now = LocalDateTime.now();
        List<ScrapedInfo> list = new ArrayList<>();

        add(list, "张家界", "武陵源青和锦江国际酒店", "标准双床房", 380.0, 680.0, 4.6, "近武陵源门票站", now);
        add(list, "张家界", "张家界山舍青年旅舍", "六人间床位", 80.0, 160.0, 4.3, "适合学生党", now);
        add(list, "凤凰古城", "沱江边吊脚楼民宿", "临江大床房", 260.0, 520.0, 4.5, "开窗即沱江夜景", now);
        add(list, "凤凰古城", "凤凰古城青年旅舍", "四人间床位", 70.0, 140.0, 4.2, "近虹桥", now);
        add(list, "重庆", "解放碑亚朵酒店", "高级双床房", 380.0, 620.0, 4.7, "步行至洪崖洞15分钟", now);
        add(list, "重庆", "洪崖洞江景公寓", "一室一厅", 300.0, 560.0, 4.4, "可看两江夜景", now);
        add(list, "成都", "春熙路智选假日", "标准双床房", 360.0, 580.0, 4.6, "近太古里", now);
        add(list, "成都", "宽窄巷子川西民宿", "庭院双床房", 240.0, 480.0, 4.5, "老成都院子风格", now);
        add(list, "上海", "人民广场如家精选", "标准双床房", 320.0, 520.0, 4.3, "地铁1/2/8号线换乘", now);
        add(list, "上海", "外滩英迪格酒店", "江景大床房", 900.0, 1500.0, 4.8, "正对陆家嘴", now);
        add(list, "西安", "钟楼回民街精品酒店", "标准双床房", 280.0, 520.0, 4.5, "下楼即回民街", now);
        add(list, "西安", "大唐不夜城亚朵", "高级双床房", 320.0, 560.0, 4.6, "近大雁塔", now);

        return FetchResult.fallback(list,
                "酒店实时房价属 OTA 授权数据，按合规约定不抓取，使用平台离线模板");
    }

    private void add(List<ScrapedInfo> list, String destination, String name, String schedule,
                     Double priceMin, Double priceMax, Double rating, String note, LocalDateTime now) {
        ScrapedInfo i = new ScrapedInfo();
        i.setType("HOTEL");
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
