package com.xuelvquan.travel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型意图解析服务：将用户一句话的自然语言行程需求解析为结构化条件（城市、天数、模式、预算、出发时间等）。
 * 优先调用本地Ollama或云端大模型；无法解析时由调用方降级为本地关键词规则。
 */
@Slf4j
@Service
public class AiIntentService {

    /** 游玩模式取值，与前端utils/format.js的ROUTE_MODE_MAP的key一一对应 */
    private static final List<String> MODES =
            List.of("OPTIMAL", "SPEED", "LEISURE", "FOOD", "SCENIC", "WILD", "CITYWALK");

    /** 预算档位取值，与前端utils/format.js的BUDGETS的value一一对应 */
    private static final List<String> BUDGETS =
            List.of("NONE", "LE1000", "LE1500", "LE2000", "LE3000");

    /** 时长提示取值 */
    private static final List<String> DURATIONS = List.of("HALF", "AFTERNOON", "FULL", "HOURS");

    /** 天数上限，与前端MAX_TRIP_DAYS一致 */
    private static final int MAX_DAYS = 7;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${xlvq.ai.enabled:true}")
    private boolean enabled;

    @Value("${xlvq.ai.base-url:}")
    private String baseUrl;

    @Value("${xlvq.ai.api-key:}")
    private String apiKey;

    @Value("${xlvq.ai.model:glm-4-flash}")
    private String model;

    @Value("${xlvq.ai.timeout-seconds:12}")
    private int timeoutSeconds;

    // ===== 本地Ollama（无key也能做语义解析，离线可用）=====
    @Value("${xlvq.ai.ollama.enabled:true}")
    private boolean ollamaEnabled;

    @Value("${xlvq.ai.ollama.base-url:http://127.0.0.1:11434}")
    private String ollamaBaseUrl;

    @Value("${xlvq.ai.ollama.model:qwen2.5:7b}")
    private String ollamaModel;

    @Value("${xlvq.ai.ollama.timeout-seconds:90}")
    private int ollamaTimeoutSeconds;

    /** 云端是否可用（有key才算） */
    private boolean cloudReady() {
        return enabled && notBlank(baseUrl) && notBlank(apiKey) && notBlank(model);
    }

    /**
     * 是否已具备语义解析能力：本地Ollama或云端任意一个可用即为true。
     */
    public boolean isReady() {
        return ollamaEnabled || cloudReady();
    }

    /** 当前解析引擎名（前端展示用） */
    public String engineName() {
        if (ollamaEnabled) return "ollama:" + ollamaModel;
        if (cloudReady()) return "cloud:" + model;
        return "rule";
    }

    public String getModel() {
        return model;
    }

    /** 以base-url推断厂商名，仅用于前端展示 */
    public String getProvider() {
        String u = baseUrl == null ? "" : baseUrl;
        if (u.contains("bigmodel")) return "智谱 GLM";
        if (u.contains("siliconflow")) return "硅基流动";
        if (u.contains("deepseek")) return "DeepSeek";
        if (u.contains("dashscope")) return "通义千问";
        if (u.contains("openai.com")) return "OpenAI";
        return "自定义（OpenAI 兼容）";
    }

    /**
     * 调大模型把一句话解析成结构化行程条件。
     *
     * @param text   用户原话
     * @param cities 平台已有景点库里的城市列表（模型只能从中挑，避免推出不存在的城市）
     * @return 命中的字段（至少含source=llm）+ 一句模型回复；任何异常返回null表示"降级"
     */
    /** 兼容旧调用：不提供候选景点名 */
    public Map<String, Object> parseIntent(String text, List<String> cities) {
        return parseIntent(text, cities, List.of());
    }

    /**
     * 调大模型把一句话解析成结构化行程条件（本地Ollama优先，云端兜底）。
     *
     * @param text      用户原话
     * @param cities    平台已有景点库里的城市列表（模型只能从中挑，避免推出不存在的城市）
     * @param spotNames 平台景点名列表（帮模型识别「省博物馆」「橘子洲」这类具体地点）
     * @return 命中的字段（至少含engine）+ 一句模型回复；模型都不可用/解析失败返回null
     */
    public Map<String, Object> parseIntent(String text, List<String> cities, List<String> spotNames) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String prompt = buildSystemPrompt(cities, spotNames);

        // 本地Ollama优先：免费、离线、无需key
        if (ollamaEnabled) {
            try {
                String content = callChat(ollamaChatUrl(), ollamaModel, "ollama", prompt, text,
                        Math.min(ollamaTimeoutSeconds, 45), 0.05);
                Map<String, Object> hit = extract(content, cities);
                if (hit != null) {
                    hit.put("engine", "ollama");
                    log.info("[学旅圈] Ollama 意图解析：{} → {}", text, hit);
                    return hit;
                }
            } catch (Exception e) {
                log.warn("[学旅圈] Ollama 意图解析失败，尝试云端：{}", e.getMessage());
            }
        }

