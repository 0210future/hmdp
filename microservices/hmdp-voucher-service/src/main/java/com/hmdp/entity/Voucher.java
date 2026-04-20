package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券实体，对应 `tb_voucher` 表。
 * `stock`、`beginTime`、`endTime` 为联表查询秒杀券时补充出的扩展字段。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_voucher")
public class Voucher implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 优惠券主键 ID。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属商铺 ID。
     */
    private Long shopId;

    /**
     * 优惠券标题。
     */
    private String title;

    /**
     * 优惠券副标题。
     */
    private String subTitle;

    /**
     * 使用规则说明。
     */
    private String rules;

    /**
     * 支付金额，单位为分。
     */
    private Long payValue;

    /**
     * 抵扣后实际价值，单位为分。
     */
    private Long actualValue;

    /**
     * 优惠券类型，通常 1 表示普通券，2 表示秒杀券。
     */
    private Integer type;

    /**
     * 优惠券状态，1 表示启用。
     */
    private Integer status;

    /**
     * 秒杀库存，非表字段，通过联表查询返回。
     */
    @TableField(exist = false)
    private Integer stock;

    /**
     * 秒杀开始时间，非表字段，通过联表查询返回。
     */
    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime beginTime;

    /**
     * 秒杀结束时间，非表字段，通过联表查询返回。
     */
    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
