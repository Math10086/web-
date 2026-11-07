package com.xuelvquan.travel.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 标签字典（用户标签体系v1）。
 *
 * 统一承载三类标签：用户维度标签（scope=USER）、攻略供给标签（scope=POST）、
 * 向导供给标签（scope=GUIDE）。dimension区分维度，code为落库值，
 * label为展示名。
 *
 * 字典内容由B起草、A冻结，冻结后三方只读引用；前端一律通过/api/tags/dict读取，
 * 不要在页面里硬编码标签文案。
 */
@Data
@Entity
@Table(name = "tag_dict")
public class TagDict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** USER用户维度/POST攻略供给/GUIDE向导供给 */
    private String scope;

    /** 维度编码，如pace/interest/budget/destination ... */
    private String dimension;

    /** 标签编码（落库值） */
    private String code;

    /** 展示名 */
    private String label;

    /** 该维度是否多选 */
    private Boolean multi = false;

    /** 是否新用户必填的核心标签（4个核心维度） */
    private Boolean core = false;

    private Integer sortNo = 0;

    private Boolean active = true;
}
