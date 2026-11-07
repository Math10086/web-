package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.AiChatMessage;
import com.xuelvquan.travel.repository.SpotRepository;
import com.xuelvquan.travel.security.AuthUtil;
import com.xuelvquan.travel.service.AiChatService;
import com.xuelvquan.travel.service.AiIntentService;
import com.xuelvquan.travel.service.SmartRouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI行程助手接口（大模型接入层）。
 *
 * 前端「一句话规划」的流程：用户提问 → POST/api/ai/parse-intent → 后端调大模型解析成结构化条件
 * → 前端把条件回填到左侧表单并重新规划。
 *
 * 降级约定：未配置key或大模型调用失败时，仍返回code=0的正常响应，
 * source标记为rule，由前端改用本地关键词规则继续工作。
 *
 * 个性化AI聊天（/api/ai/chat）：本地Ollama为主力引擎、云端API兜底、
 * 离线话术再兜底；画像注入+两层记忆见AiChatService。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiIntentService aiIntentService;
    private final SpotRepository spotRepository;
    private final AiChatService aiChatService;
    private final SmartRouteService smartRouteService;

    /**
     * 一句话智能规划：解析意图、结合用户画像选点并生成行程
     * @param body 用户输入的描述文本
     * @return Result<Map<String,Object>> 规划结果，含行程与AI规划元数据
     */
    @PostMapping("/smart-plan")
    public Result<Map<String, Object>> smartPlan(@RequestBody Map<String, String> body) {
        Long userId = AuthUtil.requireUserId();
        String text = body == null ? null : body.get("text");
        if (text == null || text.isBlank()) {
            return Result.fail(400, "说一句想去哪玩吧，例如「长沙2天，喜欢拍照和美食」");
        }
        // round表示第几次换路线，>0时换一批备选景点
        int round = 0;
        String roundStr = body.get("round");
        if (roundStr != null && !roundStr.isBlank()) {
            try {
                round = Math.max(0, Integer.parseInt(roundStr.trim()));
            } catch (NumberFormatException ignored) {
                round = 0;
            }
        }
        return Result.ok(smartRouteService.smartPlan(userId, text, round));
    }

    /** 前端据此显示当前解析引擎（本地Ollama/云端大模型/本地规则） */
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", aiIntentService.isReady());
        boolean local = aiIntentService.engineName().startsWith("ollama");
        m.put("provider", local ? "本地 Ollama" : aiIntentService.getProvider());
        m.put("model", local ? aiIntentService.engineName().substring("ollama:".length())
                : aiIntentService.getModel());
        m.put("engine", aiIntentService.engineName());
        m.put("chat", aiChatService.engineStatus());
        return Result.ok(m);
    }

    // ==================== 个性化AI聊天 ====================

    /**
     * AI聊天对话，需要登录
     * @param body 对话内容
     * @return Result<Map<String,Object>> 回复内容、解析来源与模型名称
     */
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        Long userId = AuthUtil.requireUserId();
        String text = body == null ? null : body.get("text");
        if (text == null || text.isBlank()) {
            return Result.fail(400, "想聊什么，先说一句吧");
        }
        if (text.length() > 2000) {
            return Result.fail(400, "一次别说太多，2000字以内");
        }
        AiChatService.ChatResult r = aiChatService.chat(userId, text.trim());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reply", r.reply());
        m.put("source", r.source());
        m.put("model", r.model());
        return Result.ok(m);
    }

    /** 当前用户的对话历史（时间正序） */
    @GetMapping("/chat/history")
    public Result<List<AiChatMessage>> chatHistory() {
        return Result.ok(aiChatService.history(AuthUtil.requireUserId()));
    }

    /** 清空对话历史（不动远程记忆；想彻底「失忆」用delete-memory） */
    @DeleteMapping("/chat/history")
    public Result<Void> clearHistory() {
        aiChatService.clearHistory(AuthUtil.requireUserId());
        return Result.ok(null);
    }

    /** AI记忆摘要：当前画像摘要与对话轮数 */
    @GetMapping("/memory")
    public Result<Map<String, Object>> memory() {
        return Result.ok(aiChatService.memoryOf(AuthUtil.requireUserId()));
    }

    /** 彻底遗忘：清空记忆摘要与轮数（对话历史保留，可配合清历史一起用） */
    @DeleteMapping("/memory")
    public Result<Void> forgetMemory() {
        aiChatService.forgetMemory(AuthUtil.requireUserId());
        return Result.ok(null);
    }


    @PostMapping("/parse-intent")
    public Result<Map<String, Object>> parseIntent(@RequestBody Map<String, String> body) {
        String text = body == null ? null : body.get("text");
        if (text == null || text.isBlank()) {
            return Result.fail(400, "请先说点什么");
        }

        if (!aiIntentService.isReady()) {
            return Result.ok(ruleFallback("未配置大模型"));
        }

        List<String> cities = spotRepository.findCities();
        List<String> spotNames = spotRepository.findAll().stream().map(s -> s.getName()).toList();
        Map<String, Object> hit = aiIntentService.parseIntent(text, cities, spotNames);
        if (hit == null) {
            return Result.ok(ruleFallback("大模型调用失败"));
        }
        hit.put("source", "llm");
        return Result.ok(hit);
    }

    private Map<String, Object> ruleFallback(String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", "rule");
        m.put("reason", reason);
        return m;
    }
}
