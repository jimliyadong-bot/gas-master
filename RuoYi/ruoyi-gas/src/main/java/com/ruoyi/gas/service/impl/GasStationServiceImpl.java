package com.ruoyi.gas.service.impl;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.gas.domain.GasBuyer;
import com.ruoyi.gas.domain.GasInventoryPool;
import com.ruoyi.gas.domain.GasPurchaseBatch;
import com.ruoyi.gas.domain.GasStation;
import com.ruoyi.gas.mapper.GasBuyerMapper;
import com.ruoyi.gas.mapper.GasGunMapper;
import com.ruoyi.gas.mapper.GasInventoryPoolMapper;
import com.ruoyi.gas.mapper.GasOrderMapper;
import com.ruoyi.gas.mapper.GasPurchaseBatchMapper;
import com.ruoyi.gas.mapper.GasStationMapper;
import com.ruoyi.gas.service.IGasConfigService;
import com.ruoyi.gas.service.IGasStationService;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.service.ISysDeptService;

/**
 * 加气站 服务实现
 *
 * <p>建站自动在根部门下创建 sys_dept（Dev_Plan §1.1），站点改名同步部门名，
 * 删站联动删部门；站点二维码内容 = gas_config[h5.base.url] + ?station=二维码标识。</p>
 *
 * @author gas
 */
@Service
public class GasStationServiceImpl implements IGasStationService
{
    /** 根部门ID（阶段1初始化：dept_id=100，parent_id=0） */
    private static final Long ROOT_DEPT_ID = 100L;

    /** H5 基础地址参数键 */
    public static final String CONFIG_H5_BASE_URL = "h5.base.url";

    @Autowired
    private GasStationMapper stationMapper;

    @Autowired
    private GasGunMapper gunMapper;

    @Autowired
    private GasBuyerMapper buyerMapper;

    @Autowired
    private GasPurchaseBatchMapper batchMapper;

    @Autowired
    private GasInventoryPoolMapper poolMapper;

    @Autowired
    private GasOrderMapper orderMapper;

    @Autowired
    private IGasConfigService configService;

    @Autowired
    private SysDeptMapper deptMapper;

    @Autowired
    private ISysDeptService deptService;

    @Override
    public List<GasStation> selectStationList(GasStation station)
    {
        return stationMapper.selectStationList(station);
    }

    @Override
    public GasStation selectStationById(Long stationId)
    {
        return stationMapper.selectStationById(stationId);
    }

    @Override
    public GasStation selectStationByQrToken(String qrToken)
    {
        return stationMapper.selectStationByQrToken(qrToken);
    }

    @Override
    @Transactional
    public int insertStation(GasStation station)
    {
        checkBuyerValid(station.getBuyerId());
        SysDept root = requireRootDept();
        checkDeptName(station.getStationName(), root.getDeptId(), null);

        // 自动创建站点部门（站点管理员/加气员挂在此部门下，数据权限=本部门）
        SysDept dept = new SysDept();
        dept.setParentId(root.getDeptId());
        dept.setAncestors(root.getAncestors() + "," + root.getDeptId());
        dept.setDeptName(station.getStationName());
        dept.setOrderNum(1);
        dept.setStatus("0");
        dept.setCreateBy(station.getCreateBy());
        deptMapper.insertDept(dept);
        // 若依的 insertDept 不回填自增主键，且已校验同级无重名，按 名称+父部门 反查新建部门
        SysDept created = deptMapper.checkDeptNameUnique(dept.getDeptName(), dept.getParentId());
        if (StringUtils.isNull(created) || StringUtils.isNull(created.getDeptId()))
        {
            throw new ServiceException("站点部门创建失败，请重试");
        }

        station.setDeptId(created.getDeptId());
        station.setQrToken(IdUtils.fastSimpleUUID());
        return stationMapper.insertStation(station);
    }

    @Override
    @Transactional
    public int updateStation(GasStation station)
    {
        GasStation old = stationMapper.selectStationById(station.getStationId());
        if (StringUtils.isNull(old))
        {
            throw new ServiceException("站点不存在或已被删除");
        }
        // API 直调可能携带 buyerId，变更归属时同样校验有效性
        if (StringUtils.isNotNull(station.getBuyerId()) && station.getBuyerId().longValue() != 0L)
        {
            checkBuyerValid(station.getBuyerId());
            if (station.getBuyerId().longValue() != old.getBuyerId().longValue() && hasBusinessData(station.getStationId()))
            {
                throw new ServiceException("站点已有采购、库存或订单数据，不允许直接变更采购商归属");
            }
        }
        // 站点改名同步部门名
        if (StringUtils.isNotEmpty(station.getStationName()) && !station.getStationName().equals(old.getStationName()))
        {
            SysDept dept = deptMapper.selectDeptById(old.getDeptId());
            if (StringUtils.isNotNull(dept))
            {
                checkDeptName(station.getStationName(), dept.getParentId(), dept.getDeptId());
                dept.setDeptName(station.getStationName());
                dept.setUpdateBy(station.getUpdateBy());
                deptMapper.updateDept(dept);
            }
        }
        return stationMapper.updateStation(station);
    }

