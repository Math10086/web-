package com.xuelvquan.travel.common;

/**
 * B6信誉标签映射：把信誉分映射成头像信息卡上的展示标签。
 *
 * 分值口径与CreditService/管理员调整接口保持一致。
 */
public final class CreditBadge {

    private CreditBadge() {
    }

    /** 金牌守信 */
    public static final String GOLD = "GOLD";
    /** 诚信用户 */
    public static final String GREEN = "GREEN";
    /** 信誉良好 */
    public static final String BLUE = "BLUE";
    /** 信用一般 */
    public static final String NORMAL = "NORMAL";
    /** 信誉警示 */
    public static final String RED = "RED";

    public static String levelOf(Integer score) {
        int s = score == null ? 100 : score;
        if (s >= 120) {
            return GOLD;
        }
        if (s >= 100) {
            return GREEN;
        }
        if (s >= 80) {
            return BLUE;
        }
        if (s >= 60) {
            return NORMAL;
        }
        return RED;
    }

    public static String labelOf(Integer score) {
        return switch (levelOf(score)) {
            case GOLD -> "金牌守信";
            case GREEN -> "诚信用户";
            case BLUE -> "信誉良好";
            case NORMAL -> "信用一般";
            default -> "信誉警示";
        };
    }
}
