package com.xuelvquan.travel.service.crawl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 采集器公用的HTTP客户端封装（基于JDK HttpClient，无额外依赖）。
 *
 * 统一的超时、UA与Referer设置；返回体按UTF-8解析，并统一做JSON解析入口。
 */
@Slf4j
public final class CrawlHttp {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0 Safari/537.36";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 带Cookie会话的客户端（12306等站点需要先预热页面拿到会话Cookie） */
    private static final java.net.CookieManager COOKIES = new java.net.CookieManager();

    private static final HttpClient SESSION_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .cookieHandler(COOKIES)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CrawlHttp() {
    }

    /**
     * GET请求，返回响应体文本
     * @param url 请求地址
     * @param referer 来源页地址，为空则不携带
     * @return String 响应体正文（按UTF-8解码）
     */
    public static String getText(String url, String referer) throws Exception {
        return send(CLIENT, url, referer);
    }

    /**
     * 带会话Cookie的GET（同一次运行内共享Cookie，用于需要预热与防盗链的站点）
     * @param url 请求地址
     * @param referer 来源页地址，为空则不携带
     * @return String 响应体正文（按UTF-8解码）
     */
    public static String getTextSession(String url, String referer) throws Exception {
        return send(SESSION_CLIENT, url, referer);
    }

    private static String send(HttpClient client, String url, String referer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .GET()
                .header("User-Agent", UA)
                .header("Accept", "text/html,application/json,application/javascript,*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9");
        if (referer != null) {
            b.header("Referer", referer);
        }
        HttpResponse<byte[]> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " ← " + url);
        }
        return new String(resp.body(), StandardCharsets.UTF_8);
    }

    /**
     * GET请求并解析为JSON
     * @param url 请求地址
     * @param referer 来源页地址，为空则不携带
     * @return JsonNode 解析后的JSON节点
     */
    public static JsonNode getJson(String url, String referer) throws Exception {
        return MAPPER.readTree(getText(url, referer));
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