    @Override
    @Transactional
    public int deleteStationByIds(Long[] stationIds)
    {
        for (Long stationId : stationIds)
        {
            GasStation station = stationMapper.selectStationById(stationId);
            if (StringUtils.isNull(station))
            {
                continue;
            }
            if (gunMapper.countGunByStationId(stationId) > 0)
            {
                throw new ServiceException(String.format("站点[%s]名下存在枪号，无法删除", station.getStationName()));
            }
            if (hasBusinessData(stationId))
            {
                throw new ServiceException(String.format("站点[%s]已有采购、库存或订单数据，只能停用，无法删除", station.getStationName()));
            }
            if (StringUtils.isNotNull(station.getDeptId()) && deptService.checkDeptExistUser(station.getDeptId()))
            {
                throw new ServiceException(String.format("站点[%s]的部门下存在用户，请先调整人员归属", station.getStationName()));
            }
        }
        for (Long stationId : stationIds)
        {
            GasStation station = stationMapper.selectStationById(stationId);
            if (StringUtils.isNotNull(station) && StringUtils.isNotNull(station.getDeptId()))
            {
                deptMapper.deleteDeptById(station.getDeptId());
            }
        }
        return stationMapper.deleteStationByIds(stationIds);
    }

    @Override
    public boolean checkStationCodeUnique(GasStation station)
    {
        Long stationId = StringUtils.isNull(station.getStationId()) ? -1L : station.getStationId();
        GasStation info = stationMapper.checkStationCodeUnique(station.getStationCode());
        if (StringUtils.isNotNull(info) && info.getStationId().longValue() != stationId.longValue())
        {
            return false;
        }
        return true;
    }

    @Override
    public byte[] buildQrPng(GasStation station)
    {
        String base = configService.selectConfigValue(CONFIG_H5_BASE_URL, "");
        String content = base + (base.endsWith("/") ? "" : "/") + "?station=" + station.getQrToken();
        try
        {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", os);
            return os.toByteArray();
        }
        catch (Exception e)
        {
            throw new ServiceException("生成站点二维码失败：" + e.getMessage());
        }
    }

    private boolean hasBusinessData(Long stationId)
    {
        GasPurchaseBatch batchQuery = new GasPurchaseBatch();
        batchQuery.setStationId(stationId);
        GasInventoryPool poolQuery = new GasInventoryPool();
        poolQuery.setStationId(stationId);
        return orderMapper.countByStationId(stationId) > 0
                || !batchMapper.selectBatchList(batchQuery).isEmpty()
                || !poolMapper.selectPoolList(poolQuery).isEmpty();
    }

    /**
     * 校验归属采购商存在且未停用（无数据库外键，服务层保证引用有效）
     */
    private void checkBuyerValid(Long buyerId)
    {
        if (StringUtils.isNull(buyerId) || buyerId.longValue() == 0L)
        {
            throw new ServiceException("所属采购商不能为空");
        }
        GasBuyer buyer = buyerMapper.selectBuyerById(buyerId);
        if (StringUtils.isNull(buyer))
        {
            throw new ServiceException("所属采购商不存在或已被删除");
        }
        if (!"0".equals(buyer.getStatus()))
        {
            throw new ServiceException(String.format("所属采购商[%s]已停用，请先启用", buyer.getBuyerName()));
        }
    }

    /**
     * 校验同级部门下名称不重复（excludeDeptId 用于改名时排除自身）
     */
    private void checkDeptName(String deptName, Long parentId, Long excludeDeptId)
    {
        SysDept dup = deptMapper.checkDeptNameUnique(deptName, parentId);
        if (StringUtils.isNotNull(dup) && (StringUtils.isNull(excludeDeptId)
                || dup.getDeptId().longValue() != excludeDeptId.longValue()))
        {
            throw new ServiceException(String.format("名称[%s]已存在同名部门，请更换站点名称", deptName));
        }
    }

    /**
     * 获取并校验根部门（parent_id=0）
     */
    private SysDept requireRootDept()
    {
        SysDept root = deptMapper.selectDeptById(ROOT_DEPT_ID);
        if (StringUtils.isNull(root) || root.getParentId() == null || root.getParentId().longValue() != 0L)
        {
            throw new ServiceException("未找到根部门（dept_id=100 且 parent_id=0），请检查部门树初始化");
        }
        if (!"0".equals(root.getStatus()))
        {
            throw new ServiceException("根部门处于停用状态，无法新增站点");
        }
        return root;
    }
}
