package com.xuelvquan.travel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xuelvquan.travel.entity.AiChatMessage;
import com.xuelvquan.travel.entity.AiUserMemory;
import com.xuelvquan.travel.entity.TagDict;
import com.xuelvquan.travel.entity.TripPref;
import com.xuelvquan.travel.entity.User;
import com.xuelvquan.travel.repository.AiChatMessageRepository;
import com.xuelvquan.travel.repository.AiUserMemoryRepository;
import com.xuelvquan.travel.repository.TagDictRepository;
import com.xuelvquan.travel.repository.TripPrefRepository;
import com.xuelvquan.travel.repository.UserRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 学旅圈个性化AI聊天服务（AI旅行搭子），本地Ollama → 云端 → 离线三级降级
 */
@Slf4j
@Service
public class AiChatService {

    /** 近程上下文轮数（user+assistant合计条数=轮数*2） */
    private static final int CONTEXT_ROUNDS = 6;

    /** 记忆摘要长度上限（字符） */
    private static final int MEMORY_MAX = 1200;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** 记忆更新专用单线程池：异步执行，不阻塞聊天响应 */
    private final ExecutorService memoryExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ai-memory-updater");
        t.setDaemon(true);
        return t;
    });

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;
    private final TripPrefRepository tripPrefRepository;
    private final TagDictRepository tagDictRepository;
    private final AiChatMessageRepository chatMessageRepository;
    private final AiUserMemoryRepository memoryRepository;

    // ===== 本地Ollama配置 =====
    @Value("${xlvq.ai.ollama.enabled:true}")
    private boolean ollamaEnabled;

    @Value("${xlvq.ai.ollama.base-url:http://127.0.0.1:11434}")
    private String ollamaBaseUrl;

    @Value("${xlvq.ai.ollama.model:qwen2.5:7b}")
    private String ollamaModel;

    @Value("${xlvq.ai.ollama.timeout-seconds:90}")
    private int ollamaTimeoutSeconds;

    // ===== 云端兜底配置（与AiIntentService共用xlvq.ai下的key/base-url/model）=====
    @Value("${xlvq.ai.enabled:true}")
    private boolean cloudEnabled;

    @Value("${xlvq.ai.base-url:}")
    private String cloudBaseUrl;

    @Value("${xlvq.ai.api-key:}")
    private String cloudApiKey;

    @Value("${xlvq.ai.model:glm-4-flash}")
    private String cloudModel;

    @Value("${xlvq.ai.chat-timeout-seconds:30}")
    private int cloudTimeoutSeconds;

    public AiChatService(UserRepository userRepository,
                         TripPrefRepository tripPrefRepository,
                         TagDictRepository tagDictRepository,
                         AiChatMessageRepository chatMessageRepository,
                         AiUserMemoryRepository memoryRepository) {
        this.userRepository = userRepository;
        this.tripPrefRepository = tripPrefRepository;
        this.tagDictRepository = tagDictRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.memoryRepository = memoryRepository;
    }

    /** 聊天结果：回复+实际使用的引擎 */
    public record ChatResult(String reply, String source, String model) {
    }

    /**
     * 带画像与记忆的一轮对话主入口
     * @param userId 用户ID
     * @param userText 用户输入文本
     * @return ChatResult 含reply（回复）、source（引擎来源）、model（模型名）
     */
    public ChatResult chat(Long userId, String userText) {
        saveMessage(userId, "user", userText, null);

        String systemPrompt = buildSystemPrompt(userId);
        List<ObjectNode> history = recentHistory(userId);
        List<String> errors = new java.util.ArrayList<>();

        // ①本地Ollama
        if (ollamaEnabled) {
            try {
                String reply = callOpenAiCompatible(
                        ollamaChatUrl(), ollamaModel, "ollama", systemPrompt, history, userText,
                        ollamaTimeoutSeconds);
                return finish(userId, reply, "ollama", ollamaModel, userText, errors);
            } catch (Exception e) {
                errors.add("ollama: " + brief(e.getMessage()));
                log.warn("[学旅圈] Ollama 调用失败，尝试云端兜底：{}", brief(e.getMessage()));
            }
        }

        // ②云端OpenAI兼容接口（DeepSeek/智谱…，配置了key才启用）
        if (cloudEnabled && notBlank(cloudBaseUrl) && notBlank(cloudApiKey)) {
            try {
                String reply = callOpenAiCompatible(
                        cloudChatUrl(), cloudModel, "Bearer " + cloudApiKey, systemPrompt,
                        history, userText, cloudTimeoutSeconds);
                return finish(userId, reply, "cloud", cloudModel, userText, errors);
            } catch (Exception e) {
                errors.add("cloud: " + brief(e.getMessage()));
                log.warn("[学旅圈] 云端兜底也失败，走离线话术：{}", brief(e.getMessage()));
            }
        }

        // ③离线兜底：功能不中断
        String reply = "我现在离线啦（本地模型未就绪、云端也未配置），暂时只能陪你简单聊聊。"
                +"你可以先逛逛景点库和攻略，等我回来再帮你规划路线～";
        return finish(userId, reply, "offline", "-", userText, errors);
    }

    /** 当前对话使用的引擎（供前端展示） */
    public Map<String, Object> engineStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ollamaEnabled", ollamaEnabled);
        m.put("ollamaModel", ollamaModel);
        m.put("cloudConfigured", cloudEnabled && notBlank(cloudBaseUrl) && notBlank(cloudApiKey));
        m.put("cloudModel", cloudModel);
        m.put("contextRounds", CONTEXT_ROUNDS);
        return m;
    }

    /**
     * 无副作用的单轮补全（路线算法解释层专用），Ollama → 云端 → null三级降级
     * @param systemPrompt 系统提示词
     * @param userText 用户输入
     * @return String 模型回复；两引擎均不可用时返回null
     */
    public String complete(String systemPrompt, String userText) {
        if (ollamaEnabled) {
            try {
                return callOpenAiCompatible(ollamaChatUrl(), ollamaModel, "ollama",
                        systemPrompt, List.of(), userText, Math.min(ollamaTimeoutSeconds, 15));
            } catch (Exception e) {
                log.warn("[学旅圈] 解释层 Ollama 调用失败：{}", brief(e.getMessage()));
            }
        }
        if (cloudEnabled && notBlank(cloudBaseUrl) && notBlank(cloudApiKey)) {
            try {
                return callOpenAiCompatible(cloudChatUrl(), cloudModel, "Bearer " + cloudApiKey,
                        systemPrompt, List.of(), userText, Math.min(cloudTimeoutSeconds, 15));
            } catch (Exception e) {
                log.warn("[学旅圈] 解释层云端兜底失败：{}", brief(e.getMessage()));
            }
        }
        return null;
    }

    public List<AiChatMessage> history(Long userId) {
        return chatMessageRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId);
    }

    public void clearHistory(Long userId) {
        chatMessageRepository.deleteByUserId(userId);
    }

    /**
     * 当前记忆摘要
     * @param userId 用户ID
     * @return Map 含记忆摘要、对话轮数与更新时间，没有记忆时字段为空
     */
    public Map<String, Object> memoryOf(Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        memoryRepository.findByUserId(userId).ifPresentOrElse(
                mem -> {
                    m.put("memory", mem.getMemorySummary());
                    m.put("chatCount", mem.getChatCount());
                    m.put("updatedAt", mem.getUpdatedAt());
                },
                () -> {
                    m.put("memory", null);
                    m.put("chatCount", 0);
                    m.put("updatedAt", null);
                });
        return m;
    }

    /**
     * 彻底遗忘：清空画像摘要（对话历史保留，由clearHistory单独清）
     * @param userId 用户ID
     */
    public void forgetMemory(Long userId) {
        memoryRepository.findByUserId(userId).ifPresent(mem -> {
            mem.setMemorySummary(null);
            mem.setUpdatedAt(LocalDateTime.now());
            memoryRepository.save(mem);
        });
    }

    // ==================== 内部实现 ====================

    private ChatResult finish(Long userId, String reply, String source, String model,
                              String userText, List<String> errors) {
        saveMessage(userId, "assistant", reply, source);
        updateMemoryAsync(userId, userText, reply);
        ChatResult r = new ChatResult(reply, source, model);
        if (!errors.isEmpty()) {
            log.info("[学旅圈] AI 聊天降级轨迹：{} → 最终引擎 {}", errors, source);
        }
        return r;
    }

    private void saveMessage(Long userId, String role, String content, String source) {
        AiChatMessage msg = new AiChatMessage();
        msg.setUserId(userId);
        msg.setRole(role);
        msg.setContent(clip(content, 4000));
        msg.setSource(source);
        msg.setCreatedAt(LocalDateTime.now());
        chatMessageRepository.save(msg);
    }

    /**
     * 组system prompt：人设+静态画像（标签/偏好）+ 远程记忆
     * @param userId 用户ID
     * @return String 拼接好的系统提示词
     */
    private String buildSystemPrompt(Long userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「学旅圈」平台的 AI 旅行搭子，服务对象是大学生旅行者。")
          .append("说话亲切自然、像学长学姐，偶尔用一点校园流行语但不过火。")
          .append("回答务必简洁实用：推荐具体地点时优先使用平台景点库里存在的城市与景点；")
          .append("涉及预算用人民币；涉及安全（夜爬、涉水、独行）要主动提醒。")
          .append("用户没说目的地时可以追问，一次只问一个问题。\n\n");

        String profile = staticProfileText(userId);
        if (!profile.isBlank()) {
            sb.append("【你知道的这位用户】\n").append(profile).append("\n\n");
        }

        memoryRepository.findByUserId(userId).ifPresent(mem -> {
            if (notBlank(mem.getMemorySummary())) {
                sb.append("【你与 TA 的相处记忆】\n")
                  .append(mem.getMemorySummary())
                  .append("\n（以上记忆来自历史对话的自动总结，可以自然引用，比如「上次你说…」）\n\n");
            }
        });

        sb.append("请用不超过150字回答（用户明确要求详细时除外），不要使用 markdown 标题。");
        return sb.toString();
    }

    /**
     * 静态画像：档案标签（code翻译成中文）+ 结伴偏好+基本信息
     * @param userId 用户ID
     * @return String 画像文本，用户资料为空时返回空串
     */
    private String staticProfileText(Long userId) {
        StringBuilder sb = new StringBuilder();
        userRepository.findById(userId).ifPresent(u -> {
            List<String> parts = new java.util.ArrayList<>();
            if (notBlank(u.getSchool())) {
                parts.add(u.getSchool() + " 的学生");
            }
            String tagText = translateTags(u.getProfileTags());
            if (notBlank(tagText)) {
                parts.add("旅行风格标签：" + tagText);
            }
            if (notBlank(u.getInterests())) {
                parts.add("兴趣：" + u.getInterests());
            }
            if (notBlank(u.getBio())) {
                parts.add("个性签名：" + clip(u.getBio(), 80));
            }
            if (!parts.isEmpty()) {
                sb.append(String.join("；", parts)).append("。\n");
            }
        });

        TripPref pref = tripPrefRepository.findByUserId(userId).orElse(null);
        if (pref != null) {
            List<String> parts = new java.util.ArrayList<>();
            if (notBlank(pref.getPaceType())) {
                parts.add("节奏偏好" + pref.getPaceType());
            }
            if (pref.getBudget() != null) {
                parts.add("预算上限约" + pref.getBudget() + " 元");
            }
            if (notBlank(pref.getDestinationPref())) {
                parts.add("想去" + pref.getDestinationPref());
            }
            if (notBlank(pref.getInterestTags())) {
                parts.add("兴趣标签" + pref.getInterestTags());
            }
            if (!parts.isEmpty()) {
                sb.append("结伴偏好：").append(String.join("，", parts)).append("。\n");
            }
        }
        return sb.toString();
    }

    /**
     * 把 "pace:BALANCED,interest:PHOTO" 翻译成 "节奏 BALANCED、兴趣 PHOTO"（查tag_dict的label）
     * @param profileTags 形如 "pace:BALANCED,interest:PHOTO" 的标签串
     * @return String 中文标签名，用「、」连接，没有可用标签时返回空串
     */
    private String translateTags(String profileTags) {
        if (!notBlank(profileTags)) {
            return "";
        }
        Map<String, String> labelMap = tagDictRepository
                .findByScopeAndActiveTrueOrderBySortNoAscIdAsc("USER").stream()
                .collect(Collectors.toMap(TagDict::getCode, TagDict::getLabel, (a, b) -> a));
        return Arrays.stream(profileTags.split(","))
                .map(String::trim)
                .filter(s -> s.contains(":"))
                .map(s -> s.substring(s.indexOf(':') + 1))
                .map(code -> labelMap.getOrDefault(code, code))
                .collect(Collectors.joining("、"));
    }

    /**
     * 近程记忆：最近N轮对话，按时间正序返回
     * @param userId 用户ID
     * @return List 最近若干条对话消息，按时间正序
     */
    private List<ObjectNode> recentHistory(Long userId) {
        Pageable limit = PageRequest.of(0, CONTEXT_ROUNDS * 2);
        List<AiChatMessage> recent =
                chatMessageRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, limit);
        java.util.Collections.reverse(recent);
        return recent.stream().map(m -> {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", m.getRole());
            node.put("content", m.getContent());
            return node;
        }).collect(Collectors.toList());
    }

    /**
     * 发一次OpenAI兼容的chat/completions（Ollama与云端共用同一条代码路径）。
     *
     * @param url 接口完整地址
     * @param model 模型名
     * @param authHeader Ollama 传 "ollama"（服务端忽略），云端传 "Bearer xxx"
     * @param systemPrompt 系统提示词
     * @param history 历史对话消息
     * @param userText 本轮用户输入
     * @param timeoutSeconds 请求超时秒数，最小按5秒
     * @return String 模型回复正文
     */
    private String callOpenAiCompatible(String url, String model, String authHeader,
                                        String systemPrompt, List<ObjectNode> history,
                                        String userText, int timeoutSeconds) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0.7);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        for (ObjectNode h : history) {
            messages.add(h);
        }
        ObjectNode usr = messages.addObject();
        usr.put("role", "user");
        usr.put("content", userText);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
        if (!"ollama".equals(authHeader)) {
            builder.header("Authorization", authHeader);
        }

        HttpResponse<String> resp =
                CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " " + brief(resp.body()));
        }
        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("响应里没有 choices");
        }
        String content = choices.get(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("回复内容为空");
        }
        return content.trim();
    }

    private String ollamaChatUrl() {
        String base = ollamaBaseUrl == null ? "" : ollamaBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/v1/chat/completions";
    }

    private String cloudChatUrl() {
        String base = cloudBaseUrl == null ? "" : cloudBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
    }

    /**
     * 异步更新远程记忆：将旧摘要与本轮对话融合为新摘要，失败仅记日志不影响主流程
     * @param userId 用户ID
     * @param userText 本轮用户输入
     * @param assistantReply 本轮助手回复
     */
    private void updateMemoryAsync(Long userId, String userText, String assistantReply) {
        memoryExecutor.submit(() -> {
            try {
                AiUserMemory mem = memoryRepository.findByUserId(userId).orElseGet(() -> {
                    AiUserMemory m = new AiUserMemory();
                    m.setUserId(userId);
                    m.setChatCount(0);
                    return m;
                });
                mem.setChatCount((mem.getChatCount() == null ? 0 : mem.getChatCount()) + 1);
                mem.setUpdatedAt(LocalDateTime.now());

                String engine = ollamaEnabled ? ollamaModel
                        : (notBlank(cloudApiKey) ? cloudModel : null);
                if (engine != null) {
                    String url = ollamaEnabled ? ollamaChatUrl() : cloudChatUrl();
                    String oldMemory = clip(mem.getMemorySummary(), MEMORY_MAX);
                    String prompt = """
                            请把「已有记忆」和「最新对话」融合成一份新的用户画像摘要，供旅行 AI 助手下
                            次对话时使用。只保留对旅行推荐有用的信息：性格/沟通风格、旅行偏好（节奏、
                            预算、目的地、兴趣）、承诺过的事、重要的个人信息。已有的信息不要丢，新的
                            信息合并进去，去掉无关寒暄。直接输出摘要正文，不要任何前后缀说明，严格
                            不超过600字。

                            【已有记忆】
                            %s

                            【最新对话】
                            用户：%s
                            助手：%s
                            """.formatted(
                            oldMemory == null ? "（空，这是第一次对话）" : oldMemory,
                            clip(userText, 800),
                            clip(assistantReply, 800));

                    String auth = ollamaEnabled ? "ollama" : "Bearer " + cloudApiKey;
                    String summary = callOpenAiCompatible(url, engine, auth,
                            "你是档案管理员，只输出摘要正文。", List.of(), prompt, ollamaTimeoutSeconds);
                    mem.setMemorySummary(clip(summary, MEMORY_MAX));
                }
                memoryRepository.save(mem);
                log.info("[学旅圈] 用户 {} 的 AI 记忆已更新（第 {} 轮）", userId, mem.getChatCount());
            } catch (Exception e) {
                // 记忆更新失败不影响聊天，仅落库轮数
                try {
                    AiUserMemory mem = memoryRepository.findByUserId(userId).orElse(null);
                    if (mem != null) {
                        mem.setChatCount((mem.getChatCount() == null ? 0 : mem.getChatCount()) + 1);
                        mem.setUpdatedAt(LocalDateTime.now());
                        memoryRepository.save(mem);
                    }
                } catch (Exception ignore) {
                    // 完全失败也静默
                }
                log.warn("[学旅圈] 用户 {} 的 AI 记忆更新失败（不影响聊天）：{}",
                        userId, brief(e.getMessage()));
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        memoryExecutor.shutdownNow();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String brief(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }
}
