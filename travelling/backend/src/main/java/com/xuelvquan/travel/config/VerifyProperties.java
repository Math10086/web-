package com.xuelvquan.travel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 学号格式校验配置。
 *
 * 认证走「学号格式校验自动通过」，各校学号位数不同，因此把正则放在配置里，
 * 改application.yml即可调整，不用改代码：
 *
 * <pre>
 * xlvq:
 *   verify:
 *     student-no-patterns:
 *       CSU: '^\d{8}$'
 *       HUNNU: '^\d{10}$'
 *     student-no-hints:
 *       CSU: '中南大学学号应为8位数字'
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "xlvq.verify")
public class VerifyProperties {

    /** 学校代码（CSU/HUNNU/HNU）-> 学号正则 */
    private Map<String, String> studentNoPatterns = new LinkedHashMap<>();

    /** 学校代码 -> 校验失败时的友好提示 */
    private Map<String, String> studentNoHints = new LinkedHashMap<>();

    /**
     * 取某校的学号正则；未配置时默认8-12位数字，避免因漏配而无法认证
     * @param school 学校代码（CSU/HUNNU/HNU）
     * @return String 该校的学号正则
     */
    public String patternOf(String school) {
        String p = studentNoPatterns.get(school);
        return (p == null || p.isBlank()) ? "^\\d{8,12}$" : p;
    }

    /**
     * 取某校的校验提示
     * @param school 学校代码（CSU/HUNNU/HNU）
     * @return String 该校学号格式不正确的提示语
     */
    public String hintOf(String school) {
        String h = studentNoHints.get(school);
        if (h != null && !h.isBlank()) {
            return h;
        }
        return school + " 学号格式不正确";
    }
}
