package com.ruoyi.gas.domain;

/** 站管请求幂等记录，与业务写入同事务提交；失败不留半完成记录。 */
public class GasStationWriteRequest
{
    private Long stationId;
    private Long userId;
    private String operation;
    private String requestId;
    private String payloadHash;
    private Long resultId;

    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public Long getResultId() { return resultId; }
    public void setResultId(Long resultId) { this.resultId = resultId; }
}
