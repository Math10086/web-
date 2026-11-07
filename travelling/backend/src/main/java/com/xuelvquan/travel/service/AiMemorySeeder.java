package com.xuelvquan.travel.service;

import com.xuelvquan.travel.entity.AiChatMessage;
import com.xuelvquan.travel.entity.AiUserMemory;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.AiChatMessageRepository;
import com.xuelvquan.travel.repository.AiUserMemoryRepository;
import com.xuelvquan.travel.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI记忆种子器：为每个账号预置10轮AI对话+用户画像确认摘要。
 *
 * 这样演示时，任何一个账号打开AI搭子/AI聊天页，
 * 都能看到一段已经养熟的对话历史与画像摘要（chatCount=10），不用现场真聊10轮。
 *
 * 幂等：只对ai_chat_messages里一条记录都没有的账号播种；
 * 聊过天的账号一律跳过，绝不覆盖真实对话。时间戳向前推2天、
 * 每轮间隔几分钟，保证「最近对话」排序自然。
 */
@Slf4j
@Component
@Order(40)
@RequiredArgsConstructor
public class AiMemorySeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AiChatMessageRepository chatMessageRepository;
    private final AiUserMemoryRepository memoryRepository;

    /** 一轮对话：用户一句+AI一句 */
    private record Round(String q, String a) {}

    @Override
    public void run(String... args) {
        int seededUsers = 0;
        int seededMessages = 0;
        for (User u : userRepository.findAll()) {
            // 幂等：聊过天的账号不播种，绝不覆盖真实对话
            if (!chatMessageRepository.findByUserIdOrderByCreatedAtAscIdAsc(u.getId()).isEmpty()) {
                continue;
            }
            List<Round> rounds = dialogFor(u);
            LocalDateTime t = LocalDateTime.now().minusDays(2);
            for (Round r : rounds) {
                t = t.plusMinutes(7);
                chatMessageRepository.save(msg(u.getId(), "user", r.q(), null, t));
                t = t.plusMinutes(2);
                chatMessageRepository.save(msg(u.getId(), "assistant", r.a(), "ollama", t));
            }
            AiUserMemory mem = memoryRepository.findByUserId(u.getId()).orElseGet(AiUserMemory::new);
            mem.setUserId(u.getId());
            mem.setMemorySummary(summaryFor(u));
            mem.setChatCount(rounds.size());
            mem.setUpdatedAt(LocalDateTime.now());
            memoryRepository.save(mem);
            seededUsers++;
        }
        if (seededUsers > 0) {
            log.info("[学旅圈] AI 记忆播种：为 {} 个账号各预置10轮对话与画像确认（已有对话的账号自动跳过）", seededUsers);
        }
    }

    private AiChatMessage msg(Long userId, String role, String content, String source, LocalDateTime t) {
        AiChatMessage m = new AiChatMessage();
        m.setUserId(userId);
        m.setRole(role);
        m.setContent(content);
        m.setSource(source);
        m.setCreatedAt(t);
        return m;
    }

    /**
     * 按账号/角色生成10轮个性化对话（覆盖：画像初步 → 偏好确认 → 功能使用 → 画像收尾）
     * @param u 待播种的用户
     * @return List 该账号对应的10轮问答
     */
    private List<Round> dialogFor(User u) {
        return switch (u.getUsername()) {
            case "xiaolu" -> xiaolu();
            case "aya" -> aya();
            case "dashan" -> dashan();
            case "dao" -> dao();
            case "shang" -> shang();
            case "admin" -> adminRounds();
            // 其他自定义账号：按角色给一套通用对话
            default -> generic(u.getNickname() == null ? "同学" : u.getNickname(), u.getRole());
        };
    }

    /* ---------- 小麓 · 攻略博主（爱拍照，内容创作向） ---------- */
    private List<Round> xiaolu() {
        return List.of(
            new Round("你好呀，我是写旅游攻略的，想让你帮我规划内容选题",
                "你好小麓！知道你是攻略博主后，我会偏向给你「有画面感、有传播点」的建议。想先从哪个城市开始？"),
            new Round("长沙适合拍照的地方有哪些？",
                "长沙出片率高的点位：岳麓山爱晚亭（秋色）、谢子龙影像艺术馆（白墙极简风）、后湖艺术园（文艺感）、IFS 国金中心楼顶打卡。建议清晨去爱晚亭，光线柔和人又少。"),
            new Round("我想做一期「两天一夜长沙深度游」的攻略，路线怎么排？",
                "D1：五一广场 → 太平街 → 坡子街火宫殿（市井烟火 + 小吃）；D2：岳麓山爱晚亭 → 岳麓书院 → 谢子龙影像馆（从历史到现代收尾）。这条线节奏松紧交替，很适合两天一夜。"),
            new Round("帮我把这个路线里的网红店标注一下，攻略里要写探店",
                "已帮你标注：茶颜悦色（太平街店）、超级文和友（海信广场）、黑色经典臭豆腐（坡子街）。攻略里建议配上排队时段提示，读者会很需要。"),
            new Round("我的读者主要是大学生，预算有限的那种",
                "记住了：你的读者画像是「预算有限的大学生」。那我会优先推荐人均30以内的吃住组合，比如青旅 + 地铁沿线景点，门票侧重免费点位（岳麓山、湘江风光带都免费）。"),
            new Round("结伴功能怎么用才能给我的帖子引流？",
                "两条路：① 发攻略时挂上结伴链接，读者一键加入你的队伍；② 在结伴广场发起「周末Citywalk」主题队，队伍页会展示你的帖子。双向导流效果最好。"),
            new Round("帮我看看上个月我的帖子数据怎么样",
                "从创作中心看：你的「长沙citywalk路线」阅读量最高，互动集中在晚餐时段发布的内容。建议固定晚上8点左右发布，符合学生刷手机的习惯。"),
            new Round("冬天有什么适合拍雪景的周边目的地？",
                "长沙周边雪景首推张家界（12月-1月雪凇绝美）和衡山（雾凇 + 日出）。提醒你的读者带防滑鞋套，山路结冰。要不要我帮你排一版张家界两日拍摄路线？"),
            new Round("好呀，那你记住我的偏好了吗？总结一下",
                "当然！你的画像我已经记住了：攻略博主小麓，读者是预算有限的大学生，偏爱「拍照点位 + 探店 + 避峰时段」类内容，倾向免费/低成本点位，关注冬季雪景选题。之后每次建议我都会按这个口径来。"),
            new Round("不错，以后就按这个方向给我出主意",
                "没问题！以后我给你的建议都会默认：大学生预算口径 + 出片优先 + 附带避峰时段。随时来找我更新选题～")
        );
    }

    /* ---------- 阿雅 · 博主（美食与文艺向） ---------- */
    private List<Round> aya() {
        return List.of(
            new Round("嗨，我是阿雅，主要写美食探店和文艺小众路线",
                "阿雅你好！美食 + 文艺的组合我很喜欢。以后给你推荐时我会侧重「苍蝇馆子 + 展览 + 老街区」这条线。"),
            new Round("长沙本地人常去的小吃街是哪条？不要游客向的",
                "本地人去的多是四方坪夜市和扬帆夜市：口味虾、糖油粑粑、紫苏桃子姜都正宗。坡子街偏游客，你可以写一篇「本地人不会告诉你的夜市地图」做差异化。"),
            new Round("帮我规划一条「一天吃遍长沙」的路线",
                "早上：公交新村的粉店（肉丝粉开胃）；中午：四方坪夜市提前踩点补拍素材；下午：后湖艺术园消食拍照；晚上：扬帆夜市正式扫街。一天四站，荤素冷热都齐了。"),
            new Round("我想在帖子里加消费明细，学生党最关心这个",
                "好主意！按我给的路线：粉12+ 夜市小吃40+ 咖啡25+ 晚市50 ≈ 人均130。你可以在消费报表里核对真实支出，数据比我估的更有说服力。"),
            new Round("有没有适合雨天backup的室内路线？",
                "雨天路线：湖南省博物馆（免费预约）→ IFS 逛吃 → 谢子龙影像馆。三站全是室内，动线上顺路。"),
            new Round("杭州和成都哪个更适合我下一篇？",
                "按你的定位，成都更合适：小众咖啡密度高、苍蝇馆子多、文艺街区（玉林、泡桐树街）出片。杭州偏风景向，和你的美食主线契合度低一点。"),
            new Round("帮我留意一下成都的选题灵感",
                "已记住：你下阶段的主攻方向是成都美食 + 文艺路线。我建议的三个选题：玉林路小酒馆巡礼、建设路小吃巷对比测评、人民公园鹤鸣茶社的慢生活。"),
            new Round("成都的帖子什么时候发比较好？",
                "美食内容周六上午11点发布最好——用户正在决定中午吃什么；文艺路线晚上9点发，睡前刷手机的人最容易收藏。两条线错开发，一周两更节奏刚好。"),
            new Round("总结一下你对我的了解吧",
                "你的画像：博主阿雅，美食探店 + 文艺小众双主线，重视消费明细与学生党视角，避开游客向点位，下一篇主攻成都。之后推荐我都按这个来。"),
            new Round("很好，就这样",
                "收到！祝成都之行素材满满，有新灵感随时来聊～")
        );
    }

    /* ---------- 大山队长 · 普通用户（结伴组织者） ---------- */
    private List<Round> dashan() {
        return List.of(
            new Round("你好，我经常在平台上组织结伴出游",
                "大山队长你好！组织者角色的重点我记下了：以后会多给你队伍管理、行程节奏和安全方面的建议。"),
            new Round("帮我策划一次岳麓山的徒步结伴活动",
                "建议周六早上8:30东大门集合，走爱晚亭 → 索道上站 → 电视塔小环线，全程约3小时，强度适中，适合20-30人队伍。记得设2个收队岗。"),
            new Round("20个人的队伍吃饭怎么安排？",
                "山脚下枫林路的农家菜馆可以包桌，人均35-40。建议提前一天订位并收定金，临时放鸽子是大队伍最常见的翻车点。"),
            new Round("下雨了怎么办，有备案吗？",
                "雨天备案：改走湖南省森林植物园（林荫大道 + 温室）或湖南省博物馆，都是雨天可玩性高的点位。发公告时记得说明改期规则。"),
            new Round("怎么在平台上快速招人？",
                "三个技巧：① 队伍标题带具体日期和强度（「11月2日岳麓山轻徒步」比「周末爬山」报名率高很多）；② 首图放上期活动合照；③ 设置报名截止时间制造节奏。"),
            new Round("上次的队伍有人放鸽子，有什么办法？",
                "平台有信用分体系：报名后爽约会扣信用分，信用低的用户报名需要你审核。你发布队伍时可以开启「仅限信用分达标者报名」，能有效过滤。"),
            new Round("下一站想去张家界，两天怎么排？",
                "D1：森林公园（金鞭溪 → 百龙天梯 → 袁家界）；D2：天门山（玻璃栈道 + 通天大道）。张家界山高路陡，大队伍建议分4-5人小队行动，指定每队队长。"),
            new Round("预算人均多少合适？",
                "张家界两天人均600-750（含门票 + 山上交通 + 标间拼房 + 餐费）。学生党可以坐火车硬座再省80左右。"),
            new Round("帮我记一下我们的队伍偏好，以后都用得上",
                "已记住：你组织的队伍以「周末短途轻徒步」为主，规模15-30人，重视信用管理和雨天备案，预算控制在人均300以内（省内两日700以内）。之后策划我都会按这个口径出方案。"),
            new Round("好，下次活动前我来找你对方案",
                "随时欢迎！提前一周来找我，我帮你把集合时间、路线强度、雨天备案一次排好。")
        );
    }

    /* ---------- 老道 · 导游（带团+经营向） ---------- */
    private List<Round> dao() {
        return List.of(
            new Round("你好，我是持证导游，想用你帮我优化带团安排",
                "老道你好！导游视角我记下了：以后建议会围绕「避峰时段、带队节奏、向导服务经营」三个重点。"),
            new Round("周五带一个40人团去橘子洲，几点最合适？",
                "周五9:00前入园最稳：团队巴士8:30前能停到指定车位，9点后旅行团密集排队。下午2点后观光小火车要排40分钟以上，尽量上午完成主游线。"),
            new Round("40人的团在景区怎么防走散？",
                "三招：① 分8组每组5人，指定组长并互加平台联系人；② 用平台的位置共享功能绑定全团；③ 约定「走散就地不动」原则，10分钟内组长上报。"),
            new Round("十一黄金周带团有什么避坑建议？",
                "黄金周铁律：酒店提前3周锁房、门票锁定预约时段、每天发车比计划提前20分钟。行程上砍掉1个点留缓冲，比赶5个点体验好得多。"),
            new Round("我的向导服务在平台上曝光不高，怎么提升？",
                "三个动作：① 服务标题写清差异化（「金牌讲解 + 耳麦」，别只写「长沙一日游」）；② 首图换成你带团的真实照片；③ 请游客带团后写评价，前10条评价决定转化。"),
            new Round("需求洞察里哪个城市缺口最大？",
                "按平台供需数据：西安、重庆的高缺口城市靠前——需求热度高但在售向导服务少。如果你有资源，挂这两个城市的服务曝光会更好。"),
            new Round("帮我看看下个月适合推什么主题线路",
                "下个月入秋：推荐「岳麓山红叶 + 湘江风光带落日」和「张家界秋色两日」两个主题。需求洞察显示秋季户外主题的队伍兴趣环比上升。"),
            new Round("导游证年审和平台认证的关系帮我理一下",
                "平台认证查的是你的导游资质（证号 + 从业状态），年审由文旅部门管。平台这边只要资质有效就能接单；若年审期间证件状态变化，记得更新平台资料避免接单受限。"),
            new Round("把我的带队偏好记下来吧",
                "已记住：持证导游老道，主做30-50人大团，重视避峰时段、防走散机制和黄金周缓冲设计，同时在经营平台向导服务（差异化标题 + 评价积累）。之后建议都按这个口径。"),
            new Round("好，以后团期排班都先来问你",
                "没问题！发团前来报日期和人数，我帮你出避峰方案 + 应急预案。")
        );
    }

    /* ---------- 山野老板 · 商家（选品经营向） ---------- */
    private List<Round> shang() {
        return List.of(
            new Round("你好，我在平台开了家户外用品店",
                "山野老板你好！商家视角已记住：以后建议围绕「选品、定价、投放」来给。"),
            new Round("现在学生出游最常买什么？",
                "按平台消费画像：露营垫、便携椅、一次性雨衣、登山杖是队伍出行前加购率最高的四类。轻量化 + 单价50以内最好走量。"),
            new Round("怎么判断该不该补货某个品？",
                "看商户中心的热力图：销量高且评分高的品优先补；销量高但退货多的先查质量。缺口榜也能看需求趋势——结伴队伍集中的城市，对应装备需求会先起量。"),
            new Round("广告投在哪最划算？",
                "投「结伴广场」和「地图路线页」的广告位转化最好——用户正处在决策链路上。避开首页通栏，曝光高但意图弱。"),
            new Round("定价有什么建议？学生砍价狠",
                "锚定「人均一杯奶茶钱」的话术：39、49、59的心理档位比40、50、60转化好。再配「队伍满5人95折」，把组织者变成你的分销节点。"),
            new Round("双十一平台有什么活动位？",
                "平台会在双11前两周开放「出游装备专场」招商，商家中心可报名。建议提前备货轻量化品类，历年这个专场户外品类增速最高。"),
            new Round("帮我看看竞品在卖什么价",
                "商户中心的品类均价榜可以看同类目分布：目前登山杖主流价29-49，露营垫39-69。你定价卡在区间中位 + 差异化赠品，比硬拼低价更稳。"),
            new Round("旺季是什么时候要提前备货？",
                "三个波峰：3-4月春游、7-8月暑期、9-10月秋游。提前3-4周备货，其中9月开学季的「新生周边游」增量最猛。"),
            new Round("总结一下我的经营画像",
                "你的画像：户外用品商家山野老板，主抓轻量化学生装备，打法是心理档位定价 + 队伍满减 + 决策链路广告位，备货节奏跟三波峰走。之后分析都按这个来。"),
            new Round("可以，就按这个来",
                "收到！大促或上新前来找我，我帮你对着供需数据再过一遍选品。")
        );
    }

    /* ---------- admin · 平台管理员 ---------- */
    private List<Round> adminRounds() {
        return List.of(
            new Round("你好，我是平台管理员，看看你能帮我做什么",
                "管理员你好！我可以帮你解读平台数据、梳理治理规则、准备答辩演示口径。想先看哪块？"),
            new Round("平台现在的核心数据指标有哪些？",
                "四大块：用户侧（注册/活跃/信用分）、内容侧（帖子/攻略/互动率）、交易侧（订单/GMV/退款）、AI 侧（路线规划次数/AI 对话轮数）。统计报表页都有现成图。"),
            new Round("算法这块答辩时怎么讲比较稳？",
                "讲三层：① 路线算法（K-means 分天 + 最近邻 + 2-opt 优化）；② 个性化（靠画像摘要维持长期记忆，不是微调）；③ 热度合成（真实行为加权 + 时间衰减 + 样本量合规门槛）。每层都有落地代码和实测数据支撑。"),
            new Round("内容治理有什么自动化的做法？",
                "举报 → 信用分联动 → 自动降权是主链路：帖子被举报核实后，作者信用分扣减，低信用内容自动降权。管理员后台只处理申诉和复核。"),
            new Round("怎么向老师演示「数据是真实的」？",
                "两个抓手：① 热度合成有口径公式和行为明细，不是拍脑袋填的数；② 样本量门槛会隐藏不达标的目的地——宁可展示少而真实，也不造假。这是隐私合规的加分项。"),
            new Round("AI 功能的边界在哪？会不会胡说？",
                "有三道护栏：路线类回答以确定性算法结果为准，LLM 只负责解释；解释层有天数字、矛盾表述的一致性校验，违规就退回确定性文案；所有建议标注数据来源。"),
            new Round("如果评委问高并发怎么办？",
                "如实答当前规模：课程项目，单机部署，MySQL + 连接池。设计上接口无状态（JWT）可水平扩容，AI 推理走独立通道不影响核心链路。诚实 + 有思路比吹牛安全。"),
            new Round("记一下我的关注点吧",
                "已记住：你关注答辩口径（算法三层、数据真实性、AI 护栏）和平台治理（信用分联动、自动降权）。之后解读数据我会往这两个方向倾斜。"),
            new Round("演示前帮我最后梳理一遍亮点",
                "答辩主线建议：AI 对话式改路线（点名地点 + 轮次偏移）→ 全国热力地图（合规口径）→ 桑基消费流转 → 极坐标变种图。每个点都对应真实现场可操作的功能。"),
            new Round("好，就这样安排",
                "收到！预祝答辩顺利，需要临场口径随时来找我。")
        );
    }

    /* ---------- 其他自定义账号：按角色生成通用10轮 ---------- */
    private List<Round> generic(String nick, String role) {
        String identity = switch (role == null ? "USER" : role) {
            case "GUIDE" -> "导游";
            case "MERCHANT" -> "商家";
            case "BLOGGER" -> "攻略博主";
            default -> "热爱旅行的同学";
        };
        List<Round> list = new ArrayList<>();
        Map<Integer, String[]> base = Map.ofEntries(
            Map.entry(0, new String[]{"你好，我是" + nick + "，一个" + identity,
                    "你好" + nick + "！知道你的身份后，我的建议会按" + identity +"的需要来定制。先聊聊你最近的出行计划？"}),
            Map.entry(1, new String[]{"长沙两天怎么玩比较顺？",
                    "经典顺路版：D1五一广场 → 太平街 → 坡子街；D2岳麓山爱晚亭 → 岳麓书院 → 湘江风光带看落日。节奏不赶，还能留出吃小吃的时间。"}),
            Map.entry(2, new String[]{"预算大概多少合适？",
                    "学生党口径：两天人均350-450（青旅/快捷酒店 + 公共交通 + 小吃正餐混合）。住宿选地铁2号线沿线最方便。"}),
            Map.entry(3, new String[]{"帮我看看天气适合哪天出门",
                    "出行前三天来问我，我会结合天气给建议：雨天自动换成博物馆 + 室内展馆动线，晴天优先户外和江边日落。"}),
            Map.entry(4, new String[]{"结伴功能怎么找到合适队友？",
                    "在结伴广场按「目的地 + 日期」筛队伍，报名前看队长信用分和历史队伍评价。也可以自己发队，标题写清日期和强度更容易招到人。"}),
            Map.entry(5, new String[]{"我的消费记录能帮你分析吗？",
                    "能！消费报表页可以看类目占比和人均区间。授权后我会结合你的消费习惯，推荐时自动控制在你舒服的预算带里。"}),
            Map.entry(6, new String[]{"推荐几个小众点的拍照地方",
                    "小众点位：后湖国际艺术园、谢子龙影像艺术馆外景、西湖公园白鹭岛。都不收费，工作日去基本不用排队。"}),
            Map.entry(7, new String[]{"路线规划结果能直接存成行程吗？",
                    "可以，规划页一键保存为行程，之后在行程页能按天打卡，走过的点会自动进足迹，年底还能生成你的年度旅行报告。"}),
            Map.entry(8, new String[]{"总结一下你对我的了解吧",
                    "你的画像：" + identity + nick + "，近期以长沙周边短途为主，预算敏感、喜欢小众点位、重视行程顺路度。之后推荐我都按这个口径来。"}),
            Map.entry(9, new String[]{"很好，以后就这样给我建议",
                    "没问题！你的偏好我已经记住，随时来更新计划～"})
        );
        for (int i = 0; i < 10; i++) {
            String[] pair = base.get(i);
            list.add(new Round(pair[0], pair[1]));
        }
        return list;
    }

    /**
     * 每个账号的「用户画像确认」摘要（写入ai_user_memories.memory_summary）
     * @param u 待生成摘要的用户
     * @return String 一段中文画像摘要
     */
    private String summaryFor(User u) {
        return switch (u.getUsername()) {
            case "xiaolu" -> "攻略博主小麓（中南大学）：读者为预算有限的大学生；内容主线是拍照点位 + 探店 + 避峰时段，倾向免费/低成本点位；关注冬季雪景选题；发布习惯为晚间8点。沟通口径：建议附出片点位与排队时段提示。（画像已经10轮对话确认）";
            case "aya" -> "博主阿雅（湖南师大）：美食探店 + 文艺小众双主线，避开游客向点位；重视消费明细与学生党视角；本地夜市（四方坪/扬帆）优先于游客街区；下一篇主攻成都（玉林路、建设路、鹤鸣茶社方向）。（画像已经10轮对话确认）";
            case "dashan" -> "结伴队长「大山队长」（湖南大学）：常组织15-30人周末轻徒步；重视信用管理（仅信用达标者报名）、雨天备案与收队机制；省内两日预算人均700以内；下一站张家界。（画像已经10轮对话确认）";
            case "dao" -> "持证导游「老道」：主做30-50人大团，核心诉求为避峰时段、防走散机制（分组 + 位置共享）与黄金周缓冲设计；同时经营平台向导服务，关注西安/重庆高缺口城市与秋季主题线路。（画像已经10轮对话确认）";
            case "shang" -> "户外用品商家「山野老板」：主抓轻量化学生装备（露营垫/便携椅/雨衣/登山杖）；打法为心理档位定价 + 队伍满5人95折 + 结伴/路线页广告位；备货跟3-4月、7-8月、9-10月三波峰。（画像已经10轮对话确认）";
            case "admin" -> "平台管理员：关注答辩口径（算法三层、数据真实性、AI 护栏、高并发答法）与治理机制（信用分联动、自动降权）；演示主线为对话式改路线 → 热力地图 → 桑基流转 → 极坐标图。（画像已经10轮对话确认）";
            default -> (u.getNickname() == null ? u.getUsername() : u.getNickname())
                + "：" + (u.getRole() == null ? "普通用户" : u.getRole())
                + "；近期以长沙周边短途为主，预算敏感、偏好小众免费点位、重视行程顺路度与天气适配。（画像已经10轮对话确认）";
        };
    }
}
