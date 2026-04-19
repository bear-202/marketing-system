package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDate;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 智能客服到店预约记录表
 * </p>
 *
 * @author tangwang
 * @since 2026-03-23
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop_reservation")
public class ShopReservation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 预约单ID（主键，自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商铺名称
     */
    @TableField("store_name")
    private String storeName;

    /**
     * 用户姓名
     */
    @TableField("user_name")
    private String userName;

    /**
     * 用户联系方式（手机号）
     */
    @TableField("user_phone")
    private String userPhone;

    /**
     * 备注（可选）
     */
    @TableField("remark")
    private String remark;

    /**
     * 预约日期
     */
    @TableField("reservation_date")
    private LocalDate reservationDate;

    /**
     * 预约创建时间
     */
    @TableField("reservation_time")
    private LocalDateTime reservationTime;

    /**
     * 预约状态：1=待到店，2=已到店，3=已取消
     */
    @TableField("reservation_status")
    private Integer reservationStatus;


}
