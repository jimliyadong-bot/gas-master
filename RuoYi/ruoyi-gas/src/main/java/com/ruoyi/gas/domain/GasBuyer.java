package com.ruoyi.gas.domain;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 采购商对象 gas_buyer
 *
 * @author gas
 */
public class GasBuyer extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 采购商ID */
    @Excel(name = "采购商ID", cellType = Excel.ColumnType.NUMERIC)
    private Long buyerId;

    /** 采购商名称 */
    @Excel(name = "采购商名称")
    private String buyerName;

    /** 简称 */
    @Excel(name = "简称")
    private String shortName;

    /** 联系人 */
    @Excel(name = "联系人")
    private String contactName;

    /** 联系电话 */
    @Excel(name = "联系电话")
    private String contactPhone;

    /** 短信通知手机号，多个用英文逗号分隔 */
    @Excel(name = "短信通知手机号")
    private String notifyPhones;

    /** 开票提醒：批次剩余比例(%) */
    @Excel(name = "开票提醒比例", cellType = Excel.ColumnType.NUMERIC)
    private BigDecimal invoiceRemindRatio;

    /** 开票提醒：用完未开票的提醒间隔(天) */
    private Integer invoiceRemindDays;

    /** 开票提醒：用完未开票最多提醒次数 */
    private Integer invoiceRemindMax;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    /** 拉卡拉商户配置（与 gas_buyer_merchant 1:1，编辑页联动维护） */
    private GasBuyerMerchant merchant;

    public Long getBuyerId()
    {
        return buyerId;
    }

    public void setBuyerId(Long buyerId)
    {
        this.buyerId = buyerId;
    }

    @NotBlank(message = "采购商名称不能为空")
    @Size(min = 0, max = 100, message = "采购商名称长度不能超过100个字符")
    public String getBuyerName()
    {
        return buyerName;
    }

    public void setBuyerName(String buyerName)
    {
        this.buyerName = buyerName;
    }

    public String getShortName()
    {
        return shortName;
    }

    public void setShortName(String shortName)
    {
        this.shortName = shortName;
    }

    public String getContactName()
    {
        return contactName;
    }

    public void setContactName(String contactName)
    {
        this.contactName = contactName;
    }

    public String getContactPhone()
    {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone)
    {
        this.contactPhone = contactPhone;
    }

    @Pattern(regexp = "^$|^1[3-9]\\d{9}(,1[3-9]\\d{9})*$", message = "短信通知手机号格式不正确，多个号码用英文逗号分隔")
    public String getNotifyPhones()
    {
        return notifyPhones;
    }

    public void setNotifyPhones(String notifyPhones)
    {
        this.notifyPhones = notifyPhones;
    }

    @DecimalMin(value = "0", message = "开票提醒比例不能小于0")
    @DecimalMax(value = "100", message = "开票提醒比例不能超过100")
    public BigDecimal getInvoiceRemindRatio()
    {
        return invoiceRemindRatio;
    }

    public void setInvoiceRemindRatio(BigDecimal invoiceRemindRatio)
    {
        this.invoiceRemindRatio = invoiceRemindRatio;
    }

    @Min(value = 0, message = "开票提醒间隔不能小于0天")
    @Max(value = 365, message = "开票提醒间隔不能超过365天")
    public Integer getInvoiceRemindDays()
    {
        return invoiceRemindDays;
    }

    public void setInvoiceRemindDays(Integer invoiceRemindDays)
    {
        this.invoiceRemindDays = invoiceRemindDays;
    }

    @Min(value = 0, message = "开票提醒次数不能小于0")
    @Max(value = 99, message = "开票提醒次数不能超过99")
    public Integer getInvoiceRemindMax()
    {
        return invoiceRemindMax;
    }

    public void setInvoiceRemindMax(Integer invoiceRemindMax)
    {
        this.invoiceRemindMax = invoiceRemindMax;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public GasBuyerMerchant getMerchant()
    {
        return merchant;
    }

    public void setMerchant(GasBuyerMerchant merchant)
    {
        this.merchant = merchant;
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("buyerId", getBuyerId())
            .append("buyerName", getBuyerName())
            .append("shortName", getShortName())
            .append("contactName", getContactName())
            .append("contactPhone", getContactPhone())
            .append("notifyPhones", getNotifyPhones())
            .append("status", getStatus())
            .append("remark", getRemark())
            .toString();
    }
}