        // 云端OpenAI兼容接口兜底
        if (cloudReady()) {
            try {
                String content = callChat(trimEnd(baseUrl) + "/chat/completions", model,
                        "Bearer " + apiKey, prompt, text, timeoutSeconds, 0.1);
                Map<String, Object> hit = extract(content, cities);
                if (hit != null) {
                    hit.put("engine", "cloud");
                    log.info("[学旅圈] 云端意图解析：{} → {}", text, hit);
                    return hit;
                }
            } catch (Exception e) {
                log.warn("[学旅圈] 云端意图解析失败，降级为本地规则：{}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 把模型返回的JSON校验后转成命中条件；不是合法JSON返回null
     * @param content 模型返回的原始文本
     * @param cities 平台已有城市列表，用于校验模型给出的城市
     * @return Map 校验后的命中条件；内容为空或不是合法JSON时返回null
     */
    private Map<String, Object> extract(String content, List<String> cities) {
        if (content == null || content.isBlank()) {
            return null;
        }
        JsonNode node = readJsonLoose(content);
        if (node == null) {
            log.warn("[学旅圈] 大模型返回内容无法解析为 JSON：{}", content);
            return null;
        }

        Map<String, Object> hit = new LinkedHashMap<>();

        // 城市：必须存在于景点库，否则丢弃（防幻觉）
        String city = textOf(node, "city");
        if (city != null && cities != null && cities.contains(city)) {
            hit.put("city", city);
        }

        // 天数：只在1..MAX_DAYS之间才采纳，越界一律丢弃
        Integer days = intOf(node, "tripDays");
        if (days != null && days >= 1 && days <= MAX_DAYS) {
            hit.put("tripDays", days);
        }

        // 模式/预算：只能是固定枚举
        String mode = textOf(node, "mode");
        if (mode != null && MODES.contains(mode.toUpperCase())) {
            hit.put("mode", mode.toUpperCase());
        }
        String budget = textOf(node, "budgetLevel");
        if (budget != null && BUDGETS.contains(budget.toUpperCase())) {
            hit.put("budgetLevel", budget.toUpperCase());
        }

        // 出发时间：必须是合法的24小时制HH:mm，越界（如25:99）丢弃
        String st = textOf(node, "startTime");
        if (st != null && st.matches("\\d{1,2}:\\d{2}")) {
            String[] p = st.split(":");
            int h = Integer.parseInt(p[0]);
            int m = Integer.parseInt(p[1]);
            if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
                hit.put("startTime", String.format("%02d:%02d", h, m));
            }
        }

        // 时长提示：HALF(半天) / AFTERNOON(下午半天) / FULL(全天) / HOURS(几小时)
        String dur = textOf(node, "durationHint");
        if (dur != null && DURATIONS.contains(dur.toUpperCase())) {
            hit.put("durationHint", dur.toUpperCase());
        }

        // 用户点名的具体景点（数组，最多4个）
        JsonNode sn = node.get("spotNames");
        if (sn != null && sn.isArray() && !sn.isEmpty()) {
            List<String> names = new java.util.ArrayList<>();
            sn.forEach(n -> {
                String s = n.asText("").trim();
                if (!s.isEmpty() && s.length() <= 20) names.add(s);
            });
            if (!names.isEmpty()) {
                hit.put("spotNames", names.subList(0, Math.min(4, names.size())));
            }
        }

        // 同行人数
        Integer companions = intOf(node, "companions");
        if (companions != null && companions >= 1 && companions <= 20) {
            hit.put("companions", companions);
        }

        // 出发地点（自由文本，仅用于回复文案与展示）
        String startPoint = textOf(node, "startPoint");
        if (startPoint != null && startPoint.length() <= 40) {
            hit.put("startPoint", startPoint);
        }

        // 模型的一句确认/追问语（信息不全时用它给用户自然的口语回应）
        String reply = textOf(node, "reply");
        if (reply != null && !reply.isBlank()) {
            hit.put("reply", reply);
        }
        return hit;
    }


    /**
     * 构造system提示词：约束输出格式，并把可选枚举/城市/景点列表喂给模型
     * @param cities 候选城市列表，模型只能从中挑选
     * @param spotNames 候选景点名列表，用于把用户口语对应到真实景点
     * @return String 拼接好的系统提示词
     */
    private String buildSystemPrompt(List<String> cities, List<String> spotNames) {
        String cityList = (cities == null || cities.isEmpty()) ? "（暂无）" : String.join("、", cities);
        String spotList = (spotNames == null || spotNames.isEmpty()) ? "（暂无）" : String.join("、", spotNames);
        return """
                你是「学旅圈」旅游平台的行程条件解析器，服务对象是中国大学生。
                用户会用很随意、口语化的方式说话（可能只提一个场馆名、可能只说"想出去玩半天"）。
                请理解 TA 的真实意图，抽取旅行条件。只输出一个 JSON 对象，不要任何解释文字，
                不要用 markdown 代码块包裹。

                JSON 字段（没提到、也推断不出来的字段一律填 null，不要编造）：
                - city: 目的地城市。只能从候选城市里选。若用户只说了某个具体的景点/场馆
                  （例如"省博物馆"），请到候选景点列表里找最接近的一个，用它的城市填这里。
                  候选城市：%s
                - spotNames: 用户明确点名的景点/场馆名（字符串数组，最多4个）。用户没点名就填 []。
                  例：["省博物馆","橘子洲"]。允许填用户原话，不用非得和候选景点名一模一样。
                - tripDays: 行程天数（整数1-7）。用户说"半天/一下午/几个小时"时填1。
                - durationHint: 时长，只能取 HALF(半天/一下午) / FULL(全天) / HOURS(几个小时) / null
                - mode: 游玩模式，只能取以下之一：
                  OPTIMAL(最优顺路) / SPEED(特种兵速通赶时间) / LEISURE(休闲慢游放松) /
                  FOOD(美食为主) / SCENIC(景点打卡看风景) / WILD(野路探险刺激) / CITYWALK(城市漫步闲逛)
                  用户没说偏好就填 OPTIMAL；提到"和室友/同学一起""不想太累"倾向 LEISURE。
                - budgetLevel: 预算档位，只能取 NONE / LE1000 / LE1500 / LE2000 / LE3000
                - startTime: 出发时间24小时制 "HH:mm"。用户说"下午去"→"13:30"，"上午"→"09:00"，
                  "8 点"→"08:00"；没说就 null。
                - startPoint: 出发地点（原话里的地名，如"中南大学麓南校区"），没有就 null
                - companions: 同行人数（整数，用户说"和 4 位室友"→5，含自己；没说就 null）
                - reply: 一句不超过30字的中文口语回复。若城市已确定，就自然地确认你理解到的安排
                  （可以带上具体景点）；若连城市都无法确定，就亲切地追问一句（例如
                  "你想去哪座城市呀？长沙、张家界我都能排～"），不要用模板腔。

                参考候选景点（用于把用户口语对应到真实景点）：%s

                示例：
                输入：我要去省博物馆玩，帮我规划路线
                输出：{"city":"长沙","spotNames":["省博物馆"],"tripDays":1,"durationHint":"HALF","mode":"LEISURE","budgetLevel":null,"startTime":null,"startPoint":null,"companions":null,"reply":"湖南博物院挺好逛的，半天刚好，我给你排一下"}
                """.formatted(cityList, spotList);
    }

    /**
     * 发一次chat/completions请求（Ollama与云端共用），返回choices[0].message.content
     * @param url 完整请求地址
     * @param useModel 请求使用的模型名
     * @param authHeader 鉴权头内容，为空则不携带
     * @param systemPrompt 系统提示词
     * @param userText 用户输入的原话
     * @param timeout 请求超时秒数
     * @param temperature 采样温度，越小输出越稳定
     * @return String 模型回复正文；请求失败或返回结构异常时返回null
     */
    private String callChat(String url, String useModel, String authHeader,
                            String systemPrompt, String userText,
                            int timeout, double temperature) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", useModel);
        body.put("temperature", temperature);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        ObjectNode usr = messages.addObject();
        usr.put("role", "user");
        usr.put("content", userText);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, timeout)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
        if (authHeader != null && !authHeader.isBlank()) {
            builder.header("Authorization", authHeader);
        }

        HttpResponse<String> resp = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            log.warn("[学旅圈] 大模型返回非200：{} {}", resp.statusCode(), brief(resp.body()));
            return null;
        }
        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).path("message").path("content").asText(null);
    }

    /** Ollama的OpenAI兼容端点 */
    private String ollamaChatUrl() {
        String base = ollamaBaseUrl == null ? "" : ollamaBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/v1/chat/completions";
    }

    /**
     * 宽松解析模型返回的JSON：先直接parse；失败则从文本里截取第一个 { 到最后一个 } 再试。
     * （有些模型会习惯性地加上「好的，结果是：」或```json包裹）
     * @param content 模型返回的原始文本
     * @return JsonNode 解析出的JSON节点；无法解析时返回null
     */
    private JsonNode readJsonLoose(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (Exception ignored) {
            int l = content.indexOf('{');
            int r = content.lastIndexOf('}');
            if (l >= 0 && r > l) {
                try {
                    return objectMapper.readTree(content.substring(l, r + 1));
                } catch (Exception ignored2) {
                    return null;
                }
            }
            return null;
        }
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText("").trim();
        // 模型有时会把「没有」写成字符串 "null"
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return null;
        }
        return s;
    }

    private static Integer intOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isInt() || v.isLong()) {
            return v.asInt();
        }
        try {
            return Integer.parseInt(v.asText().replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String trimEnd(String s) {
        return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String brief(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
