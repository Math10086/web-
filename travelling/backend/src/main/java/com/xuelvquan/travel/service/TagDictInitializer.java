package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.TagDict;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.TagDictRepository;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 标签字典初始化与演示档案补齐：每次启动等待执行，
 * 灌入标签字典、补齐老用户档案并补充同校演示账号。
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class TagDictInitializer implements CommandLineRunner {

    private final TagDictRepository tagDictRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedDictionary();
        backfillProfiles();
        seedDemoStudents();
    }

    // —— 1) 标签字典v1 ——

    private void seedDictionary() {
        List<TagDict> list = new ArrayList<>();

        // —— scope = USER：8个用户维度 ——
        String U = "USER";
        // 1出行节奏（单选 · 核心）
        dict(list, U, "pace", "出行节奏", false, true,
                "ENERGETIC", "精力满满",
                "BALANCED", "节奏适中",
                "RELAXED", "纯休闲放松");
        // 2兴趣主题（多选 · 核心）
        dict(list, U, "interest", "兴趣主题", true, true,
                "HIKING", "徒步登山",
                "PHOTO", "摄影",
                "FOOD", "美食",
                "HISTORY", "历史人文",
                "NATURE", "自然风光",
                "CITYWALK", "城市漫步",
                "CAMPING", "露营",
                "CYCLING", "骑行");
        // 3预算档（单选 · 核心）
        dict(list, U, "budget", "预算档", false, true,
                "ECONOMY", "经济 <300",
                "MODERATE", "适中300–800",
                "COMFORT", "舒适800–1500",
                "PREMIUM", "品质 >1500");
        // 4目的地偏好（多选 · 核心）
        dict(list, U, "destination", "目的地偏好", true, true,
                "MOUNTAIN", "山野",
                "SEASIDE", "海滨",
                "ANCIENT_TOWN", "古镇",
                "CITY", "都市",
                "BORDER", "边境",
                "SNOW", "雪原");
        // 5同行规模（单选）
        dict(list, U, "groupSize", "同行规模", false, false,
                "G2", "2人",
                "G3_4", "3–4人",
                "G5_6", "5–6人",
                "G7", "6人以上");
        // 6住宿偏好（单选）
        dict(list, U, "lodging", "住宿偏好", false, false,
                "HOSTEL", "青旅床位",
                "HOTEL", "快捷酒店",
                "HOMESTAY", "民宿",
                "CAMP", "露营",
                "STAR", "星级");
        // 7交通偏好（单选）
        dict(list, U, "transport", "交通偏好", false, false,
                "TRAIN", "高铁火车",
                "BUS", "长途大巴",
                "CAR", "自驾租车",
                "CARPOOL", "拼车顺风车",
                "BIKE", "骑行徒步");
        // 8社交意愿（单选）
        dict(list, U, "social", "社交意愿", false, false,
                "SOLO", "安静独行",
                "SMALL", "小圈子",
                "OPEN", "乐于认识新朋友");

        // —— scope = POST：攻略供给标签 ——
        String P = "POST";
        dict(list, P, "costLevel", "人均预算档", false, false,
                "ECONOMY", "经济 <300",
                "MODERATE", "适中300–800",
                "COMFORT", "舒适800–1500",
                "PREMIUM", "品质 >1500");
        dict(list, P, "dayRange", "建议天数", false, false,
                "D1", "1天",
                "D2_3", "2–3天",
                "D4_7", "4–7天",
                "D7P", "7天以上");
        dict(list, P, "avoid", "避坑主题", true, false,
                "SCAM", "防宰",
                "TICKET", "门票坑",
                "TRAFFIC", "交通陷阱",
                "LODGING", "住宿雷区");
        dict(list, P, "suitable", "适合人群", true, false,
                "STUDENT", "学生党",
                "COUPLE", "情侣",
                "FAMILY", "亲子",
                "SOLO", "独行",
                "TEAM", "团队");

        // —— scope = GUIDE：向导供给标签 ——
        String G = "GUIDE";
        dict(list, G, "theme", "擅长主题", true, false,
                "PHOTO_FOLLOW", "摄影跟拍",
                "FOOD_TOUR", "美食探店",
                "HIKING_GUIDE", "徒步向导",
                "CULTURE", "人文讲解",
                "PARENT_CHILD", "亲子");
        dict(list, G, "language", "服务语言", true, false,
                "ZH", "中文",
                "EN", "英语",
                "DIALECT", "方言");
        dict(list, G, "serviceRadius", "服务半径", false, false,
                "CAMPUS", "本校周边",
                "CITY", "同城",
                "CROSS_CITY", "跨城");
        dict(list, G, "supplyTier", "供给层级", false, false,
                "STUDENT_GUIDE", "学生导游",
                "CERTIFIED_GUIDE", "平台认证导游",
                "AGENCY", "旅行社");

        // 幂等更新：以代码定义为事实来源，修正库中的multi/core/sortNo/active
        int inserted = 0;
        int updated = 0;
        for (TagDict want : list) {
            TagDict exist = tagDictRepository
                    .findByScopeAndDimensionAndCode(want.getScope(), want.getDimension(), want.getCode())
                    .orElse(null);
            if (exist == null) {
                tagDictRepository.save(want);
                inserted++;
                continue;
            }
            boolean changed = !java.util.Objects.equals(exist.getMulti(), want.getMulti())
                    || !java.util.Objects.equals(exist.getCore(), want.getCore())
                    || !java.util.Objects.equals(exist.getSortNo(), want.getSortNo())
                    || !java.util.Objects.equals(exist.getActive(), want.getActive())
                    || !java.util.Objects.equals(exist.getLabel(), want.getLabel());
            if (changed) {
                exist.setMulti(want.getMulti());
                exist.setCore(want.getCore());
                exist.setSortNo(want.getSortNo());
                exist.setActive(want.getActive());
                exist.setLabel(want.getLabel());
                tagDictRepository.save(exist);
                updated++;
            }
        }
        log.info("[B0] 标签字典 v1同步完成：共 {} 条（新增 {} / 修正 {}），用户8维 + 攻略4维 + 向导4维",
                list.size(), inserted, updated);
    }

    /**
     * 一个维度一次性写入：codes与labels交替给出，从1开始排序
     * @param box 待写入的标签字典集合
     * @param scope 标签所属范围，如USER/POST/GUIDE
     * @param dimension 维度编码，如pace/interest
     * @param dimLabel 维度中文名
     * @param multi 是否多选
     * @param core 是否核心维度
     * @param codeAndLabel 编码与中文名交替排列的参数
     */
    private void dict(List<TagDict> box, String scope, String dimension, String dimLabel,
                      boolean multi, boolean core, String... codeAndLabel) {
        for (int i = 0; i < codeAndLabel.length; i += 2) {
            TagDict t = new TagDict();
            t.setScope(scope);
            t.setDimension(dimension);
            t.setCode(codeAndLabel[i]);
            t.setLabel(codeAndLabel[i + 1]);
            t.setMulti(multi);
            t.setCore(core);
            t.setSortNo(i / 2 + 1);
            t.setActive(true);
            box.add(t);
        }
    }

    // —— 2) 老用户档案补齐 ——

    /** 用户名 -> { 学院, 专业, 届别, 档案标签 } */
    private static final Map<String, String[]> ARCHIVE = Map.of(
            "xiaolu", new String[]{"新闻传播学院", "新闻学", "2023",
                    "pace:BALANCED,interest:CITYWALK,interest:FOOD,interest:PHOTO,budget:MODERATE,destination:CITY,destination:ANCIENT_TOWN,groupSize:G3_4,lodging:HOMESTAY,transport:BIKE,social:OPEN"},
            "dashan", new String[]{"土木工程学院", "土木工程", "2022",
                    "pace:ENERGETIC,interest:HIKING,interest:CAMPING,interest:NATURE,interest:PHOTO,budget:COMFORT,destination:MOUNTAIN,groupSize:G5_6,lodging:CAMP,transport:TRAIN,social:SMALL"},
            "aya", new String[]{"旅游学院", "旅游管理", "2022",
                    "pace:RELAXED,interest:FOOD,interest:PHOTO,interest:CITYWALK,budget:MODERATE,destination:ANCIENT_TOWN,destination:CITY,groupSize:G3_4,lodging:HOMESTAY,transport:BUS,social:OPEN"},
            "dao", new String[]{"体育学院", "社会体育指导与管理", "2019",
                    "pace:ENERGETIC,interest:HIKING,interest:NATURE,interest:CAMPING,budget:MODERATE,destination:MOUNTAIN,groupSize:G3_4,lodging:CAMP,transport:TRAIN,social:SMALL"},
            "shang", new String[]{"商学院", "市场营销", "2019",
                    "pace:BALANCED,interest:CAMPING,interest:HIKING,budget:COMFORT,destination:MOUNTAIN,groupSize:G5_6,lodging:CAMP,transport:CAR,social:OPEN"}
    );

    /**
     * 老种子账号的示例学号：补齐学号以满足各校正则校验，避免信息卡学号位为空。
     */
    private static final Map<String, String> DEMO_STUDENT_NO = Map.of(
            "xiaolu", "20230116",   // CSU 8位
            "dashan", "20220823",   // HNU 8位
            "aya", "2022030517"     // HUNNU 10位
    );

    private void backfillProfiles() {
        int fixed = 0;
        for (Map.Entry<String, String[]> e : ARCHIVE.entrySet()) {
            User u = userRepository.findByUsername(e.getKey()).orElse(null);
            if (u == null) {
                continue;
            }
            String[] v = e.getValue();
            boolean dirty = false;
            if (isBlank(u.getCollege())) { u.setCollege(v[0]); dirty = true; }
            if (isBlank(u.getMajor())) { u.setMajor(v[1]); dirty = true; }
            if (isBlank(u.getGrade())) { u.setGrade(v[2]); dirty = true; }
            if (isBlank(u.getProfileTags())) { u.setProfileTags(v[3]); dirty = true; }
            String demoNo = DEMO_STUDENT_NO.get(e.getKey());
            if (demoNo != null && isBlank(u.getStudentNo())) {
                u.setStudentNo(demoNo);
                u.setVerifyMethod("STUDENT_NO");
                u.setVerifiedAt(LocalDateTime.now().minusDays(20));
                dirty = true;
            }
            if (dirty) {
                userRepository.save(u);
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("[B0/B6] 已为 {} 个既有账号补齐学院/专业/届别、示例学号与标签档案", fixed);
        }
    }

    // —— 3) 撮合演示账号（同校学生） ——

    private static final String AV1 = "/images/av_xiaolu.jpg";
    private static final String AV2 = "/images/av_dashan.jpg";
    private static final String AV3 = "/images/av_aya.jpg";

    private void seedDemoStudents() {
        if (userRepository.findByUsername("linzi").isPresent()) {
            return;
        }
        String pwd = passwordEncoder.encode("123456");
        List<User> demo = new ArrayList<>();
        // 同校同届、标签高度重合的演示账号
        demo.add(student("linzi", pwd, "林子", "CSU", "计算机学院", "软件工程", "2023", AV1, 108,
                "中南大学软件工程，周末爱拍照和扫街，找搭子一起Citywalk",
                "摄影,美食,Citywalk,长沙",
                "pace:BALANCED,interest:PHOTO,interest:FOOD,interest:CITYWALK,budget:MODERATE,destination:CITY,destination:ANCIENT_TOWN,groupSize:G3_4,lodging:HOMESTAY,transport:BIKE,social:OPEN"));
        // 同校、偏户外的演示账号
        demo.add(student("ake", pwd, "阿珂", "CSU", "外国语学院", "英语", "2022", AV2, 104,
                "中南外院，爱爬山露营，野线也敢走，欢迎组队",
                "徒步,露营,登山",
                "pace:ENERGETIC,interest:HIKING,interest:NATURE,interest:CAMPING,budget:ECONOMY,destination:MOUNTAIN,groupSize:G3_4,lodging:CAMP,transport:TRAIN,social:SMALL"));
        // 同校同届、人文向的演示账号
        demo.add(student("mumu", pwd, "木木", "CSU", "建筑与艺术学院", "城乡规划", "2022", AV3, 112,
                "建艺院城规专业，爱逛古镇老街，喜欢拍照记录建筑",
                "城市漫步,摄影,历史人文",
                "pace:BALANCED,interest:CITYWALK,interest:PHOTO,interest:HISTORY,budget:COMFORT,destination:ANCIENT_TOWN,destination:CITY,groupSize:G3_4,lodging:HOMESTAY,transport:TRAIN,social:OPEN"));
        // 同校同届、休闲美食向的演示账号
        demo.add(student("tangtang", pwd, "糖糖", "CSU", "商学院", "会计学", "2023", AV1, 100,
                "中南会计，慢节奏旅行党，主打一个吃好睡好",
                "美食,摄影,海滨",
                "pace:RELAXED,interest:FOOD,interest:PHOTO,budget:COMFORT,destination:SEASIDE,destination:CITY,groupSize:G2,lodging:HOTEL,transport:TRAIN,social:OPEN"));
        // 同校同届、骑行自然向的演示账号
        demo.add(student("chengzi", pwd, "橙子", "CSU", "计算机学院", "人工智能", "2023", AV2, 96,
                "中南 AI 方向，周末骑行+拍照，偶尔发呆看风景",
                "骑行,摄影,自然风光",
                "pace:BALANCED,interest:PHOTO,interest:CYCLING,interest:NATURE,budget:MODERATE,destination:MOUNTAIN,destination:CITY,groupSize:G3_4,lodging:HOTEL,transport:BIKE,social:SMALL"));
        userRepository.saveAll(demo);
        log.info("[B0] 已补充 {} 个同校学生演示账号（密码123456），供 B5/B7撮合演示", demo.size());
    }

    private User student(String username, String pwd, String nickname, String school,
                         String college, String major, String grade, String avatar, int credit,
                         String bio, String interests, String profileTags) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(pwd);
        u.setNickname(nickname);
        u.setRole("USER");
        u.setSchool(school);
        u.setCollege(college);
        u.setMajor(major);
        u.setGrade(grade);
        u.setAvatar(avatar);
        u.setVerified(true);
        u.setVerifyMethod("STUDENT_NO");
        u.setVerifiedAt(LocalDateTime.now().minusDays(10));
        u.setBanned(false);
        u.setCreditScore(credit);
        u.setBloggerLevel(0);
        u.setBloggerScore(0);
        u.setBio(bio);
        u.setInterests(interests);
        u.setProfileTags(profileTags);
        u.setCreatedAt(LocalDateTime.now().minusDays(12));
        return u;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
