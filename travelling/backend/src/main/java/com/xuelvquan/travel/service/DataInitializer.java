package com.xuelvquan.travel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xuelvquan.travel.entity.*;
import com.xuelvquan.travel.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动种子数据：user表为空时执行，密码全部BCrypt加密。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    // ================= 本地图片（存放在resources/static/images，随jar打包） =================
    private static final String AV_ADMIN = "/images/av_admin.jpg";
    private static final String AV_XIAOLU = "/images/av_xiaolu.jpg";
    private static final String AV_DASHAN = "/images/av_dashan.jpg";
    private static final String AV_AYA = "/images/av_aya.jpg";
    private static final String AV_SHANG = "/images/av_shang.jpg";
    private static final String AV_DAO = "/images/av_dao.jpg";
    private static final String P_CHANGSHA_CW = "/images/p_changsha_cw.jpg";
    private static final String P_ZJJ_TREK = "/images/p_zjj_trek.jpg";
    private static final String P_CQ_NIGHT = "/images/p_cq_night.jpg";
    private static final String P_CD_FOOD = "/images/p_cd_food.jpg";
    private static final String P_XIAN_WALL = "/images/p_xian_wall.jpg";
    private static final String P_XM_SEA = "/images/p_xm_sea.jpg";
    private static final String P_CS_POZI = "/images/p_cs_pozi.jpg";
    private static final String P_CQ_TRAIN = "/images/p_cq_train.jpg";
    private static final String P_ZJJ_QUEUE = "/images/p_zjj_queue.jpg";
    private static final String P_CD_TEA = "/images/p_cd_tea.jpg";
    private static final String P_YELU_WILD = "/images/p_yelu_wild.jpg";
    private static final String P_DT_BUYE = "/images/p_dt_buye.jpg";
    private static final String P_TMS = "/images/p_tms.jpg";
    private static final String P_PANDA = "/images/p_panda.jpg";
    private static final String S_AWT = "/images/s_awt.jpg";
    private static final String S_JZZ = "/images/s_jzz.jpg";
    private static final String S_MUSEUM = "/images/s_museum.jpg";
    private static final String S_WHY = "/images/s_why.jpg";
    private static final String S_HGD = "/images/s_hgd.jpg";
    private static final String S_YL_WILD = "/images/s_yl_wild.jpg";
    private static final String S_WINDOW = "/images/s_window.jpg";
    private static final String S_WUYI = "/images/s_wuyi.jpg";
    private static final String S_TAIPING = "/images/s_taiping.jpg";
    private static final String S_BOTANY = "/images/s_botany.jpg";
    private static final String S_FOREST = "/images/s_forest.jpg";
    private static final String S_TIANMEN = "/images/s_tianmen.jpg";
    private static final String S_GLASS = "/images/s_glass.jpg";
    private static final String S_LAODAO = "/images/s_laodao.jpg";
    private static final String S_HYD = "/images/s_hyd.jpg";
    private static final String S_CABLE = "/images/s_cable.jpg";
    private static final String S_LIZIBA = "/images/s_liziba.jpg";
    private static final String S_SHANCHENG = "/images/s_shancheng.jpg";
    private static final String S_KZ = "/images/s_kz.jpg";
    private static final String S_PANDA_BASE = "/images/s_panda_base.jpg";
    private static final String S_DT = "/images/s_dt.jpg";
    private static final String S_CITYWALL = "/images/s_citywall.jpg";
    private static final String PRD_BAG = "/images/prd_bag.jpg";
    private static final String PRD_TENT = "/images/prd_tent.jpg";
    private static final String PRD_MAT = "/images/prd_mat.jpg";
    private static final String PRD_CAMERA = "/images/prd_camera.jpg";
    private static final String PRD_SHOES = "/images/prd_shoes.jpg";
    private static final String PRD_JACKET = "/images/prd_jacket.jpg";
    private static final String PRD_POWER = "/images/prd_power.jpg";
    private static final String PRD_CUP = "/images/prd_cup.jpg";
    private static final String FP_AWT = "/images/fp_awt.jpg";
    private static final String FP_JZZ = "/images/fp_jzz.jpg";
    private static final String FP_WUYI = "/images/fp_wuyi.jpg";
    private static final String AD_AUTUMN = "/images/ad_autumn.jpg";
    private static final String AD_GEAR = "/images/ad_gear.jpg";
    private static final String AD_CAMP = "/images/ad_camp.jpg";
    private static final String AD_SHOE = "/images/ad_shoe.jpg";
    private static final String AD_TRAIN = "/images/ad_train.jpg";

    private final UserRepository userRepository;
    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final InteractionRepository interactionRepository;
    private final SpotRepository spotRepository;
    private final RouteRepository routeRepository;
    private final FootprintRepository footprintRepository;
    private final TransportOptionRepository transportOptionRepository;
    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final GuideProfileRepository guideProfileRepository;
    private final GuideServiceRepository guideServiceRepository;
    private final GuideBookingRepository guideBookingRepository;
    private final AdRepository adRepository;
    private final ReportRepository reportRepository;
    private final EmergencyContactRepository emergencyContactRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripPrefRepository tripPrefRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final RoutePlannerService routePlannerService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0) {
            return;
        }
        log.info("[学旅圈] 开始初始化种子数据...");

        String pwd = passwordEncoder.encode("123456");
        String adminPwd = passwordEncoder.encode("admin123");

        // —— 用户 ——
        User admin = mkUser("admin", adminPwd, "平台管理员", "ADMIN", null, AV_ADMIN, false, 100, 0, 0,
                "平台运营与审核", null);
        User xiaolu = mkUser("xiaolu", pwd, "小麓", "BLOGGER", "CSU", AV_XIAOLU, true, 100, 3, 286,
                "中南大学新闻系，爱逛爱吃爱拍，长沙Citywalk十级学者",
                "Citywalk,摄影,美食,探店,长沙");
        User dashan = mkUser("dashan", pwd, "大山队长", "USER", "HNU", AV_DASHAN, true, 100, 0, 0,
                "湖南大学土木系，户外徒步爱好者，周末必进山",
                "徒步,露营,登山,野路");
        User aya = mkUser("aya", pwd, "阿雅", "BLOGGER", "HUNNU", AV_AYA, true, 100, 2, 175,
                "湖南师大美食博主，相信美食是认识一座城最快的方式",
                "美食,摄影,露营,成都,重庆");
        User shang = mkUser("shang", pwd, "山野老板", "MERCHANT", null, AV_SHANG, true, 100, 0, 0,
                "山野户外店，装备齐全，学生党友好价", "露营,装备,户外");
        User dao = mkUser("dao", pwd, "老道", "GUIDE", null, AV_DAO, true, 105, 0, 0,
                "土生土长张家界人，持证户外向导，带队8年零事故",
                "徒步,登山,野路,向导");
        userRepository.saveAll(List.of(admin, xiaolu, dashan, aya, shang, dao));

        // ================= 圈层 =================
        Circle cFood = mkCircle("美食圈", "FOOD", "一起发现街头巷尾的宝藏小店，拼饭拼奶茶。", "☕", aya);
        Circle cTrek = mkCircle("徒步圈", "OUTDOOR", "山野在召唤，周末徒步、登山、野线穿越。", "⛰", dashan);
        Circle cPhoto = mkCircle("摄影圈", "PHOTO", "用镜头记录旅途，互勉约拍与机位分享。", "✈", aya);
        Circle cCamp = mkCircle("露营圈", "CAMP", "帐篷、星空与篝火，露营装备与营地交流。", "⛺", shang);
        Circle cCw = mkCircle("Citywalk圈", "CITYWALK", "用脚步丈量城市，老街、巷子与随机惊喜。", "⛺", xiaolu);
        Circle cSpeed = mkCircle("特种兵速通圈", "SPEED", "周末48小时极限打卡，主打一个高效。", "⚡", dashan);
        circleRepository.saveAll(List.of(cFood, cTrek, cPhoto, cCamp, cCw, cSpeed));
        for (Circle c : List.of(cFood, cTrek, cPhoto, cCamp, cCw, cSpeed)) {
            join(c.getId(), c.getCreatorId());
        }
        // 多加入几个成员让圈层更热闹
        join(cFood.getId(), xiaolu.getId());
        join(cFood.getId(), dashan.getId());
        join(cTrek.getId(), xiaolu.getId());
        join(cTrek.getId(), dao.getId());
        join(cPhoto.getId(), xiaolu.getId());
        join(cPhoto.getId(), dashan.getId());
        join(cCw.getId(), aya.getId());
        join(cSpeed.getId(), xiaolu.getId());
        join(cSpeed.getId(), aya.getId());
        join(cCamp.getId(), dashan.getId());

        // ================= 帖子 =================
        List<Post> posts = new ArrayList<>();
        posts.add(mkPost(xiaolu, "GUIDE", "长沙两日Citywalk完全攻略｜本地人路线不踩雷",
                "Day1岳麓山爱晚亭→湖南博物院→五一广场→太平街；Day2橘子洲→超级文和友→坡子街火宫殿。全程地铁可达，博物院记得提前7天预约，晚上的橘子洲灯光秀别错过。附避坑：火车站附近拉客的一日游不要报！",
                "长沙", "长沙市区", "LEISURE", cCw.getId(), P_CHANGSHA_CW, false, "APPROVED", 1, 865, 96, 45, 12));
        posts.add(mkPost(xiaolu, "GUIDE", "张家界三天两夜徒步路线｜森林公园+天门山+老道湾",
                "三天路线：D1金鞭溪-袁家界阿凡达山；D2天子山看日出后下山；D3天门山玻璃栈道。时间充裕可加老道湾野线，但必须请向导、结伴而行，切勿独行。门票和索道都要提前在公众号抢。",
                "张家界", "武陵源-天门山", "WILD", cTrek.getId(), P_ZJJ_TREK, false, "APPROVED", 2, 1204, 158, 88, 21));
        posts.add(mkPost(dashan, "MOMENT", "重庆山城夜游随手拍，洪崖洞亮灯瞬间封神",
                "晚上7点半洪崖洞准时亮灯，千厮门大桥上的机位最绝。拍完坐长江索道过江，重庆的夜景真的怎么拍都出片。",
                "重庆", "洪崖洞", null, cPhoto.getId(), P_CQ_NIGHT, false, "APPROVED", 2, 432, 52, 12, 1));
        posts.add(mkPost(aya, "LOCAL", "成都本地人带你吃宽窄巷子｜这几家店游客真不知道",
                "宽窄巷子主街偏贵，要吃请转进旁边的奎星楼街：冒椒火辣、二娘鸡爪、严太婆锅盔。三大炮5块钱尝个热闹就行，真正的老成都味道在巷子里的茶馆和苍蝇馆子。",
                "成都", "宽窄巷子-奎星楼", "FOOD", cFood.getId(), P_CD_FOOD, true, "APPROVED", 3, 756, 88, 60, 6));
        posts.add(mkPost(xiaolu, "GUIDE", "西安特种兵一日速通｜城墙骑行到大唐不夜城",
                "早上8点城墙南门骑行一圈（约100分钟），中午永兴坊吃biangbiang面，下午陕西历史博物馆，晚上大唐不夜城看不倒翁小姐姐。一天3万步，穿最舒服的鞋！",
                "西安", "西安城墙-大唐不夜城", "SPEED", cSpeed.getId(), P_XIAN_WALL, false, "APPROVED", 3, 980, 120, 33, 9));
        posts.add(mkPost(dashan, "MOMENT", "鼓浪屿看海的下午，时间都慢下来了",
                "避开人群往岛的西侧走，鼓声洞旁边的礁石滩几乎没人，海风、老别墅和猫，适合带一本书坐一下午。",
                "厦门", "鼓浪屿", null, cPhoto.getId(), P_XM_SEA, false, "APPROVED", 4, 355, 41, 20, 0));
        posts.add(mkPost(aya, "LOCAL", "长沙土著私藏的坡子街正确打开方式",
                "火宫殿晚上比白天热闹，臭豆腐去黑色经典，糖油粑粑要趁热吃。太平街的贾谊故居免费参观，逛吃顺序建议：先太平街再坡子街，一路吃过去不用走回头路。",
                "长沙", "坡子街-太平街", "FOOD", cFood.getId(), P_CS_POZI, true, "APPROVED", 4, 640, 77, 51, 4));
        posts.add(mkPost(xiaolu, "GUIDE", "重庆魔幻8D交通避坑指南｜轻轨索道这么坐",
                "李子坝穿楼建议下午去，顺光好拍；长江索道提前在公众号买票能省1小时排队；山城步道比洪崖洞更有老重庆味道，而且免费！导航在重庆会骗人，多问路人。",
                "重庆", "渝中半岛", "OPTIMAL", cSpeed.getId(), P_CQ_TRAIN, false, "APPROVED", 5, 1102, 143, 70, 18));
        posts.add(mkPost(dashan, "QUESTION", "十一去张家界需要提前订票吗？求本地人解答",
                "计划10月2号到4号去，想问下森林公园门票和天门山索道票分别要提前几天抢？现场买得到吗？求各位大佬指点。",
                "张家界", "武陵源", null, cTrek.getId(), P_ZJJ_QUEUE, false, "APPROVED", 5, 210, 8, 2, 0));
        posts.add(mkPost(aya, "GUIDE", "成都慢生活三日休闲路线｜喝茶看熊猫吃火锅",
                "不赶时间版本：D1宽窄巷子+人民公园鹤鸣茶社；D2大熊猫基地（一定赶早！）+建设路小吃；D3锦里武侯祠+蜀大侠火锅。成都就适合慢悠悠地晃。",
                "成都", "成都市区", "LEISURE", cCw.getId(), P_CD_TEA, false, "APPROVED", 6, 530, 61, 34, 3));
        posts.add(mkPost(xiaolu, "QUESTION", "岳麓山野线有没有同行过的？一个人敢走吗",
                "听说岳麓山后山有条野路上山，想周末去试试，有没有走过的同学说说路况和注意事项？一个人有点虚。",
                "长沙", "岳麓山后山", "WILD", cTrek.getId(), P_YELU_WILD, false, "APPROVED", 6, 188, 15, 5, 0));
        posts.add(mkPost(dashan, "MOMENT", "大唐不夜城夜景yyds，汉服拍照太出片",
                "街上全是穿汉服的小姐姐，仿佛一秒穿越回盛唐。建议提前在附近租好妆造，晚上8点有演出巡游。",
                "西安", "大唐不夜城", null, cPhoto.getId(), P_DT_BUYE, false, "APPROVED", 0, 470, 55, 14, 1));
        posts.add(mkPost(aya, "LOCAL", "张家界本地向导教你玩天门山｜999级天梯这样爬省力",
                "本地人手把手教：A线索道上、环保车下最省力；玻璃栈道鞋套5元；天门洞999级台阶下比上轻松，但膝盖不好建议坐扶梯。山顶温差大记得带外套。",
                "张家界", "天门山", null, cTrek.getId(), P_TMS, true, "PENDING", 0, 0, 0, 0, 0));
        posts.add(mkPost(xiaolu, "MOMENT", "看大熊猫的正确时间：早上八点半，没有之一",
                "九点之后熊猫基本都进屋睡觉了，八点半入园冲幼年别墅，能看到活跃到模糊的团子们！",
                "成都", "大熊猫基地", null, cCw.getId(), P_PANDA, false, "PENDING", 0, 0, 0, 0, 0));
        postRepository.saveAll(posts);

        // ================= 评论 =================
        mkComment(posts.get(0), dashan, "码住！周末就照着走");
        mkComment(posts.get(0), aya, "五一广场晚上人真的多，注意保管手机");
        mkComment(posts.get(1), dashan, "老道湾那段一定要结伴，去年有人迷路");
        mkComment(posts.get(1), aya, "玻璃栈道照片太绝了，国庆冲！");
        mkComment(posts.get(2), xiaolu, "洪崖洞亮灯瞬间确实封神，我能看一晚上");
        mkComment(posts.get(3), xiaolu, "三大炮确实好吃，奎星楼街记下了！");
        mkComment(posts.get(3), dashan, "求推荐不辣的店哈哈，广东人落泪");
        mkComment(posts.get(4), dashan, "特种兵+1，一天刷完腿已废");
        mkComment(posts.get(4), dao, "城墙骑行注意防晒，顶上没遮阴");
        mkComment(posts.get(6), xiaolu, "火宫殿晚上最热闹，黑色经典+1");
        mkComment(posts.get(7), dashan, "李子坝拍照机位get，谢谢分享");
        mkComment(posts.get(7), aya, "长江索道建议提前排队，旺季真的吓人");
        mkComment(posts.get(9), xiaolu, "盖碗茶可以坐一下午，成都人的终极松弛");
        mkComment(posts.get(11), aya, "汉服拍照太出片了，已约妆造");
        mkComment(posts.get(8), dao, "国庆务必提前3天在官方公众号预约门票，现场基本没戏");
        // 统算commentCount，保证与评论数一致
        for (Post p : posts) {
            p.setCommentCount((int) commentRepository.findByPostIdOrderByCreatedAtAsc(p.getId()).size());
        }
        postRepository.saveAll(posts);

        // ================= 互动（点赞/收藏，用于当前用户态演示） =================
        like(xiaolu, posts.get(2)); like(xiaolu, posts.get(3)); fav(xiaolu, posts.get(3));
        like(xiaolu, posts.get(5)); like(xiaolu, posts.get(6)); fav(xiaolu, posts.get(6));
        like(xiaolu, posts.get(8)); like(xiaolu, posts.get(11)); like(xiaolu, posts.get(12));
        like(dashan, posts.get(0)); like(dashan, posts.get(1)); fav(dashan, posts.get(1));
        like(dashan, posts.get(3)); like(dashan, posts.get(4)); like(dashan, posts.get(6));
        like(dashan, posts.get(7)); fav(dashan, posts.get(7)); like(dashan, posts.get(9));
        like(dashan, posts.get(12)); like(dashan, posts.get(13));
        like(aya, posts.get(0)); fav(aya, posts.get(0)); like(aya, posts.get(1));
        like(aya, posts.get(2)); like(aya, posts.get(4)); fav(aya, posts.get(4));
        like(aya, posts.get(7)); like(aya, posts.get(10)); like(aya, posts.get(11));
        like(dao, posts.get(1)); fav(dao, posts.get(1)); like(dao, posts.get(7));
        like(dao, posts.get(8)); like(dao, posts.get(10));
        like(shang, posts.get(1)); like(shang, posts.get(7));
        like(admin, posts.get(0));

        // —— 景点 ——
        Map<String, Spot> spots = new LinkedHashMap<>();
        spot("岳麓山爱晚亭", "NATURE", "长沙", 112.9345, 28.1835, "中国四大名亭之一，岳麓山半山腰，秋季红枫环绕，毛泽东青年时代常来此读书锻炼。", S_AWT, 92, spots);
        spot("橘子洲", "HISTORY", "长沙", 112.9680, 28.1900, "湘江中心的长岛，青年毛泽东艺术雕塑所在地，每周六晚有烟花与灯光秀。", S_JZZ, 98, spots);
        spot("湖南博物院", "HISTORY", "长沙", 112.9915, 28.2100, "马王堆汉墓素纱襌衣、T型帛画的家，国家一级博物馆，需提前预约。", S_MUSEUM, 85, spots);
        spot("超级文和友", "TRENDY", "长沙", 112.9735, 28.1905, "复古怀旧风楼内街景，小龙虾与老长沙小吃，拍照与排队同样出名。", S_WHY, 90, spots);
        spot("坡子街火宫殿", "FOOD", "长沙", 112.9720, 28.1935, "长沙美食地标，臭豆腐、糖油粑粑、口味虾一站式打卡。", S_HGD, 88, spots);
        spot("岳麓山野路", "SECRET", "长沙", 112.9250, 28.1815, "后山未开发野路，坡陡林密，有迷路与滑坠风险，务必结伴报备。", S_YL_WILD, 62, spots);
        spot("长沙世界之窗", "THEME", "长沙", 113.0420, 28.2360, "大型主题乐园，过山车、摩天轮与节日活动，适合结伴玩一整天。", S_WINDOW, 70, spots);
        spot("五一广场", "TRENDY", "长沙", 112.9790, 28.1975, "长沙最繁华商圈，IFS楼顶KAWS雕塑与黄兴路步行街都在这里。", S_WUYI, 95, spots);
        spot("太平街", "FOOD", "长沙", 112.9725, 28.1965, "保留青石板路的历史老街，贾谊故居与各色特色小店云集。", S_TAIPING, 82, spots);
        spot("湖南省森林植物园", "NATURE", "长沙", 113.0350, 28.1450, "大片花海、水杉林与樱花湖，春有樱花秋有彩林，免费预约入园。", S_BOTANY, 68, spots);
        spot("张家界国家森林公园", "NATURE", "张家界", 110.435, 29.325, "中国第一个国家森林公园，石英砂岩峰林地貌，金鞭溪与袁家界核心景区。", S_FOREST, 96, spots);
        spot("天门山", "NATURE", "张家界", 110.482, 29.048, "湘西第一神山，999级上天梯通向天门洞，山顶玻璃栈道闻名全国。", S_TIANMEN, 94, spots);
        spot("天门山玻璃栈道", "TRENDY", "张家界", 110.483, 29.052, "悬于海拔1400米绝壁上的透明步道，脚下即是深渊，胆勇者的打卡地。", S_GLASS, 91, spots);
        spot("老道湾野线", "SECRET", "张家界", 110.503, 29.021, "未开发原始山谷野线，溪流密林与碎石坡，高风险，必须请持证向导。", S_LAODAO, 58, spots);
        spot("洪崖洞", "TRENDY", "重庆", 106.5836, 29.563, "十一层吊脚楼建筑群，夜景神似千与千寻，重庆第一打卡地标。", S_HYD, 93, spots);
        spot("长江索道", "TRENDY", "重庆", 106.587, 29.555, "横跨长江的空中公交车，俯瞰两江四岸立体山城，建议线上提前购票。", S_CABLE, 80, spots);
        spot("李子坝轻轨穿楼", "TRENDY", "重庆", 106.547, 29.557, "2号线列车从居民楼中穿过，8D魔幻交通的代表作，楼下有专用观景台。", S_LIZIBA, 86, spots);
        spot("山城步道", "SECRET", "重庆", 106.566, 29.553, "悬崖上的老石阶步道，老重庆民居与江景并存，游客稀少的宝藏路线。", S_SHANCHENG, 64, spots);
        spot("宽窄巷子", "HISTORY", "成都", 104.06, 30.67, "清代古街区，由宽巷子、窄巷子和井巷子组成，茶馆与川西民居风情。", S_KZ, 84, spots);
        spot("大熊猫繁育研究基地", "THEME", "成都", 104.1455, 30.7335, "距离市区最近的熊猫家园，赶早入园才能看到干饭活跃的团子。", S_PANDA_BASE, 90, spots);
        spot("大唐不夜城", "THEME", "西安", 108.959, 34.218, "盛唐主题步行街，灯火辉煌、演出不断，不倒翁小姐姐就在这里。", S_DT, 92, spots);
        spot("西安城墙", "HISTORY", "西安", 108.947, 34.259, "中国现存最完整的古代城垣，可租自行车骑行一圈，俯瞰老城街巷。", S_CITYWALL, 87, spots);

        // ===== 长沙补充 =====
        spot("梅溪湖文化艺术中心", "TRENDY", "长沙", 112.9020, 28.1980, "扎哈设计的白色流线建筑群，临湖拍照极出片，周边有商场与剧院。", null, 74, spots);
        spot("杜甫江阁", "HISTORY", "长沙", 112.9707, 28.1870, "湘江东岸的仿唐楼阁，为纪念杜甫而建，与橘子洲隔江相望，夜晚亮灯后适合江边散步拍照。", null, 80, spots);
        spot("天心阁", "HISTORY", "长沙", 112.9818, 28.1846, "古城墙上仅存的一段明清城楼，登阁可看老城区天际线，周边是简牍博物馆。", null, 76, spots);
        spot("都正街", "FOOD", "长沙", 112.9770, 28.1900, "藏在太平街旁边的安静老街，老长沙小吃与茶馆并存，游客相对少。", null, 72, spots);
        spot("洋湖湿地公园", "NATURE", "长沙", 112.9100, 28.1120, "城市南端的大片湿地，芦苇丛与水杉林适合骑行散步，全年免费开放。", null, 70, spots);

        // ===== 张家界补充 =====
        spot("袁家界", "NATURE", "张家界", 110.4300, 29.3600, "《阿凡达》悬浮山原型所在地，乾坤柱与天下第一桥是核心看点，云雾天最美。", null, 95, spots);
        spot("天子山", "NATURE", "张家界", 110.4400, 29.3900, "峰林观景台群，云海翻涌时如入仙境，索道与环保车衔接方便。", null, 90, spots);
        spot("宝峰湖", "NATURE", "张家界", 110.4800, 29.3400, "高峡出平湖，坐船穿行于峰壁之间，还能听土家山歌对唱。", null, 76, spots);

        // ===== 重庆补充 =====
        spot("解放碑步行街", "TRENDY", "重庆", 106.5760, 29.5580, "重庆最繁华的商圈与城市原点，周围火锅小面密集，夜里灯火通明。", null, 90, spots);
        spot("磁器口古镇", "HISTORY", "重庆", 106.4440, 29.5850, "嘉陵江边的千年古镇，青石板路配吊脚楼，陈麻花现做现卖。", null, 82, spots);
        spot("南山一棵树观景台", "TRENDY", "重庆", 106.5840, 29.5290, "俯瞰渝中半岛的最佳机位，日落前后半小时到夜景初上最出片。", null, 87, spots);
        spot("鹅岭二厂文创公园", "TRENDY", "重庆", 106.5560, 29.5490, "老厂房改造的文创园区，涂鸦墙与天台视角俱佳，年轻人扎堆。", null, 78, spots);
        spot("武隆天生三桥", "NATURE", "重庆", 107.5430, 29.4710, "喀斯特天坑与天生桥群，《满城尽带黄金甲》取景地，从主城出发需一整天。", null, 89, spots);
        spot("白象居", "SECRET", "重庆", 106.5810, 29.5590, "没有电梯的魔幻居民楼，多部电影取景地，楼道窗口能拍到过江索道。", null, 72, spots);

        // ===== 成都补充 =====
        spot("锦里古街", "HISTORY", "成都", 104.0437, 30.6471, "紧邻武侯祠的川西古镇街区，入夜红灯笼亮起最有味道，小吃与手作店密集。", null, 86, spots);
        spot("武侯祠", "HISTORY", "成都", 104.0435, 30.6485, "纪念诸葛亮与刘备的君臣合祀祠庙，三顾茅庐与出师表石刻都在这里。", null, 83, spots);
        spot("杜甫草堂", "HISTORY", "成都", 104.0280, 30.6585, "诗圣杜甫寓居成都的故居，竹林清幽，是市区里难得的安静园林。", null, 81, spots);
        spot("人民公园鹤鸣茶社", "FOOD", "成都", 104.0630, 30.6580, "成都最老的茶馆之一，竹椅盖碗茶配掏耳朵，体验本地人的松弛日常。", null, 88, spots);
        spot("春熙路太古里", "TRENDY", "成都", 104.0810, 30.6530, "成都最时尚的商圈，大慈寺与玻璃幕墙共存，街拍与逛街两不误。", null, 90, spots);
        spot("青城山", "NATURE", "成都", 103.5730, 30.9020, "道教发源地之一，山幽林密、湿润清凉，前山宫观集中、后山野趣更足。", null, 79, spots);
        spot("都江堰景区", "NATURE", "成都", 103.6190, 31.0020, "两千多年仍在服役的水利工程，鱼嘴分水、飞沙堰排沙，强烈建议请讲解。", null, 85, spots);

        // ===== 西安补充 =====
        spot("秦始皇兵马俑", "HISTORY", "西安", 109.2730, 34.3850, "世界第八大奇迹，一号坑军阵最震撼，务必提前网上购票并请讲解。", null, 97, spots);
        spot("华清宫", "HISTORY", "西安", 109.2200, 34.3600, "骊山脚下的唐代离宫，温泉汤池与西安事变旧址同在，《长恨歌》演出有名。", null, 84, spots);
        spot("大雁塔", "HISTORY", "西安", 108.9640, 34.2180, "玄奘译经藏经之地，塔身七层可登，北广场音乐喷泉晚上人最多。", null, 86, spots);
        spot("陕西历史博物馆", "HISTORY", "西安", 108.9500, 34.2280, "周秦汉唐文物大观，何家村窖藏金银器必看，需提前预约且常常约满。", null, 88, spots);
        spot("回民街", "FOOD", "西安", 108.9330, 34.2650, "钟鼓楼旁的美食街，肉夹馍、羊肉泡馍、酸梅汤一条街吃到撑。", null, 85, spots);
        spot("大唐芙蓉园", "THEME", "西安", 108.9700, 34.2130, "仿唐皇家园林，水幕电影与夜间演出很出片，适合傍晚进场一直待到亮灯。", null, 80, spots);
        spot("华山", "NATURE", "西安", 110.0850, 34.4900, "五岳之险，长空栈道与鹞子翻身惊险出名，夜爬看日出请结伴并备足体力。", null, 92, spots);

        // ================= 已保存路线（用路线规划算法生成） =================
        Map<String, Object> plan1 = routePlannerService.plan("长沙", "OPTIMAL", null, null, null);
        saveRoute(xiaolu, "长沙最优打卡一日线", plan1);
        Map<String, Object> plan2 = routePlannerService.plan("长沙", "FOOD", null, null, null);
        saveRoute(aya, "长沙美食专线", plan2);

        // ================= 足迹 =================
        mkFootprint(xiaolu, spots.get("岳麓山爱晚亭"), LocalDate.now().minusDays(20), FP_AWT, "枫叶还没全红，但亭子本身就很出片");
        mkFootprint(xiaolu, spots.get("橘子洲"), LocalDate.now().minusDays(15), FP_JZZ, "沿江走到雕像，晚上灯光很美");
        mkFootprint(xiaolu, spots.get("五一广场"), LocalDate.now().minusDays(7), FP_WUYI, "逛吃逛吃的一晚");

        // —— 交通方案 ——
        transport("长沙", "张家界", "TRAIN", 160, "148.5", "班次密集（长沙南直达）", 5, 2, 5, "高铁最快最稳，建议提前12306购票，节假日紧张");
        transport("长沙", "张家界", "BUS", 270, "100", "每日多班", 3, 3, 4, "汽车东站发车，时间较长但票价友好");
        transport("长沙", "张家界", "CAR", 240, "320(均摊)", "随时出发", 4, 5, 4, "长张高速直达，3-4人拼车最划算，注意长张高速隧道多");
        transport("长沙", "张家界", "CARPOOL", 240, "120", "每日发车", 3, 4, 3, "车站与校园群均可拼，注意甄别车主、核对身份并分享行程");
        transport("长沙", "张家界", "BIKE", 4320, "80", "随自己节奏", 1, 5, 2, "骑行徒步约3天：仅限户外老手，必须结伴，备齐头盔车灯与补胎工具");
        transport("长沙", "武汉", "TRAIN", 80, "164.5", "班次密集", 5, 2, 5, "长沙南到武汉站，周末往返首选");
        transport("长沙", "武汉", "BUS", 240, "120", "每日6班", 3, 3, 4, "适合买不到高铁票时的备选");
        transport("长沙", "武汉", "CAR", 210, "280(均摊)", "随时出发", 4, 5, 4, "京港澳高速，注意进城拥堵");
        transport("长沙", "武汉", "CARPOOL", 210, "110", "每日多班", 3, 4, 3, "学生拼车群常见，务必核验信息");
        transport("长沙", "广州", "TRAIN", 140, "314", "班次密集", 5, 2, 5, "长沙南直达广州南，抢票要快");
        transport("长沙", "广州", "BUS", 480, "220", "每日3班", 2, 3, 4, "夜班车可省一晚住宿，但比较辛苦");
        transport("长沙", "广州", "CAR", 390, "500(均摊)", "随时出发", 4, 5, 4, "京港澳全程高速，建议两人轮换驾驶");
        transport("长沙", "广州", "CARPOOL", 390, "260", "每日发车", 2, 4, 3, "长途拼车注意休息区轮换，避免疲劳驾驶");
        transport("长沙", "重庆", "TRAIN", 320, "449.5", "班次较多", 4, 2, 5, "高铁约5小时20分，重庆西站离市中心较远，留足接驳时间");
        transport("长沙", "重庆", "BUS", 600, "260", "每日1-2班", 2, 3, 4, "长途大巴约10小时，仅作备选，建议优先高铁");
        transport("长沙", "重庆", "CAR", 540, "700(均摊)", "随时出发", 4, 5, 4, "沪昆高速转兰海高速，山区隧道多，建议两人轮换驾驶");
        transport("长沙", "重庆", "CARPOOL", 540, "300", "每日发车", 3, 4, 3, "长途拼车注意休息区轮换，避免疲劳驾驶");
        transport("长沙", "成都", "TRAIN", 410, "478.5", "班次密集", 4, 2, 5, "高铁约6小时50分，节假日票紧张，提前购票");
        transport("长沙", "成都", "BUS", 840, "320", "每日1班", 2, 3, 4, "约14小时车程，仅适合极限预算，一般不建议");
        transport("长沙", "成都", "CAR", 690, "800(均摊)", "随时出发", 3, 5, 4, "沪渝高速全程高速，路程长，务必规划好休息点");
        transport("长沙", "成都", "CARPOOL", 690, "350", "每日发车", 3, 4, 3, "长途拼车价格偏高，人数多时不如租车");
        transport("长沙", "西安", "TRAIN", 340, "454.5", "班次密集", 5, 2, 5, "高铁约5小时40分，到达后地铁直达市区");
        transport("长沙", "西安", "BUS", 900, "300", "每日1班", 2, 2, 4, "约15小时，夜间班车可省一晚住宿但比较辛苦");
        transport("长沙", "西安", "CAR", 600, "750(均摊)", "随时出发", 4, 5, 4, "京港澳转连霍高速，路况好但距离长，注意限速");
        transport("长沙", "西安", "CARPOOL", 600, "320", "每日发车", 3, 4, 3, "全程约1000公里，注意甄别车主信息");
        transport("张家界", "长沙", "TRAIN", 160, "148.5", "班次密集（长沙南直达）", 5, 2, 5, "从张家界乘高铁直达长沙南，返程票同样紧张，建议提前购票");
        transport("张家界", "长沙", "BUS", 270, "100", "每日多班", 3, 3, 4, "从张家界返程大巴班次偏少，建议提前一天订票");
        transport("张家界", "长沙", "CAR", 240, "320(均摊)", "随时出发", 4, 5, 4, "高速返程注意服务区休息，两人轮换驾驶更安全");
        transport("张家界", "长沙", "CARPOOL", 240, "120", "每日发车", 3, 4, 3, "返程拼车群常见，务必核验车主身份并分享行程");
        transport("张家界", "长沙", "BIKE", 4320, "80", "随自己节奏", 1, 5, 2, "骑行返程约3天：仅限户外老手，必须结伴同行，备好车灯与补胎工具");
        transport("武汉", "长沙", "TRAIN", 80, "164.5", "班次密集", 5, 2, 5, "从武汉乘高铁直达长沙南，返程票同样紧张，建议提前购票");
        transport("武汉", "长沙", "BUS", 240, "120", "每日6班", 3, 3, 4, "从武汉返程大巴班次偏少，建议提前一天订票");
        transport("武汉", "长沙", "CAR", 210, "280(均摊)", "随时出发", 4, 5, 4, "高速返程注意服务区休息，两人轮换驾驶更安全");
        transport("武汉", "长沙", "CARPOOL", 210, "110", "每日多班", 3, 4, 3, "返程拼车群常见，务必核验车主身份并分享行程");
        transport("广州", "长沙", "TRAIN", 140, "314", "班次密集", 5, 2, 5, "从广州乘高铁直达长沙南，返程票同样紧张，建议提前购票");
        transport("广州", "长沙", "BUS", 480, "220", "每日3班", 2, 3, 4, "从广州返程大巴班次偏少，建议提前一天订票");
        transport("广州", "长沙", "CAR", 390, "500(均摊)", "随时出发", 4, 5, 4, "高速返程注意服务区休息，两人轮换驾驶更安全");
        transport("广州", "长沙", "CARPOOL", 390, "260", "每日发车", 2, 4, 3, "返程拼车群常见，务必核验车主身份并分享行程");
        transport("重庆", "长沙", "TRAIN", 320, "449.5", "班次较多", 4, 2, 5, "从重庆乘高铁直达长沙南，返程票同样紧张，建议提前购票");
        transport("重庆", "长沙", "BUS", 600, "260", "每日1-2班", 2, 3, 4, "从重庆返程大巴班次偏少，建议提前一天订票");
        transport("重庆", "长沙", "CAR", 540, "700(均摊)", "随时出发", 4, 5, 4, "高速返程注意服务区休息，两人轮换驾驶更安全");
        transport("重庆", "长沙", "CARPOOL", 540, "300", "每日发车", 3, 4, 3, "返程拼车群常见，务必核验车主身份并分享行程");
        transport("成都", "长沙", "TRAIN", 410, "478.5", "班次密集", 4, 2, 5, "从成都乘高铁直达长沙南，返程票同样紧张，建议提前购票");
        transport("成都", "长沙", "BUS", 840, "320", "每日1班", 2, 3, 4, "从成都返程大巴班次偏少，建议提前一天订票");
        transport("成都", "长沙", "CAR", 690, "800(均摊)", "随时出发", 3, 5, 4, "高速返程注意服务区休息，两人轮换驾驶更安全");
        transport("成都", "长沙", "CARPOOL", 690, "350", "每日发车", 3, 4, 3, "返程拼车群常见，务必核验车主身份并分享行程");
        transport("西安", "长沙", "TRAIN", 340, "454.5", "班次密集", 5, 2, 5, "从西安乘高铁直达长沙南，返程票同样紧张，建议提前购票");
        transport("西安", "长沙", "BUS", 900, "300", "每日1班", 2, 2, 4, "从西安返程大巴班次偏少，建议提前一天订票");
        transport("西安", "长沙", "CAR", 600, "750(均摊)", "随时出发", 4, 5, 4, "高速返程注意服务区休息，两人轮换驾驶更安全");
        transport("西安", "长沙", "CARPOOL", 600, "320", "每日发车", 3, 4, 3, "返程拼车群常见，务必核验车主身份并分享行程");
        transport("重庆", "成都", "TRAIN", 90, "154", "班次密集", 5, 2, 5, "成渝高铁约1.5小时，两城往返像坐地铁");
        transport("重庆", "成都", "BUS", 240, "120", "每小时一班", 3, 3, 4, "成渝高速大巴，班次多但耗时较长");
        transport("重庆", "成都", "CAR", 210, "300(均摊)", "随时出发", 4, 5, 4, "成渝高速约300公里，路况好，自驾很轻松");
        transport("重庆", "成都", "CARPOOL", 210, "110", "每日发车", 3, 4, 3, "拼车群常见，价格比高铁便宜一半");
        transport("成都", "重庆", "TRAIN", 90, "154", "班次密集", 5, 2, 5, "成渝高铁约1.5小时，到重庆北站换乘地铁方便");
        transport("成都", "重庆", "BUS", 240, "120", "每小时一班", 3, 3, 4, "成渝高速大巴，班次多但耗时较长");
        transport("成都", "重庆", "CAR", 210, "300(均摊)", "随时出发", 4, 5, 4, "成渝高速约300公里，路况好，注意进城拥堵");
        transport("成都", "重庆", "CARPOOL", 210, "110", "每日发车", 3, 4, 3, "拼车群常见，返程注意核对车牌信息");

        // ================= 交通方案补充：7城全互通 =================
        transport("武汉", "张家界", "TRAIN", 280, "232", "班次较多", 5, 2, 5, "动车约4小时40分（经宜昌），也可从长沙中转");
        transport("武汉", "张家界", "BUS", 430, "180", "每日1-2班", 2, 3, 4, "长途大巴约7小时，仅作备选，建议优先高铁");
        transport("武汉", "张家界", "CAR", 390, "410(均摊)", "随时出发", 4, 5, 4, "沪渝高速转张花高速自驾，全程约550公里，注意休息轮换驾驶");
        transport("武汉", "张家界", "CARPOOL", 390, "180", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("张家界", "武汉", "TRAIN", 280, "232", "班次较多", 5, 2, 5, "动车约4小时40分（经宜昌），也可从长沙中转");
        transport("张家界", "武汉", "BUS", 430, "180", "每日1-2班", 2, 3, 4, "长途大巴约7小时，仅作备选，建议优先高铁");
        transport("张家界", "武汉", "CAR", 390, "410(均摊)", "随时出发", 4, 5, 4, "沪渝高速转张花高速自驾，全程约550公里，注意休息轮换驾驶");
        transport("张家界", "武汉", "CARPOOL", 390, "180", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("重庆", "武汉", "TRAIN", 300, "402", "班次较多", 5, 2, 5, "高铁约5小时（郑渝高铁，重庆北→汉口）");
        transport("重庆", "武汉", "BUS", 700, "300", "每日1-2班", 2, 3, 4, "长途大巴约12小时，仅作备选，建议优先高铁");
        transport("重庆", "武汉", "CAR", 640, "680(均摊)", "随时出发", 4, 5, 4, "沪渝高速自驾，全程约900公里，注意休息轮换驾驶");
        transport("重庆", "武汉", "CARPOOL", 640, "310", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("武汉", "重庆", "TRAIN", 300, "402", "班次较多", 5, 2, 5, "高铁约5小时（郑渝高铁，重庆北→汉口）");
        transport("武汉", "重庆", "BUS", 700, "300", "每日1-2班", 2, 3, 4, "长途大巴约12小时，仅作备选，建议优先高铁");
        transport("武汉", "重庆", "CAR", 640, "680(均摊)", "随时出发", 4, 5, 4, "沪渝高速自驾，全程约900公里，注意休息轮换驾驶");
        transport("武汉", "重庆", "CARPOOL", 640, "310", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("广州", "武汉", "TRAIN", 230, "463", "班次较多", 5, 2, 5, "高铁约3小时50分（京广高铁，广州南→武汉站）");
        transport("广州", "武汉", "BUS", 780, "330", "每日1-2班", 2, 3, 4, "长途大巴约13小时，仅作备选，建议优先高铁");
        transport("广州", "武汉", "CAR", 710, "750(均摊)", "随时出发", 4, 5, 4, "京港澳高速自驾，全程约1000公里，注意休息轮换驾驶");
        transport("广州", "武汉", "CARPOOL", 710, "340", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("武汉", "广州", "TRAIN", 230, "463", "班次较多", 5, 2, 5, "高铁约3小时50分（京广高铁，广州南→武汉站）");
        transport("武汉", "广州", "BUS", 780, "330", "每日1-2班", 2, 3, 4, "长途大巴约13小时，仅作备选，建议优先高铁");
        transport("武汉", "广州", "CAR", 710, "750(均摊)", "随时出发", 4, 5, 4, "京港澳高速自驾，全程约1000公里，注意休息轮换驾驶");
        transport("武汉", "广州", "CARPOOL", 710, "340", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("成都", "西安", "TRAIN", 240, "263", "班次较多", 5, 2, 5, "高铁约4小时（西成高铁）");
        transport("成都", "西安", "BUS", 560, "240", "每日1-2班", 2, 3, 4, "长途大巴约9小时，仅作备选，建议优先高铁");
        transport("成都", "西安", "CAR", 510, "540(均摊)", "随时出发", 4, 5, 4, "京昆高速自驾，全程约720公里，注意休息轮换驾驶");
        transport("成都", "西安", "CARPOOL", 510, "240", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("西安", "成都", "TRAIN", 240, "263", "班次较多", 5, 2, 5, "高铁约4小时（西成高铁）");
        transport("西安", "成都", "BUS", 560, "240", "每日1-2班", 2, 3, 4, "长途大巴约9小时，仅作备选，建议优先高铁");
        transport("西安", "成都", "CAR", 510, "540(均摊)", "随时出发", 4, 5, 4, "京昆高速自驾，全程约720公里，注意休息轮换驾驶");
        transport("西安", "成都", "CARPOOL", 510, "240", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("重庆", "西安", "TRAIN", 310, "298", "班次较多", 5, 2, 5, "高铁约5小时（经重庆北→西安北）");
        transport("重庆", "西安", "BUS", 540, "230", "每日1-2班", 2, 3, 4, "长途大巴约9小时，仅作备选，建议优先高铁");
        transport("重庆", "西安", "CAR", 490, "520(均摊)", "随时出发", 4, 5, 4, "包茂高速自驾，全程约700公里，注意休息轮换驾驶");
        transport("重庆", "西安", "CARPOOL", 490, "230", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("西安", "重庆", "TRAIN", 310, "298", "班次较多", 5, 2, 5, "高铁约5小时（经重庆北→西安北）");
        transport("西安", "重庆", "BUS", 540, "230", "每日1-2班", 2, 3, 4, "长途大巴约9小时，仅作备选，建议优先高铁");
        transport("西安", "重庆", "CAR", 490, "520(均摊)", "随时出发", 4, 5, 4, "包茂高速自驾，全程约700公里，注意休息轮换驾驶");
        transport("西安", "重庆", "CARPOOL", 490, "230", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("张家界", "重庆", "TRAIN", 230, "152", "班次较多", 5, 2, 5, "动车约3小时50分（黔张常铁路，张家界西→重庆北）");
        transport("张家界", "重庆", "BUS", 410, "170", "每日1-2班", 2, 3, 4, "长途大巴约7小时，仅作备选，建议优先高铁");
        transport("张家界", "重庆", "CAR", 370, "390(均摊)", "随时出发", 4, 5, 4, "包茂高速转黔江方向自驾，全程约520公里，注意休息轮换驾驶");
        transport("张家界", "重庆", "CARPOOL", 370, "180", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("重庆", "张家界", "TRAIN", 230, "152", "班次较多", 5, 2, 5, "动车约3小时50分（黔张常铁路，张家界西→重庆北）");
        transport("重庆", "张家界", "BUS", 410, "170", "每日1-2班", 2, 3, 4, "长途大巴约7小时，仅作备选，建议优先高铁");
        transport("重庆", "张家界", "CAR", 370, "390(均摊)", "随时出发", 4, 5, 4, "包茂高速转黔江方向自驾，全程约520公里，注意休息轮换驾驶");
        transport("重庆", "张家界", "CARPOOL", 370, "180", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("张家界", "成都", "TRAIN", 730, "216", "每日1-2班", 4, 2, 5, "暂无直达高铁：普速约12小时夕发朝至，或经重庆中转动车");
        transport("张家界", "成都", "BUS", 660, "280", "每日1-2班", 2, 3, 4, "长途大巴约11小时，仅作备选，建议优先高铁");
        transport("张家界", "成都", "CAR", 600, "640(均摊)", "随时出发", 4, 5, 4, "经重庆中转，沪渝高速转张花高速自驾，全程约850公里，注意休息轮换驾驶");
        transport("张家界", "成都", "CARPOOL", 600, "290", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("成都", "张家界", "TRAIN", 730, "216", "每日1-2班", 4, 2, 5, "暂无直达高铁：普速约12小时夕发朝至，或经重庆中转动车");
        transport("成都", "张家界", "BUS", 660, "280", "每日1-2班", 2, 3, 4, "长途大巴约11小时，仅作备选，建议优先高铁");
        transport("成都", "张家界", "CAR", 600, "640(均摊)", "随时出发", 4, 5, 4, "经重庆中转，沪渝高速转张花高速自驾，全程约850公里，注意休息轮换驾驶");
        transport("成都", "张家界", "CARPOOL", 600, "290", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("张家界", "广州", "TRAIN", 340, "330", "每日1-2班", 5, 2, 5, "高铁约5小时40分（张家界西→广州南）");
        transport("张家界", "广州", "BUS", 730, "310", "每日1-2班", 2, 3, 4, "长途大巴约12小时，仅作备选，建议优先高铁");
        transport("张家界", "广州", "CAR", 660, "700(均摊)", "随时出发", 4, 5, 4, "二广高速自驾，全程约940公里，注意休息轮换驾驶");
        transport("张家界", "广州", "CARPOOL", 660, "320", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("广州", "张家界", "TRAIN", 340, "330", "每日1-2班", 5, 2, 5, "高铁约5小时40分（张家界西→广州南）");
        transport("广州", "张家界", "BUS", 730, "310", "每日1-2班", 2, 3, 4, "长途大巴约12小时，仅作备选，建议优先高铁");
        transport("广州", "张家界", "CAR", 660, "700(均摊)", "随时出发", 4, 5, 4, "二广高速自驾，全程约940公里，注意休息轮换驾驶");
        transport("广州", "张家界", "CARPOOL", 660, "320", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("张家界", "西安", "TRAIN", 540, "388", "每日1-2班", 4, 2, 5, "暂无直达高铁，经长沙中转全程约9小时");
        transport("张家界", "西安", "BUS", 780, "330", "每日1-2班", 2, 3, 4, "长途大巴约13小时，仅作备选，建议优先高铁");
        transport("张家界", "西安", "CAR", 710, "750(均摊)", "随时出发", 4, 5, 4, "二广高速转连霍高速自驾，全程约1000公里，注意休息轮换驾驶");
        transport("张家界", "西安", "CARPOOL", 710, "340", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("西安", "张家界", "TRAIN", 540, "388", "每日1-2班", 4, 2, 5, "暂无直达高铁，经长沙中转全程约9小时");
        transport("西安", "张家界", "BUS", 780, "330", "每日1-2班", 2, 3, 4, "长途大巴约13小时，仅作备选，建议优先高铁");
        transport("西安", "张家界", "CAR", 710, "750(均摊)", "随时出发", 4, 5, 4, "二广高速转连霍高速自驾，全程约1000公里，注意休息轮换驾驶");
        transport("西安", "张家界", "CARPOOL", 710, "340", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("武汉", "成都", "TRAIN", 520, "412", "每日1-2班", 4, 2, 5, "动车约8小时40分（经宜昌、重庆）");
        transport("武汉", "成都", "BUS", 890, "380", "每日1-2班", 2, 3, 4, "长途大巴约15小时，仅作备选，建议优先高铁");
        transport("武汉", "成都", "CAR", 810, "860(均摊)", "随时出发", 4, 5, 4, "沪蓉高速自驾，全程约1150公里，注意休息轮换驾驶");
        transport("武汉", "成都", "CARPOOL", 810, "390", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("成都", "武汉", "TRAIN", 520, "412", "每日1-2班", 4, 2, 5, "动车约8小时40分（经宜昌、重庆）");
        transport("成都", "武汉", "BUS", 890, "380", "每日1-2班", 2, 3, 4, "长途大巴约15小时，仅作备选，建议优先高铁");
        transport("成都", "武汉", "CAR", 810, "860(均摊)", "随时出发", 4, 5, 4, "沪蓉高速自驾，全程约1150公里，注意休息轮换驾驶");
        transport("成都", "武汉", "CARPOOL", 810, "390", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("武汉", "西安", "TRAIN", 260, "454", "班次较多", 5, 2, 5, "高铁约4小时20分（武汉→西安北）");
        transport("武汉", "西安", "BUS", 570, "240", "每日1-2班", 2, 3, 4, "长途大巴约10小时，仅作备选，建议优先高铁");
        transport("武汉", "西安", "CAR", 520, "560(均摊)", "随时出发", 4, 5, 4, "福银高速自驾，全程约740公里，注意休息轮换驾驶");
        transport("武汉", "西安", "CARPOOL", 520, "250", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("西安", "武汉", "TRAIN", 260, "454", "班次较多", 5, 2, 5, "高铁约4小时20分（武汉→西安北）");
        transport("西安", "武汉", "BUS", 570, "240", "每日1-2班", 2, 3, 4, "长途大巴约10小时，仅作备选，建议优先高铁");
        transport("西安", "武汉", "CAR", 520, "560(均摊)", "随时出发", 4, 5, 4, "福银高速自驾，全程约740公里，注意休息轮换驾驶");
        transport("西安", "武汉", "CARPOOL", 520, "250", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("广州", "重庆", "TRAIN", 400, "528", "每日1-2班", 5, 2, 5, "高铁约6小时40分（广州南→重庆西）");
        transport("广州", "重庆", "BUS", 1090, "460", "每日1班", 2, 3, 4, "长途大巴约18小时，仅作备选，建议优先高铁");
        transport("广州", "重庆", "CAR", 990, "1050(均摊)", "随时出发", 3, 5, 4, "兰海高速转沪渝高速自驾，全程约1400公里，注意休息轮换驾驶");
        transport("广州", "重庆", "CARPOOL", 990, "470", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("重庆", "广州", "TRAIN", 400, "528", "每日1-2班", 5, 2, 5, "高铁约6小时40分（广州南→重庆西）");
        transport("重庆", "广州", "BUS", 1090, "460", "每日1班", 2, 3, 4, "长途大巴约18小时，仅作备选，建议优先高铁");
        transport("重庆", "广州", "CAR", 990, "1050(均摊)", "随时出发", 3, 5, 4, "兰海高速转沪渝高速自驾，全程约1400公里，注意休息轮换驾驶");
        transport("重庆", "广州", "CARPOOL", 990, "470", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("广州", "成都", "TRAIN", 560, "542", "每日1-2班", 4, 2, 5, "高铁约9小时20分（经贵阳）");
        transport("广州", "成都", "BUS", 1280, "540", "每日1班", 2, 3, 4, "长途大巴约21小时，仅作备选，建议优先高铁");
        transport("广州", "成都", "CAR", 1160, "1240(均摊)", "随时出发", 3, 5, 4, "厦蓉高速转京港澳高速自驾，全程约1650公里，注意休息轮换驾驶");
        transport("广州", "成都", "CARPOOL", 1160, "560", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("成都", "广州", "TRAIN", 560, "542", "每日1-2班", 4, 2, 5, "高铁约9小时20分（经贵阳）");
        transport("成都", "广州", "BUS", 1280, "540", "每日1班", 2, 3, 4, "长途大巴约21小时，仅作备选，建议优先高铁");
        transport("成都", "广州", "CAR", 1160, "1240(均摊)", "随时出发", 3, 5, 4, "厦蓉高速转京港澳高速自驾，全程约1650公里，注意休息轮换驾驶");
        transport("成都", "广州", "CARPOOL", 1160, "560", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("广州", "西安", "TRAIN", 480, "813", "每日1-2班", 4, 2, 5, "高铁约8小时（京广转徐兰高铁）");
        transport("广州", "西安", "BUS", 1360, "580", "每日1班", 2, 3, 4, "长途大巴约23小时，仅作备选，建议优先高铁");
        transport("广州", "西安", "CAR", 1240, "1310(均摊)", "随时出发", 3, 5, 4, "京港澳高速转连霍高速自驾，全程约1750公里，注意休息轮换驾驶");
        transport("广州", "西安", "CARPOOL", 1240, "590", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");
        transport("西安", "广州", "TRAIN", 480, "813", "每日1-2班", 4, 2, 5, "高铁约8小时（京广转徐兰高铁）");
        transport("西安", "广州", "BUS", 1360, "580", "每日1班", 2, 3, 4, "长途大巴约23小时，仅作备选，建议优先高铁");
        transport("西安", "广州", "CAR", 1240, "1310(均摊)", "随时出发", 3, 5, 4, "京港澳高速转连霍高速自驾，全程约1750公里，注意休息轮换驾驶");
        transport("西安", "广州", "CARPOOL", 1240, "590", "每日发车", 3, 4, 3, "拼车均摊油费过路费，注意甄别车主、核对身份并分享行程");

        // ================= 商户与商品 =================
        Merchant shop = new Merchant();
        shop.setUserId(shang.getId());
        shop.setShopName("山野户外装备社");
        shop.setQualification("营业执照+户外用品经营许可（已上传）");
        shop.setDescription("中南学长开的户外装备店，主营徒步露营装备，学生凭证件享9折，长沙高校可自提。");
        shop.setStatus("APPROVED");
        shop.setCredit(100);
        shop.setCreatedAt(LocalDateTime.now().minusDays(60));
        merchantRepository.save(shop);
        product(shop, "专业登山背包45L", "户外装备", 199.0, 200, 156, PRD_BAG, "承重护腰设计，自带防雨罩，周末徒步三日刚刚好");
        product(shop, "双人防雨露营帐篷", "户外装备", 599.0, 80, 64, PRD_TENT, "双层防暴雨，3分钟速开，配防风绳地钉");
        product(shop, "铝膜防潮垫", "户外装备", 49.0, 500, 312, PRD_MAT, "轻便保暖，卷收设计，学生党露营入门首选");
        product(shop, "微单相机套机", "摄影数码", 899.0, 30, 12, PRD_CAMERA, "二手成色好，适合旅拍入门，送32G内存卡");
        product(shop, "高帮防水徒步鞋", "户外装备", 399.0, 120, 98, PRD_SHOES, "Vibram大底防滑防水，野线也能hold住");
        product(shop, "三合一冲锋衣", "户外装备", 459.0, 90, 47, PRD_JACKET, "抓绒内胆可拆，防风防雨，秋冬徒步必备");
        product(shop, "20000mAh快充移动电源", "数码电器", 129.0, 300, 289, PRD_POWER, "可上飞机，双向快充，山里拍片不怕断电");
        product(shop, "316不锈钢保温杯500ml", "户外装备", 29.0, 600, 455, PRD_CUP, "24小时保温保冷，轻巧便携，性价比之王");

        // ================= 导游 =================
        GuideProfile daoProfile = new GuideProfile();
        daoProfile.setUserId(dao.getId());
        daoProfile.setThemes("户外徒步,张家界向导,野线穿越");
        daoProfile.setIntro("张家界本地人，持证户外向导，带队8年，熟悉武陵源与天门山所有路线，老道湾野线救援志愿者。擅长学生小团，节奏可快可慢。");
        daoProfile.setStatus("APPROVED");
        daoProfile.setRatingAvg(4.8);
        daoProfile.setRatingCount(36);
        daoProfile.setOrderCount(28);
        daoProfile.setCreatedAt(LocalDateTime.now().minusDays(90));
        guideProfileRepository.save(daoProfile);

        GuideProfile xlProfile = new GuideProfile();
        xlProfile.setUserId(xiaolu.getId());
        xlProfile.setThemes("Citywalk,长沙本地逛吃,摄影陪拍");
        xlProfile.setIntro("长沙在读学生，深度Citywalk玩家，可提供本地逛吃+摄影陪拍半日导览。");
        xlProfile.setStatus("PENDING");
        xlProfile.setRatingAvg(0.0);
        xlProfile.setRatingCount(0);
        xlProfile.setOrderCount(0);
        xlProfile.setCreatedAt(LocalDateTime.now().minusDays(2));
        guideProfileRepository.save(xlProfile);

        GuideService gs1 = guideService(dao, "张家界国家森林公园深度徒步", "张家界", "户外徒步", 199.0,
                "全天8小时：金鞭溪→袁家界→天子山，含路线规划与拍照",
                "适合第一次到张家界的同学，不走回头路，包含小众观景台机位；不含门票索道与餐饮。");
        GuideService gs2 = guideService(dao, "天门山玻璃栈道一日陪游", "张家界", "网红打卡,徒步", 299.0,
                "全天6小时：索道上山→玻璃栈道→天门洞→环保车下山",
                "帮抢A线票，全程陪同拍照，玻璃栈道与999级天梯都安排明白。");
        GuideService gs3 = guideService(dao, "老道湾野线探险向导", "张家界", "野线穿越,刺激探险", 399.0,
                "全天10小时，4人成团，含安全装备检查与应急保障",
                "高风险野线，仅限有徒步经验者；行前报备、装备检查，恶劣天气无条件取消。");
        guideServiceRepository.saveAll(List.of(gs1, gs2, gs3));

        GuideBooking booking = new GuideBooking();
        booking.setServiceId(gs2.getId());
        booking.setGuideId(dao.getId());
        booking.setTravelerId(dashan.getId());
        booking.setTripDate(LocalDate.of(2026, 10, 6));
        booking.setAmount(299.0);
        booking.setRemark("我们3个人，希望走慢一点多拍照");
        booking.setStatus("CONFIRMED");
        booking.setCommissionRate(0.1);
        booking.setFee(0.0);
        booking.setGuideIncome(0.0);
        booking.setCreatedAt(LocalDateTime.now().minusDays(2));
        guideBookingRepository.save(booking);

        // ================= 广告 =================
        ad("BANNER", "秋季结伴季·找搭子就上学旅圈", AD_AUTUMN, "/pages/trips", "学旅圈官方", "长沙",
                "APPROVED", 2000.0, 1280, 96, LocalDate.of(2026, 9, 25).atStartOfDay(), LocalDate.of(2026, 10, 31).atTime(23, 59));
        ad("BANNER", "山野户外店·秋季装备促销低至7折", AD_GEAR, "/pages/shop", "山野户外装备社", null,
                "APPROVED", 1500.0, 860, 54, LocalDate.of(2026, 9, 28).atStartOfDay(), LocalDate.of(2026, 10, 25).atTime(23, 59));
        ad("FEED", "周末星空露营计划，装备一站备齐", AD_CAMP, "/pages/products", "山野户外装备社", null,
                "APPROVED", 800.0, 2103, 187, LocalDate.of(2026, 9, 20).atStartOfDay(), LocalDate.of(2026, 11, 15).atTime(23, 59));
        ad("SIDEBAR", "徒步鞋冲锋衣秋日上新", AD_SHOE, "/pages/products", "山野户外装备社", null,
                "APPROVED", 300.0, 542, 31, LocalDate.of(2026, 10, 1).atStartOfDay(), LocalDate.of(2026, 10, 31).atTime(23, 59));
        ad("FEED", "高铁说走就走·周末城市盲盒", AD_TRAIN, "/pages/trips", "某出行平台", null,
                "PENDING", 600.0, 0, 0, LocalDate.of(2026, 10, 5).atStartOfDay(), LocalDate.of(2026, 11, 5).atTime(23, 59));

        // ================= 举报 =================
        Report report = new Report();
        report.setReporterId(aya.getId());
        report.setTargetType("POST");
        report.setTargetId(posts.get(8).getId());
        report.setReason("疑似在问答区发布第三方拼团引流信息");
        report.setEvidence("评论区出现微信号与外部链接，已截图");
        report.setStatus("PENDING");
        report.setCreatedAt(LocalDateTime.now().minusDays(1));
        reportRepository.save(report);

        // ================= 行程与成员 =================
        Trip trip = new Trip();
        trip.setCaptainId(dashan.getId());
        trip.setTitle("国庆张家界徒步3日结伴｜森林公园+天门山");
        trip.setDestination("张家界");
        trip.setDepartAt(LocalDate.of(2026, 10, 5).atTime(8, 0));
        trip.setMaxMembers(6);
        trip.setBudget(800.0);
        trip.setTransport("TRAIN");
        trip.setMeetPoint("长沙南站东广场集合，G6409次");
        trip.setDescription("三天两夜徒步局，Day1金鞭溪袁家界，Day2天子山日出，Day3天门山玻璃栈道。要求：已认证学籍、有徒步鞋、能接受日均2万步，野路段必须跟队。");
        trip.setRiskLevel("MIDDLE");
        trip.setStatus("OPEN");
        // 结伴偏好：节奏、天数、兴趣标签与校际开放范围
        trip.setPaceType("ENERGETIC");
        trip.setPrefDays(3);
        trip.setInterestTags("徒步,登山,摄影");
        trip.setSchoolScope("ALL");
        trip.setCreatedAt(LocalDateTime.now().minusDays(3));
        // AA账单统一存expenses表（DemoContentInitializer播团队消费种子），
        // 旧trips.expenses_json口径与团队总支出不一致，已废弃
        tripRepository.save(trip);

        mkMember(trip.getId(), xiaolu.getId(), "APPROVED", "有徒步经验，装备齐全，不拖队", 100);
        mkMember(trip.getId(), aya.getId(), "PENDING", "摄影担当，可以帮忙出片，但体力一般求照顾", 100);

        // 另一支演示队伍（不同节奏/兴趣/校际范围）
        Trip tripAya = new Trip();
        tripAya.setCaptainId(aya.getId());
        tripAya.setTitle("长沙出发·凤凰古城2日美食慢游");
        tripAya.setDestination("凤凰古城");
        tripAya.setDepartAt(LocalDate.of(2026, 10, 18).atTime(9, 0));
        tripAya.setMaxMembers(4);
        tripAya.setBudget(450.0);
        tripAya.setTransport("BUS");
        tripAya.setMeetPoint("长沙汽车西站候车厅集合");
        tripAya.setDescription("古城慢逛＋沱江夜游，主打吃与拍，睡到自然醒，拒绝赶行程，适合想放松的同学。");
        tripAya.setRiskLevel("LOW");
        tripAya.setStatus("OPEN");
        tripAya.setPaceType("RELAXED");
        tripAya.setPrefDays(2);
        tripAya.setInterestTags("美食,古镇,摄影");
        tripAya.setSchoolScope("ALL");
        tripAya.setCreatedAt(LocalDateTime.now().minusDays(2));
        tripRepository.save(tripAya);
        mkMember(tripAya.getId(), xiaolu.getId(), "PENDING", "想跟着吃一圈，摄影可以出图", 100);

        Trip tripXiaolu = new Trip();
        tripXiaolu.setCaptainId(xiaolu.getId());
        tripXiaolu.setTitle("岳麓山Citywalk半日局｜后湖＋麓山南路小吃");
        tripXiaolu.setDestination("长沙岳麓山");
        tripXiaolu.setDepartAt(LocalDate.of(2026, 10, 12).atTime(14, 0));
        tripXiaolu.setMaxMembers(5);
        tripXiaolu.setBudget(120.0);
        tripXiaolu.setTransport("BIKE");
        tripXiaolu.setMeetPoint("中南大学南校区门口集合");
        tripXiaolu.setDescription("半日轻徒步＋校园周边扫街，重点是拍照和吃小吃，全程4小时左右，仅收本校同学。");
        tripXiaolu.setRiskLevel("LOW");
        tripXiaolu.setStatus("OPEN");
        tripXiaolu.setPaceType("BALANCED");
        tripXiaolu.setPrefDays(1);
        tripXiaolu.setInterestTags("Citywalk,美食,摄影");
        tripXiaolu.setSchoolScope("SAME");
        tripXiaolu.setCreatedAt(LocalDateTime.now().minusDays(1));
        tripRepository.save(tripXiaolu);

        // —— 结伴偏好（TripPref） ——
        TripPref prefDashan = new TripPref();
        prefDashan.setUserId(dashan.getId());
        prefDashan.setExpectDays(3);
        prefDashan.setPaceType("ENERGETIC");
        prefDashan.setInterestTags("徒步,登山,摄影");
        prefDashan.setBudget(900.0);
        prefDashan.setTransport("TRAIN");
        prefDashan.setOpenToCrossSchool(true);
        prefDashan.setUpdatedAt(LocalDateTime.now().minusDays(5));

        TripPref prefAya = new TripPref();
        prefAya.setUserId(aya.getId());
        prefAya.setExpectDays(2);
        prefAya.setPaceType("RELAXED");
        prefAya.setDestinationPref("凤凰");
        prefAya.setInterestTags("美食,古镇,摄影");
        prefAya.setBudget(500.0);
        prefAya.setTransport("BUS");
        prefAya.setOpenToCrossSchool(true);
        prefAya.setUpdatedAt(LocalDateTime.now().minusDays(3));

        TripPref prefXiaolu = new TripPref();
        prefXiaolu.setUserId(xiaolu.getId());
        prefXiaolu.setExpectDays(1);
        prefXiaolu.setPaceType("BALANCED");
        prefXiaolu.setInterestTags("Citywalk,美食,摄影");
        prefXiaolu.setBudget(300.0);
        prefXiaolu.setTransport("BIKE");
        prefXiaolu.setOpenToCrossSchool(true);
        prefXiaolu.setUpdatedAt(LocalDateTime.now().minusDays(1));

        tripPrefRepository.saveAll(List.of(prefDashan, prefAya, prefXiaolu));

        // ================= 紧急联系人与保险 =================
        EmergencyContact ec1 = new EmergencyContact();
        ec1.setUserId(xiaolu.getId());
        ec1.setName("麓妈妈");
        ec1.setRelation("PARENT");
        ec1.setPhone("13800001234");
        ec1.setAuthorized(true);
        ec1.setCreatedAt(LocalDateTime.now().minusDays(30));
        emergencyContactRepository.save(ec1);

        EmergencyContact ec2 = new EmergencyContact();
        ec2.setUserId(dashan.getId());
        ec2.setName("王辅导员");
        ec2.setRelation("COUNSELOR");
        ec2.setPhone("13900005678");
        ec2.setAuthorized(false);
        ec2.setCreatedAt(LocalDateTime.now().minusDays(20));
        emergencyContactRepository.save(ec2);

        InsurancePolicy policy = new InsurancePolicy();
        policy.setUserId(dashan.getId());
        policy.setTripId(trip.getId());
        policy.setPlanName("学旅出行意外险·畅享版");
        policy.setPolicyNo("XLV" + System.currentTimeMillis() +"528");
        policy.setStatus("EFFECTIVE");
        policy.setCoverageEnd(LocalDate.now().plusDays(30));
        policy.setCreatedAt(LocalDateTime.now().minusDays(2));
        insurancePolicyRepository.save(policy);

        // ================= 私信 =================
        msg(xiaolu, dashan, "大山队长，国庆张家界局还缺人吗？", LocalDateTime.now().minusHours(20));
        msg(dashan, xiaolu, "还缺2个，你有徒步鞋就行，高铁票记得自己先抢", LocalDateTime.now().minusHours(19));
        msg(xiaolu, dashan, "票已搞定！到时候东广场见～", LocalDateTime.now().minusHours(18));
        msg(aya, xiaolu, "你那篇长沙Citywalk攻略能不能转载到我们公众号？注明出处的", LocalDateTime.now().minusHours(6));
        msg(dashan, dao, "老道，3号到了联系你，野线那天请你带队", LocalDateTime.now().minusHours(3));

        // ================= 通知 =================
        notify(xiaolu, "TRIP", "报名已通过", "你报名的「国庆张家界徒步3日结伴」已被队长通过", trip.getId(), LocalDateTime.now().minusDays(2));
        notify(dashan, "TRIP", "新的组队报名", "阿雅 申请加入「国庆张家界徒步3日结伴」，快去审核", trip.getId(), LocalDateTime.now().minusHours(10));
        notify(xiaolu, "INTERACT", "您的攻略被引用", "您的帖子《张家界三天两夜徒步路线》被引用，博主积分+2", posts.get(1).getId(), LocalDateTime.now().minusDays(1));
        notify(aya, "AUDIT", "攻略审核中", "您的帖子《张家界本地向导教你玩天门山》已提交审核，请耐心等待", posts.get(12).getId(), LocalDateTime.now().minusHours(2));
        notify(shang, "TRADE", "您有新的订单", "店铺收到一笔新订单，请尽快发货", null, LocalDateTime.now().minusHours(30));
        notify(dao, "TRADE", "您有新的导游订单", "用户预订了「天门山玻璃栈道一日陪游」，平台已代收 ¥299", booking.getId(), LocalDateTime.now().minusDays(2));

        log.info("[学旅圈] 种子数据初始化完成：6用户 / 6圈层 / 14帖子 / 22景点 / 2路线 / 13交通方案 / 8商品 / 3导游服务 / 5广告");
    }

    // ================= helpers =================

    private User mkUser(String username, String pwd, String nickname, String role, String school,
                        String avatar, boolean verified, int credit, int level, int bloggerScore,
                        String bio, String interests) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(pwd);
        u.setNickname(nickname);
        u.setRole(role);
        u.setSchool(school);
        u.setAvatar(avatar);
        u.setVerified(verified);
        u.setBanned(false);
        u.setCreditScore(credit);
        u.setBloggerLevel(level);
        u.setBloggerScore(bloggerScore);
        u.setBio(bio);
        u.setInterests(interests);
        u.setCreatedAt(LocalDateTime.now().minusDays(30));
        return u;
    }

    private Circle mkCircle(String name, String theme, String desc, String icon, User creator) {
        Circle c = new Circle();
        c.setName(name);
        c.setTheme(theme);
        c.setDescription(desc);
        c.setIcon(icon);
        c.setCreatorId(creator.getId());
        c.setMemberCount(0);
        c.setCreatedAt(LocalDateTime.now().minusDays(28));
        return c;
    }

    private void join(Long circleId, Long userId) {
        CircleMember m = new CircleMember();
        m.setCircleId(circleId);
        m.setUserId(userId);
        m.setJoinedAt(LocalDateTime.now().minusDays(20));
        circleMemberRepository.save(m);
        Circle c = circleRepository.findById(circleId).orElseThrow();
        c.setMemberCount(c.getMemberCount() + 1);
        circleRepository.save(c);
    }

    private Post mkPost(User author, String type, String title, String content, String city,
                        String destination, String modeTag, Long circleId, String image,
                        boolean trusted, String status, int daysAgo,
                        int views, int likes, int favs, int cites) {
        Post p = new Post();
        p.setType(type);
        p.setTitle(title);
        p.setContent(content);
        p.setCity(city);
        p.setDestination(destination);
        p.setModeTag(modeTag);
        p.setCircleId(circleId);
        p.setImages(image);
        p.setAuthorId(author.getId());
        p.setTrustedBadge(trusted);
        p.setStatus(status);
        p.setViewCount(views);
        p.setLikeCount(likes);
        p.setFavoriteCount(favs);
        p.setCitationCount(cites);
        p.setCommentCount(0);
        p.setCreatedAt(LocalDateTime.now().minusDays(daysAgo).withHour(10).withMinute(30).withSecond(0).withNano(0));
        return postRepository.save(p);
    }

    private void mkComment(Post p, User u, String content) {
        Comment c = new Comment();
        c.setPostId(p.getId());
        c.setUserId(u.getId());
        c.setContent(content);
        c.setCreatedAt(LocalDateTime.now().minusHours(5));
        commentRepository.save(c);
    }

    private void like(User u, Post p) {
        interaction(u, p, "LIKE");
    }

    private void fav(User u, Post p) {
        interaction(u, p, "FAVORITE");
    }

    private void interaction(User u, Post p, String type) {
        Interaction i = new Interaction();
        i.setUserId(u.getId());
        i.setTargetId(p.getId());
        i.setType(type);
        i.setCreatedAt(LocalDateTime.now().minusHours(8));
        interactionRepository.save(i);
    }

    private void spot(String name, String category, String city, double lng, double lat,
                      String desc, String image, int heat, Map<String, Spot> bag) {
        Spot s = new Spot();
        s.setName(name);
        s.setCategory(category);
        s.setCity(city);
        s.setLng(lng);
        s.setLat(lat);
        s.setDescription(desc);
        s.setImage(image);
        s.setHeat(heat);
        bag.put(name, spotRepository.save(s));
    }

    @SuppressWarnings("unchecked")
    private void saveRoute(User author, String name, Map<String, Object> plan) {
        Route r = new Route();
        r.setAuthorId(author.getId());
        r.setName(name);
        List<Spot> routeSpots = (List<Spot>) plan.get("spots");
        r.setCity(routeSpots.isEmpty() ? "" : routeSpots.get(0).getCity());
        r.setMode((String) plan.get("mode"));
        r.setColor((String) plan.get("color"));
        r.setSpotIdsCsv(routeSpots.stream().map(s -> String.valueOf(s.getId())).reduce((a, b) -> a + "," + b).orElse(""));
        r.setTotalDistance(Double.valueOf(String.valueOf(plan.get("totalDistanceKm"))));
        r.setTotalDuration((Integer) plan.get("totalDurationMin"));
        r.setRiskLevel((String) plan.get("riskLevel"));
        r.setTips((String) plan.get("tips"));
        r.setCreatedAt(LocalDateTime.now().minusDays(5));
        routeRepository.save(r);
    }

    private void mkFootprint(User u, Spot s, LocalDate date, String photo, String note) {
        Footprint f = new Footprint();
        f.setUserId(u.getId());
        f.setSpotId(s.getId());
        f.setName(s.getName());
        f.setCity(s.getCity());
        f.setLng(s.getLng());
        f.setLat(s.getLat());
        f.setVisitedAt(date);
        f.setPhoto(photo);
        f.setNote(note);
        f.setCreatedAt(LocalDateTime.now().minusDays(2));
        footprintRepository.save(f);
    }

    private void transport(String origin, String dest, String mode, int duration, String price,
                           String freq, int comfort, int freedom, int safety, String note) {
        TransportOption t = new TransportOption();
        t.setOrigin(origin);
        t.setDestination(dest);
        t.setMode(mode);
        t.setDuration(String.valueOf(duration));
        t.setPrice(price);
        t.setFrequency(freq);
        t.setComfort(comfort);
        t.setFreedom(freedom);
        t.setSafety(safety);
        t.setNote(note);
        transportOptionRepository.save(t);
    }

    private void product(Merchant shop, String name, String category, double price,
                         int stock, int sales, String image, String desc) {
        Product p = new Product();
        p.setMerchantId(shop.getId());
        p.setName(name);
        p.setCategory(category);
        p.setPrice(price);
        p.setStock(stock);
        p.setSales(sales);
        p.setImage(image);
        p.setDescription(desc);
        p.setCreatedAt(LocalDateTime.now().minusDays(20));
        productRepository.save(p);
    }

    private GuideService guideService(User guide, String title, String destination, String themes,
                                      double price, String schedule, String intro) {
        GuideService s = new GuideService();
        s.setGuideId(guide.getId());
        s.setTitle(title);
        s.setDestination(destination);
        s.setThemes(themes);
        s.setPrice(price);
        s.setSchedule(schedule);
        s.setIntro(intro);
        s.setStatus("APPROVED");
        s.setCreatedAt(LocalDateTime.now().minusDays(20));
        return s;
    }

    private void ad(String slot, String title, String image, String link, String advertiser,
                    String city, String status, double price, int views, int clicks,
                    LocalDateTime start, LocalDateTime end) {
        Ad a = new Ad();
        a.setSlot(slot);
        a.setTitle(title);
        a.setImage(image);
        a.setLink(link);
        a.setAdvertiser(advertiser);
        a.setTargetCity(city);
        a.setStatus(status);
        a.setPrice(price);
        a.setViewCount(views);
        a.setClickCount(clicks);
        a.setStartAt(start);
        a.setEndAt(end);
        a.setCreatedAt(LocalDateTime.now().minusDays(10));
        adRepository.save(a);
    }

    private void mkMember(Long tripId, Long userId, String status, String reason, int credit) {
        TripMember m = new TripMember();
        m.setTripId(tripId);
        m.setUserId(userId);
        m.setStatus(status);
        m.setReason(reason);
        m.setCreditSnapshot(credit);
        m.setCreatedAt(LocalDateTime.now().minusDays(2));
        tripMemberRepository.save(m);
    }

    private void msg(User from, User to, String content, LocalDateTime time) {
        Message m = new Message();
        m.setFromId(from.getId());
        m.setToId(to.getId());
        m.setContent(content);
        m.setMsgType("CHAT");
        m.setReadFlag(false);
        m.setCreatedAt(time);
        messageRepository.save(m);
    }

    private void notify(User u, String type, String title, String content, Long refId, LocalDateTime time) {
        Notification n = new Notification();
        n.setUserId(u.getId());
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefId(refId);
        n.setReadFlag(false);
        n.setCreatedAt(time);
        notificationRepository.save(n);
    }
}
