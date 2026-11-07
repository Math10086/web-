package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.entity.TagDict;
import com.xuelvquan.travel.repository.TagDictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签字典读取接口
 *
 * 字典是纯展示配置，不含个人数据，公开可读（注册前的表单、未登录的浏览页都可能要用），
 * 因此这里不做登录校验；写操作不在本控制器，字典改动走SQL或后续管理端。
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagDictRepository tagDictRepository;

    /**
     * 全量字典，按scope -> dimension两级分组：
     * <pre>
     * { "USER": { "pace": { multi:false, core:true, options:[{code,label},...] }, ... },
     *   "POST": {...}, "GUIDE": {...} }
     * </pre>
     */
    @GetMapping("/dict")
    public Result<Map<String, Object>> dict() {
        Map<String, Object> byScope = new LinkedHashMap<>();
        for (TagDict t : tagDictRepository.findByActiveTrueOrderBySortNoAscIdAsc()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> scopes = (Map<String, Object>) byScope
                    .computeIfAbsent(t.getScope(), k -> new LinkedHashMap<String, Object>());
            @SuppressWarnings("unchecked")
            Map<String, Object> dim = (Map<String, Object>) scopes
                    .computeIfAbsent(t.getDimension(), k -> {
                        Map<String, Object> d = new LinkedHashMap<>();
                        d.put("dimension", t.getDimension());
                        d.put("multi", Boolean.TRUE.equals(t.getMulti()));
                        d.put("core", Boolean.TRUE.equals(t.getCore()));
                        d.put("options", new ArrayList<Map<String, Object>>());
                        return d;
                    });
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> options = (List<Map<String, Object>>) dim.get("options");
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("code", t.getCode());
            o.put("label", t.getLabel());
            options.add(o);
        }
        return Result.ok(byScope);
    }

    /**
     * 单个scope的字典（USER/POST/GUIDE）
     * @param scope 字典范围，大小写不敏感
     * @return Result<List<TagDict>> 该范围下的标签列表
     */
    @GetMapping("/dict/{scope}")
    public Result<List<TagDict>> dictOfScope(@PathVariable String scope) {
        return Result.ok(tagDictRepository.findByScopeAndActiveTrueOrderBySortNoAscIdAsc(scope.toUpperCase()));
    }

    /** 新用户必填的核心标签（4个维度） */
    @GetMapping("/core")
    public Result<List<TagDict>> core() {
        List<TagDict> core = new ArrayList<>();
        for (TagDict t : tagDictRepository.findByScopeAndActiveTrueOrderBySortNoAscIdAsc("USER")) {
            if (Boolean.TRUE.equals(t.getCore())) {
                core.add(t);
            }
        }
        return Result.ok(core);
    }

    /**
     * 某维度的全部可选标签
     * @param dimension 维度编码
     * @return Result<List<TagDict>> 该维度下的标签列表
     */
    @GetMapping("/dict/dimension/{dimension}")
    public Result<List<TagDict>> dictOfDimension(@PathVariable String dimension) {
        return Result.ok(tagDictRepository.findByDimensionAndActiveTrueOrderBySortNoAscIdAsc(dimension));
    }
}
