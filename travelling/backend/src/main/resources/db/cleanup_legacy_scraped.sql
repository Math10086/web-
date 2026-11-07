SET NAMES utf8;

-- 清理业务语义变更前的旧演示数据：
-- 1) 酒店 / 门票 的 origin 曾误填为出发地"长沙"（该类信息本无出发地），业务键与新版不一致，会与新采集数据并存
-- 2) 旧记录来源标注为"12306 公开时刻 / 航司公开航班 / OTA 公开房价 / 景区官方公示"，与新版"离线模板（…）"口径不统一
DELETE FROM scraped_infos WHERE type IN ('HOTEL', 'TICKET') AND origin IS NOT NULL;
DELETE FROM scraped_infos
WHERE source IN ('12306 公开时刻', '航司公开航班', 'OTA 公开房价', '景区官方公示');

SELECT type, COUNT(*) AS cnt FROM scraped_infos GROUP BY type ORDER BY type;
